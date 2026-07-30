import { describe, it, expect, beforeEach } from 'vitest';
import { createHash } from 'node:crypto';
import { ok, type Result } from '@supernutrition/shared';
import { runInferencePipeline, type PipelineDeps } from './pipeline.js';
import { TransferStateTracker } from './transfer-state.js';
import {
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

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46]);
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

  async analyze(_request: InferenceRequest): Promise<Result<InferenceResult, InferenceFailure>> {
    this.calls += 1;
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
  consentReceipt: receiptFor(bytes),
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
    payloads: {
      put: () => {},
      dispose: (attemptId) => disposed.push(attemptId),
    },
    transfers: new TransferStateTracker(() => NOW),
    resolveMarket: (marketId, modality, now) => resolver.resolve(marketId, modality, now),
    remoteInferenceEnabled: () => true,
    supportedRetentionVersions: ['retention/2'],
    now: () => NOW,
    requestIdFactory: () => 'req_0000000000000000',
  };
});

const run = (context: unknown, bytes: Uint8Array = JPEG) =>
  runInferencePipeline(deps, {
    context: context as never,
    payload: { bytes, contentType: 'image/jpeg' },
    auth: AUTH,
    clientIp: '203.0.113.5',
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
});
