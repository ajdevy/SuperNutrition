import type { Modality } from '@supernutrition/shared';
import {
  GATEWAY_CODES,
  GatewayError,
  type AuthContext,
  type EffectStore,
  type MarketResolution,
  type RateLimiter,
} from '../../security/index.js';
import { enforceRateLimits } from '../../middleware/index.js';
import type { InferenceOrchestrator } from '../../modules/inference/index.js';
import {
  assertRemoteEnabled,
  assertTransferAuthorized,
  validateRequest,
  verifyConsentAndMarket,
  EFFECT_TTL_SECONDS,
  type GatewayResponseBody,
  type InferenceRequestInput,
  type OutcomeOut,
} from './inference.handler.js';
import type { TransferStateTracker } from './transfer-state.js';

/**
 * Executes GATEWAY_PIPELINE in order.
 *
 * Transport-free by design: this takes plain inputs and returns a plain body,
 * so every gate is testable without an HTTP server. The single most important
 * property — that steps 1..5 reject with ZERO provider calls — is asserted by
 * counting calls on a fake orchestrator, which only works if the pipeline is
 * callable directly.
 */

export interface PayloadSink {
  put(attemptId: string, bytes: Uint8Array): void;
  dispose(attemptId: string): void;
}

export interface PipelineDeps {
  readonly orchestrator: InferenceOrchestrator;
  readonly limiter: RateLimiter;
  readonly effects: EffectStore;
  readonly payloads: PayloadSink;
  readonly transfers: TransferStateTracker;
  readonly resolveMarket: (marketId: string, modality: Modality, now: Date) => MarketResolution;
  readonly remoteInferenceEnabled: () => boolean;
  readonly supportedRetentionVersions: readonly string[];
  readonly now: () => Date;
  readonly requestIdFactory: () => string;
}

export interface PipelineInput {
  readonly context: InferenceRequestInput;
  readonly payload: { bytes: Uint8Array; contentType: string };
  readonly auth: AuthContext;
  readonly clientIp: string;
}

export const runInferencePipeline = async (
  deps: PipelineDeps,
  input: PipelineInput,
): Promise<{ status: number; body: GatewayResponseBody }> => {
  const now = deps.now();
  const { context, payload, auth } = input;

  // 0 — kill switch, before anything is parsed or charged for.
  assertRemoteEnabled(deps.remoteInferenceEnabled());

  // 1 — parse and validate.
  validateRequest(context, payload);

  // 2 — authentication happened upstream; scope is re-checked here so a route
  // wired without the auth hook cannot silently accept anonymous callers.
  if (auth.scope !== 'gateway' && auth.scope !== 'both') {
    throw new GatewayError(GATEWAY_CODES.INSUFFICIENT_SCOPE);
  }

  // 3 — rate limits, per installation and per IP.
  await enforceRateLimits({
    limiter: deps.limiter,
    keys: [
      { layer: 'installation', principal: auth.installationId },
      { layer: 'ip', principal: input.clientIp },
    ],
  });

  // 4 — market resolution, then consent. The jurisdiction is resolved
  // server-side; the client's claim is a lookup hint and nothing more.
  const modality = context.modality as Modality;
  const market = deps.resolveMarket(
    context.claimedMarketJurisdiction ?? context.consentReceipt.marketJurisdiction,
    modality,
    now,
  );

  verifyConsentAndMarket({
    receipt: context.consentReceipt,
    payload: payload.bytes,
    attemptId: context.attemptId,
    modality: context.modality,
    market,
    now,
    knownConsentCopyVersions: market.consentCopyVersion ? [market.consentCopyVersion] : [],
    supportedRetentionVersions: deps.supportedRetentionVersions,
  });

  // 4b — the test-mode fence is the LAST gate before transfer, not the first.
  // Applied earlier it would mask a market denial: an unresolved market is
  // refused because it is unresolved, which is the more useful answer, and a
  // test-mode token only needs stopping where the market would otherwise
  // permit (docs/09 §9.2).
  assertTransferAuthorized(auth, market.marketJurisdiction);

  // 5 — at-most-once transfer start (AD-18). A restored app or a retried
  // request must not start a second transfer.
  const claimed = await deps.effects.claimOnce(
    context.attemptId,
    context.effectId,
    EFFECT_TTL_SECONDS,
  );
  if (!claimed) {
    throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
      details: [{ field: 'effectId', issue: 'duplicate_effect' }],
    });
  }

  // ---- everything below may call a provider -------------------------------

  const controller = deps.transfers.begin(context.attemptId);
  deps.payloads.put(context.attemptId, payload.bytes);

  try {
    deps.transfers.markTransferring(context.attemptId, payload.bytes.byteLength);
    deps.transfers.markTransferred(context.attemptId, payload.bytes.byteLength);

    // 6 — orchestrate.
    const result = await deps.orchestrator.analyze(
      {
        attemptId: context.attemptId as never,
        modality,
        payload: {
          attemptId: context.attemptId as never,
          byteLength: payload.bytes.byteLength,
          contentType: payload.contentType,
          digest: context.consentReceipt.digest,
        },
        context: {
          catalogCountry: context.inferenceContext.catalogCountry,
          mealType: context.inferenceContext.mealType,
          locale: context.inferenceContext.locale,
          measurementSystem: context.inferenceContext.measurementSystem,
          ...(context.inferenceContext.priorMealSummary
            ? { priorMealSummary: context.inferenceContext.priorMealSummary }
            : {}),
        },
        contractVersion: context.contractVersion,
      },
      {
        signal: controller.signal,
        marketJurisdiction: market.marketJurisdiction,
        processingRegion: market.processingRegion,
      },
    );

    // A late result after cancellation is discarded, never returned: exactly
    // one terminal outcome per attempt (AD-6).
    if (deps.transfers.isCancelled(context.attemptId)) {
      throw new GatewayError(GATEWAY_CODES.CLIENT_CANCELLED);
    }

    // 7 — normalize to contract-v1 evidence.
    const outcome: OutcomeOut = result.ok
      ? result.value.outcome === 'candidates'
        ? {
            outcomeType: 'candidates',
            candidates: result.value.candidates.map((candidate) => ({
              candidateId: candidate.candidateId,
              label: candidate.label,
              rank: candidate.rank,
              confidence: {
                score: candidate.confidence.score,
                band: candidate.confidence.band,
              },
              unresolvedAssumptions: candidate.unresolvedAssumptions,
              provenance: candidate.provenance,
            })),
            requiresConfirmation: result.value.requiresConfirmation,
          }
        : {
            outcomeType: 'abstention',
            reasonCode: 'no-food-detected',
            userMessageKey: 'inference.abstention.no_food_detected',
            manualAlternativeAvailable: true,
          }
      : failureOutcome(result.error);

    return {
      status: 200,
      body: {
        contractVersion: 'mobile-api/v1',
        requestId: deps.requestIdFactory(),
        attemptId: context.attemptId,
        resolvedMarketJurisdiction: market.marketJurisdiction,
        resolvedProcessingRegion: market.processingRegion,
        outcome,
      },
    };
  } finally {
    // 8 — disposal, on EVERY terminal path: success, error, timeout, and
    // cancellation are the same code path, not four (AD-8, docs/09 §9.6).
    deps.payloads.dispose(context.attemptId);
    deps.transfers.release(context.attemptId);
  }
};

const FAILURE_MESSAGE_KEYS: Readonly<
  Record<string, { key: string; recoverable: boolean; retry: number | null }>
> = {
  timeout: { key: 'error.provider_timeout', recoverable: true, retry: 15 },
  'rate-limited': { key: 'error.rate_limited', recoverable: true, retry: 30 },
  'provider-unavailable': { key: 'error.provider_unavailable', recoverable: true, retry: 60 },
  'content-rejected': { key: 'error.invalid_modality', recoverable: false, retry: null },
  'malformed-response': { key: 'error.internal', recoverable: false, retry: null },
  cancelled: { key: 'error.cancelled', recoverable: false, retry: null },
  'budget-exceeded': { key: 'error.budget_exceeded', recoverable: false, retry: null },
  'kill-switch': { key: 'error.remote_disabled', recoverable: true, retry: 300 },
};

/**
 * A provider failure is returned INSIDE a 200 envelope as a failure outcome,
 * not as an HTTP error: the request itself was valid and fully processed, and
 * the client needs the resolved market values that a bare error body omits.
 */
const failureOutcome = (failure: string): OutcomeOut => {
  const spec = FAILURE_MESSAGE_KEYS[failure] ?? {
    key: 'error.internal',
    recoverable: false,
    retry: null,
  };
  return {
    outcomeType: 'failure',
    failureCode: failure,
    userMessageKey: spec.key,
    recoverable: spec.recoverable,
    retryAfterSeconds: spec.retry,
  };
};
