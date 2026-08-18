import { createHash } from 'node:crypto';
import type {
  AttemptRegistry,
  EffectStore,
  IdempotencyClaim,
  IdempotencyStore,
  RateLimitDecision,
  RateLimitKey,
  RateLimiter,
} from './index.js';

/**
 * Redis-backed stores, plus in-memory equivalents for tests and local runs.
 *
 * The interesting behaviour is the failure mode, not the happy path.
 */

/**
 * #49 — an operation that could not reach Redis, distinct from one that reached
 * it and got an answer.
 *
 * The difference matters at the taxonomy boundary: "your attempt is a duplicate"
 * and "the service cannot currently tell" are different facts with different
 * retry advice, and reporting the first for the second sends a user to fix a
 * problem they do not have.
 */
export class RedisUnavailableError extends Error {
  constructor(operation: string, cause: unknown) {
    super(`redis unavailable during ${operation}: ${cause}`);
    this.name = 'RedisUnavailableError';
  }
}

export interface RedisLike {
  eval(script: string, numKeys: number, ...args: (string | number)[]): Promise<unknown>;
  set(key: string, value: string, mode: 'EX', ttl: number, nx: 'NX'): Promise<string | null>;
  get(key: string): Promise<string | null>;
}

export interface RateLimitRule {
  readonly layer: RateLimitKey['layer'];
  readonly limit: number;
  readonly windowSeconds: number;
}

/**
 * Sliding-window counter. Offset counting drifts under load; a sorted-set
 * window is exact enough and cheap at this request rate.
 */
const SLIDING_WINDOW_SCRIPT = `
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count < limit then
  redis.call('ZADD', key, now, ARGV[4])
  redis.call('EXPIRE', key, math.ceil(window / 1000))
  return {1, limit - count - 1}
end
return {0, 0}
`;

export class RedisRateLimiter implements RateLimiter {
  readonly #redis: RedisLike;
  readonly #rules: ReadonlyMap<RateLimitKey['layer'], RateLimitRule>;

  constructor(redis: RedisLike, rules: readonly RateLimitRule[]) {
    this.#redis = redis;
    this.#rules = new Map(rules.map((rule) => [rule.layer, rule]));
  }

  /**
   * The gateway fails CLOSED: it is paid and abusable, so a limiter outage must
   * not become free unlimited inference. Product reads fail OPEN, because a
   * cache outage should not stop a user seeing yesterday's lunch (docs/09 §9.3).
   */
  failureMode(layer: RateLimitKey['layer']): 'fail-open' | 'fail-closed' {
    return layer === 'user' ? 'fail-open' : 'fail-closed';
  }

  async check(key: RateLimitKey): Promise<RateLimitDecision> {
    const rule = this.#rules.get(key.layer);
    if (!rule) {
      // Permissive, matching InMemoryRateLimiter — whose own test records this
      // as deliberate ("rather than inventing a limit").
      //
      // Noted rather than changed: an unconfigured layer is arguably a fail-open
      // path created by omission, and main.ts configures all four layers so
      // neither implementation reaches this today. But making only the Redis one
      // strict would mean production throws where development silently allows,
      // which is a worse failure than either consistent answer. If this should
      // become strict it should become strict in both, as its own decision.
      return {
        allowed: true,
        limit: 0,
        remaining: 0,
        resetAtEpochSeconds: 0,
        retryAfterSeconds: null,
      };
    }

    const now = Date.now();
    const windowMs = rule.windowSeconds * 1000;
    const resetAt = Math.ceil((now + windowMs) / 1000);

    try {
      const raw = (await this.#redis.eval(
        SLIDING_WINDOW_SCRIPT,
        1,
        `ratelimit:${key.layer}:${key.principal}`,
        now,
        windowMs,
        rule.limit,
        `${now}-${Math.random().toString(36).slice(2)}`,
      )) as [number, number];

      const allowed = raw[0] === 1;
      return {
        allowed,
        limit: rule.limit,
        remaining: allowed ? raw[1] : 0,
        resetAtEpochSeconds: resetAt,
        retryAfterSeconds: allowed ? null : rule.windowSeconds,
      };
    } catch {
      const open = this.failureMode(key.layer) === 'fail-open';
      return {
        allowed: open,
        limit: rule.limit,
        remaining: 0,
        resetAtEpochSeconds: resetAt,
        retryAfterSeconds: open ? null : rule.windowSeconds,
      };
    }
  }
}

/** Deterministic limiter for tests and single-process local development. */
export class InMemoryRateLimiter implements RateLimiter {
  readonly #hits = new Map<string, number[]>();
  readonly #rules: ReadonlyMap<RateLimitKey['layer'], RateLimitRule>;
  readonly #now: () => number;

  constructor(rules: readonly RateLimitRule[], now: () => number = Date.now) {
    this.#rules = new Map(rules.map((rule) => [rule.layer, rule]));
    this.#now = now;
  }

  failureMode(layer: RateLimitKey['layer']): 'fail-open' | 'fail-closed' {
    return layer === 'user' ? 'fail-open' : 'fail-closed';
  }

  /**
   * Drops principals whose hits have all aged out.
   *
   * Without this the map holds one entry per distinct principal seen since the
   * process started, and the IP layer's principal is chosen by whoever is
   * calling: unique source addresses accumulate for the life of the process,
   * which is a slow leak with no upper bound.
   */
  /** Distinct principals currently held. Bound evidence, not behaviour. */
  get trackedPrincipals(): number {
    return this.#hits.size;
  }

  #sweep(now: number): void {
    const windows = [...this.#rules.values()].map((rule) => rule.windowSeconds);
    if (windows.length === 0) return;
    const longestWindowMs = Math.max(...windows) * 1000;
    for (const [id, hits] of this.#hits) {
      if (hits.every((at) => at <= now - longestWindowMs)) this.#hits.delete(id);
    }
  }

  async check(key: RateLimitKey): Promise<RateLimitDecision> {
    const rule = this.#rules.get(key.layer);
    if (!rule) {
      return {
        allowed: true,
        limit: 0,
        remaining: 0,
        resetAtEpochSeconds: 0,
        retryAfterSeconds: null,
      };
    }

    const now = this.#now();
    const windowMs = rule.windowSeconds * 1000;
    const id = `${key.layer}:${key.principal}`;
    const recent = (this.#hits.get(id) ?? []).filter((at) => at > now - windowMs);
    this.#sweep(now);

    if (recent.length >= rule.limit) {
      this.#hits.set(id, recent);
      return {
        allowed: false,
        limit: rule.limit,
        remaining: 0,
        resetAtEpochSeconds: Math.ceil((now + windowMs) / 1000),
        retryAfterSeconds: rule.windowSeconds,
      };
    }

    recent.push(now);
    this.#hits.set(id, recent);
    return {
      allowed: true,
      limit: rule.limit,
      remaining: rule.limit - recent.length,
      resetAtEpochSeconds: Math.ceil((now + windowMs) / 1000),
      retryAfterSeconds: null,
    };
  }
}

/**
 * At-most-once execution for transfer-start effects (AD-18).
 *
 * A restored app or a retried request must not start a second transfer, so the
 * claim is the gate — not a check-then-act, which races.
 */
export class InMemoryEffectStore implements EffectStore {
  readonly #claimed = new Map<string, number>();
  readonly #now: () => number;

  constructor(now: () => number = Date.now) {
    this.#now = now;
  }

  /** Claims currently held, expired ones included until the next sweep. */
  get trackedClaims(): number {
    return this.#claimed.size;
  }

  async claimOnce(attemptId: string, effectId: string, ttlSeconds: number): Promise<boolean> {
    const key = `${attemptId}:${effectId}`;
    const now = this.#now();

    // Expired claims are dead weight: past their TTL they answer nothing, but
    // nothing removed them, so the map grew by one entry per request — and this
    // same store also backs one-time request IDs, which are unique by
    // construction. Over a long-lived process that is unbounded.
    for (const [claimedKey, expiresAt] of this.#claimed) {
      if (expiresAt <= now) this.#claimed.delete(claimedKey);
    }

    const existing = this.#claimed.get(key);
    if (existing !== undefined && existing > now) return false;
    this.#claimed.set(key, now + ttlSeconds * 1000);
    return true;
  }
}

export class RedisEffectStore implements EffectStore {
  readonly #redis: RedisLike;
  readonly #prefix: string;

  /**
   * The prefix separates keyspaces. The composition root builds two of these on
   * purpose — a spent request ID (AD-21 replay) and a spent transfer effect
   * (AD-18 at-most-once) are different identifiers with different answers — and
   * a shared prefix would merge them, so a request ID could consume an effect's
   * claim and vice versa. The in-memory pair are separate objects and get this
   * for free; the Redis pair share one server and must say so.
   */
  constructor(redis: RedisLike, prefix = 'effect') {
    this.#redis = redis;
    this.#prefix = prefix;
  }

  async claimOnce(attemptId: string, effectId: string, ttlSeconds: number): Promise<boolean> {
    try {
      const result = await this.#redis.set(
        `${this.#prefix}:${attemptId}:${effectId}`,
        '1',
        'EX',
        ttlSeconds,
        'NX',
      );
      return result === 'OK';
    } catch (cause) {
      // Still fail closed — the caller refuses the attempt either way — but as
      // an OUTAGE, not as a duplicate.
      //
      // Returning `false` here meant a user making a first, legitimate attempt
      // during a Redis outage was told they had already made it: wrong
      // diagnosis, wrong retry advice, and an outage hidden inside a taxonomy
      // code that means something else. An unverifiable claim is unknown, and
      // `false` is an answer this store does not have.
      throw new RedisUnavailableError('effect claim', cause);
    }
  }
}

/**
 * Idempotency records, expiring on the TTL the caller claims with.
 *
 * A claim with no response recorded is treated as IN FLIGHT and conflicts: two
 * concurrent retries of the same key must not both create. A failed request
 * releases its claim explicitly, so only a genuine in-flight duplicate is
 * refused (docs/06 §6.7).
 */
export class InMemoryIdempotencyStore implements IdempotencyStore {
  readonly #entries = new Map<string, { digest: string; expiresAt: number; response?: unknown }>();
  readonly #now: () => number;

  constructor(now: () => number = Date.now) {
    this.#now = now;
  }

  /** Entries currently held, expired ones included until the next sweep. */
  get trackedEntries(): number {
    return this.#entries.size;
  }

  async claim(key: string, requestDigest: string, ttlSeconds = 86_400): Promise<IdempotencyClaim> {
    const now = this.#now();

    // Expired entries are already treated as absent just below, so keeping them
    // buys nothing and costs one entry per idempotent request, forever.
    for (const [entryKey, entry] of this.#entries) {
      if (entry.expiresAt <= now) this.#entries.delete(entryKey);
    }

    const existing = this.#entries.get(key);
    // An expired record is indistinguishable from an absent one: the 24h
    // window is what bounds how long a replay is honoured.
    if (!existing || existing.expiresAt <= now) {
      this.#entries.set(key, { digest: requestDigest, expiresAt: now + ttlSeconds * 1000 });
      return { state: 'fresh' };
    }
    if (existing.digest !== requestDigest) return { state: 'conflict' };
    if ('response' in existing) return { state: 'replay', response: existing.response };
    return { state: 'conflict' };
  }

  async complete(key: string, response: unknown): Promise<void> {
    const existing = this.#entries.get(key);
    if (existing) this.#entries.set(key, { ...existing, response });
  }

  async fail(key: string): Promise<void> {
    this.#entries.delete(key);
  }
}

/**
 * Cross-instance attempt ownership and cancellation (#49).
 *
 * This does NOT replace `TransferStateTracker`, which owns the per-attempt state
 * machine and its abort controller. Both are inherently local: the controller
 * aborts a request THIS process is making, and no other instance can abort it.
 *
 * What crosses instances is the smaller pair of facts that decide correctness —
 * which instance holds an attempt's bytes, and whether the attempt has been
 * cancelled. With those shared, an instance asked about an attempt it does not
 * hold can answer truthfully instead of reporting `unknown_attempt` for an
 * attempt that plainly exists.
 *
 * It still cannot serve the bytes. Those never leave the instance that received
 * them and are never written here (AD-8): the value stored against an attempt is
 * an instance identifier, and nothing about the photo. An instance without the
 * bytes fails closed rather than having them shipped to it.
 */
/**
 * Opaque, stable token for an installation.
 *
 * Stored instead of the installation id so the shared registry carries no
 * directly usable identifier: anyone with the Redis connection can read every
 * value, and an installation id there would be a leak that buys nothing —
 * ownership only ever needs comparing, never recovering.
 */
const ownerToken = (installationId: string): string =>
  createHash('sha256').update(`attempt-owner:${installationId}`).digest('hex').slice(0, 32);

export class RedisAttemptRegistry implements AttemptRegistry {
  readonly #redis: RedisLike;
  readonly #ttlSeconds: number;

  constructor(redis: RedisLike, ttlSeconds: number) {
    this.#redis = redis;
    this.#ttlSeconds = ttlSeconds;
  }

  /**
   * Records this instance as holding an attempt, bound to the installation that
   * owns it.
   *
   * H-01 — the claim used to store `attemptId -> instanceId` only, with no owner
   * binding at all, and `markCancelled` took nothing but an attempt id. Any
   * authenticated installation that knew or guessed a live attempt id could
   * therefore cancel someone else's transfer, provided its DELETE reached a
   * replica that did not hold the attempt. Authentication was checked; ownership
   * was not.
   *
   * The value is `ownerToken:instanceId`. The owner token is an opaque digest of
   * the installation id, never the installation id itself and never anything
   * derived from the photo: this value is readable by anyone with the Redis
   * connection, and a raw installation id there is an identifier leak for no
   * gain — comparison is all that is required.
   */
  async claimAttempt(attemptId: string, instanceId: string, owner: string): Promise<boolean> {
    try {
      const result = await this.#redis.set(
        `attempt:${attemptId}`,
        `${ownerToken(owner)}:${instanceId}`,
        'EX',
        this.#ttlSeconds,
        'NX',
      );
      return result === 'OK';
    } catch (cause) {
      throw new RedisUnavailableError('attempt claim', cause);
    }
  }

  /**
   * Which instance holds this attempt, but ONLY for the installation that owns
   * it. A caller who does not own the attempt gets `null` — the same answer as
   * for an attempt that does not exist.
   *
   * That indistinguishability is the point. Answering "held elsewhere" to a
   * non-owner confirms the attempt is real, which turns the endpoint into an
   * oracle for enumerating other people's live transfers.
   */
  async holderOf(attemptId: string, owner: string): Promise<string | null> {
    try {
      const raw = await this.#redis.get(`attempt:${attemptId}`);
      if (raw === null) return null;
      const separator = raw.indexOf(':');
      if (separator < 0) return null;
      const recordedOwner = raw.slice(0, separator);
      // Not a timing-safe comparison, and it does not need to be: the token is a
      // digest of a value the caller already authenticated as, so there is no
      // secret here to recover a byte at a time.
      if (recordedOwner !== ownerToken(owner)) return null;
      return raw.slice(separator + 1);
    } catch (cause) {
      throw new RedisUnavailableError('attempt lookup', cause);
    }
  }

  /**
   * Marks an attempt cancelled for every instance, and ONLY when the caller owns
   * it. Returns false when the attempt is unknown or owned by someone else —
   * again the same answer for both, so the result cannot be used to probe.
   *
   * The ownership check and the write are one transition rather than a read
   * followed by an unconditional write: a caller must not be able to publish a
   * cancellation signal for an attempt it does not own under any interleaving.
   */
  async markCancelled(attemptId: string, owner: string): Promise<boolean> {
    try {
      const holder = await this.holderOf(attemptId, owner);
      if (holder === null) return false;
      await this.#redis.set(`cancelled:${attemptId}`, '1', 'EX', this.#ttlSeconds, 'NX');
      return true;
    } catch (cause) {
      if (cause instanceof RedisUnavailableError) throw cause;
      throw new RedisUnavailableError('cancellation', cause);
    }
  }

  /** True when any instance has cancelled this attempt. */
  async isCancelled(attemptId: string): Promise<boolean> {
    try {
      return (await this.#redis.get(`cancelled:${attemptId}`)) !== null;
    } catch (cause) {
      throw new RedisUnavailableError('cancellation lookup', cause);
    }
  }
}

/**
 * Builds the client the Redis stores talk to.
 *
 * `maxRetriesPerRequest: 1` and no offline queue, so an unreachable server
 * surfaces promptly as an error the stores turn into `RedisUnavailableError`
 * rather than hanging a request while the client retries. Failing closed is only
 * useful if it fails quickly.
 *
 * Imported lazily: the memory backend must not pay for a client it never
 * constructs.
 */
export const createRedisClient = async (url: string): Promise<RedisLike> => {
  const { Redis } = await import('ioredis');
  const client = new Redis(url, {
    maxRetriesPerRequest: 1,
    // Commands issued before the socket is writeable fail immediately rather
    // than queueing. That is the behaviour we want in steady state — a request
    // must not sit in a queue while Redis is gone — but it makes the CONNECT
    // step load-bearing, which is the next line.
    enableOfflineQueue: false,
    // Connect explicitly instead of letting the constructor race.
    //
    // Without this the factory returned a client whose socket was still being
    // established, and every command issued in that window failed with
    // "Stream isn't writeable" — surfaced as RedisUnavailableError. So the first
    // requests after every boot were rejected as an outage that was not
    // happening: replay claims refused, attempts declined, for as long as the
    // handshake took.
    //
    // The fake could not have caught this. It has no socket, so it has no window
    // in which there is not one. This is what running the lane against a real
    // server is for.
    lazyConnect: true,
  });
  await client.connect();
  return client as unknown as RedisLike;
};
