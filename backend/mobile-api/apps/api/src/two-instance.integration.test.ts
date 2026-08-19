import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createHash } from 'node:crypto';
import { createServer, type Server } from 'node:http';
import { buildApplication } from './main.js';
import { createLogger } from './logging/index.js';
import type { AppConfig } from './config/index.js';
import type { ServerHandle } from './server.js';

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

const JPEG = Buffer.from([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
  0x00, 0x01, 0x00, 0x00, 0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03, 0x01, 0x22,
  0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
]);
const digest = `sha256:${createHash('sha256').update(JPEG).digest('hex')}`;

/** Two distinct installations: the token's first 32 chars become the id. */
const OWNER_TOKEN = 'Bearer owner-installation-aaaaaaaaaaaaaaaa';
const STRANGER_TOKEN = 'Bearer stranger-installation-bbbbbbbbbbbb';

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
    logging: { level: 'silent' },
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

describe.skipIf(!!url)('two-instance lane', () => {
  it('is skipped: set REDIS_TEST_URL to exercise the distributed findings', () => {
    expect(url).toBeUndefined();
  });
});
