import { GATEWAY_CODES, GatewayError, verifyConsent } from '../../security/index.js';
import type { AuthContext, MarketResolution } from '../../security/index.js';
import { mayAuthorizeRemoteTransfer } from '../../config/index.js';
import type { ConsentReceiptInput } from '../../security/consent.js';

/**
 * The gateway pipeline, as a function of its inputs.
 *
 * Deliberately transport-agnostic: Fastify wiring lives in the route module, so
 * every gate here is exercisable without an HTTP server. That matters because
 * the property under test is "this rejects without calling a provider", and a
 * test that has to stand up a server to prove it tends not to get written.
 *
 * Order is load-bearing. Steps 1–5 must all be able to reject WITHOUT a
 * provider call, because a provider call is the expensive, externally visible,
 * privacy-relevant act (E4.3 acceptance criteria).
 */

export interface CandidateOut {
  readonly candidateId: string;
  readonly label: string;
  readonly rank: number;
  readonly confidence: { score: number; band: string };
  readonly unresolvedAssumptions: readonly { code: string; userMessageKey: string }[];
  readonly provenance: {
    route: string;
    engineId: string;
    modelVersion: string;
    calibrationVersion: string;
    contractVersion: string;
  };
}

export type OutcomeOut =
  | {
      outcomeType: 'candidates';
      candidates: readonly CandidateOut[];
      requiresConfirmation: boolean;
    }
  | {
      outcomeType: 'abstention';
      reasonCode: string;
      userMessageKey: string;
      manualAlternativeAvailable: true;
    }
  | {
      outcomeType: 'failure';
      failureCode: string;
      userMessageKey: string;
      recoverable: boolean;
      retryAfterSeconds: number | null;
    };

export interface GatewayResponseBody {
  readonly contractVersion: string;
  readonly requestId: string;
  readonly attemptId: string;
  readonly resolvedMarketJurisdiction: string;
  readonly resolvedProcessingRegion: string | null;
  readonly outcome: OutcomeOut;
}

export const SUPPORTED_CONTRACT_VERSIONS = ['mobile-api/v1'] as const;
export const ALLOWED_CONTENT_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export const MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
export const EFFECT_TTL_SECONDS = 86_400;

const ATTEMPT_PATTERN = /^att_[A-Za-z0-9]{16,64}$/;
const EFFECT_PATTERN = /^eff_[A-Za-z0-9]{16,64}$/;
const REQUEST_ID_PATTERN = /^req_[A-Za-z0-9]{16,64}$/;

/** How long a spent X-Request-Id stays unusable. Matches the effect window. */
export const REQUEST_ID_TTL_SECONDS = 86_400;

/**
 * Header gates, in the order `gateway-request.toml [validation_order]` sets:
 * contract-version, then authentication, then request-id-replay. Both headers
 * are `optional = false` in the release, so an absent one is as much a
 * rejection as a wrong one — a caller that omits the version is not asserting
 * compatibility with anything, and accepting it would silently bind them to
 * whatever the server happens to speak today.
 *
 * The replay CLAIM is not made here: it must happen after authentication, and
 * spending a one-time identifier on a request that is about to fail auth would
 * make a legitimate retry impossible.
 */
export const validateGatewayHeaders = (headers: {
  contractVersion: string | undefined;
  requestId: string | undefined;
}): { requestId: string } => {
  if (
    headers.contractVersion === undefined ||
    !SUPPORTED_CONTRACT_VERSIONS.includes(headers.contractVersion as 'mobile-api/v1')
  ) {
    throw new GatewayError(GATEWAY_CODES.UNSUPPORTED_VERSION, {
      details: [
        {
          field: 'X-Contract-Version',
          issue: headers.contractVersion === undefined ? 'missing' : 'unsupported',
        },
      ],
    });
  }

  if (headers.requestId === undefined || !REQUEST_ID_PATTERN.test(headers.requestId)) {
    throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
      details: [
        {
          field: 'X-Request-Id',
          issue: headers.requestId === undefined ? 'missing' : 'malformed',
        },
      ],
    });
  }

  return { requestId: headers.requestId };
};

/** Magic bytes, so a declared content type that lies is rejected. */
const MAGIC: Readonly<Record<string, readonly number[][]>> = {
  'image/jpeg': [[0xff, 0xd8, 0xff]],
  'image/png': [[0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]],
  'image/webp': [[0x52, 0x49, 0x46, 0x46]],
};

export const magicBytesMatch = (contentType: string, bytes: Uint8Array): boolean => {
  const signatures = MAGIC[contentType];
  if (!signatures) return false;
  return signatures.some((sig) => sig.every((byte, index) => bytes[index] === byte));
};

export interface InferenceRequestInput {
  readonly contractVersion: string;
  readonly attemptId: string;
  readonly effectId: string;
  readonly modality: string;
  readonly claimedMarketJurisdiction?: string | null;
  readonly consentReceipt: ConsentReceiptInput;
  readonly inferenceContext: {
    readonly catalogCountry: string;
    readonly mealType: string;
    readonly locale: string;
    readonly measurementSystem: string;
    readonly configurationVersion: string;
    readonly priorMealSummary?: string | null;
  };
}

/** Step 1 — shape and payload validation. Cheapest rejection first. */
export const validateRequest = (
  input: InferenceRequestInput,
  payload: { bytes: Uint8Array; contentType: string },
): void => {
  if (!SUPPORTED_CONTRACT_VERSIONS.includes(input.contractVersion as 'mobile-api/v1')) {
    throw new GatewayError(GATEWAY_CODES.UNSUPPORTED_VERSION, {
      details: [{ field: 'contractVersion', issue: 'unsupported' }],
    });
  }
  if (!ATTEMPT_PATTERN.test(input.attemptId)) {
    throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
      details: [{ field: 'attemptId', issue: 'malformed' }],
    });
  }
  if (!EFFECT_PATTERN.test(input.effectId)) {
    throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
      details: [{ field: 'effectId', issue: 'malformed' }],
    });
  }
  if (input.modality !== 'photo') {
    throw new GatewayError(GATEWAY_CODES.INVALID_MODALITY, {
      details: [{ field: 'modality', issue: 'unsupported' }],
    });
  }
  if (payload.bytes.byteLength === 0) {
    throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
      details: [{ field: 'payload', issue: 'missing-normalized-photo-payload' }],
    });
  }
  if (payload.bytes.byteLength > MAX_PAYLOAD_BYTES) {
    throw new GatewayError(GATEWAY_CODES.CONTENT_TOO_LARGE, {
      details: [{ field: 'payload', issue: 'exceeds_maximum' }],
    });
  }
  if (!ALLOWED_CONTENT_TYPES.includes(payload.contentType as 'image/jpeg')) {
    throw new GatewayError(GATEWAY_CODES.UNSUPPORTED_MEDIA_TYPE, {
      details: [{ field: 'payload', issue: 'content_type_not_allowlisted' }],
    });
  }
  if (!magicBytesMatch(payload.contentType, payload.bytes)) {
    throw new GatewayError(GATEWAY_CODES.UNSUPPORTED_MEDIA_TYPE, {
      details: [{ field: 'payload', issue: 'magic_bytes_mismatch' }],
    });
  }
};

/** Step 4b — a test-mode token may never reach a real market's transfer path. */
export const assertTransferAuthorized = (auth: AuthContext, marketId: string): void => {
  if (!mayAuthorizeRemoteTransfer(auth.authMode, marketId)) {
    throw new GatewayError(GATEWAY_CODES.TEST_MODE_FORBIDDEN, {
      details: [{ field: 'authMode', issue: 'test_mode_cannot_reach_real_market' }],
    });
  }
};

/** Step 0 — operator kill switch (AD-24). Effective without a deploy. */
export const assertRemoteEnabled = (enabled: boolean): void => {
  if (!enabled) throw new GatewayError(GATEWAY_CODES.KILL_SWITCH);
};

/** Step 4 — consent, market posture, and region, all fail-closed. */
export const verifyConsentAndMarket = (input: {
  readonly receipt: ConsentReceiptInput;
  readonly payload: Uint8Array;
  readonly attemptId: string;
  readonly modality: string;
  readonly market: MarketResolution;
  readonly now: Date;
  readonly knownConsentCopyVersions: readonly string[];
  readonly supportedRetentionVersions: readonly string[];
}): void => {
  verifyConsent({
    receipt: input.receipt,
    payload: input.payload,
    attemptId: input.attemptId,
    modality: input.modality,
    market: input.market,
    now: input.now,
    knownConsentCopyVersions: input.knownConsentCopyVersions,
    supportedRetentionVersions: input.supportedRetentionVersions,
    supportedConfigurationVersions: null,
  });
};
