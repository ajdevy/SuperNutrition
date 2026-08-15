import { describe, it, expect } from 'vitest';
import {
  RedisAttemptRegistry,
  RedisEffectStore,
  RedisRateLimiter,
  RedisUnavailableError,
  type RedisLike,
} from './stores.js';

/**
 * #49 — cross-instance coordination.
 *
 * The Redis stores already existed and were never composed: `main.ts` built the
 * in-memory ones and `REDIS_URL` was read by nothing. So the gap was partly
 * wiring and partly two failure modes that answered a question they could not
 * answer.
 *
 * **What these tests do NOT establish.** Story 4.4 criterion 3 wants the
 * concurrent-claim property demonstrated against a REAL Redis. The fake below
 * implements the command semantics faithfully, but a fake cannot prove the
 * server is atomic under genuine concurrency — that is the whole property. No
 * `redis-server` exists in this environment, so criterion 3 is UNVERIFIED and is
 * reported as such. `redis-integration.test.ts` holds that lane.
 */

/** A Redis whose commands behave as the real server documents them. */
class FakeRedis implements RedisLike {
  readonly store = new Map<string, { value: string; expiresAt: number }>();
  readonly zsets = new Map<string, { score: number; member: string }[]>();
  now = 1_000_000;
  /** Every value written, so a test can prove what was and was not stored. */
  readonly written: string[] = [];

  #live(key: string) {
    const entry = this.store.get(key);
    if (!entry) return undefined;
    if (entry.expiresAt <= this.now) {
      this.store.delete(key);
      return undefined;
    }
    return entry;
  }

  async set(key: string, value: string, _mode: 'EX', ttl: number, _nx: 'NX') {
    if (this.#live(key)) return null;
    this.store.set(key, { value, expiresAt: this.now + ttl * 1000 });
    this.written.push(value);
    return 'OK';
  }

  async get(key: string) {
    return this.#live(key)?.value ?? null;
  }

  /** Enough of the sliding-window script's behaviour to decide the same way. */
  async eval(_script: string, _numKeys: number, ...args: (string | number)[]) {
    const [key, now, window, limit, member] = args as [string, number, number, number, string];
    const entries = (this.zsets.get(key) ?? []).filter((e) => e.score > now - window);
    if (entries.length < limit) {
      entries.push({ score: now, member });
      this.zsets.set(key, entries);
      this.written.push(member);
      return [1, limit - entries.length];
    }
    this.zsets.set(key, entries);
    return [0, 0];
  }
}

/** A Redis that is down. Every command rejects. */
const unreachable = (): RedisLike => ({
  eval: async () => {
    throw new Error('ECONNREFUSED');
  },
  set: async () => {
    throw new Error('ECONNREFUSED');
  },
  get: async () => {
    throw new Error('ECONNREFUSED');
  },
});

describe('effect claims are at-most-once across instances (AD-18)', () => {
  it('lets exactly one of two instances claim the same effect', async () => {
    // The property the story exists for: per-process stores let both claim, and
    // one photo reached a provider twice.
    const redis = new FakeRedis();
    const instanceA = new RedisEffectStore(redis);
    const instanceB = new RedisEffectStore(redis);

    const results = await Promise.all([
      instanceA.claimOnce('att_1', 'eff_1', 60),
      instanceB.claimOnce('att_1', 'eff_1', 60),
    ]);

    expect(results.filter(Boolean)).toHaveLength(1);
  });

  it('releases the claim once its TTL passes', async () => {
    const redis = new FakeRedis();
    const store = new RedisEffectStore(redis);

    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(true);
    redis.now += 61_000;
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(true);
  });

  it('raises an outage rather than reporting a duplicate it cannot see', async () => {
    // This returned `false` on any error. That refuses the attempt, which is
    // fail-closed in effect — but it refuses it as a DUPLICATE EFFECT, so a
    // user making a first, legitimate attempt during a Redis outage was told
    // they had already made it. Wrong diagnosis, wrong retry advice, and it
    // hides an outage inside a taxonomy code that means something else.
    const store = new RedisEffectStore(unreachable());
    await expect(store.claimOnce('att_1', 'eff_1', 60)).rejects.toThrow(RedisUnavailableError);
  });
});

describe('rate limits are shared, not multiplied by the replica count', () => {
  const rules = [{ layer: 'installation' as const, limit: 2, windowSeconds: 60 }];

  it('counts hits from every instance against one limit', async () => {
    const redis = new FakeRedis();
    const a = new RedisRateLimiter(redis, rules);
    const b = new RedisRateLimiter(redis, rules);
    const key = { layer: 'installation' as const, principal: 'inst_1' };

    expect((await a.check(key)).allowed).toBe(true);
    expect((await b.check(key)).allowed).toBe(true);
    // Third hit against a limit of two, whichever instance served the first two.
    expect((await a.check(key)).allowed).toBe(false);
  });

  it('fails closed for the gateway layers when Redis is unreachable', async () => {
    const limiter = new RedisRateLimiter(unreachable(), rules);
    const decision = await limiter.check({ layer: 'installation', principal: 'inst_1' });
    expect(decision.allowed).toBe(false);
  });

  it('still fails open for the product read layer, as documented', async () => {
    // A cache outage should not stop a user seeing yesterday's lunch. This
    // predates the story and is deliberate; asserted so it stays deliberate.
    const limiter = new RedisRateLimiter(unreachable(), [
      { layer: 'user', limit: 5, windowSeconds: 60 },
    ]);
    const decision = await limiter.check({ layer: 'user', principal: 'user_1' });
    expect(decision.allowed).toBe(true);
  });

  it('treats an unconfigured layer exactly as the in-memory limiter does', async () => {
    // Both permit. That is arguably a fail-open path created by omission, but it
    // is pre-existing, deliberate, and documented in the in-memory limiter's own
    // test. Asserted here so the two cannot silently diverge — production
    // throwing where development allows would be worse than either answer.
    const redisDecision = await new RedisRateLimiter(new FakeRedis(), rules).check({
      layer: 'ip',
      principal: '127.0.0.1',
    });
    expect(redisDecision.allowed).toBe(true);
  });
});

describe('attempt ownership and cancellation cross instances', () => {
  it('lets exactly one instance hold an attempt', async () => {
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);

    expect(await registry.claimAttempt('att_1', 'instance-a')).toBe(true);
    expect(await registry.claimAttempt('att_1', 'instance-b')).toBe(false);
  });

  it('makes a cancellation on one instance visible to another', async () => {
    // The gap that made cancellation answer for the wrong instance, or report
    // `unknown_attempt` for an attempt that exists.
    const redis = new FakeRedis();
    const a = new RedisAttemptRegistry(redis, 3600);
    const b = new RedisAttemptRegistry(redis, 3600);

    expect(await b.isCancelled('att_1')).toBe(false);
    await a.markCancelled('att_1');
    expect(await b.isCancelled('att_1')).toBe(true);
  });

  it('names the instance holding an attempt, so another can refuse honestly', async () => {
    // The bytes never travel. An instance that does not hold them fails closed
    // rather than having them shipped to it (AD-8).
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);
    await registry.claimAttempt('att_1', 'instance-a');
    expect(await registry.holderOf('att_1')).toBe('instance-a');
    expect(await registry.holderOf('att_unknown')).toBeNull();
  });

  it('fails closed when Redis is unreachable', async () => {
    const registry = new RedisAttemptRegistry(unreachable(), 3600);
    await expect(registry.claimAttempt('att_1', 'a')).rejects.toThrow(RedisUnavailableError);
    await expect(registry.isCancelled('att_1')).rejects.toThrow(RedisUnavailableError);
    await expect(registry.holderOf('att_1')).rejects.toThrow(RedisUnavailableError);
  });
});

describe('AD-8 — no image bytes reach Redis', () => {
  it('writes only counters, markers and instance identifiers', async () => {
    // Asserted, not promised in prose. If a future change starts writing
    // anything payload-derived, this fails.
    const redis = new FakeRedis();
    const effects = new RedisEffectStore(redis);
    const registry = new RedisAttemptRegistry(redis, 3600);
    const limiter = new RedisRateLimiter(redis, [
      { layer: 'installation', limit: 5, windowSeconds: 60 },
    ]);

    await effects.claimOnce('att_1', 'eff_1', 60);
    await registry.claimAttempt('att_1', 'instance-a');
    await registry.markCancelled('att_1');
    await limiter.check({ layer: 'installation', principal: 'inst_1' });

    for (const value of redis.written) {
      expect(value.length).toBeLessThan(64);
    }
    expect(redis.written.some((v) => v.startsWith('sha256:'))).toBe(false);
  });
});
