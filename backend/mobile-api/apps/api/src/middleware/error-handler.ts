import { GatewayError, GATEWAY_CODES, isGatewayError } from '../security/gateway-errors.js';
import type { GatewayCode } from '../security/gateway-errors.js';

/**
 * Maps anything thrown on the gateway surface to a B-5 taxonomy entry.
 *
 * Nothing reaches a client unmapped. An unrecognised throwable becomes
 * `internal-error` with its detail confined to the log, because a stack trace
 * or a provider message in a response body is an information leak.
 */

export interface MappedErrorResponse {
  readonly status: number;
  readonly body: ReturnType<GatewayError['toBody']>;
  readonly headers: Readonly<Record<string, string>>;
  /** Kept for logging only; never serialized. */
  readonly cause: unknown;
}

const FASTIFY_CODE_MAP: Readonly<Record<string, GatewayCode>> = {
  FST_ERR_CTP_BODY_TOO_LARGE: GATEWAY_CODES.CONTENT_TOO_LARGE,
  FST_REQ_FILE_TOO_LARGE: GATEWAY_CODES.CONTENT_TOO_LARGE,
  FST_ERR_CTP_INVALID_MEDIA_TYPE: GATEWAY_CODES.UNSUPPORTED_MEDIA_TYPE,
  FST_ERR_CTP_EMPTY_JSON_BODY: GATEWAY_CODES.VALIDATION_ERROR,
  FST_ERR_VALIDATION: GATEWAY_CODES.VALIDATION_ERROR,
};

export const toGatewayError = (error: unknown): GatewayError => {
  if (isGatewayError(error)) return error;

  if (error !== null && typeof error === 'object') {
    const code = (error as { code?: unknown }).code;
    if (typeof code === 'string' && code in FASTIFY_CODE_MAP) {
      return new GatewayError(FASTIFY_CODE_MAP[code] as GatewayCode, { cause: error });
    }
    const name = (error as { name?: unknown }).name;
    if (name === 'AbortError') {
      return new GatewayError(GATEWAY_CODES.CLIENT_CANCELLED, { cause: error });
    }
  }

  return new GatewayError(GATEWAY_CODES.INTERNAL_ERROR, { cause: error });
};

export const mapError = (error: unknown, correlationId: string): MappedErrorResponse => {
  const gatewayError = toGatewayError(error);
  const headers: Record<string, string> = { 'X-Correlation-Id': correlationId };

  // Retry-After is advertised only where the taxonomy says the client may
  // retry. Advertising it on a terminal failure invites a pointless retry.
  if (gatewayError.recoverable && gatewayError.retryAfter > 0) {
    headers['Retry-After'] = String(gatewayError.retryAfter);
  }

  return {
    status: gatewayError.httpStatus,
    body: gatewayError.toBody(correlationId),
    headers,
    cause: gatewayError.cause,
  };
};
