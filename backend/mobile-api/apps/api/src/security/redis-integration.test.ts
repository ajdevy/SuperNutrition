import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import {
  RedisAttemptRegistry,
  RedisIdempotencyStore,
  RedisEffectStore,
  RedisRateLimiter,
  createRedisClient,
  type RedisLike,
} from './stores.js';

/**
 * #49 story 4.4, criterion 3 — the concurrent-claim property against a REAL
 * Redis, not a fake.
 *
 * A fake proves the adapter uses the commands correctly. It cannot prove the
 * server is atomic under genuine concurrency, which is the property the whole
 * design rests on: `SET NX` deciding exactly one winner between two processes
 * racing for the same identifier. Only a real server can answer that.
 *
 * ## This lane is SKIPPED unless `REDIS_TEST_URL` is set
 *
 * Skipped loudly, never silently — a skipped criterion reported as green is the
 * same defect as a fence reported as enforcement (P-01). When it skips, story
 * 4.4 criterion 3 is UNVERIFIED and must be reported that way.
 *
 * Run it with a disposable server:
 *
 *   docker run --rm -p 6380:6379 redis:7-alpine
 *   REDIS_TEST_URL=redis://127.0.0.1:6380 npm test -- redis-integration
 *
 * The keys it writes are namespaced and short-lived, but point it at a
 * disposable instance regardless: it is a test, and a test does not get to
 * assume which database it is talking to.
 */

const url = process.env['REDIS_TEST_URL'];

/** The same factory the composition root uses, so this exercises real wiring. */
const connect = async (target: string): Promise<{ client: RedisLike; close: () => void }> => {
  const client = await createRedisClient(target);
  return {
    client,
    close: () => {
      void (client as unknown as { quit: () => Promise<unknown> }).quit();
    },
  };
};

describe.skipIf(!url)('against a real Redis server', () => {
  let a: Awaited<ReturnType<typeof connect>>;
  let b: Awaited<ReturnType<typeof connect>>;
  // Every run uses its own identifiers so a re-run cannot pass on a previous
  // run's keys, and two runs cannot interfere.
  const run = process.hrtime.bigint().toString(36);

  beforeAll(async () => {
    // Two connections, standing in for two instances.
    a = await connect(url!);
    b = await connect(url!);
  });

  afterAll(() => {
    a?.close();
    b?.close();
  });

  it('decides exactly one winner when instances claim concurrently', async () => {
    const effectsA = new RedisEffectStore(a.client);
    const effectsB = new RedisEffectStore(b.client);
    const attemptId = `att_${run}`;

    // Twenty claimants, alternating between two connections, all fired before
    // any resolves. Two racers is a race a broken implementation can win by
    // luck; twenty is not. Sequential calls would pass even against a
    // non-atomic implementation, which is why none of these is awaited early.
    const claimants = Array.from({ length: 20 }, (_, i) =>
      (i % 2 === 0 ? effectsA : effectsB).claimOnce(attemptId, 'eff_1', 60),
    );
    const results = await Promise.all(claimants);

    expect(results.filter(Boolean)).toHaveLength(1);
  });

  it('keeps the replay and effect keyspaces isolated', async () => {
    // AD-21 replay IDs and AD-18 transfer effects share one Redis. The
    // composition root builds two stores with different prefixes for exactly
    // this reason: without them, spending a request ID would consume the
    // matching transfer effect's claim, and one identifier would answer for
    // two different questions.
    const effects = new RedisEffectStore(a.client, 'effect');
    const requestIds = new RedisEffectStore(b.client, 'request-id');
    const shared = `att_ns_${run}`;

    expect(await effects.claimOnce(shared, 'id_1', 60)).toBe(true);
    // Same attempt, same identifier, other keyspace: must still be claimable.
    expect(await requestIds.claimOnce(shared, 'id_1', 60)).toBe(true);
    // And each is now spent within its own keyspace only.
    expect(await effects.claimOnce(shared, 'id_1', 60)).toBe(false);
    expect(await requestIds.claimOnce(shared, 'id_1', 60)).toBe(false);
  });

  it('shares rate-limit decisions across instances', async () => {
    const rules = [{ layer: 'installation' as const, limit: 2, windowSeconds: 60 }];
    const limiterA = new RedisRateLimiter(a.client, rules);
    const limiterB = new RedisRateLimiter(b.client, rules);
    const key = { layer: 'installation' as const, principal: `inst_${run}` };

    expect((await limiterA.check(key)).allowed).toBe(true);
    expect((await limiterB.check(key)).allowed).toBe(true);
    expect((await limiterA.check(key)).allowed).toBe(false);
  });

  it('makes an owner cancellation on one instance visible to the other', async () => {
    const registryA = new RedisAttemptRegistry(a.client, 60);
    const registryB = new RedisAttemptRegistry(b.client, 60);
    const attemptId = `att_cancel_${run}`;
    const owner = `inst_owner_${run}`;

    await registryA.claimAttempt(attemptId, 'instance-a', owner);
    expect(await registryB.isCancelled(attemptId)).toBe(false);
    expect(await registryA.markCancelled(attemptId, owner)).toBe(true);
    expect(await registryB.isCancelled(attemptId)).toBe(true);
  });

  it("DS-F19: a lapsed predecessor cannot release its successor's claim", async () => {
    // Against the real server, because the guard IS the Lua. The fake in
    // redis-idempotency.test.ts reimplements these semantics in TypeScript, so
    // a test there passes whatever the script says — it exercises the stand-in,
    // not the thing that ships. Only this lane can fail when the Lua is wrong.
    const rows = new Map<string, unknown>();
    const store = new RedisIdempotencyStore(a.client, {
      describe: (response: unknown) => {
        const body = (response as { body?: { id?: string } })?.body;
        const status = (response as { status?: number })?.status;
        if (typeof body?.id !== 'string' || typeof status !== 'number') return null;
        rows.set(body.id, response);
        return { status, ref: body.id };
      },
      rehydrate: async (ref: string) =>
        (rows.get(ref) as { body?: unknown } | undefined)?.body ?? null,
    });
    const key = `f19-release-${run}`;
    const digest = 'a'.repeat(64);
    // One second in flight: long enough to claim against, short enough to lapse
    // inside a test.
    const bounds = { inFlightSeconds: 1, completedSeconds: 60 };

    const first = await store.claim(key, digest, bounds, 'owner');
    expect(first.state).toBe('fresh');

    // The first request is still running when its bound lapses on the server.
    await new Promise((resolve) => setTimeout(resolve, 1_400));

    const successor = await store.claim(key, digest, bounds, 'owner');
    expect(successor.state, 'the successor could not claim the lapsed key').toBe('fresh');

    // The predecessor finally gives up and releases — a claim that is no longer
    // its own.
    await store.fail(key, first.state === 'fresh' ? first.token : '');

    expect(
      (await store.claim(key, digest, bounds, 'owner')).state,
      "the predecessor deleted its successor's live claim",
    ).toBe('conflict');
  }, 20_000);

  it('DS-F19: a lapsed predecessor cannot stamp its result over a successor', async () => {
    const rows = new Map<string, unknown>();
    const store = new RedisIdempotencyStore(a.client, {
      describe: (response: unknown) => {
        const body = (response as { body?: { id?: string } })?.body;
        const status = (response as { status?: number })?.status;
        if (typeof body?.id !== 'string' || typeof status !== 'number') return null;
        rows.set(body.id, response);
        return { status, ref: body.id };
      },
      rehydrate: async (ref: string) =>
        (rows.get(ref) as { body?: unknown } | undefined)?.body ?? null,
    });
    const key = `f19-complete-${run}`;
    const digest = 'b'.repeat(64);
    const bounds = { inFlightSeconds: 1, completedSeconds: 60 };

    const first = await store.claim(key, digest, bounds, 'owner');
    await new Promise((resolve) => setTimeout(resolve, 1_400));
    const successor = await store.claim(key, digest, bounds, 'owner');
    expect(successor.state).toBe('fresh');

    await store.complete(
      key,
      { status: 201, body: { id: '55555555-5555-4555-8555-555555555555' } },
      bounds,
      first.state === 'fresh' ? first.token : '',
    );

    expect(
      (await store.claim(key, digest, bounds, 'owner')).state,
      'the predecessor stamped a done record over a successor still creating',
    ).toBe('conflict');
  }, 20_000);

  const idempotencyStore = (client: (typeof a)['client']) => {
    const rows = new Map<string, unknown>();
    return new RedisIdempotencyStore(client, {
      describe: (response: unknown) => {
        const body = (response as { body?: { id?: string } })?.body;
        const status = (response as { status?: number })?.status;
        if (typeof body?.id !== 'string' || typeof status !== 'number') return null;
        rows.set(body.id, response);
        return { status, ref: body.id };
      },
      rehydrate: async (ref: string) =>
        (rows.get(ref) as { body?: unknown } | undefined)?.body ?? null,
    });
  };

  it('replays the status that was stored, not 201', async () => {
    // Nothing completed with a non-201 anywhere, so re-hardcoding 201 in
    // `claim` passed every lane. A replay's whole job is reproducing the
    // original response, and the status is half of it.
    const store = idempotencyStore(a.client);
    const key = `status-${run}`;
    const bounds = { inFlightSeconds: 60, completedSeconds: 60 };
    const digest = 'c'.repeat(64);

    const claim = await store.claim(key, digest, bounds, 'owner');
    await store.complete(
      key,
      { status: 202, body: { id: '66666666-6666-4666-8666-666666666666' } },
      bounds,
      claim.state === 'fresh' ? claim.token : '',
    );

    const replay = await store.claim(key, digest, bounds, 'owner');
    expect(replay.state).toBe('replay');
    expect(
      replay.state === 'replay' ? (replay.response as { status: number }).status : 0,
      'a completed 202 replayed as something else',
    ).toBe(202);
  }, 20_000);

  it('gives an in-flight claim the short bound and a completed one the replay window', async () => {
    // The 60/86400 split was pinned on the in-memory store only, so a
    // Redis-only regression to a single uniform TTL passed everything.
    const store = idempotencyStore(a.client);
    const key = `split-${run}`;
    const bounds = { inFlightSeconds: 30, completedSeconds: 600 };
    const digest = 'd'.repeat(64);
    const ttlOf = (k: string) =>
      (a.client as unknown as { ttl: (key: string) => Promise<number> }).ttl(`idem:${k}`);

    const claim = await store.claim(key, digest, bounds, 'owner');
    const inFlight = await ttlOf(key);
    expect(inFlight, 'an unfinished claim took more than the in-flight bound').toBeLessThanOrEqual(
      30,
    );
    expect(inFlight).toBeGreaterThan(0);

    await store.complete(
      key,
      { status: 201, body: { id: '77777777-7777-4777-8777-777777777777' } },
      bounds,
      claim.state === 'fresh' ? claim.token : '',
    );

    const completed = await ttlOf(key);
    expect(completed, 'a completed record was left on the short bound').toBeGreaterThan(30);
    expect(completed).toBeLessThanOrEqual(600);
  }, 20_000);

  it('bounds the cancellation marker to the attempt it describes', async () => {
    // The Lua copies the attempt key's REMAINING TTL onto the marker, and no
    // lane asserted it — the bound could have been removed and every suite
    // stayed green. Attempt ids are client-chosen, so a marker outliving its
    // attempt answers "cancelled" about a later, unrelated request under a
    // reused id.
    const registry = new RedisAttemptRegistry(a.client, 3_600);
    const attemptId = `att_marker_ttl_${run}`;
    const owner = `inst_owner_${run}`;

    await registry.claimAttempt(attemptId, 'instance-a', owner);
    // Shrink the attempt's own window, so a marker taking the registry's 3600s
    // default instead of the remaining TTL is visibly wrong.
    await (a.client as unknown as { expire: (k: string, s: number) => Promise<number> }).expire(
      `attempt:${attemptId}`,
      5,
    );
    expect(await registry.markCancelled(attemptId, owner)).toBe(true);

    const markerTtl = await (a.client as unknown as { ttl: (k: string) => Promise<number> }).ttl(
      `cancelled:${attemptId}`,
    );

    expect(markerTtl, 'the marker has no expiry at all').toBeGreaterThan(0);
    expect(markerTtl, 'the marker outlives the attempt it describes').toBeLessThanOrEqual(5);
  }, 20_000);

  it('clamps the cancellation marker when the attempt key reports no expiry', async () => {
    // The 0/-1 edge of the TTL bound, which no lane touched: reverting the
    // clamp changed nothing in the suite. `TTL` answers -1 for a key with no
    // expiry, and the old code read that as "use the full window" — handing the
    // marker a lifetime far past the attempt, which is the staleness the bound
    // exists to prevent.
    //
    // PERSIST rather than a race against a sub-second expiry: `TTL` rounds up,
    // so 0 cannot be produced reliably, while -1 is exact.
    const registry = new RedisAttemptRegistry(a.client, 3_600);
    const attemptId = `att_persist_${run}`;
    const owner = `inst_owner_${run}`;
    const raw = a.client as unknown as {
      persist: (key: string) => Promise<number>;
      ttl: (key: string) => Promise<number>;
    };

    await registry.claimAttempt(attemptId, 'instance-a', owner);
    await raw.persist(`attempt:${attemptId}`);
    expect(await raw.ttl(`attempt:${attemptId}`), 'the attempt key still has an expiry').toBe(-1);

    expect(await registry.markCancelled(attemptId, owner)).toBe(true);

    const markerTtl = await raw.ttl(`cancelled:${attemptId}`);
    expect(markerTtl, 'the marker was left without an expiry').toBeGreaterThan(0);
    expect(markerTtl, 'the marker took the full window from an unbounded attempt').toBeLessThan(
      3_600,
    );
  }, 20_000);

  it('DS-F12: a lost reply does not lock the attempt id for a day', async () => {
    // The scenario the fake cannot stage: the SET applies on the server and the
    // client never learns it did — a dropped reply, a socket reset, a timeout
    // that fires after the write landed. The caller retries the identical claim.
    //
    // That used to answer `attempt_already_used` for 24 hours about an attempt
    // that had sent nothing, and the client had no way to distinguish it from a
    // genuine collision.
    const registry = new RedisAttemptRegistry(a.client, 60);
    const attemptId = `att_reentrant_${run}`;
    const owner = `inst_owner_${run}`;

    expect(await registry.claimAttempt(attemptId, 'instance-a', owner)).toBe(true);
    // The retry, byte-identical, as a client re-sending after no answer.
    expect(
      await registry.claimAttempt(attemptId, 'instance-a', owner),
      'the holder was refused its own attempt against a real server',
    ).toBe(true);

    // Still exactly one holder, and still the right one.
    expect(await registry.holderOf(attemptId, owner)).toBe('instance-a');
  });

  it('DS-F12: re-entrancy does not extend to another replica or another owner', async () => {
    // The half that must NOT move. A second replica claiming the same id is the
    // duplicate provider call this registry exists to stop, and a different
    // installation on the same box is someone else's transfer.
    const registryA = new RedisAttemptRegistry(a.client, 60);
    const registryB = new RedisAttemptRegistry(b.client, 60);
    const attemptId = `att_reentrant_guard_${run}`;
    const owner = `inst_owner_${run}`;
    const stranger = `inst_stranger_${run}`;

    expect(await registryA.claimAttempt(attemptId, 'instance-a', owner)).toBe(true);
    expect(
      await registryB.claimAttempt(attemptId, 'instance-b', owner),
      'a second replica was allowed to claim a held attempt',
    ).toBe(false);
    expect(
      await registryA.claimAttempt(attemptId, 'instance-a', stranger),
      'a different installation was allowed to claim a held attempt',
    ).toBe(false);
  });

  it('H-01: refuses a foreign cancellation against a real server', async () => {
    // The security defect, against Redis rather than a fake: a registry with no
    // owner binding let any authenticated installation cancel any attempt id it
    // could reach through a non-holding replica.
    const registryA = new RedisAttemptRegistry(a.client, 60);
    const registryB = new RedisAttemptRegistry(b.client, 60);
    const attemptId = `att_foreign_${run}`;
    const owner = `inst_owner_${run}`;
    const stranger = `inst_stranger_${run}`;

    await registryA.claimAttempt(attemptId, 'instance-a', owner);

    expect(await registryB.markCancelled(attemptId, stranger)).toBe(false);
    expect(await registryA.isCancelled(attemptId)).toBe(false);
    // And the stranger cannot tell it apart from an id that was never used.
    expect(await registryB.holderOf(attemptId, stranger)).toBeNull();
    expect(await registryB.holderOf(`att_never_${run}`, stranger)).toBeNull();
  });

  it('expires keys rather than holding them past their window', async () => {
    const effects = new RedisEffectStore(a.client);
    const attemptId = `att_ttl_${run}`;

    expect(await effects.claimOnce(attemptId, 'eff_1', 1)).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, 1200));
    expect(await effects.claimOnce(attemptId, 'eff_1', 1)).toBe(true);
  });

  it('stores an instance identifier against an attempt, never image bytes', async () => {
    const registry = new RedisAttemptRegistry(a.client, 60);
    const attemptId = `att_bytes_${run}`;
    await registry.claimAttempt(attemptId, 'instance-a', `inst_owner_${run}`);

    expect(await registry.holderOf(attemptId, `inst_owner_${run}`)).toBe('instance-a');
  });
});

describe.skipIf(!!url)('real-Redis lane', () => {
  it('is skipped, and story 4.4 criterion 3 is therefore unverified', () => {
    // Present so the skip is visible in the report rather than an absence
    // nobody notices. This test asserts the state of the evidence, not the code.
    expect(url).toBeUndefined();
  });
});

describe('an unreachable Redis is refused at connect time', () => {
  it('rejects rather than returning a client that will fail every command', async () => {
    // #49 fail-closed, at the boundary that matters most: boot.
    //
    // `createRedisClient` now awaits the connection, so a deployment configured
    // for the redis backend against a Redis that is not there fails to build the
    // application at all — non-zero exit — instead of starting, serving, and
    // refusing every request as a spurious outage.
    //
    // Port 1 is reserved and nothing listens on it.
    const { createRedisClient: connectTo } = await import('./stores.js');
    await expect(connectTo('redis://127.0.0.1:1')).rejects.toThrow();
  }, 20_000);
});
