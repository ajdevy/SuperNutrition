import { describe, it, expect } from 'vitest';
import {
  InMemoryRateLimiter,
  InMemoryEffectStore,
  InMemoryIdempotencyStore,
  RedisRateLimiter,
  RedisEffectStore,
  type RedisLike,
} from './stores.js';

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
    // An unverifiable claim must not authorize a second transfer.
    expect(await store.claimOnce('att_1', 'eff_1', 60)).toBe(false);
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
    expect(await store.claim('k', 'digest-1')).toEqual({ state: 'fresh' });
    await store.complete('k', { ok: true });
    expect(await store.claim('k', 'digest-1')).toEqual({ state: 'replay', response: { ok: true } });
  });

  it('conflicts when the same key carries a different request', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1');
    expect(await store.claim('k', 'digest-2')).toEqual({ state: 'conflict' });
  });

  it('conflicts while the first request is still in flight', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1');
    expect(await store.claim('k', 'digest-1')).toEqual({ state: 'conflict' });
  });

  it('allows a retry after a failed attempt released its claim', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1');
    await store.fail('k');
    expect(await store.claim('k', 'digest-1')).toEqual({ state: 'fresh' });
  });

  it('replays a completed response for the same digest', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1');
    await store.complete('k', { status: 201, body: { id: 'meal-1' } });
    expect(await store.claim('k', 'digest-1')).toEqual({
      state: 'replay',
      response: { status: 201, body: { id: 'meal-1' } },
    });
  });

  it('conflicts when the same key carries a different request', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1');
    await store.complete('k', { status: 201, body: { id: 'meal-1' } });
    expect(await store.claim('k', 'digest-2')).toEqual({ state: 'conflict' });
  });

  // A response body that happens to look like an internal marker must still
  // round-trip: "failed" is a separate call, never a shape sniffed out of a
  // stored value.
  it('stores a response whose body contains a failed key', async () => {
    const store = new InMemoryIdempotencyStore();
    await store.claim('k', 'digest-1');
    await store.complete('k', { status: 201, body: { failed: true } });
    expect(await store.claim('k', 'digest-1')).toEqual({
      state: 'replay',
      response: { status: 201, body: { failed: true } },
    });
  });

  it('treats an expired record as absent, so a replay is not honoured forever', async () => {
    let clock = 1_000;
    const store = new InMemoryIdempotencyStore(() => clock);
    await store.claim('k', 'digest-1', 60);
    await store.complete('k', { status: 201, body: { id: 'meal-1' } });

    clock += 61_000;
    expect(await store.claim('k', 'digest-1', 60)).toEqual({ state: 'fresh' });
  });

  it('ignores completion for an unknown key', async () => {
    const store = new InMemoryIdempotencyStore();
    await expect(store.complete('missing', {})).resolves.toBeUndefined();
  });
});
