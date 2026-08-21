import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createHash } from 'node:crypto';
import { createServer, type Server } from 'node:http';
import { buildApplication } from './main.js';
import { createLogger } from './logging/index.js';
import type { AppConfig } from './config/index.js';
import type { ServerHandle } from './server.js';
import { COORDINATION_TTL_CEILING_SECONDS } from './security/retention.js';

/**
 * handover-6 — TWO REAL APPLICATION INSTANCES against ONE REAL REDIS.
 *
 * Everything before this exercised either the registry primitives against a real
 * server, or the composed route against an in-memory fake. Neither answers the
 * question the distributed findings are actually about: what happens when a
 * request for an attempt held by instance A arrives at instance B.
 *
 * So both instances here are built by `buildApplication` — the production
 * composition root, not a harness — and driven through their real HTTP routes.
 * The provider is a local server this test controls, so a call can be held open
 * and the abort observed at the socket rather than inferred.
 *
 * SKIPPED unless `REDIS_TEST_URL` is set, loudly, for the same reason the other
 * Redis lane is: a skipped distributed test reported as green is worse than one
 * that never existed.
 */

const url = process.env['REDIS_TEST_URL'];
const dbUrl = process.env['DATABASE_TEST_URL'];

const JPEG = Buffer.from([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
  0x00, 0x01, 0x00, 0x00, 0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03, 0x01, 0x22,
  0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
]);
const digest = `sha256:${createHash('sha256').update(JPEG).digest('hex')}`;

/**
 * Identifiers unique to THIS run, not just this process.
 *
 * A per-process counter collides with keys a previous run left in the same
 * Redis — claims outlive the process by design — so a re-run against a
 * non-disposable server failed on leftovers rather than on the code. Found by
 * re-running after a mutation check, which is exactly the situation that makes
 * an order-dependent test worse than no test.
 */
const RUN = process.hrtime.bigint().toString(36).slice(-8);
let seq = 0;
const nextId = (prefix: string) =>
  `${prefix}_${RUN}${String(seq++).padStart(4, '0')}${'0'.repeat(20 - RUN.length)}`;
/**
 * Two distinct installations, unique to THIS run: the token's first 32 chars
 * become the installation id.
 *
 * Per-run rather than fixed, for the same reason the ids are. A fixed principal
 * shares the installation rate limit across every run against the same Redis —
 * 20 per hour — so the lane passed once and then failed on its own history.
 * Found the same way as the id collision: by re-running it.
 */
const OWNER_TOKEN = `Bearer owner-${RUN}-aaaaaaaaaaaaaaaaaaaaaaaa`;
const STRANGER_TOKEN = `Bearer stranger-${RUN}-bbbbbbbbbbbbbbbbbbbb`;

interface HeldCall {
  release: () => void;
  aborted: Promise<void>;
}

/**
 * A provider that answers when told to, and reports when the client hangs up.
 *
 * The point of H-02 is that the gateway ABORTS the transport, so the assertion
 * has to be made at the socket. A fake orchestrator can only report that it was
 * asked to stop.
 */
class ControllableProvider {
  #server: Server | null = null;
  #port = 0;
  readonly calls: HeldCall[] = [];
  hold = false;

  async start(): Promise<string> {
    this.#server = createServer((req, res) => {
      const aborted = new Promise<void>((resolve) => {
        req.on('aborted', () => resolve());
        req.on('close', () => {
          if (!res.writableEnded) resolve();
        });
      });
      const respond = () => {
        if (res.headersSent || res.writableEnded) return;
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(
          JSON.stringify({
            choices: [{ message: { content: '{"candidates":[]}' } }],
            model: 'test-model',
          }),
        );
      };
      this.calls.push({ release: respond, aborted });
      if (!this.hold) respond();
    });
    await new Promise<void>((resolve) => this.#server!.listen(0, '127.0.0.1', resolve));
    const address = this.#server!.address();
    this.#port = typeof address === 'object' && address ? address.port : 0;
    return `http://127.0.0.1:${this.#port}`;
  }

  async stop(): Promise<void> {
    for (const call of this.calls) call.release();
    await new Promise<void>((resolve) => this.#server?.close(() => resolve()));
  }
}

const configFor = (instanceId: string, providerBaseUrl: string): AppConfig =>
  ({
    env: 'development',
    nodeEnv: 'development',
    server: { host: '127.0.0.1', port: 0 },
    database: {
      url: 'postgres://user:pass@127.0.0.1:5432/none',
      maxConnections: 1,
      idleTimeoutSeconds: 1,
      connectTimeoutSeconds: 1,
    },
    redis: { url: url ?? '' },
    // Each process declares one instance, which is what a real deployment does.
    // The fence permits that; what it refuses is a process claiming two.
    deployment: { instanceCount: 1, gatewayStateBackend: 'redis' },
    auth: { issuer: 'i', accessTtlSeconds: 900, refreshTtlSeconds: 1000, testMode: true },
    inference: {
      remoteEnabled: true,
      modelProfilesPath: './config/model-profiles.toml',
      configurationSnapshotPath: './config/configuration-snapshot.toml',
      openRouterBaseUrl: providerBaseUrl,
    },
    stubInference: false,
    logging: { level: 'error' },
    __instanceId: instanceId,
  }) as unknown as AppConfig;

const contextFor = (attemptId: string, effectId: string) => ({
  contractVersion: 'mobile-api/v1',
  attemptId,
  effectId,
  modality: 'photo',
  claimedMarketJurisdiction: 'ZZ',
  consentReceipt: {
    version: '1',
    attemptId,
    digest,
    modality: 'photo',
    purpose: 'food-identification',
    decision: 'granted',
    marketJurisdiction: 'ZZ',
    processingRegion: 'fixture-region',
    consentCopyVersion: 'photo-consent/fixture-1',
    retentionVersion: 'retention/2',
    retentionDays: 0,
    configurationVersion: 'cfg/2026-07-26.4',
    recordedAt: new Date(Date.now() - 60_000).toISOString(),
    expiresAt: new Date(Date.now() + 600_000).toISOString(),
  },
  inferenceContext: {
    catalogCountry: 'ZZ',
    mealType: 'lunch',
    locale: 'en-GB',
    measurementSystem: 'metric',
    configurationVersion: 'cfg/2026-07-26.4',
  },
});

const multipart = (ctx: unknown, token: string) => {
  const boundary = '----two-instance';
  const head = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="context"\r\n\r\n${JSON.stringify(ctx)}\r\n` +
      `--${boundary}\r\nContent-Disposition: form-data; name="payload"; filename="a.jpg"\r\n` +
      `Content-Type: image/jpeg\r\n\r\n`,
  );
  return {
    payload: Buffer.concat([head, JPEG, Buffer.from(`\r\n--${boundary}--\r\n`)]),
    headers: {
      'content-type': `multipart/form-data; boundary=${boundary}`,
      authorization: token,
      'x-contract-version': 'mobile-api/v1',
      'x-request-id': nextId('req'),
    },
  };
};

/** Reads the shared state directly, so assertions do not infer it from behaviour. */
const redisGet = async (key: string): Promise<string | null> => {
  const { Redis } = await import('ioredis');
  const client = new Redis(url!, { maxRetriesPerRequest: 1 });
  try {
    return await client.get(key);
  } finally {
    void client.quit();
  }
};

/** TTL in seconds for a key: -1 means "no expiry", -2 means "gone". */
const redisTtl = async (key: string): Promise<number> => {
  const { Redis } = await import('ioredis');
  const client = new Redis(url!, { maxRetriesPerRequest: 1 });
  try {
    return await client.ttl(key);
  } finally {
    void client.quit();
  }
};

const redisKeys = async (): Promise<string[]> => {
  const { Redis } = await import('ioredis');
  const client = new Redis(url!, { maxRetriesPerRequest: 1 });
  try {
    return await client.keys('*');
  } finally {
    void client.quit();
  }
};

/**
 * The keys a single action writes, and only those.
 *
 * Three tests here scan Redis to prove what is and is not stored, and all three
 * originally swept the whole keyspace. That store is shared with every other
 * suite and outlives the run: the scans picked up an installation rate-limit
 * key an earlier run had left behind, and keys another test file had just
 * written. Both failures were real keys with nothing to do with the assertion
 * being made.
 */
const keysWrittenBy = async <T>(action: () => Promise<T>): Promise<[T, string[]]> => {
  const before = new Set(await redisKeys());
  const result = await action();
  const written = (await redisKeys()).filter((key) => !before.has(key));
  return [result, written];
};

/**
 * Every value under a key, whatever its type.
 *
 * The rate limiter uses a sorted set, so a blanket GET fails with WRONGTYPE —
 * and a byte-leak assertion that silently skipped the keys it could not read
 * would be worse than none.
 */
const redisValues = async (key: string): Promise<string[]> => {
  const { Redis } = await import('ioredis');
  const client = new Redis(url!, { maxRetriesPerRequest: 1 });
  try {
    const type = await client.type(key);
    if (type === 'string') return [(await client.get(key)) ?? ''];
    if (type === 'zset') return await client.zrange(key, 0, -1);
    if (type === 'list') return await client.lrange(key, 0, -1);
    if (type === 'set') return await client.smembers(key);
    if (type === 'hash') return Object.values(await client.hgetall(key));
    throw new Error(`unhandled redis type ${type} for ${key}`);
  } finally {
    void client.quit();
  }
};

describe.skipIf(!url)('two application instances sharing one Redis', () => {
  let provider: ControllableProvider;
  let a: ServerHandle;
  let b: ServerHandle;

  beforeAll(async () => {
    process.env['OPENROUTER_API_KEY'] = 'test-key-not-a-real-credential';
    provider = new ControllableProvider();
    const base = await provider.start();
    const configA = configFor('instance-a', base);
    const configB = configFor('instance-b', base);
    process.env['APP_INSTANCE_ID'] = 'instance-a';
    a = await buildApplication(configA, createLogger(configA));
    process.env['APP_INSTANCE_ID'] = 'instance-b';
    b = await buildApplication(configB, createLogger(configB));
  }, 30_000);

  afterAll(async () => {
    await a?.close();
    await b?.close();
    await provider?.stop();
    delete process.env['APP_INSTANCE_ID'];
    delete process.env['OPENROUTER_API_KEY'];
  });

  it('H-01: a stranger cancelling through the other replica cannot touch the attempt', async () => {
    // The security defect, end to end. Installation A starts an attempt that
    // instance A holds; installation B sends DELETE to instance B, which does
    // not hold it and so never runs the local ownership check.
    const attemptId = nextId('att');
    const body = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    provider.hold = false;
    const started = await a.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: body.payload,
      headers: body.headers,
    });
    expect(started.statusCode).toBe(200);

    const foreign = await b.app.inject({
      method: 'DELETE',
      url: `/gw/v1/inference/${attemptId}`,
      headers: { authorization: STRANGER_TOKEN },
    });

    // Indistinguishable from an attempt that never existed.
    const unknown = await b.app.inject({
      method: 'DELETE',
      url: `/gw/v1/inference/${nextId('att')}`,
      headers: { authorization: STRANGER_TOKEN },
    });
    expect(foreign.statusCode).toBe(unknown.statusCode);
    expect(JSON.parse(foreign.body).error?.gatewayCode).toBe(
      JSON.parse(unknown.body).error?.gatewayCode,
    );

    // And the owner's own record is untouched: still cancellable BY THE OWNER.
    const byOwner = await a.app.inject({
      method: 'DELETE',
      url: `/gw/v1/inference/${attemptId}`,
      headers: { authorization: OWNER_TOKEN },
    });
    expect(byOwner.statusCode).toBe(202);
  }, 30_000);

  it('H-01: writes no shared cancellation marker for a foreign DELETE', async () => {
    // The response being indistinguishable is necessary but not sufficient: the
    // finding is that a stranger could publish a marker the holder would act on.
    // This reads the shared state directly rather than inferring it.
    const attemptId = nextId('att');
    const body = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    provider.hold = false;
    await a.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: body.payload,
      headers: body.headers,
    });

    await b.app.inject({
      method: 'DELETE',
      url: `/gw/v1/inference/${attemptId}`,
      headers: { authorization: STRANGER_TOKEN },
    });

    const marker = await redisGet(`cancelled:${attemptId}`);
    expect(marker, 'a stranger published a cancellation marker').toBeNull();
  }, 30_000);

  it('H-01: no key written by a real request carries payload bytes', async () => {
    // Asserted against the REAL server and the REAL request flow, not a fake.
    // The fake-based test proves the adapters do not write bytes; this proves
    // nothing else in the request path does either.
    const attemptId = nextId('att');
    const body = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    provider.hold = false;
    const [, keys] = await keysWrittenBy(() =>
      a.app.inject({
        method: 'POST',
        url: '/gw/v1/inference/photo',
        payload: body.payload,
        headers: body.headers,
      }),
    );

    const jpegHead = JPEG.subarray(0, 8).toString('base64');
    expect(keys.length, 'the request wrote nothing to Redis at all').toBeGreaterThan(0);
    for (const key of keys) {
      for (const value of await redisValues(key)) {
        // No raw bytes, no base64 of them, and no payload digest.
        expect(value).not.toContain(jpegHead);
        expect(value).not.toContain(digest);
        expect(value.length, `key ${key} holds a suspiciously large value`).toBeLessThan(128);
      }
    }
  }, 30_000);

  it('H-02: an owner cancellation through the other replica aborts the held provider call', async () => {
    // The whole point of the finding: the abort has to reach the socket, not
    // merely suppress a result that already came back.
    const attemptId = nextId('att');
    const body = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    provider.hold = true;
    const before = provider.calls.length;

    const inFlight = a.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: body.payload,
      headers: body.headers,
    });

    // Wait for the provider to actually receive the call.
    const deadline = Date.now() + 10_000;
    while (provider.calls.length === before && Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 25));
    }
    expect(provider.calls.length, 'the provider was never called').toBeGreaterThan(before);
    const held = provider.calls[provider.calls.length - 1]!;

    // Owner cancels via the OTHER instance.
    await b.app.inject({
      method: 'DELETE',
      url: `/gw/v1/inference/${attemptId}`,
      headers: { authorization: OWNER_TOKEN },
    });

    // The held request must be hung up on before it is answered.
    await expect(
      Promise.race([
        held.aborted.then(() => 'aborted' as const),
        new Promise<'timeout'>((r) => setTimeout(() => r('timeout'), 8000)),
      ]),
    ).resolves.toBe('aborted');

    held.release();
    const response = await inFlight;
    // Whatever the outcome, no late result is delivered as a success.
    expect([200, 202, 499, 500]).toContain(response.statusCode);
    provider.hold = false;
  }, 30_000);

  it('H-01/H-02: a stranger cancelling does NOT abort a held provider call', async () => {
    // The mirror of the test above, and the one that would catch an
    // owner-check regression that still aborts.
    const attemptId = nextId('att');
    const body = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    provider.hold = true;
    const before = provider.calls.length;

    const inFlight = a.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: body.payload,
      headers: body.headers,
    });

    const deadline = Date.now() + 10_000;
    while (provider.calls.length === before && Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 25));
    }
    const held = provider.calls[provider.calls.length - 1]!;

    await b.app.inject({
      method: 'DELETE',
      url: `/gw/v1/inference/${attemptId}`,
      headers: { authorization: STRANGER_TOKEN },
    });

    const outcome = await Promise.race([
      held.aborted.then(() => 'aborted' as const),
      new Promise<'still-running'>((r) => setTimeout(() => r('still-running'), 2000)),
    ]);
    expect(outcome, 'a stranger aborted the owner transfer').toBe('still-running');

    held.release();
    await inFlight;
    provider.hold = false;
  }, 30_000);

  it('refuses the same attempt id on the second instance (cross-instance at-most-once)', async () => {
    const attemptId = nextId('att');
    provider.hold = false;
    const first = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    const started = await a.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: first.payload,
      headers: first.headers,
    });
    expect(started.statusCode).toBe(200);

    // Same attempt, fresh effect, other instance: the local tracker on B knows
    // nothing about it, so only the shared registry can refuse this.
    const second = multipart(contextFor(attemptId, nextId('eff')), OWNER_TOKEN);
    const replay = await b.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: second.payload,
      headers: second.headers,
    });
    expect(replay.statusCode).toBeGreaterThanOrEqual(400);
    expect(replay.statusCode).toBeLessThan(500);
  }, 30_000);
});

describe.skipIf(!url)('the fence, asserted through the real composition root', () => {
  /**
   * H-04's acceptance test: "verify the fence refuses two replicas whenever the
   * shared idempotency binding is absent."
   *
   * The unit tests cover `assertSingleInstanceState` directly. This covers the
   * thing that actually protects the deployment — that `buildApplication`
   * derives completeness from what it really composed. A unit test passes if
   * someone flips the flag to true without binding the store; this does not.
   *
   * It lives in the Redis lane because the redis backend connects at boot, so
   * the fence is only reachable with a real server behind it.
   */
  it('now PERMITS two replicas on redis, because every store is shared', async () => {
    // The fence stayed closed while Product idempotency was process-local. It is
    // now bound to Redis alongside the rest, so the reason for the refusal is
    // gone — and this is the assertion that would fail if a future change added
    // a protected store and forgot to share it.
    const config = {
      ...(configFor('fence-probe', 'http://127.0.0.1:1') as unknown as Record<string, unknown>),
      deployment: { instanceCount: 2, gatewayStateBackend: 'redis' },
    } as unknown as AppConfig;

    const handle = await buildApplication(config, createLogger(config));
    await handle.close();
  }, 30_000);

  it('still refuses two replicas on the memory backend', async () => {
    const config = {
      ...(configFor('fence-memory', 'http://127.0.0.1:1') as unknown as Record<string, unknown>),
      deployment: { instanceCount: 2, gatewayStateBackend: 'memory' },
    } as unknown as AppConfig;

    await expect(buildApplication(config, createLogger(config))).rejects.toThrow(/per-process/);
  }, 30_000);

  it('permits a single replica on redis, which is the supported deployment', async () => {
    const config = configFor('fence-probe-single', 'http://127.0.0.1:1');
    const handle = await buildApplication(config, createLogger(config));
    await handle.close();
  }, 30_000);
});

describe.skipIf(!!url)('two-instance lane', () => {
  it('is skipped: set REDIS_TEST_URL to exercise the distributed findings', () => {
    expect(url).toBeUndefined();
  });
});

/**
 * Story 4.4 criterion 3 — multi-instance Product idempotency, ONE durable write.
 *
 * Needs both a real Redis and a real database: the whole finding is that two
 * replicas each accept one Idempotency-Key and each write a row. A fake store
 * cannot produce that, and neither can a fake database.
 */
describe.skipIf(!url || !dbUrl)('one durable write across two replicas', () => {
  let a: ServerHandle;
  let b: ServerHandle;
  const token = `Bearer product-${RUN}-cccccccccccccccccccc`;

  const withDb = (instanceId: string): AppConfig =>
    ({
      ...(configFor(instanceId, 'http://127.0.0.1:1') as unknown as Record<string, unknown>),
      database: {
        url: dbUrl!,
        maxConnections: 5,
        idleTimeoutSeconds: 5,
        connectTimeoutSeconds: 5,
      },
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

  const post = (server: ServerHandle, key: string, body: unknown) =>
    server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: body as never,
      headers: {
        authorization: token,
        'content-type': 'application/json',
        'idempotency-key': key,
      },
    });

  beforeAll(async () => {
    process.env['APP_INSTANCE_ID'] = 'db-instance-a';
    const ca = withDb('db-instance-a');
    a = await buildApplication(ca, createLogger(ca));
    process.env['APP_INSTANCE_ID'] = 'db-instance-b';
    const cb = withDb('db-instance-b');
    b = await buildApplication(cb, createLogger(cb));
  }, 60_000);

  afterAll(async () => {
    await a?.close();
    await b?.close();
    delete process.env['APP_INSTANCE_ID'];
  });

  it('creates exactly one meal when the same key reaches both replicas', async () => {
    const key = `idem-${RUN}-concurrent`;
    const body = mealBody();

    // Fired together, before either resolves. Sequential calls would pass even
    // with per-process stores, which is how this went unnoticed.
    const [first, second] = await Promise.all([post(a, key, body), post(b, key, body)]);

    const codes = [first.statusCode, second.statusCode].sort();
    // One creates. The other either replays that same 201 or conflicts because
    // the winner was still in flight — both are correct, and both mean it did
    // NOT create a second meal.
    expect(codes.filter((c) => c === 201)).toHaveLength(1);
    // The OTHER code, named. This read `codes.some(c => c === 201 || c === 409)`,
    // which the line above already guarantees: once exactly one code is 201,
    // `some` is true whatever the second replica answered — a 500 included. The
    // assertion tested nothing.
    const other = codes.find((code) => code !== 201) ?? codes[1];
    expect([201, 409], `the second replica answered ${other}`).toContain(other);

    const created = [first, second].find((r) => r.statusCode === 201)!;
    const mealId = JSON.parse(created.body).id as string;

    // The decisive assertion: one row, whatever the two replicas answered.
    const listed = await a.app.inject({
      method: 'GET',
      url: '/api/v1/meals?limit=50',
      headers: { authorization: token },
    });
    const rows = JSON.parse(listed.body).data as { id: string }[];
    expect(rows.filter((r) => r.id === mealId)).toHaveLength(1);
  }, 60_000);

  it('replays the first response to a later retry on the other replica', async () => {
    const key = `idem-${RUN}-sequential`;
    const body = mealBody();

    const created = await post(a, key, body);
    expect(created.statusCode).toBe(201);
    const mealId = JSON.parse(created.body).id as string;

    // Same key, other replica, after the first completed: the body is re-read
    // from Postgres, because Redis holds only the status and the reference.
    const replayed = await post(b, key, body);
    expect(replayed.statusCode).toBe(201);
    expect(JSON.parse(replayed.body).id).toBe(mealId);
  }, 60_000);

  it('conflicts when the same key carries a different body', async () => {
    const key = `idem-${RUN}-conflict`;
    expect((await post(a, key, mealBody())).statusCode).toBe(201);

    const different = { ...mealBody(), mealType: 'dinner' };
    expect((await post(b, key, different)).statusCode).toBe(409);
  }, 60_000);

  it('lets two users reuse the same client key, each creating their own meal', async () => {
    // M4: Idempotency-Key is chosen by the client, and "1" or "meal" are
    // entirely ordinary. A single global keyspace made one user's key collide
    // with another's — at best a 409 for a request they never made, at worst
    // someone else's meal replayed to them. Asserted here against the SHARED
    // store, where a collision would now span replicas as well as users.
    const key = 'shared-client-key';
    const otherToken = `Bearer product-${RUN}-dddddddddddddddddddd`;

    const mine = await post(a, key, mealBody());
    expect(mine.statusCode).toBe(201);

    const theirs = await b.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: mealBody() as never,
      headers: {
        authorization: otherToken,
        'content-type': 'application/json',
        'idempotency-key': key,
      },
    });

    expect(theirs.statusCode).toBe(201);
    expect(JSON.parse(theirs.body).id).not.toBe(JSON.parse(mine.body).id);
  }, 60_000);

  it('releases the key when creation fails, so a later request can use it', async () => {
    // handover-6 H-04: "a failed creation can retry according to the existing
    // contract". A claim that is never completed and never released keeps
    // conflicting forever, so one failed create would make that key permanently
    // unusable.
    //
    // The failure has to happen AFTER the claim — the claim is taken before
    // `create()` runs — so this uses a body that passes the route and fails
    // inside the controller. A first attempt at authentication would fail before
    // the claim and prove nothing.
    const key = `idem-${RUN}-release`;
    const broken = { ...mealBody(), localDate: '2026-02-30' }; // no such date

    const failed = await post(a, key, broken);
    expect(failed.statusCode, 'a validation failure became a server error').toBe(400);

    // Same key, valid body, other replica. Were the claim still held, this would
    // conflict — the digest differs, and a held claim conflicts on any digest.
    const retried = await post(b, key, mealBody());
    expect(retried.statusCode, 'the failed claim was never released').toBe(201);
  }, 60_000);

  it('keeps the user and the client key out of Redis KEY NAMES (DS-F7)', async () => {
    // The canary scan checked VALUES only, so "no user content in Redis" was
    // asserted about half the keyspace. The names were
    // `idem:<raw user uuid>:<verbatim client key>` — a client-chosen free-text
    // string and a raw account identifier, both readable by anyone with the
    // connection. The gateway hashes installation ids into opaque tokens for
    // exactly this reason; this path did not.
    const clientKey = `CANARYKEY-${RUN}-CHOSENBYCLIENT`;

    // Only the keys THIS request writes. The store is shared and long-lived, so
    // scanning everything means asserting about keys other runs left behind —
    // which is how this test first "failed" against a fix that was working.
    const [created, keys] = await keysWrittenBy(() => post(a, clientKey, mealBody()));
    expect(created.statusCode, 'the canary meal was never created').toBe(201);
    expect(keys.length, 'the request wrote nothing to Redis at all').toBeGreaterThan(0);

    const idempotencyKeys = keys.filter((key) => key.startsWith('idem:'));
    expect(
      idempotencyKeys.length,
      'no idempotency key was written, so this proves nothing',
    ).toBeGreaterThan(0);

    for (const key of idempotencyKeys) {
      expect(key, 'the client-chosen key is readable in the keyspace').not.toContain(clientKey);
      expect(key, 'a raw account identifier is readable in the keyspace').not.toMatch(
        /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i,
      );
    }
  }, 60_000);

  it('keeps no meal content in Redis, and expires every key it writes', async () => {
    // A canary rather than a word from the fixture. 'Banana' and 'lunch' are
    // short enough to collide with hex by luck, and an assertion that can pass
    // by coincidence can also fail by coincidence — this string appears in
    // Redis only if the body was written there.
    const canary = `CANARY-${RUN}-DISHNAME`;
    const body = mealBody();
    body.items[0]!.displayName = canary;

    const [created, keys] = await keysWrittenBy(() => post(a, `idem-${RUN}-canary`, body));
    expect(created.statusCode, 'the canary meal was never created').toBe(201);
    expect(keys.length, 'the request wrote nothing to Redis at all').toBeGreaterThan(0);

    let scanned = 0;
    for (const key of keys) {
      // Story 4.4 criterion 4: nothing outlives the at-most-once horizon. A key
      // with no expiry is the failure this criterion is about — it would sit in
      // Redis until an operator noticed.
      const ttl = await redisTtl(key);
      expect(ttl, `key ${key} has no expiry at all`).toBeGreaterThan(0);
      expect(ttl, `key ${key} outlives the coordination horizon`).toBeLessThanOrEqual(
        COORDINATION_TTL_CEILING_SECONDS,
      );

      for (const value of await redisValues(key)) {
        scanned += 1;
        expect(value, `key ${key} holds the meal body`).not.toContain(canary);
      }
    }

    // Without this the loop above passes by reading nothing, which is exactly
    // how a "no user content" assertion quietly stops asserting anything.
    expect(scanned, 'no value was actually read, so the canary check was vacuous').toBeGreaterThan(
      0,
    );
  }, 60_000);
});
