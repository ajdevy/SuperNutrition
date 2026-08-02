import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { createHash } from 'node:crypto';
import { ok } from '@supernutrition/shared';
import { buildServer, describeServer, type ServerHandle } from './server.js';
import { MIDDLEWARE_ORDER } from './middleware/index.js';
import { createLogger } from './logging/index.js';
import type { AppConfig } from './config/index.js';
import { TransferStateTracker } from './routes/gateway/index.js';
import {
  FIXTURE_MARKET_POLICY,
  InMemoryEffectStore,
  InMemoryRateLimiter,
  StaticMarketPolicyResolver,
  TestModeAuthenticator,
} from './security/index.js';

/**
 * HTTP-level behaviour against a real Fastify instance via `inject`.
 *
 * The pipeline's decisions are tested directly elsewhere. What is asserted here
 * is the transport contract: status codes, headers, and that nothing reaches a
 * client unmapped.
 */

const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46]);
const digest = `sha256:${createHash('sha256').update(JPEG).digest('hex')}`;
const NOW = new Date('2026-07-29T10:20:00Z');

const config = {
  env: 'development',
  nodeEnv: 'development',
  server: { host: '127.0.0.1', port: 0 },
  database: {
    url: 'postgres://x',
    maxConnections: 1,
    idleTimeoutSeconds: 1,
    connectTimeoutSeconds: 1,
  },
  redis: { url: 'redis://x' },
  auth: { issuer: 'i', accessTtlSeconds: 900, refreshTtlSeconds: 1000, testMode: true },
  inference: { remoteEnabled: true, modelProfilesPath: 'p', openRouterBaseUrl: 'https://x' },
  logging: { level: 'silent', allowlistPath: 'a' },
} as unknown as AppConfig;

const context = () => ({
  contractVersion: 'mobile-api/v1',
  attemptId: 'att_0000000000000000',
  effectId: 'eff_0000000000000000',
  modality: 'photo',
  claimedMarketJurisdiction: 'ZZ',
  consentReceipt: {
    version: '1',
    attemptId: 'att_0000000000000000',
    digest,
    modality: 'photo',
    purpose: 'food-identification',
    decision: 'granted',
    marketJurisdiction: 'ZZ',
    processingRegion: 'fixture-region',
    consentCopyVersion: 'photo-consent/fixture-1',
    retentionVersion: 'retention/2',
    retentionDays: 0,
    configurationVersion: 'cfg/1',
    recordedAt: '2026-07-29T10:15:00Z',
    expiresAt: '2026-07-29T10:30:00Z',
  },
  inferenceContext: {
    catalogCountry: 'ZZ',
    mealType: 'lunch',
    locale: 'en-GB',
    measurementSystem: 'metric',
    configurationVersion: 'cfg/1',
  },
});

/** A credential long enough to be accepted; test mode skips attestation only. */
const TOKEN = 'Bearer test.installation-0001';

/** X-Request-Id is one-time, so every request needs its own. */
let requestSeq = 0;
const nextRequestId = () => `req_0000000000000000${(requestSeq += 1).toString().padStart(4, '0')}`;

const multipartBody = (
  ctx: unknown,
  bytes: Buffer,
  headerOverrides: Record<string, string | undefined> = {},
) => {
  const boundary = '----test-boundary';
  const head = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="context"\r\n\r\n${JSON.stringify(ctx)}\r\n` +
      `--${boundary}\r\nContent-Disposition: form-data; name="payload"; filename="a.jpg"\r\n` +
      `Content-Type: image/jpeg\r\n\r\n`,
  );
  const tail = Buffer.from(`\r\n--${boundary}--\r\n`);
  const headers: Record<string, string> = {
    'content-type': `multipart/form-data; boundary=${boundary}`,
    authorization: TOKEN,
    'x-contract-version': 'mobile-api/v1',
    'x-request-id': nextRequestId(),
  };
  for (const [name, value] of Object.entries(headerOverrides)) {
    if (value === undefined) delete headers[name];
    else headers[name] = value;
  }
  return { payload: Buffer.concat([head, bytes, tail]), headers };
};

let server: ServerHandle;
let remoteEnabled = true;

beforeEach(async () => {
  remoteEnabled = true;
  const resolver = new StaticMarketPolicyResolver(FIXTURE_MARKET_POLICY, 'fixture/1');
  const authenticator = new TestModeAuthenticator('development');

  server = await buildServer({
    config,
    logger: createLogger(config),
    gateway: {
      orchestrator: {
        analyze: async () =>
          ok({
            attemptId: 'att_0000000000000000' as never,
            outcome: 'candidates' as const,
            candidates: [
              {
                candidateId: 'cand_01',
                label: 'Banana',
                rank: 1,
                confidence: {
                  score: 0.93,
                  band: 'high',
                  engineId: 'e',
                  calibrationVersion: 'c',
                } as never,
                unresolvedAssumptions: [],
                provenance: {
                  route: 'remote' as const,
                  engineId: 'e',
                  modelVersion: 'm',
                  calibrationVersion: 'c',
                  contractVersion: 'photo-inference-engine/v1',
                },
              },
            ],
            confidence: null,
            modelId: 'm',
            durationMs: 5,
            passesRun: 1,
            requiresConfirmation: false,
          }),
      } as never,
      limiter: new InMemoryRateLimiter([
        { layer: 'installation', limit: 20, windowSeconds: 3600 },
        { layer: 'ip', limit: 300, windowSeconds: 60 },
      ]),
      effects: new InMemoryEffectStore(),
      requestIds: new InMemoryEffectStore(),
      payloads: { put: () => {}, dispose: () => {} },
      transfers: new TransferStateTracker(() => NOW),
      resolveMarket: (id, modality, now) => resolver.resolve(id, modality, now),
      resolveMarketPolicy: (id, modality, now) => resolver.resolve(id, modality, now),
      policyVersion: 'fixture/1',
      remoteInferenceEnabled: () => remoteEnabled,
      supportedRetentionVersions: ['retention/2'],
      now: () => NOW,
      requestIdFactory: () => 'req_0000000000000000',
      authenticate: async (request) => authenticator.authenticate(request.headers.authorization),
      issueTestSession: async () => ({
        accessToken: 'test.token',
        refreshToken: 'refresh',
        accessExpiresAt: NOW.toISOString(),
        refreshExpiresAt: NOW.toISOString(),
        authMode: 'test-mode',
      }),
    },
    // Surface B behaviour is asserted in routes/product; here it only has to be
    // registered, so the controller is a double.
    product: {
      meals: {
        listMeals: async () => ({ data: [], page: { nextCursor: null, hasMore: false } }),
      } as never,
      idempotency: {
        claim: async () => ({ state: 'fresh' }),
        complete: async () => {},
        fail: async () => {},
      } as never,
      limiter: new InMemoryRateLimiter([{ layer: 'user', limit: 120, windowSeconds: 60 }]),
      authenticate: async () => ({ userId: 'user-1' }),
    },
  });
});

afterEach(async () => {
  await server.close();
});

describe('server assembly', () => {
  it('declares middleware in the documented order', () => {
    expect(describeServer().middleware).toEqual(MIDDLEWARE_ORDER);
  });

  it('guards content length before authentication and validation', () => {
    const { middleware } = describeServer();
    expect(middleware.indexOf('content-length-guard')).toBeLessThan(
      middleware.indexOf('authentication'),
    );
    expect(middleware.indexOf('authentication')).toBeLessThan(
      middleware.indexOf('schema-validation'),
    );
  });

  it('assigns a correlation id first', () => {
    expect(describeServer().middleware[0]).toBe('correlation-id');
  });

  it('registers both surfaces', () => {
    const { routes } = describeServer();
    expect(routes.some((r) => r.path.startsWith('/gw/v1'))).toBe(true);
    expect(routes.some((r) => r.path.startsWith('/api/v1'))).toBe(true);
  });
});

describe('health', () => {
  it('reports liveness', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/health/live' });
    expect(response.statusCode).toBe(200);
  });

  // No environment can be in test mode unnoticed (docs/09 §9.2).
  it('surfaces authMode and kill-switch state on readiness', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/health/ready' });
    expect(response.json()).toMatchObject({
      authMode: 'test-mode',
      remoteInferenceEnabled: true,
      contractVersion: 'mobile-api/v1',
    });
  });

  it('sets hardening headers', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/health/live' });
    expect(response.headers['cache-control']).toBe('no-store');
    expect(response.headers['x-content-type-options']).toBe('nosniff');
    expect(response.headers['referrer-policy']).toBe('no-referrer');
  });
});

describe('POST /gw/v1/inference/photo', () => {
  it('accepts a well-formed multipart request', async () => {
    const response = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      ...multipartBody(context(), JPEG),
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.contractVersion).toBe('mobile-api/v1');
    expect(body.outcome.outcomeType).toBe('candidates');
    expect(body.resolvedMarketJurisdiction).toBe('ZZ');
  });

  it('emits no nutrition in an HTTP response', async () => {
    const response = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      ...multipartBody(context(), JPEG),
    });
    const raw = response.body.toLowerCase();
    for (const key of ['calories', 'protein', 'carb', 'sodium', 'nutrition', 'gramweight']) {
      expect(raw, `leaked ${key}`).not.toContain(`"${key}`);
    }
  });

  it('rejects a non-multipart body with the contract media-type code', async () => {
    const response = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: { hello: 'world' },
    });
    expect(response.statusCode).toBe(415);
    expect(response.json().error.gatewayCode).toBe('unsupported-media-type');
  });

  it('returns the kill-switch code with Retry-After when remote is disabled', async () => {
    remoteEnabled = false;
    const response = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      ...multipartBody(context(), JPEG),
    });

    expect(response.statusCode).toBe(503);
    expect(response.json().error).toMatchObject({
      gatewayCode: 'kill-switch',
      recoverable: true,
      retryAfter: 300,
    });
    // Recoverable is what lets the installed base return when the switch lifts.
    expect(response.headers['retry-after']).toBe('300');
  });

  it('carries a correlation id on success and on failure', async () => {
    const good = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      ...multipartBody(context(), JPEG),
    });
    expect(good.headers['x-correlation-id']).toBeDefined();

    const bad = await server.app.inject({ method: 'POST', url: '/gw/v1/inference/photo' });
    expect(bad.headers['x-correlation-id']).toBeDefined();
    expect(bad.json().error.correlationId).toBeDefined();
  });

  it('never sets a permissive CORS header (docs/09 §9.9)', async () => {
    const response = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      ...multipartBody(context(), JPEG),
    });
    expect(response.headers['access-control-allow-origin']).toBeUndefined();
  });

  it('leaks no provider message or stack in an error body', async () => {
    const response = await server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      payload: { hello: 'world' },
    });
    const body = response.body;
    expect(body).not.toMatch(/at\s+\w+\s+\(/);
    expect(body).not.toContain('openrouter');
  });
});

/**
 * Header gates from `gateway-request.toml`. Every one of these is
 * `optional = false` in the release, and every one of them used to be accepted:
 * an anonymous, versionless, replayable request reached a paid provider.
 */
describe('gateway request headers (contract gateway-request.toml)', () => {
  const post = (overrides: Record<string, string | undefined>) =>
    server.app.inject({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      ...multipartBody(context(), JPEG, overrides),
    });

  it.each([
    ['absent', undefined],
    ['empty', ''],
    ['not a bearer credential', 'Bearer '],
  ])('refuses a request whose Authorization is %s', async (_label, authorization) => {
    const response = await post({ authorization });
    expect(response.statusCode).toBe(401);
    expect(response.json().error.gatewayCode).toBe('authentication-failed');
  });

  it.each([
    ['absent', undefined],
    ['outside the supported matrix', 'mobile-api/v99'],
  ])('refuses a request whose X-Contract-Version is %s', async (_label, version) => {
    const response = await post({ 'x-contract-version': version });
    expect(response.statusCode).toBe(400);
    expect(response.json().error.gatewayCode).toBe('unsupported-version');
  });

  it.each([
    ['absent', undefined],
    ['malformed', 'not-a-request-id'],
  ])('refuses a request whose X-Request-Id is %s', async (_label, requestId) => {
    const response = await post({ 'x-request-id': requestId });
    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatchObject({
      gatewayCode: 'validation-error',
      details: [{ field: 'X-Request-Id' }],
    });
  });

  // A retry must carry a fresh identifier, which is why the taxonomy marks
  // token-replayed unrecoverable: retrying THIS one can never succeed (AD-21).
  it('rejects a replayed X-Request-Id as token-replayed', async () => {
    const requestId = nextRequestId();

    const first = await post({ 'x-request-id': requestId });
    expect(first.statusCode).toBe(200);

    const replay = await post({ 'x-request-id': requestId });
    expect(replay.statusCode).toBe(401);
    expect(replay.json().error).toMatchObject({
      gatewayCode: 'token-replayed',
      recoverable: false,
    });
  });
});

describe('POST /gw/v1/inference/text', () => {
  // Declared by the release and by docs/06, but `text` is reserved in v1. A 404
  // would say the path does not exist and send a client hunting for a typo.
  it('answers invalid-modality rather than 404, because text is reserved', async () => {
    const response = await server.app.inject({ method: 'POST', url: '/gw/v1/inference/text' });
    expect(response.statusCode).toBe(400);
    expect(response.json().error.gatewayCode).toBe('invalid-modality');
  });
});

describe('DELETE /gw/v1/inference/:attemptId', () => {
  it('refuses an anonymous cancellation', async () => {
    const response = await server.app.inject({
      method: 'DELETE',
      url: '/gw/v1/inference/att_0000000000000000',
    });
    expect(response.statusCode).toBe(401);
    expect(response.json().error.gatewayCode).toBe('authentication-failed');
  });

  it('returns 204 and no body for an attempt that sent nothing', async () => {
    const response = await server.app.inject({
      method: 'DELETE',
      url: '/gw/v1/inference/att_0000000000000000',
      headers: { authorization: TOKEN },
    });
    expect(response.statusCode).toBe(204);
    expect(response.body).toBe('');
  });

  it('never claims recall', async () => {
    const response = await server.app.inject({
      method: 'DELETE',
      url: '/gw/v1/inference/att_1111111111111111',
      headers: { authorization: TOKEN },
    });
    expect([202, 204]).toContain(response.statusCode);
    if (response.statusCode === 202) {
      expect(response.json().recallClaimed).toBe(false);
    }
  });
});

describe('GET /gw/v1/policy/market', () => {
  it('resolves a permitted fixture market', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/gw/v1/policy/market?jurisdiction=ZZ&modality=photo',
    });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      marketJurisdiction: 'ZZ',
      remotePermitted: true,
      residencyPosture: 'unrestricted',
    });
  });

  it('is never cached, because a posture expires at resolution time', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/gw/v1/policy/market?jurisdiction=ZZ&modality=photo',
    });
    expect(response.headers['cache-control']).toBe('no-store');
  });

  it('denies an unknown market', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/gw/v1/policy/market?jurisdiction=DE&modality=photo',
    });
    expect(response.json()).toMatchObject({
      residencyPosture: 'unresolved',
      remotePermitted: false,
      processingRegion: null,
    });
  });

  // `modality` is non-optional in market-policy.toml. Defaulting it would
  // answer the photo posture to a client that asked about nothing, which is
  // exactly the cross-modality inference MP-4 forbids.
  it('requires a modality rather than defaulting to photo', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/gw/v1/policy/market?jurisdiction=ZZ',
    });
    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatchObject({
      gatewayCode: 'validation-error',
      details: [{ field: 'modality', issue: 'required' }],
    });
  });

  it('rejects an unsupported modality', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/gw/v1/policy/market?jurisdiction=ZZ&modality=audio',
    });
    expect(response.statusCode).toBe(400);
    expect(response.json().error.gatewayCode).toBe('invalid-modality');
  });
});

describe('POST /gw/v1/session', () => {
  it('issues a test-mode session labelled as such', async () => {
    const response = await server.app.inject({ method: 'POST', url: '/gw/v1/session' });
    expect(response.statusCode).toBe(201);
    expect(response.json().authMode).toBe('test-mode');
  });
});

describe('product surface', () => {
  // The rest of Surface B belongs to later stages. It says so rather than
  // 404-ing, which would be indistinguishable from a typo in the path.
  it('returns 501 rather than 404 for a route no stage has implemented', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/api/v1/goals' });
    expect(response.statusCode).toBe(501);
    // Answers in the PRODUCT taxonomy: a gatewayCode here would be a code the
    // product client has never heard of (docs/06 §6.2).
    expect(response.json().error.code).toBe('NOT_IMPLEMENTED');
    expect(response.json().error.gatewayCode).toBeUndefined();
  });

  it('serves the meals routes instead of stubbing them', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/api/v1/meals' });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ data: [], page: { nextCursor: null, hasMore: false } });
  });
});

describe('unknown routes', () => {
  it('return a mapped contract error, not a bare Fastify 404', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/nope' });
    expect(response.statusCode).toBe(404);
    expect(response.json().error.gatewayCode).toBe('validation-error');
  });
});
