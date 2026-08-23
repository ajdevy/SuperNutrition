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
 * server is atomic under genuine concurrency — that is the whole property, and
 * it is `redis-integration.test.ts` that establishes it. That lane has since
 * been run against Redis 8.10.0 and criterion 3 is met; it found a connect race
 * no fake could have, which is the argument for keeping the distinction sharp.
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

  async set(key: string, value: string, _mode: 'EX', ttl: number, nx?: 'NX') {
    // NX honoured, not assumed: refusing every write to a live key makes the
    // fake stricter than the server and hides unconditional writes entirely.
    if (nx === 'NX' && this.#live(key)) return null;
    this.store.set(key, { value, expiresAt: this.now + ttl * 1000 });
    this.written.push(value);
    return 'OK';
  }

  async get(key: string) {
    return this.#live(key)?.value ?? null;
  }

  // Required by `RedisLike`, and absent until the suite was typechecked. A fake
  // that does not implement the port cannot stand in for it — the compiler was
  // simply never asked.
  async del(key: string) {
    return this.store.delete(key) ? 1 : 0;
  }

  /**
   * Dispatches on the script, because there is now more than one.
   *
   * A fake that ignored the script and always ran the rate-limit branch answered
   * the cancellation script with a rate-limit verdict — `[1, n]`, never the `1`
   * the caller compares against — so an owner cancellation silently reported
   * "not owned".
   */
  async eval(script: string, _numKeys: number, ...args: (string | number)[]) {
    if (script.includes("redis.call('TTL', KEYS[1])")) {
      const [attemptKey, cancelledKey, token, fallback] = args as [string, string, string, number];
      const holder = this.#live(attemptKey);
      if (!holder) return 0;
      const separator = holder.value.indexOf(':');
      if (separator < 0 || holder.value.slice(0, separator) !== token) return 0;
      // The attempt key's REMAINING window, so the marker cannot outlive it.
      const remaining = Math.max(0, holder.expiresAt - this.now);
      const expiresAt = this.now + (remaining > 0 ? remaining : fallback * 1000);
      this.store.set(cancelledKey, { value: '1', expiresAt });
      this.written.push('1');
      return 1;
    }

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
  del: async () => {
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
  const OWNER = 'inst_owner';
  const STRANGER = 'inst_stranger';

  it('lets exactly one instance hold an attempt', async () => {
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);

    expect(await registry.claimAttempt('att_1', 'instance-a', OWNER)).toBe(true);
    expect(await registry.claimAttempt('att_1', 'instance-b', OWNER)).toBe(false);
  });

  it('lets the SAME owner on the SAME instance re-claim its own attempt (DS-F12)', async () => {
    // A bare `SET NX` answers "taken" without ever looking at who took it, so a
    // reply lost after the SET applied — or a retry after a crash on the same
    // process — was refused as `attempt_already_used` for 24 hours, about an
    // attempt that had sent nothing and belonged to the caller asking.
    //
    // The existing test pins only the different-instance refusal, which is why
    // this went unnoticed: the two cases look identical from outside and are
    // opposite in meaning.
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);

    expect(await registry.claimAttempt('att_1', 'instance-a', OWNER)).toBe(true);
    expect(
      await registry.claimAttempt('att_1', 'instance-a', OWNER),
      'the holder was refused its own attempt',
    ).toBe(true);
  });

  it('still refuses a different owner on the same instance', async () => {
    // Re-entrancy must not become "anyone on this instance". The instance is
    // shared infrastructure; the owner is the authorisation.
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);

    expect(await registry.claimAttempt('att_1', 'instance-a', OWNER)).toBe(true);
    expect(await registry.claimAttempt('att_1', 'instance-a', STRANGER)).toBe(false);
  });

  it('makes an owner cancellation on one instance visible to another', async () => {
    const redis = new FakeRedis();
    const a = new RedisAttemptRegistry(redis, 3600);
    const b = new RedisAttemptRegistry(redis, 3600);

    await a.claimAttempt('att_1', 'instance-a', OWNER);
    expect(await b.isCancelled('att_1')).toBe(false);
    expect(await a.markCancelled('att_1', OWNER)).toBe(true);
    expect(await b.isCancelled('att_1')).toBe(true);
  });

  it('names the holding instance to the owner', async () => {
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);
    await registry.claimAttempt('att_1', 'instance-a', OWNER);
    expect(await registry.holderOf('att_1', OWNER)).toBe('instance-a');
  });

  it('H-01: refuses to cancel an attempt owned by someone else', async () => {
    // The security defect. The registry stored attemptId -> instanceId with no
    // owner binding and markCancelled took only an attempt id, so any
    // authenticated installation that knew or guessed a live attempt id could
    // cancel another installation's transfer — provided its DELETE reached a
    // replica that did not hold the attempt, where the local ownership check
    // never ran.
    const redis = new FakeRedis();
    const registry = new RedisAttemptRegistry(redis, 3600);
    await registry.claimAttempt('att_victim', 'instance-a', OWNER);

    expect(await registry.markCancelled('att_victim', STRANGER)).toBe(false);
    // And nothing was published: the owner's attempt runs on untouched.
    expect(await registry.isCancelled('att_victim')).toBe(false);
  });

  it('H-01: tells a stranger nothing about whether an attempt exists', async () => {
    // Unknown and foreign must be indistinguishable, or the endpoint becomes an
    // oracle for enumerating other people's live transfers.
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);
    await registry.claimAttempt('att_real', 'instance-a', OWNER);

    const foreign = await registry.holderOf('att_real', STRANGER);
    const unknown = await registry.holderOf('att_never_existed', STRANGER);
    expect(foreign).toBe(unknown);
    expect(foreign).toBeNull();
  });

  it('H-01: stores an owner token, never the installation id itself', async () => {
    // The value is readable by anyone with the Redis connection. Ownership only
    // ever needs comparing, so storing the raw id would be a leak for no gain.
    const redis = new FakeRedis();
    const registry = new RedisAttemptRegistry(redis, 3600);
    await registry.claimAttempt('att_1', 'instance-a', OWNER);

    for (const value of redis.written) {
      expect(value).not.toContain(OWNER);
    }
  });

  it('is idempotent for the owner', async () => {
    const registry = new RedisAttemptRegistry(new FakeRedis(), 3600);
    await registry.claimAttempt('att_1', 'instance-a', OWNER);
    expect(await registry.markCancelled('att_1', OWNER)).toBe(true);
    expect(await registry.markCancelled('att_1', OWNER)).toBe(true);
  });

  it('fails closed when Redis is unreachable', async () => {
    const registry = new RedisAttemptRegistry(unreachable(), 3600);
    await expect(registry.claimAttempt('att_1', 'a', OWNER)).rejects.toThrow(RedisUnavailableError);
    await expect(registry.isCancelled('att_1')).rejects.toThrow(RedisUnavailableError);
    await expect(registry.holderOf('att_1', OWNER)).rejects.toThrow(RedisUnavailableError);
    await expect(registry.markCancelled('att_1', OWNER)).rejects.toThrow(RedisUnavailableError);
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
    await registry.claimAttempt('att_1', 'instance-a', 'inst_owner');
    // The owner was omitted here, and the suite was not typechecked so nothing
    // objected. At runtime `owner` was `undefined`, its token hashed the string
    // "undefined", the ownership check never matched — and no cancellation
    // marker was written at all. This loop then inspected every keyspace EXCEPT
    // the one the line was added to cover.
    expect(
      await registry.markCancelled('att_1', 'inst_owner'),
      'the cancellation was refused, so its keyspace is not covered below',
    ).toBe(true);
    await limiter.check({ layer: 'installation', principal: 'inst_1' });

    // Four stores wrote; a scan over an empty list would pass by inspecting
    // nothing, which is how the missing marker went unnoticed.
    expect(
      redis.written.length,
      'nothing was written, so this asserts nothing',
    ).toBeGreaterThanOrEqual(4);
    for (const value of redis.written) {
      expect(value.length).toBeLessThan(64);
    }
    expect(redis.written.some((v) => v.startsWith('sha256:'))).toBe(false);
  });
});
