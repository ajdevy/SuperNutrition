import { describe, it, expect } from 'vitest';
import {
  InMemoryRateLimiter,
  InMemoryEffectStore,
  InMemoryIdempotencyStore,
  RedisRateLimiter,
  RedisEffectStore,
  RedisUnavailableError,
  StoreCapacityError,
  IN_MEMORY_STORE_CAPACITY,
  type RedisLike,
} from './stores.js';

/** The two bounds every claim now carries; the default the suite reasons about. */
const TTL = { inFlightSeconds: 60, completedSeconds: 86_400 } as const;
/** One explicit window, when a test is about expiry rather than the split. */
const ttl = (seconds: number) => ({ inFlightSeconds: seconds, completedSeconds: seconds });

const rules = [
  { layer: 'installation' as const, limit: 2, windowSeconds: 60 },
  { layer: 'user' as const, limit: 2, windowSeconds: 60 },
];

describe('sliding-window rate limiting', () => {
  it('allows up to the limit then rejects', async () => {
    const limiter = new InMemoryRateLimiter(rules);
    const key = { layer: 'installation' as const, principal: 'i1' };

    expect((await limiter.check(key)).allowed).toBe(true);
    expect((await limiter.check(key)).allowed).toBe(true);

    const third = await limiter.check(key);
    expect(third.allowed).toBe(false);
    expect(third.remaining).toBe(0);
    expect(third.retryAfterSeconds).toBe(60);
  });

  it('reports decreasing headroom', async () => {
    const limiter = new InMemoryRateLimiter(rules);
    const key = { layer: 'installation' as const, principal: 'i2' };
    expect((await limiter.check(key)).remaining).toBe(1);
    expect((await limiter.check(key)).remaining).toBe(0);
  });

  it('isolates principals', async () => {
    const limiter = new InMemoryRateLimiter(rules);
    await limiter.check({ layer: 'installation', principal: 'a' });
    await limiter.check({ layer: 'installation', principal: 'a' });
    expect((await limiter.check({ layer: 'installation', principal: 'b' })).allowed).toBe(true);
  });

  it('forgets hits once the window passes', async () => {
    let now = 1_000_000;
    const limiter = new InMemoryRateLimiter(rules, () => now);
    const key = { layer: 'installation' as const, principal: 'i3' };
    await limiter.check(key);
    await limiter.check(key);
    expect((await limiter.check(key)).allowed).toBe(false);

    now += 61_000;
    expect((await limiter.check(key)).allowed).toBe(true);
  });

  it('permits an unconfigured layer rather than inventing a limit', async () => {
    const limiter = new InMemoryRateLimiter([]);
    expect((await limiter.check({ layer: 'ip', principal: 'x' })).allowed).toBe(true);
  });
});

/**
 * docs/09 §9.3: the gateway fails CLOSED because it is paid and abusable;
 * product reads fail OPEN because a cache outage should not stop a user seeing
 * yesterday's lunch. One global policy gets one of the two wrong.
 */
describe('failure modes differ by layer', () => {
  const failing: RedisLike = {
    eval: async () => {
      throw new Error('redis down');
    },
    set: async () => {
      throw new Error('redis down');
    },
    get: async () => {
      throw new Error('redis down');
    },
  };

  it('declares fail-closed for gateway layers and fail-open for product reads', () => {
    const limiter = new RedisRateLimiter(failing, rules);
    expect(limiter.failureMode('installation')).toBe('fail-closed');
    expect(limiter.failureMode('ip')).toBe('fail-closed');
    expect(limiter.failureMode('user')).toBe('fail-open');
  });

  it('rejects gateway traffic when the limiter is unavailable', async () => {
    const limiter = new RedisRateLimiter(failing, rules);
    expect((await limiter.check({ layer: 'installation', principal: 'i' })).allowed).toBe(false);
  });

  it('admits product traffic when the limiter is unavailable', async () => {
    const limiter = new RedisRateLimiter(failing, rules);
    expect((await limiter.check({ layer: 'user', principal: 'u' })).allowed).toBe(true);
  });

  it('uses the Lua result when Redis answers', async () => {
    const redis: RedisLike = {
      eval: async () => [1, 7],
      set: async () => 'OK',
      get: async () => null,
    };
    const decision = await new RedisRateLimiter(redis, rules).check({
      layer: 'installation',
      principal: 'i',
    });
    expect(decision).toMatchObject({ allowed: true, remaining: 7, limit: 2 });
  });

  it('honours a Redis rejection', async () => {
    const redis: RedisLike = {
      eval: async () => [0, 0],
      set: async () => 'OK',
      get: async () => null,
    };
    const decision = await new RedisRateLimiter(redis, rules).check({
      layer: 'installation',
      principal: 'i',
    });
    expect(decision.allowed).toBe(false);
    expect(decision.retryAfterSeconds).toBe(60);
  });
});

/** AD-18: a restored app or a retried request must not start a second transfer. */
describe('at-most-once effect claims', () => {
  it('claims once and refuses a duplicate', async () => {
    const store = new InMemoryEffectStore();
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(true);
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(false);
  });

  it('scopes the claim to the attempt and effect pair', async () => {
    const store = new InMemoryEffectStore();
    await store.claimOnce('att_1', 'eff_1', 60);
    expect(await store.claimOnce('att_1', 'eff_2', 60)).toBe(true);
    expect(await store.claimOnce('att_2', 'eff_1', 60)).toBe(true);
  });

  it('expires a claim after its ttl', async () => {
    let now = 1_000;
    const store = new InMemoryEffectStore(() => now);
    await store.claimOnce('att_1', 'eff_1', 1);
    now += 2_000;
    expect(await store.claimOnce('att_1', 'eff_1', 1)).toBe(true);
  });

  it('fails closed when Redis is unavailable', async () => {
    const store = new RedisEffectStore({
      eval: async () => null,
      get: async () => null,
      set: async () => {
        throw new Error('down');
      },
    });
    // An unverifiable claim must not authorize a second transfer. It used to
    // return `false`, which achieved that — but `false` here means "already
    // claimed", so a user making a first, legitimate attempt during an outage
    // was told they had already made it. Still fail-closed (#49), now as an
    // outage rather than as a duplicate.
    await expect(store.claimOnce('att_1', 'eff_1', 60)).rejects.toThrow(RedisUnavailableError);
  });

  it('claims through Redis SET NX', async () => {
    const store = new RedisEffectStore({
      eval: async () => null,
      get: async () => null,
      set: async () => 'OK',
    });
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(true);
  });

  it('treats a null SET reply as already claimed', async () => {
    const store = new RedisEffectStore({
      eval: async () => null,
      get: async () => null,
      set: async () => null,
    });
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(false);
  });
});

describe('idempotency', () => {
  it('replays a completed response for the same digest', async () => {
    const store = new InMemoryIdempotencyStore();
    expect(await store.claim('k', 'digest-1', TTL)).toEqual({ state: 'fresh' });
    await store.complete('k', { ok: true }, TTL);
    expect(await store.claim('k', 'digest-1', TTL)).toEqual({
      state: 'replay',
      response: { ok: true },
    });
  });

  it('conflicts when the same key carries a different request', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL);
    expect(await store.claim('k', 'digest-2', TTL)).toMatchObject({ state: 'conflict' });
  });

  it('conflicts while the first request is still in flight', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL);
    expect(await store.claim('k', 'digest-1', TTL)).toMatchObject({ state: 'conflict' });
  });

  it('allows a retry after a failed attempt released its claim', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL);
    await store.fail('k');
    expect(await store.claim('k', 'digest-1', TTL)).toEqual({ state: 'fresh' });
  });

  it('replays a completed response for the same digest', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL);
    await store.complete('k', { status: 201, body: { id: 'meal-1' } }, TTL);
    expect(await store.claim('k', 'digest-1', TTL)).toEqual({
      state: 'replay',
      response: { status: 201, body: { id: 'meal-1' } },
    });
  });

  it('conflicts when the same key carries a different request', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL);
    await store.complete('k', { status: 201, body: { id: 'meal-1' } }, TTL);
    expect(await store.claim('k', 'digest-2', TTL)).toMatchObject({ state: 'conflict' });
  });

  // A response body that happens to look like an internal marker must still
  // round-trip: "failed" is a separate call, never a shape sniffed out of a
  // stored value.
  it('stores a response whose body contains a failed key', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL);
    await store.complete('k', { status: 201, body: { failed: true } }, TTL);
    expect(await store.claim('k', 'digest-1', TTL)).toEqual({
      state: 'replay',
      response: { status: 201, body: { failed: true } },
    });
  });

  it('treats an expired record as absent, so a replay is not honoured forever', async () => {
    let clock = 1_000;
    const store = new InMemoryIdempotencyStore(() => clock);
    await store.claim('k', 'digest-1', ttl(60));
    // The COMPLETED window is what bounds a replay, so it is the one this test
    // advances past. Completing with the default 24h window would leave the
    // record alive and the assertion would be about nothing.
    await store.complete('k', { status: 201, body: { id: 'meal-1' } }, ttl(60));

    clock += 61_000;
    expect(await store.claim('k', 'digest-1', ttl(60))).toEqual({ state: 'fresh' });
  });

  it('frees an unfinished claim on the short bound, not the replay window (DS-F2)', async () => {
    // A process that died between claim and complete used to hold the key for
    // the whole 24h replay window, because one number served both states. An
    // honest retry then got a conflict about a meal nobody had made, and only
    // reaching into Redis could clear it.
    let clock = 1_000;
    const store = new InMemoryIdempotencyStore(() => clock);
    await store.claim('k', 'digest-1', { inFlightSeconds: 60, completedSeconds: 86_400 });

    clock += 30_000;
    expect(
      await store.claim('k', 'digest-1', { inFlightSeconds: 60, completedSeconds: 86_400 }),
      'a live request must still exclude a second one',
    ).toMatchObject({ state: 'conflict' });

    // Past the in-flight bound but far inside the replay window: nothing was
    // ever completed, so there is nothing to replay and the key is free.
    clock += 31_000;
    expect(
      await store.claim('k', 'digest-1', { inFlightSeconds: 60, completedSeconds: 86_400 }),
      'the key stayed locked for the replay window although nothing was created',
    ).toEqual({ state: 'fresh' });
  });

  it('keeps a COMPLETED record for the replay window, not the short bound', async () => {
    let clock = 1_000;
    const store = new InMemoryIdempotencyStore(() => clock);
    const ttls = { inFlightSeconds: 60, completedSeconds: 86_400 };
    await store.claim('k', 'digest-1', ttls);
    await store.complete('k', { status: 201, body: { id: 'meal-1' } }, ttls);

    // Well past the in-flight bound. A completed record must NOT expire with it,
    // or a retry would create a second meal for a row that exists.
    clock += 120_000;
    expect(await store.claim('k', 'digest-1', ttls)).toEqual({
      state: 'replay',
      response: { status: 201, body: { id: 'meal-1' } },
    });
  });

  it('ignores completion for an unknown key', async () => {
    const store = new InMemoryIdempotencyStore();
    await expect(store.complete('missing', {}, TTL)).resolves.toBeUndefined();
  });
});

/**
 * M1 — the in-memory stores never forgot anything.
 *
 * REDIS_URL is required by the env schema and read into config, but nothing
 * wires RedisRateLimiter or RedisEffectStore: every deployment runs the
 * in-memory implementations. Two consequences. AD-18 at-most-once and AD-21
 * replay detection hold per process, not across replicas or restarts — recorded
 * in docs rather than fixed here, because wiring Redis means adding a client
 * dependency and a connection lifecycle this change is not the place for. And
 * the maps grew without bound: one entry per distinct principal, per claim, per
 * idempotency key, for the life of the process. That half is fixed.
 */
describe('M1 — in-memory stores stay bounded', () => {
  it('forgets a principal once its hits have aged out', async () => {
    let now = 1_000_000;
    const limiter = new InMemoryRateLimiter(
      [{ layer: 'ip', limit: 5, windowSeconds: 60 }],
      () => now,
    );

    for (let i = 0; i < 50; i += 1) {
      await limiter.check({ layer: 'ip', principal: `198.51.100.${i}` });
    }
    expect(limiter.trackedPrincipals).toBe(50);

    // Well past the window, one more call from anybody sweeps the rest.
    now += 61_000;
    await limiter.check({ layer: 'ip', principal: '203.0.113.1' });
    expect(limiter.trackedPrincipals).toBe(1);
  });

  it('drops expired effect claims instead of keeping them forever', async () => {
    let now = 1_000_000;
    const store = new InMemoryEffectStore(() => now);

    for (let i = 0; i < 20; i += 1) {
      await store.claimOnce(`att_${i}`, `eff_${i}`, 60);
    }
    expect(store.trackedClaims).toBe(20);

    now += 61_000;
    await store.claimOnce('att_fresh', 'eff_fresh', 60);
    expect(store.trackedClaims).toBe(1);
  });

  it('still refuses a replayed claim inside its TTL', async () => {
    // The sweep must not become a way to replay: expiry is the only thing that
    // releases a claim.
    let now = 1_000_000;
    const store = new InMemoryEffectStore(() => now);
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(true);
    now += 30_000;
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(false);
  });

  it('drops expired idempotency entries', async () => {
    let now = 1_000_000;
    const store = new InMemoryIdempotencyStore(() => now);

    for (let i = 0; i < 20; i += 1) {
      await store.claim(`user:${i}`, 'digest', ttl(60));
    }
    expect(store.trackedEntries).toBe(20);

    now += 61_000;
    await store.claim('user:fresh', 'digest', ttl(60));
    expect(store.trackedEntries).toBe(1);
  });
});

describe('GW-F3 — in-memory stores are bounded, and refuse rather than evict', () => {
  it('refuses a new claim at capacity instead of dropping a live one', async () => {
    // The replay claim runs before the rate limiter (contract validation_order,
    // owner decision D-02), so unique valid request ids were admitted with no
    // advertised bound at all — 24h of them, at whatever rate a caller managed.
    //
    // The cap is a refusal, not an eviction. These entries ARE the at-most-once
    // guarantee: evicting a live one lets its identifier run a second time,
    // which is a duplicate provider call and a second upload of a user's photo.
    const store = new InMemoryEffectStore(() => 1_000);
    for (let index = 0; index < IN_MEMORY_STORE_CAPACITY; index += 1) {
      await store.claimOnce('att', `eff_${index}`, 3_600);
    }

    await expect(store.claimOnce('att', 'eff_one_too_many', 3_600)).rejects.toThrow(
      StoreCapacityError,
    );
  });

  it('still reports an already-held claim as held when full', async () => {
    // Order matters: were capacity checked first, a full store would answer
    // "capacity" for an id it is already holding — and a client retrying past
    // that would eventually be allowed to run it twice.
    const store = new InMemoryEffectStore(() => 1_000);
    await store.claimOnce('att', 'eff_first', 3_600);
    for (let index = 0; index < IN_MEMORY_STORE_CAPACITY; index += 1) {
      await store.claimOnce('att', `eff_${index}`, 3_600).catch(() => undefined);
    }

    await expect(store.claimOnce('att', 'eff_first', 3_600)).resolves.toBe(false);
  });

  it('reclaims expired entries, so ordinary traffic never reaches the cap', async () => {
    let clock = 1_000;
    const store = new InMemoryEffectStore(() => clock);
    for (let index = 0; index < 5_000; index += 1) {
      await store.claimOnce('att', `eff_${index}`, 60);
    }
    expect(store.trackedClaims).toBeGreaterThan(0);

    clock += 61_000;
    // One claim past the TTL triggers the amortised sweep; the dead entries go.
    await store.claimOnce('att', 'eff_after', 60);
    expect(store.trackedClaims, 'expired claims were never reclaimed').toBeLessThan(5_000);
  });
});

describe('a conflict says which of the three things went wrong', () => {
  it('names a genuinely different request', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL, 'owner');
    const conflict = await store.claim('k', 'digest-2', TTL, 'owner');
    expect(conflict).toEqual({ state: 'conflict', reason: 'different-request' });
  });

  it('names a request that is merely still running', async () => {
    // The old single message told this client it had changed a body it had sent
    // exactly once, sending it to look for a bug it does not have.
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1', TTL, 'owner');
    const conflict = await store.claim('k', 'digest-1', TTL, 'owner');
    expect(conflict).toEqual({ state: 'conflict', reason: 'in-flight' });
  });
});
