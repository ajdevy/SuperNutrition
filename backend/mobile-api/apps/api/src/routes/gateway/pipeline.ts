import type { Modality } from '@supernutrition/shared';
import {
  GATEWAY_CODES,
  GATEWAY_ERRORS,
  GatewayError,
  type AuthContext,
  type EffectStore,
  type GatewayCode,
  type MarketResolution,
  type RateLimiter,
} from '../../security/index.js';
import { enforceRateLimits, rateLimitHeaders } from '../../middleware/index.js';
import type { InferenceOrchestrator } from '../../modules/inference/index.js';
import {
  assertRemoteEnabled,
  assertTransferAuthorized,
  validateRequest,
  verifyConsentAndMarket,
  EFFECT_TTL_SECONDS,
  REQUEST_ID_TTL_SECONDS,
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
  /**
   * One-time claim per `X-Request-Id`. Separate from `effects` so a replayed
   * request ID and a duplicated transfer effect cannot collide in one keyspace:
   * they are different identifiers with different lifetimes and different
   * answers (`token-replayed` vs `validation-error`).
   */
  readonly requestIds: EffectStore;
  readonly payloads: PayloadSink;
  readonly transfers: TransferStateTracker;
  readonly resolveMarket: (marketId: string, modality: Modality, now: Date) => MarketResolution;
  readonly remoteInferenceEnabled: () => boolean;
  readonly supportedRetentionVersions: readonly string[];
  /** Configuration versions this deployment actually runs (H-06). */
  readonly supportedConfigurationVersions: readonly string[];
  readonly now: () => Date;
}

export interface PipelineInput {
  readonly context: InferenceRequestInput;
  readonly payload: { bytes: Uint8Array; contentType: string };
  readonly auth: AuthContext;
  readonly clientIp: string;
  /** Validated `X-Request-Id`. One-time: a replay is rejected, never served. */
  readonly requestId: string;
}

export const runInferencePipeline = async (
  deps: PipelineDeps,
  input: PipelineInput,
): Promise<{ status: number; body: GatewayResponseBody; headers?: Record<string, string> }> => {
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

  // 2b — replay detection (AD-21). Claimed AFTER authentication so a rejected
  // caller cannot burn a legitimate client's request ID, and BEFORE the rate
  // limiter so a replay costs nothing from the caller's budget. Retrying this
  // request can never succeed, which is why the taxonomy marks
  // `token-replayed` unrecoverable: a retry must carry a fresh identifier.
  const freshRequestId = await deps.requestIds.claimOnce(
    'request-id',
    input.requestId,
    REQUEST_ID_TTL_SECONDS,
  );
  if (!freshRequestId) {
    throw new GatewayError(GATEWAY_CODES.TOKEN_REPLAYED, {
      details: [{ field: 'X-Request-Id', issue: 'already_used' }],
    });
  }

  // 3 — rate limits, per installation and per IP.
  const rateDecision = await enforceRateLimits({
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
    supportedConfigurationVersions: deps.supportedConfigurationVersions,
    requestConfigurationVersion: context.inferenceContext.configurationVersion,
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

  // M7 — at-most-once on the ATTEMPT, not only on the effect. claimOnce above
  // keys on effectId, so the same attemptId carrying a fresh effectId reached
  // here and started a second transfer of the same photo. The claim is bound to
  // the installation, which is what later authorises a cancellation.
  const controller = deps.transfers.begin(context.attemptId, auth.installationId);
  if (controller === null) {
    throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
      details: [{ field: 'attemptId', issue: 'attempt_already_used' }],
    });
  }
  deps.payloads.put(context.attemptId, payload.bytes);

  try {
    // #48 — neither transition is made here any more.
    //
    // Both used to run back to back before the provider was called at all, so an
    // attempt the provider refused, timed out on, or reset was recorded as
    // complete. Marking `transferring` here was wrong for the mirror-image
    // reason: this code cannot tell a configured provider from an absent one, so
    // a deployment with no provider — supported, and logged as such — reported
    // bytes in flight when nothing had left the process.
    //
    // Both signals now come from the adapter, which is the only layer that knows
    // when bytes go on the wire and when a provider answers.

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
        onTransferStarted: () =>
          deps.transfers.markTransferring(context.attemptId, payload.bytes.byteLength),
        onProviderAdmitted: () =>
          deps.transfers.markTransferred(context.attemptId, payload.bytes.byteLength),
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
        : abstentionOutcome('no-food-detected')
      : outcomeForFailure(result.error);

    // The client is about to hold this result. A cancellation arriving after
    // this point cannot honestly say the result was suppressed, so the tracker
    // is told before `finally` retires the attempt.
    deps.transfers.markResultDelivered(context.attemptId);

    return {
      status: 200,
      headers: { ...rateLimitHeaders(rateDecision) },
      body: {
        contractVersion: 'mobile-api/v1',
        requestId: input.requestId,
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

/** Closed set of abstention reasons (gateway-response.toml). */
type AbstentionReason =
  | 'no-food-detected'
  | 'image-unusable'
  | 'ambiguous-scene'
  | 'below-confidence-floor'
  | 'content-rejected-by-provider';

const ABSTENTION_MESSAGE_KEYS: Readonly<Record<AbstentionReason, string>> = {
  'no-food-detected': 'inference.abstention.no_food_detected',
  'image-unusable': 'inference.abstention.image_unusable',
  'ambiguous-scene': 'inference.abstention.ambiguous_scene',
  'below-confidence-floor': 'inference.abstention.below_confidence_floor',
  'content-rejected-by-provider': 'inference.abstention.content_rejected_by_provider',
};

const abstentionOutcome = (reasonCode: AbstentionReason): OutcomeOut => ({
  outcomeType: 'abstention',
  reasonCode,
  userMessageKey: ABSTENTION_MESSAGE_KEYS[reasonCode],
  // Always true in v1: manual entry and catalog search work without this
  // service, which is what makes a fail-closed remote path a degraded
  // experience rather than an outage.
  manualAlternativeAvailable: true,
});

/**
 * Internal failure kinds mapped onto released gateway codes.
 *
 * `outcome.failureCode` is contractually "one gatewayCode from
 * error-taxonomy.toml", so the orchestrator's own vocabulary (`timeout`,
 * `cancelled`, `malformed-response`) may not appear on the wire: a client
 * branching on the taxonomy would meet a discriminant it has never heard of.
 */
const FAILURE_CODES: Readonly<Record<string, GatewayCode>> = {
  timeout: GATEWAY_CODES.PROVIDER_TIMEOUT,
  'rate-limited': GATEWAY_CODES.RATE_LIMITED,
  'provider-unavailable': GATEWAY_CODES.PROVIDER_UNAVAILABLE,
  'malformed-response': GATEWAY_CODES.INTERNAL_ERROR,
  cancelled: GATEWAY_CODES.CLIENT_CANCELLED,
  'budget-exceeded': GATEWAY_CODES.BUDGET_EXCEEDED,
  'kill-switch': GATEWAY_CODES.KILL_SWITCH,
};

/**
 * A provider failure is returned INSIDE a 200 envelope as a failure outcome,
 * not as an HTTP error: the request itself was valid and fully processed, and
 * the client needs the resolved market values that a bare error body omits.
 *
 * A provider refusing the content is the one case that is NOT a failure. The
 * attempt succeeded and the answer is "I will not identify this", which the
 * release models as an abstention with `content-rejected-by-provider` — so the
 * client offers manual entry rather than a retry that cannot help.
 */
const outcomeForFailure = (failure: string): OutcomeOut => {
  if (failure === 'content-rejected') return abstentionOutcome('content-rejected-by-provider');

  const code = FAILURE_CODES[failure] ?? GATEWAY_CODES.INTERNAL_ERROR;
  // userMessageKey and recoverable MIRROR the taxonomy entry rather than being
  // restated here; a second copy is a second thing to drift.
  const spec = GATEWAY_ERRORS[code];
  return {
    outcomeType: 'failure',
    failureCode: code,
    userMessageKey: spec.userMessageKey,
    recoverable: spec.recoverable,
    // "Present only when recoverable and a wait is required."
    retryAfterSeconds: spec.recoverable && spec.retryAfter > 0 ? spec.retryAfter : null,
  };
};
