import type {
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

  constructor(redis: RedisLike) {
    this.#redis = redis;
  }

  async claimOnce(attemptId: string, effectId: string, ttlSeconds: number): Promise<boolean> {
    try {
      const result = await this.#redis.set(
        `effect:${attemptId}:${effectId}`,
        '1',
        'EX',
        ttlSeconds,
        'NX',
      );
      return result === 'OK';
    } catch {
      // Fail closed: an unverifiable claim must not authorize a second transfer.
      return false;
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
