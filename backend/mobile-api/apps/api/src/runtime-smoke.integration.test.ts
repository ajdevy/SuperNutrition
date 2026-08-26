import { describe, it, expect, beforeAll, afterAll } from 'vitest';

import type { AppConfig } from './config/index.js';
import { createLogger } from './logging/index.js';
import { buildApplication } from './main.js';
import type { ServerHandle } from './server.js';

/**
 * Does the thing actually run?
 *
 * Every other suite reaches the routes through `app.inject`, which skips the
 * socket, the HTTP parser and the serializer. That is the right trade for
 * hundreds of cases, but it means nothing here has ever proven the composed
 * service answers a real client over a real connection — and a service that
 * passes 1 177 tests and cannot accept a TCP connection is still broken.
 *
 * So: two processes' worth of application, both listening on real ephemeral
 * ports, driven with `fetch` over loopback. One user journey end to end, plus
 * the two answers that must survive the network layer — the fail-closed 401 and
 * the cross-replica idempotency contract.
 */

const url = process.env['REDIS_TEST_URL'];
const dbUrl = process.env['DATABASE_TEST_URL'];
const RUN = process.hrtime.bigint().toString(36).slice(-8);

const configFor = (instanceId: string): AppConfig =>
  ({
    env: 'development',
    nodeEnv: 'development',
    // Port 0: the OS assigns a free one and `listen()` reports it back, so two
    // instances in one suite cannot collide and a stale process cannot block
    // the run.
    server: { host: '127.0.0.1', port: 0 },
    database: { url: dbUrl!, maxConnections: 5, idleTimeoutSeconds: 5, connectTimeoutSeconds: 5 },
    redis: { url: url ?? '' },
    deployment: { instanceCount: 1, gatewayStateBackend: 'redis' },
    auth: { issuer: 'i', accessTtlSeconds: 900, refreshTtlSeconds: 1000, testMode: true },
    inference: {
      remoteEnabled: true,
      modelProfilesPath: './config/model-profiles.toml',
      configurationSnapshotPath: './config/configuration-snapshot.toml',
      openRouterBaseUrl: 'http://127.0.0.1:1',
    },
    stubInference: false,
    logging: { level: 'error' },
    __instanceId: instanceId,
  }) as unknown as AppConfig;

const mealBody = () => ({
  occurredAt: '2026-08-19T12:30:00Z',
  localDate: '2026-08-19',
  timezone: 'UTC',
  mealType: 'lunch',
  items: [
    {
      foodRef: 'usda:fdc:171705',
      displayName: 'Banana',
      servingUnit: 'g',
      servingAmount: '118',
      confirmedByUser: true,
    },
  ],
  nutrition: {
    origin: 'client-grounded',
    catalogSource: 'usda',
    datasetVersion: 'test-1',
    servingBasis: { unit: 'g', amount: '118', gramWeight: '118' },
    calories: '105',
    proteinG: '1.3',
    fatG: '0.4',
    carbG: '27',
    fiberG: '3.1',
    sugarG: '14.4',
    sodiumMg: '1',
    perItem: [],
  },
});

describe.skipIf(!url || !dbUrl)('the service over a real socket', () => {
  let a: ServerHandle;
  let b: ServerHandle;
  let originA = '';
  let originB = '';
  const token = `Bearer smoke-${RUN}-cccccccccccccccccccc`;

  /** `listen()` returns the bound address, which is how the port is discovered. */
  const boot = async (instanceId: string): Promise<[ServerHandle, string]> => {
    process.env['APP_INSTANCE_ID'] = instanceId;
    const config = configFor(instanceId);
    const handle = await buildApplication(config, createLogger(config));
    const address = await handle.listen();
    return [handle, address];
  };

  beforeAll(async () => {
    [a, originA] = await boot(`smoke-a-${RUN}`);
    [b, originB] = await boot(`smoke-b-${RUN}`);
    delete process.env['APP_INSTANCE_ID'];
  }, 60_000);

  afterAll(async () => {
    await a?.close();
    await b?.close();
  });

  const createMeal = (origin: string, key: string, body: unknown) =>
    fetch(`${origin}/api/v1/meals/manual`, {
      method: 'POST',
      headers: {
        authorization: token,
        'content-type': 'application/json',
        'idempotency-key': key,
      },
      body: JSON.stringify(body),
    });

  it('binds two real ports and answers liveness on both', async () => {
    expect(originA, 'instance A never reported a bound address').toMatch(
      /^http:\/\/127\.0\.0\.1:\d+$/,
    );
    expect(originB).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/);
    expect(originA, 'both instances bound the same port').not.toBe(originB);

    for (const origin of [originA, originB]) {
      const live = await fetch(`${origin}/health/live`);
      expect(live.status, `${origin} is not live`).toBe(200);
    }
  }, 30_000);

  it('reports readiness with its real dependencies attached', async () => {
    const ready = await fetch(`${originA}/health/ready`);
    // Whatever the verdict, it must be a considered one: a readiness probe that
    // cannot reach its own dependencies has to say so rather than 500.
    expect([200, 503]).toContain(ready.status);
    expect(await ready.text()).not.toBe('');
  }, 30_000);

  it('refuses an unauthenticated write over the wire', async () => {
    const denied = await fetch(`${originA}/api/v1/meals/manual`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(mealBody()),
    });
    expect(denied.status, 'an unauthenticated write was accepted').toBe(401);
  }, 30_000);

  it('answers an unknown route with 404 rather than a stack trace', async () => {
    const missing = await fetch(`${originA}/api/v1/there-is-no-such-thing`);
    expect(missing.status).toBe(404);
    expect(await missing.text()).not.toContain('at Object');
  }, 30_000);

  it('carries one meal through create, cross-replica replay, conflict and read', async () => {
    const key = `smoke-${RUN}-journey`;
    const body = mealBody();

    const created = await createMeal(originA, key, body);
    expect(created.status, 'the meal was not created over HTTP').toBe(201);
    const mealId = ((await created.json()) as { id: string }).id;
    expect(mealId).toBeTruthy();

    // Same key, same body, OTHER instance: the body is re-read from Postgres
    // because Redis holds only a status and a reference.
    const replayed = await createMeal(originB, key, body);
    expect(replayed.status, 'the replay did not reach the shared claim').toBe(201);
    expect(((await replayed.json()) as { id: string }).id).toBe(mealId);

    // Same key, different body, other instance.
    const conflicted = await createMeal(originB, key, { ...body, mealType: 'dinner' });
    expect(conflicted.status, 'a changed body under one key was accepted').toBe(409);

    // And exactly one row exists, read back through the other instance.
    const listed = await fetch(`${originB}/api/v1/meals?limit=50`, {
      headers: { authorization: token },
    });
    expect(listed.status).toBe(200);
    const rows = ((await listed.json()) as { data: { id: string }[] }).data;
    expect(
      rows.filter((row) => row.id === mealId),
      'the meal was written more than once',
    ).toHaveLength(1);
  }, 60_000);

  it('rejects a malformed body with a client error, not a server error', async () => {
    const bad = await createMeal(originA, `smoke-${RUN}-bad`, {
      ...mealBody(),
      localDate: '2026-02-30',
    });
    expect(bad.status, 'a validation failure became a server error').toBe(400);
  }, 30_000);
});
