import { describe, it, expect, beforeEach } from 'vitest';
import { createHash } from 'node:crypto';
import { ok, err, type Result } from '@supernutrition/shared';
import { runInferencePipeline, type PipelineDeps } from './pipeline.js';
import { TransferStateTracker } from './transfer-state.js';
import {
  GATEWAY_CODES,
  GATEWAY_ERRORS,
  GatewayError,
  InMemoryEffectStore,
  InMemoryRateLimiter,
  StaticMarketPolicyResolver,
  FIXTURE_MARKET_POLICY,
  type AuthContext,
} from '../../security/index.js';
import type {
  InferenceFailure,
  InferenceRequest,
  InferenceResult,
} from '../../modules/inference/index.js';

/**
 * The property that matters most in this file: steps 1..5 reject with ZERO
 * provider calls. That is only assertable because the pipeline is callable
 * without an HTTP server — a fake orchestrator counts its own invocations.
 */

// A readable 16x16 baseline JPEG: the gateway now measures the image, so an
// 8-byte stub with no start-of-frame is correctly refused (H-04).
const JPEG = new Uint8Array([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
  0x00, 0x01, 0x00, 0x00, 0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03, 0x01, 0x22,
  0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
]);
const digestOf = (bytes: Uint8Array) =>
  `sha256:${createHash('sha256').update(bytes).digest('hex')}`;

class CountingOrchestrator {
  calls = 0;
  result: Result<InferenceResult, InferenceFailure> = ok({
    attemptId: 'att_0000000000000000' as never,
    outcome: 'candidates',
    candidates: [
      {
        candidateId: 'cand_01',
        label: 'Banana',
        rank: 1,
        confidence: {
          score: 0.93,
          band: 'high',
          engineId: 'gateway-openrouter',
          calibrationVersion: 'cal-1',
        },
        unresolvedAssumptions: [],
        provenance: {
          route: 'remote',
          engineId: 'gateway-openrouter',
          modelVersion: 'fixture',
          calibrationVersion: 'cal-1',
          contractVersion: 'photo-inference-engine/v1',
        },
      },
    ],
    confidence: null,
    modelId: 'fixture',
    durationMs: 12,
    passesRun: 1,
    requiresConfirmation: false,
  });

  async analyze(
    _request: InferenceRequest,
    options?: { onTransferStarted?: () => void; onProviderAdmitted?: () => void },
  ): Promise<Result<InferenceResult, InferenceFailure>> {
    this.calls += 1;
    // #48 — a real provider reports two moments: bytes on the wire, and the
    // provider answering. A fake that reports neither leaves every attempt at
    // `pre-transfer`; one that reports only the second describes a transfer
    // that never started.
    options?.onTransferStarted?.();
    options?.onProviderAdmitted?.();
    return this.result;
  }
}

const AUTH: AuthContext = {
  userId: 'test-user',
  installationId: 'install-1',
  scope: 'both',
  authMode: 'test-mode',
  environment: 'development',
};

const NOW = new Date('2026-07-29T10:20:00Z');

const receiptFor = (bytes: Uint8Array, over: Record<string, unknown> = {}) => ({
  version: '1',
  attemptId: 'att_0000000000000000',
  digest: digestOf(bytes),
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
  ...over,
});

const contextFor = (bytes: Uint8Array, over: Record<string, unknown> = {}) => ({
  contractVersion: 'mobile-api/v1',
  attemptId: 'att_0000000000000000',
  effectId: 'eff_0000000000000000',
  modality: 'photo',
  claimedMarketJurisdiction: 'ZZ',
  // The receipt is bound to the attempt it consents to, so overriding attemptId
  // has to carry into the receipt or the request is refused as malformed
  // consent — which is the binding working, not a test inconvenience.
  consentReceipt: {
    ...receiptFor(bytes),
    ...(typeof over.attemptId === 'string' ? { attemptId: over.attemptId } : {}),
  },
  inferenceContext: {
    catalogCountry: 'ZZ',
    mealType: 'lunch',
    locale: 'en-GB',
    measurementSystem: 'metric',
    configurationVersion: 'cfg/1',
  },
  ...over,
});

let orchestrator: CountingOrchestrator;
let deps: PipelineDeps;
let disposed: string[];

beforeEach(() => {
  orchestrator = new CountingOrchestrator();
  disposed = [];
  const resolver = new StaticMarketPolicyResolver(FIXTURE_MARKET_POLICY, 'fixture/1');
  deps = {
    orchestrator: orchestrator as never,
    limiter: new InMemoryRateLimiter([
      { layer: 'installation', limit: 20, windowSeconds: 3600 },
      { layer: 'ip', limit: 300, windowSeconds: 60 },
    ]),
    effects: new InMemoryEffectStore(),
    requestIds: new InMemoryEffectStore(),
    payloads: {
      put: () => {},
      dispose: (attemptId) => disposed.push(attemptId),
    },
    transfers: new TransferStateTracker(() => NOW),
    // Memory backend: one instance, so the local tracker is the whole truth.
    attempts: null,
    instanceId: 'test-instance',
    resolveMarket: (marketId, modality, now) => resolver.resolve(marketId, modality, now),
    remoteInferenceEnabled: () => true,
    supportedRetentionVersions: ['retention/2'],
    supportedConfigurationVersions: ['cfg/1'],
    now: () => NOW,
  };
});

// A fresh request ID per call by default: X-Request-Id is one-time, so a
// shared constant would make the second call in any test a replay.
let requestSeq = 0;
const run = (context: unknown, bytes: Uint8Array = JPEG, requestId?: string) =>
  runInferencePipeline(deps, {
    context: context as never,
    payload: { bytes, contentType: 'image/jpeg' },
    auth: AUTH,
    clientIp: '203.0.113.5',
    requestId: requestId ?? `req_00000000000000${(requestSeq += 1).toString().padStart(2, '0')}`,
  });

describe('happy path', () => {
  it('returns contract-v1 candidates and resolves the market server-side', async () => {
    const response = await run(contextFor(JPEG));
    expect(response.status).toBe(200);
    expect(response.body.contractVersion).toBe('mobile-api/v1');
    expect(response.body.resolvedMarketJurisdiction).toBe('ZZ');
    expect(response.body.resolvedProcessingRegion).toBe('fixture-region');
    expect(response.body.outcome.outcomeType).toBe('candidates');
    expect(orchestrator.calls).toBe(1);
  });

  it('disposes the payload on the success path', async () => {
    await run(contextFor(JPEG));
    expect(disposed).toContain('att_0000000000000000');
  });

  it('emits no nutrition in the response', async () => {
    const response = await run(contextFor(JPEG));
    const serialized = JSON.stringify(response.body).toLowerCase();
    for (const key of ['calories', 'protein', 'carb', 'sodium', 'nutrition', 'gramweight']) {
      expect(serialized, `leaked ${key}`).not.toContain(`"${key}`);
    }
  });
});

/**
 * E4.3 acceptance criterion: every gate rejects BEFORE provider invocation.
 * Each case asserts `orchestrator.calls === 0`.
 */
describe('every gate rejects with zero provider calls', () => {
  const expectRejection = async (context: unknown, code: string, bytes = JPEG) => {
    await expect(run(context, bytes)).rejects.toMatchObject({ gatewayCode: code });
    expect(orchestrator.calls, 'a provider was called during a rejection').toBe(0);
  };

  it('kill switch', async () => {
    deps = { ...deps, remoteInferenceEnabled: () => false };
    await expectRejection(contextFor(JPEG), 'kill-switch');
  });

  it('unsupported contract version', async () => {
    await expectRejection(
      contextFor(JPEG, { contractVersion: 'mobile-api/v2' }),
      'unsupported-version',
    );
  });

  it('malformed attempt id', async () => {
    await expectRejection(contextFor(JPEG, { attemptId: 'nope' }), 'validation-error');
  });

  it('unsupported modality', async () => {
    await expectRejection(contextFor(JPEG, { modality: 'audio' }), 'invalid-modality');
  });

  it('empty payload', async () => {
    await expectRejection(contextFor(new Uint8Array()), 'validation-error', new Uint8Array());
  });

  it('oversized payload', async () => {
    const big = new Uint8Array(9 * 1024 * 1024);
    big.set(JPEG, 0);
    await expectRejection(contextFor(big), 'content-too-large', big);
  });

  it('magic bytes disagree with the declared type', async () => {
    const notJpeg = new Uint8Array([0x00, 0x01, 0x02, 0x03]);
    await expectRejection(contextFor(notJpeg), 'unsupported-media-type', notJpeg);
  });

  it('digest does not match the received bytes', async () => {
    const context = contextFor(JPEG, {
      consentReceipt: receiptFor(JPEG, { digest: `sha256:${'0'.repeat(64)}` }),
    });
    await expectRejection(context, 'malformed-consent');
  });

  it('consent expired at resolution time', async () => {
    const context = contextFor(JPEG, {
      consentReceipt: receiptFor(JPEG, {
        recordedAt: '2026-07-29T09:00:00Z',
        expiresAt: '2026-07-29T09:15:00Z',
      }),
    });
    await expectRejection(context, 'consent-expired');
  });

  it('consent declined', async () => {
    await expectRejection(
      contextFor(JPEG, { consentReceipt: receiptFor(JPEG, { decision: 'declined' }) }),
      'consent-required',
    );
  });

  it('consent modality does not match the request', async () => {
    await expectRejection(
      contextFor(JPEG, { consentReceipt: receiptFor(JPEG, { modality: 'text' }) }),
      'consent-required',
    );
  });

  it('nonzero retention cannot be consented to', async () => {
    await expectRejection(
      contextFor(JPEG, { consentReceipt: receiptFor(JPEG, { retentionDays: 30 }) }),
      'malformed-consent',
    );
  });

  it('market posture prohibits the modality', async () => {
    const context = contextFor(JPEG, {
      claimedMarketJurisdiction: 'XX',
      consentReceipt: receiptFor(JPEG, { marketJurisdiction: 'XX' }),
    });
    await expectRejection(context, 'market-not-permitted');
  });

  it('unknown market is unresolved and denied', async () => {
    const context = contextFor(JPEG, {
      claimedMarketJurisdiction: 'DE',
      consentReceipt: receiptFor(JPEG, { marketJurisdiction: 'DE' }),
    });
    await expectRejection(context, 'market-not-permitted');
  });

  it('test-mode token cannot reach a real market', async () => {
    // Resolve a permissive REAL market, so only the test-mode fence can stop it.
    const permissive = new StaticMarketPolicyResolver(
      [
        {
          ...FIXTURE_MARKET_POLICY[0]!,
          marketId: 'DE',
          marketJurisdiction: 'DE',
        },
      ],
      'test/1',
    );
    deps = {
      ...deps,
      resolveMarket: (marketId, modality, now) => permissive.resolve(marketId, modality, now),
    };
    const context = contextFor(JPEG, {
      claimedMarketJurisdiction: 'DE',
      consentReceipt: receiptFor(JPEG, { marketJurisdiction: 'DE' }),
    });
    await expectRejection(context, 'test-mode-forbidden');
  });

  it('rate limit exhausted', async () => {
    deps = {
      ...deps,
      limiter: new InMemoryRateLimiter([{ layer: 'installation', limit: 0, windowSeconds: 60 }]),
    };
    await expectRejection(contextFor(JPEG), 'rate-limited');
  });

  it('insufficient scope', async () => {
    await expect(
      runInferencePipeline(deps, {
        context: contextFor(JPEG) as never,
        payload: { bytes: JPEG, contentType: 'image/jpeg' },
        auth: { ...AUTH, scope: 'product' },
        clientIp: '203.0.113.5',
      }),
    ).rejects.toMatchObject({ gatewayCode: 'insufficient-scope' });
    expect(orchestrator.calls).toBe(0);
  });
});

describe('at-most-once transfer start (AD-18)', () => {
  it('rejects a duplicate effect without calling a provider again', async () => {
    await run(contextFor(JPEG));
    expect(orchestrator.calls).toBe(1);

    await expect(run(contextFor(JPEG))).rejects.toMatchObject({
      gatewayCode: 'validation-error',
    });
    expect(orchestrator.calls, 'duplicate effect reached a provider').toBe(1);
  });
});

describe('payload disposal (AD-8)', () => {
  it('disposes on a rejection after the payload was accepted', async () => {
    deps = { ...deps, remoteInferenceEnabled: () => true };
    orchestrator.result = { ok: false, error: 'provider-unavailable' };
    await run(contextFor(JPEG));
    expect(disposed).toContain('att_0000000000000000');
  });

  it('disposes when the orchestrator throws', async () => {
    deps = {
      ...deps,
      orchestrator: {
        analyze: async () => {
          throw new GatewayError('internal-error');
        },
      } as never,
    };
    await expect(run(contextFor(JPEG))).rejects.toBeInstanceOf(GatewayError);
    expect(disposed).toContain('att_0000000000000000');
  });
});

describe('provider failure is an outcome, not an HTTP error', () => {
  it('returns a 200 envelope carrying the failure outcome and resolved market', async () => {
    orchestrator.result = { ok: false, error: 'provider-unavailable' };
    const response = await run(contextFor(JPEG));
    expect(response.status).toBe(200);
    expect(response.body.outcome).toMatchObject({
      outcomeType: 'failure',
      failureCode: 'provider-unavailable',
      recoverable: true,
    });
    expect(response.body.resolvedProcessingRegion).toBe('fixture-region');
  });

  /**
   * `outcome.failureCode` is contractually "one gatewayCode from
   * error-taxonomy.toml". The orchestrator's own vocabulary — `timeout`,
   * `cancelled`, `malformed-response` — may not reach the wire: a client
   * branching on the taxonomy would meet a discriminant it has never heard of.
   */
  it.each([
    ['timeout', 'provider-timeout', true, 15],
    ['cancelled', 'client-cancelled', false, null],
    ['malformed-response', 'internal-error', false, null],
    ['rate-limited', 'rate-limited', true, 30],
    ['budget-exceeded', 'budget-exceeded', false, null],
    ['kill-switch', 'kill-switch', true, 300],
  ] as const)(
    'maps the internal failure %s onto the released code %s',
    async (failure, code, recoverable, retryAfterSeconds) => {
      orchestrator.result = { ok: false, error: failure };
      const response = await run(contextFor(JPEG));

      expect(response.body.outcome).toMatchObject({
        outcomeType: 'failure',
        failureCode: code,
        userMessageKey: GATEWAY_ERRORS[code].userMessageKey,
        recoverable,
        retryAfterSeconds,
      });
    },
  );

  it('emits only codes the released taxonomy declares', async () => {
    const released = new Set(Object.values(GATEWAY_CODES));
    const failures = [
      'timeout',
      'cancelled',
      'malformed-response',
      'rate-limited',
      'provider-unavailable',
      'budget-exceeded',
      'kill-switch',
      // An unmapped failure must still land on a released code rather than
      // leaking whatever string the orchestrator invented.
      'something-nobody-mapped',
    ] as const;

    for (const [index, failure] of failures.entries()) {
      orchestrator.result = { ok: false, error: failure as never };
      // A fresh effect id AND a fresh attempt id per iteration. Both are
      // at-most-once: reusing either is a duplicate transfer start and is
      // refused before the orchestrator is reached, which is what a real client
      // doing a second analysis does — it mints a new attempt.
      const suffix = index.toString().padStart(2, '0');
      const outcome = (
        await run(
          contextFor(JPEG, {
            effectId: `eff_00000000000000${suffix}`,
            attemptId: `att_00000000000000${suffix}`,
          }),
        )
      ).body.outcome;

      if (outcome.outcomeType !== 'failure') throw new Error(`expected a failure for ${failure}`);
      expect(released.has(outcome.failureCode as never), outcome.failureCode).toBe(true);
    }
  });

  /**
   * A provider refusing the content is the one case that is NOT a failure. The
   * attempt succeeded and the answer is "I will not identify this", which the
   * release models as an abstention — so the client offers manual entry rather
   * than a retry that cannot help.
   */
  it('reports a provider content rejection as an abstention, not a failure', async () => {
    orchestrator.result = { ok: false, error: 'content-rejected' };
    const response = await run(contextFor(JPEG));

    expect(response.body.outcome).toEqual({
      outcomeType: 'abstention',
      reasonCode: 'content-rejected-by-provider',
      userMessageKey: 'inference.abstention.content_rejected_by_provider',
      manualAlternativeAvailable: true,
    });
  });

  it('reports an empty identification as a no-food abstention', async () => {
    orchestrator.result = ok({
      ...(orchestrator.result as { value: InferenceResult }).value,
      outcome: 'abstention',
      candidates: [],
    });
    const response = await run(contextFor(JPEG));

    expect(response.body.outcome).toEqual({
      outcomeType: 'abstention',
      reasonCode: 'no-food-detected',
      userMessageKey: 'inference.abstention.no_food_detected',
      manualAlternativeAvailable: true,
    });
  });
});

describe('replay detection (AD-21)', () => {
  it('rejects a reused request id as token-replayed, before the rate limiter', async () => {
    const requestId = 'req_1111111111111111';
    await run(contextFor(JPEG), JPEG, requestId);

    await expect(
      run(contextFor(JPEG, { effectId: 'eff_9999999999999999' }), JPEG, requestId),
    ).rejects.toMatchObject({ gatewayCode: 'token-replayed', recoverable: false });
    expect(orchestrator.calls, 'a replay reached a provider').toBe(1);
  });
});

describe('rate limiting (contract rate-limit.toml)', () => {
  it('advertises the remaining budget on a successful response', async () => {
    const response = await run(contextFor(JPEG));
    expect(response.headers).toMatchObject({
      'X-RateLimit-Limit': expect.any(String),
      'X-RateLimit-Remaining': expect.any(String),
      'X-RateLimit-Reset': expect.any(String),
    });
  });

  // rate-limit.toml marks all four non-optional on a 429, and the released
  // fixture asserts them. Only Retry-After used to be sent.
  it('carries the limit, remaining, and reset on a rejection', async () => {
    deps = {
      ...deps,
      limiter: new InMemoryRateLimiter([{ layer: 'installation', limit: 1, windowSeconds: 3600 }]),
    };
    await run(contextFor(JPEG));

    const rejected = await run(contextFor(JPEG, { effectId: 'eff_8888888888888888' })).catch(
      (error: GatewayError) => error,
    );
    expect(rejected).toBeInstanceOf(GatewayError);
    const error = rejected as GatewayError;
    expect(error.gatewayCode).toBe('rate-limited');
    expect(error.headers).toMatchObject({
      'X-RateLimit-Limit': '1',
      'X-RateLimit-Remaining': '0',
      'X-RateLimit-Reset': expect.any(String),
    });

    // The code never says WHICH layer rejected, so a caller cannot probe the
    // limiter topology for headroom.
    expect(JSON.stringify(error.toBody('c'))).not.toContain('installation');
  });
});

describe('the response identifies the request it answers', () => {
  // The envelope prose in gateway-response.toml reads "Server-generated,
  // one-time", and the pipeline took that literally: it minted a fresh id from
  // requestIdFactory and dropped the validated X-Request-Id on the floor.
  //
  // All three canonical fixtures that carry a response body disagree. Each
  // pairs request and response byte for byte:
  //
  //   [request.headers] "X-Request-Id" = "req_01J8X4Q2K7R9M3P5T6V8"
  //   [response.body]   requestId      = "req_01J8X4Q2K7R9M3P5T6V8"
  //
  // The fixtures are the frozen artifact the contract verifier pins, so the
  // echo is the behaviour and the prose is loose wording. A client that binds
  // the response to its own request — checking the reply answers the call it
  // made, and not some other in-flight one — rejects every single response
  // while the server invents its own id.
  it('echoes the validated X-Request-Id rather than minting a new one', async () => {
    const requestId = 'req_2222222222222222';
    const response = await run(contextFor(JPEG), JPEG, requestId);

    expect(response.status).toBe(200);
    expect(response.body.requestId).toBe(requestId);
  });

  it('carries a distinct id per request, so two attempts never collide', async () => {
    // Distinct attempt and effect ids: two calls sharing either are refused as
    // a reused attempt or a duplicate effect before a response body exists
    // (AD-18). Only fully independent requests reach the envelope.
    const first = await run(
      contextFor(JPEG, { attemptId: 'att_3333333333333333', effectId: 'eff_3333333333333333' }),
      JPEG,
      'req_3333333333333333',
    );
    const second = await run(
      contextFor(JPEG, { attemptId: 'att_4444444444444444', effectId: 'eff_4444444444444444' }),
      JPEG,
      'req_4444444444444444',
    );

    expect(first.body.requestId).toBe('req_3333333333333333');
    expect(second.body.requestId).toBe('req_4444444444444444');
  });
});

describe('H-06 — the configuration version the user consented to must be the one in force', () => {
  // verifyConsentAndMarket passed `supportedConfigurationVersions: null`, which
  // is the documented way to switch the check off, and nothing else compared the
  // receipt's version with the request's. The release requires
  // `retention-and-configuration-versions-supported`, so the disclosure a user
  // agreed to could be from a configuration the gateway no longer runs and the
  // transfer would proceed anyway.
  //
  // The supported list is server-side by construction: taking it from the
  // request would let a client declare its own stale version supported.

  it('refuses a receipt naming a configuration the gateway does not run', async () => {
    await expect(
      run(
        contextFor(JPEG, {
          attemptId: 'att_6666666666666666',
          effectId: 'eff_6666666666666666',
          consentReceipt: {
            ...receiptFor(JPEG),
            attemptId: 'att_6666666666666666',
            configurationVersion: 'cfg/ancient',
          },
        }),
        JPEG,
        'req_6666666666666666',
      ),
    ).rejects.toThrow(/consent-required|gateway error/);
  });

  it('refuses when the receipt and the request disagree about the version', async () => {
    await expect(
      run(
        contextFor(JPEG, {
          attemptId: 'att_7777777777777777',
          effectId: 'eff_7777777777777777',
          inferenceContext: {
            catalogCountry: 'ZZ',
            mealType: 'lunch',
            locale: 'en-GB',
            measurementSystem: 'metric',
            configurationVersion: 'cfg/other',
          },
        }),
        JPEG,
        'req_7777777777777777',
      ),
    ).rejects.toThrow(/consent-required|gateway error/);
  });

  it('still accepts a receipt on the current version', async () => {
    const response = await run(
      contextFor(JPEG, { attemptId: 'att_8888888888888888', effectId: 'eff_8888888888888888' }),
      JPEG,
      'req_8888888888888888',
    );

    expect(response.status).toBe(200);
  });
});

describe('#48(a) — `transferred` requires provider admission', () => {
  /**
   * The tracker's own primitives were always correct; the pipeline misused
   * them. `markTransferring` and `markTransferred` were called back to back
   * before `orchestrator.analyze()` ran, so the terminal state was reached
   * before any provider had seen a byte.
   *
   * `transfer-states.toml` declares `provider_called = true` for `transferred`.
   * Moving the transition to provider admission makes the code conform to the
   * release rather than redefining it — which is why this part of #48 needs no
   * acknowledgement.
   */
  it('is still transferring while the provider has not admitted the payload', async () => {
    let stateDuringCall: string | null = null;
    const attemptId = 'att_0000000000000000';
    deps = {
      ...deps,
      orchestrator: {
        analyze: async (_r: unknown, options: { onTransferStarted?: () => void }) => {
          // Bytes are on the wire; the provider has not answered yet.
          options.onTransferStarted?.();
          stateDuringCall = deps.transfers.stateOf(attemptId);
          return orchestrator.result;
        },
      } as never,
    };

    await runInferencePipeline(deps, {
      context: contextFor(JPEG) as never,
      payload: { bytes: JPEG, contentType: 'image/jpeg' },
      auth: AUTH,
      requestId: 'req_0000000000000000',
      clientIp: '127.0.0.1',
    });

    expect(stateDuringCall).toBe('transferring');
  });

  it('reaches transferred once the provider admits it', async () => {
    const attemptId = 'att_0000000000000000';

    await runInferencePipeline(deps, {
      context: contextFor(JPEG) as never,
      payload: { bytes: JPEG, contentType: 'image/jpeg' },
      auth: AUTH,
      requestId: 'req_0000000000000000',
      clientIp: '127.0.0.1',
    });

    expect(deps.transfers.stateOf(attemptId)).toBe('transferred');
  });

  it('does not reach transferred when the provider never admits the payload', async () => {
    // A provider that refuses, times out, or dies mid-call has not received the
    // photo in full. Recording it as transferred told the user otherwise.
    const attemptId = 'att_0000000000000000';
    deps = {
      ...deps,
      orchestrator: {
        analyze: async (_r: unknown, options: { onTransferStarted?: () => void }) => {
          // Bytes went out, then the connection died before any answer.
          options.onTransferStarted?.();
          throw new Error('provider connection reset');
        },
      } as never,
    };

    await expect(
      runInferencePipeline(deps, {
        context: contextFor(JPEG) as never,
        payload: { bytes: JPEG, contentType: 'image/jpeg' },
        auth: AUTH,
        requestId: 'req_0000000000000000',
        clientIp: '127.0.0.1',
      }),
    ).rejects.toThrow();

    expect(deps.transfers.stateOf(attemptId)).toBe('transferring');
  });
});

describe('audit findings — transfer state must not overstate OR understate', () => {
  /**
   * Four defects an adversarial audit of #48 found in the first fix. All four
   * are the same mistake in different directions: inferring where the photo went
   * from a signal that does not carry that information.
   */
  const run = (over: Record<string, unknown> = {}) =>
    runInferencePipeline({ ...deps, ...over } as PipelineDeps, {
      context: contextFor(JPEG) as never,
      payload: { bytes: JPEG, contentType: 'image/jpeg' },
      auth: AUTH,
      requestId: 'req_0000000000000000',
      clientIp: '127.0.0.1',
    });

  it('F-01: reports resultSuppressed when a result is discarded by an in-flight cancel', async () => {
    // The commonest cancellation there is: the client gives up while the
    // provider call is on the wire. `transferred && !resultDelivered` made
    // `true` structurally impossible here, so the 202 said "no result was
    // thrown away" about an attempt whose result the pipeline threw away.
    const attemptId = 'att_0000000000000000';
    let record: ReturnType<TransferStateTracker['cancel']> = null;

    await expect(
      run({
        orchestrator: {
          analyze: async (_r: unknown, options: { onTransferStarted?: () => void }) => {
            options.onTransferStarted?.();
            // Client cancels mid-call, then the provider answers anyway.
            record = deps.transfers.cancel(attemptId, AUTH.installationId);
            return orchestrator.result;
          },
        } as never,
      }),
    ).rejects.toThrow();

    expect(record).not.toBeNull();
    expect(record!.transferState).toBe('transferring');
    expect(record!.resultSuppressed).toBe(true);
  });

  it('F-02: reaches transferred when the provider answered but the answer was unusable', async () => {
    // A provider that received the whole image and replied 400, 422, or 200
    // with a body the mapper cannot use has admitted the payload. Inferring
    // admission from `response.ok` left the state at `transferring`, whose
    // published claim is that what a provider received is unknown. It was not
    // unknown — the provider answered.
    const attemptId = 'att_0000000000000000';

    await expect(
      run({
        orchestrator: {
          analyze: async (
            _r: unknown,
            options: { onTransferStarted?: () => void; onProviderAdmitted?: () => void },
          ) => {
            options.onTransferStarted?.();
            options.onProviderAdmitted?.();
            return err('malformed-response');
          },
        } as never,
      }),
    ).resolves.toBeDefined();

    expect(deps.transfers.stateOf(attemptId)).toBe('transferred');
  });

  it('F-03: stays pre-transfer when no provider is configured and nothing leaves', async () => {
    // Zero providers is a supported configuration, logged as such. Marking
    // `transferring` unconditionally told a cancelling client that bytes were
    // in flight when none had left the process — and made the published
    // pre-transfer guarantee unreachable through the API.
    const attemptId = 'att_0000000000000000';

    await run({
      orchestrator: {
        // Fires neither signal: no provider was contacted.
        analyze: async () => err('provider-unavailable'),
      } as never,
    });

    expect(deps.transfers.stateOf(attemptId)).toBe('pre-transfer');

    const record = deps.transfers.cancel(attemptId, AUTH.installationId);
    expect(record!.transferState).toBe('pre-transfer');
    expect(record!.bytesTransferred).toBe(0);
    // Nothing was sent, so nothing was suppressed.
    expect(record!.resultSuppressed).toBe(false);
  });

  it('does not report a second transfer when a retry re-sends one payload', async () => {
    // Both signals are once-per-attempt. Firing again on a retry or on pass 2
    // would overstate exposure as badly as the understatement above.
    const attemptId = 'att_0000000000000000';
    await run({
      orchestrator: {
        analyze: async (
          _r: unknown,
          options: { onTransferStarted?: () => void; onProviderAdmitted?: () => void },
        ) => {
          for (let i = 0; i < 3; i += 1) {
            options.onTransferStarted?.();
            options.onProviderAdmitted?.();
          }
          return orchestrator.result;
        },
      } as never,
    });

    expect(deps.transfers.stateOf(attemptId)).toBe('transferred');
  });
});

describe('#49 review — the attempt registry is composed and load-bearing', () => {
  /**
   * The registry was written, tested in isolation, and composed nowhere. So the
   * Redis backend coordinated replay, effects and rate limits while transfer
   * ownership and cancellation stayed process-local: the appearance of
   * distributed state, without the fact of it.
   */
  const registry = () => {
    const held = new Map<string, string>();
    const cancelled = new Set<string>();
    return {
      held,
      cancelled,
      claimAttempt: async (id: string, instance: string) => {
        if (held.has(id)) return false;
        held.set(id, instance);
        return true;
      },
      holderOf: async (id: string) => held.get(id) ?? null,
      markCancelled: async (id: string) => void cancelled.add(id),
      isCancelled: async (id: string) => cancelled.has(id),
    };
  };

  const run = (over: Record<string, unknown>) =>
    runInferencePipeline({ ...deps, ...over } as PipelineDeps, {
      context: contextFor(JPEG) as never,
      payload: { bytes: JPEG, contentType: 'image/jpeg' },
      auth: AUTH,
      requestId: 'req_0000000000000000',
      clientIp: '127.0.0.1',
    });

  it('refuses an attempt another instance already holds', async () => {
    // Two instances each granting the same attempt id is two transfers of one
    // photo. The local tracker cannot see the other instance's grant.
    const shared = registry();
    await shared.claimAttempt('att_0000000000000000', 'instance-b');

    await expect(run({ attempts: shared, instanceId: 'instance-a' })).rejects.toMatchObject({
      gatewayCode: 'validation-error',
    });
    expect(orchestrator.calls).toBe(0);
  });

  it('records this instance as the holder when it wins the attempt', async () => {
    const shared = registry();
    await run({ attempts: shared, instanceId: 'instance-a' });
    expect(await shared.holderOf('att_0000000000000000')).toBe('instance-a');
  });

  it('suppresses a result for an attempt cancelled against another instance', async () => {
    // The gap that made cancellation depend on which replica the DELETE reached.
    const shared = registry();
    await expect(
      run({
        attempts: shared,
        instanceId: 'instance-a',
        orchestrator: {
          analyze: async (
            _r: unknown,
            options: { onTransferStarted?: () => void; onProviderAdmitted?: () => void },
          ) => {
            options.onTransferStarted?.();
            options.onProviderAdmitted?.();
            // Cancelled on a different instance while this one was working.
            await shared.markCancelled('att_0000000000000000');
            return orchestrator.result;
          },
        } as never,
      }),
    ).rejects.toMatchObject({ gatewayCode: 'client-cancelled' });
  });

  it('is inert on the memory backend, where one process is the whole truth', async () => {
    await run({ attempts: null, instanceId: 'only-instance' });
    expect(orchestrator.calls).toBe(1);
  });
});

describe('handover-6 — distributed cancellation is owner-bound and prompt', () => {
  const OWNER = AUTH.installationId;
  const STRANGER = 'inst_stranger';

  /** Owner-bound registry standing in for the Redis one. */
  const registry = () => {
    const held = new Map<string, { owner: string; instance: string }>();
    const cancelled = new Set<string>();
    return {
      cancelled,
      claimAttempt: async (id: string, instance: string, owner: string) => {
        if (held.has(id)) return false;
        held.set(id, { owner, instance });
        return true;
      },
      holderOf: async (id: string, owner: string) => {
        const e = held.get(id);
        return e && e.owner === owner ? e.instance : null;
      },
      markCancelled: async (id: string, owner: string) => {
        const e = held.get(id);
        if (!e || e.owner !== owner) return false;
        cancelled.add(id);
        return true;
      },
      isCancelled: async (id: string) => cancelled.has(id),
    };
  };

  const run = (over: Record<string, unknown>) =>
    runInferencePipeline({ ...deps, ...over } as PipelineDeps, {
      context: contextFor(JPEG) as never,
      payload: { bytes: JPEG, contentType: 'image/jpeg' },
      auth: AUTH,
      requestId: 'req_0000000000000000',
      clientIp: '127.0.0.1',
    });

  it('H-01: a stranger cannot publish a cancellation for an owner attempt', async () => {
    const shared = registry();
    await shared.claimAttempt('att_victim', 'instance-a', OWNER);

    expect(await shared.markCancelled('att_victim', STRANGER)).toBe(false);
    expect(await shared.isCancelled('att_victim')).toBe(false);
    // And a stranger cannot even learn it exists.
    expect(await shared.holderOf('att_victim', STRANGER)).toBeNull();
  });

  it('H-02: aborts the in-flight provider call, not just the result', async () => {
    // The provider fetch used to keep running until it finished on its own,
    // because the registry was read only after analyze() resolved. `transferring`
    // promises the transport is ABORTED, so suppressing a completed result is
    // not the contract's claim.
    const shared = registry();
    let sawAbort = false;

    await expect(
      run({
        attempts: shared,
        instanceId: 'instance-a',
        orchestrator: {
          analyze: async (
            _r: unknown,
            options: { signal: AbortSignal; onTransferStarted?: () => void },
          ) => {
            options.onTransferStarted?.();
            // Owner cancels via ANOTHER replica while this call is on the wire.
            shared.cancelled.add('att_0000000000000000');
            // Wait for the watcher to notice and abort this process's signal.
            await new Promise<void>((resolve) => {
              if (options.signal.aborted) {
                sawAbort = true;
                resolve();
                return;
              }
              options.signal.addEventListener('abort', () => {
                sawAbort = true;
                resolve();
              });
              setTimeout(resolve, 3000);
            });
            return orchestrator.result;
          },
        } as never,
      }),
    ).rejects.toMatchObject({ gatewayCode: 'client-cancelled' });

    expect(sawAbort, 'the provider signal was never aborted').toBe(true);
  }, 10_000);

  it('H-02: aborts when the marker was written before the request arrived', async () => {
    // The interleaving a late-starting subscriber would miss.
    const shared = registry();
    shared.cancelled.add('att_0000000000000000');
    let abortedAtEntry = false;

    await expect(
      run({
        attempts: shared,
        instanceId: 'instance-a',
        orchestrator: {
          analyze: async (_r: unknown, options: { signal: AbortSignal }) => {
            abortedAtEntry = options.signal.aborted;
            return orchestrator.result;
          },
        } as never,
      }),
    ).rejects.toMatchObject({ gatewayCode: 'client-cancelled' });

    expect(abortedAtEntry, 'a pre-existing cancellation was not seen').toBe(true);
  });

  it('H-03: releases the local attempt when the remote claim throws', async () => {
    // The remote claim used to sit outside the try, so a Redis throw left the
    // local entry active for the life of the process with its AbortController
    // orphaned, and the spent request-id/effect claims made every retry look
    // like a duplicate.
    const attemptId = 'att_0000000000000000';
    await expect(
      run({
        attempts: {
          claimAttempt: async () => {
            throw new Error('ECONNREFUSED');
          },
          holderOf: async () => null,
          markCancelled: async () => false,
          isCancelled: async () => false,
        },
        instanceId: 'instance-a',
      }),
    ).rejects.toThrow();

    expect(deps.transfers.activeCount, 'the local attempt leaked').toBe(0);
    expect(disposed, 'the payload was not disposed').toContain(attemptId);
    expect(orchestrator.calls, 'a provider was called despite the failure').toBe(0);
  });
});
