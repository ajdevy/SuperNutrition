import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import {
  RedisAttemptRegistry,
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
