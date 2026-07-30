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

  async claimOnce(attemptId: string, effectId: string, ttlSeconds: number): Promise<boolean> {
    const key = `${attemptId}:${effectId}`;
    const now = this.#now();
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

export class InMemoryIdempotencyStore implements IdempotencyStore {
  readonly #entries = new Map<string, { digest: string; response?: unknown }>();

  async claim(key: string, requestDigest: string): Promise<IdempotencyClaim> {
    const existing = this.#entries.get(key);
    if (!existing) {
      this.#entries.set(key, { digest: requestDigest });
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
}
