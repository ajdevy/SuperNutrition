/**
 * The B-5 error taxonomy, as code.
 *
 * `contracts/mobile-api/v1/error-taxonomy.toml` is the source of truth. These
 * values mirror it exactly, and `gateway-contract.test.ts` reads the released
 * TOML and asserts the two agree — so a contract change that is not reflected
 * here fails the build rather than drifting silently.
 */

export const GATEWAY_CODES = {
  VALIDATION_ERROR: 'validation-error',
  INVALID_MODALITY: 'invalid-modality',
  MALFORMED_CONSENT: 'malformed-consent',
  CONSENT_EXPIRED: 'consent-expired',
  UNSUPPORTED_VERSION: 'unsupported-version',

  AUTHENTICATION_FAILED: 'authentication-failed',
  SESSION_EXPIRED: 'session-expired',
  TOKEN_REPLAYED: 'token-replayed',

  BUDGET_EXCEEDED: 'budget-exceeded',

  CONSENT_REQUIRED: 'consent-required',
  INSUFFICIENT_SCOPE: 'insufficient-scope',
  MARKET_NOT_PERMITTED: 'market-not-permitted',
  TEST_MODE_FORBIDDEN: 'test-mode-forbidden',

  CONTENT_TOO_LARGE: 'content-too-large',
  UNSUPPORTED_MEDIA_TYPE: 'unsupported-media-type',

  RATE_LIMITED: 'rate-limited',
  CLIENT_CANCELLED: 'client-cancelled',

  INTERNAL_ERROR: 'internal-error',
  PROVIDER_UNAVAILABLE: 'provider-unavailable',
  KILL_SWITCH: 'kill-switch',
  PROVIDER_TIMEOUT: 'provider-timeout',
} as const;

export type GatewayCode = (typeof GATEWAY_CODES)[keyof typeof GATEWAY_CODES];

export interface GatewayErrorSpec {
  readonly httpStatus: number;
  readonly userMessageKey: string;
  readonly recoverable: boolean;
  readonly retryAfter: number;
}

/**
 * Exhaustive by construction: adding a code without a spec is a compile error,
 * so no gateway failure can fall through to an unmapped 500.
 */
export const GATEWAY_ERRORS: Readonly<Record<GatewayCode, GatewayErrorSpec>> = {
  'validation-error': {
    httpStatus: 400,
    userMessageKey: 'error.validation',
    recoverable: true,
    retryAfter: 0,
  },
  'invalid-modality': {
    httpStatus: 400,
    userMessageKey: 'error.invalid_modality',
    recoverable: false,
    retryAfter: 0,
  },
  'malformed-consent': {
    httpStatus: 400,
    userMessageKey: 'error.consent_malformed',
    recoverable: false,
    retryAfter: 0,
  },
  'consent-expired': {
    httpStatus: 400,
    userMessageKey: 'error.consent_expired',
    recoverable: false,
    retryAfter: 0,
  },
  'unsupported-version': {
    httpStatus: 400,
    userMessageKey: 'error.unsupported_version',
    recoverable: false,
    retryAfter: 0,
  },
  'authentication-failed': {
    httpStatus: 401,
    userMessageKey: 'error.authentication_failed',
    recoverable: false,
    retryAfter: 0,
  },
  'session-expired': {
    httpStatus: 401,
    userMessageKey: 'error.session_expired',
    recoverable: true,
    retryAfter: 0,
  },
  'token-replayed': {
    httpStatus: 401,
    userMessageKey: 'error.request_replayed',
    recoverable: false,
    retryAfter: 0,
  },
  'budget-exceeded': {
    httpStatus: 402,
    userMessageKey: 'error.budget_exceeded',
    recoverable: false,
    retryAfter: 0,
  },
  'consent-required': {
    httpStatus: 403,
    userMessageKey: 'error.consent_required',
    recoverable: false,
    retryAfter: 0,
  },
  'insufficient-scope': {
    httpStatus: 403,
    userMessageKey: 'error.insufficient_scope',
    recoverable: false,
    retryAfter: 0,
  },
  'market-not-permitted': {
    httpStatus: 403,
    userMessageKey: 'error.market_not_permitted',
    recoverable: false,
    retryAfter: 0,
  },
  'test-mode-forbidden': {
    httpStatus: 403,
    userMessageKey: 'error.test_mode_forbidden',
    recoverable: false,
    retryAfter: 0,
  },
  'content-too-large': {
    httpStatus: 413,
    userMessageKey: 'error.content_too_large',
    recoverable: false,
    retryAfter: 0,
  },
  'unsupported-media-type': {
    httpStatus: 415,
    userMessageKey: 'error.unsupported_media_type',
    recoverable: false,
    retryAfter: 0,
  },
  'rate-limited': {
    httpStatus: 429,
    userMessageKey: 'error.rate_limited',
    recoverable: true,
    retryAfter: 30,
  },
  'client-cancelled': {
    httpStatus: 499,
    userMessageKey: 'error.cancelled',
    recoverable: false,
    retryAfter: 0,
  },
  'internal-error': {
    httpStatus: 500,
    userMessageKey: 'error.internal',
    recoverable: false,
    retryAfter: 0,
  },
  'provider-unavailable': {
    httpStatus: 503,
    userMessageKey: 'error.provider_unavailable',
    recoverable: true,
    retryAfter: 60,
  },
  'kill-switch': {
    httpStatus: 503,
    userMessageKey: 'error.remote_disabled',
    recoverable: true,
    retryAfter: 300,
  },
  'provider-timeout': {
    httpStatus: 504,
    userMessageKey: 'error.provider_timeout',
    recoverable: true,
    retryAfter: 15,
  },
};

export interface GatewayErrorDetail {
  readonly field: string;
  readonly issue: string;
}

/**
 * The only error type crossing the gateway boundary.
 *
 * A provider message or internal detail placed in `cause` is logged and never
 * serialized — every raw upstream error is mapped to one of these codes first.
 */
export class GatewayError extends Error {
  readonly gatewayCode: GatewayCode;
  readonly httpStatus: number;
  readonly userMessageKey: string;
  readonly recoverable: boolean;
  readonly retryAfter: number;
  readonly details: readonly GatewayErrorDetail[];
  override readonly cause?: unknown;

  constructor(
    gatewayCode: GatewayCode,
    options: {
      details?: readonly GatewayErrorDetail[];
      cause?: unknown;
      retryAfterOverride?: number;
    } = {},
  ) {
    const spec = GATEWAY_ERRORS[gatewayCode];
    super(`gateway error: ${gatewayCode}`);
    this.name = 'GatewayError';
    this.gatewayCode = gatewayCode;
    this.httpStatus = spec.httpStatus;
    this.userMessageKey = spec.userMessageKey;
    this.recoverable = spec.recoverable;
    this.retryAfter = options.retryAfterOverride ?? spec.retryAfter;
    this.details = options.details ?? [];
    if (options.cause !== undefined) this.cause = options.cause;
  }

  /** Wire shape. `cause` is deliberately absent. */
  toBody(correlationId: string): {
    error: {
      gatewayCode: GatewayCode;
      userMessageKey: string;
      recoverable: boolean;
      retryAfter: number;
      correlationId: string;
      details: readonly GatewayErrorDetail[];
    };
  } {
    return {
      error: {
        gatewayCode: this.gatewayCode,
        userMessageKey: this.userMessageKey,
        recoverable: this.recoverable,
        retryAfter: this.retryAfter,
        correlationId,
        details: this.details,
      },
    };
  }
}

export const isGatewayError = (value: unknown): value is GatewayError =>
  value instanceof GatewayError;
