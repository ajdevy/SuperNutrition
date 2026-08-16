import { AppError, ERROR_CODES, isAppError, type ErrorCode } from '@supernutrition/shared';
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

  // The rest of @fastify/multipart's error surface. Only two of its codes were
  // mapped, so a request with too many files, too many fields, too many parts, a
  // truncated body or a malformed multipart content-type fell through to
  // internal-error: the gateway reporting a server fault for something the
  // client got wrong, and telling the client to retry a request that cannot
  // succeed however many times it is sent.
  FST_FILES_LIMIT: GATEWAY_CODES.CONTENT_TOO_LARGE,
  FST_FIELDS_LIMIT: GATEWAY_CODES.CONTENT_TOO_LARGE,
  FST_PARTS_LIMIT: GATEWAY_CODES.CONTENT_TOO_LARGE,
  FST_MP_PREMATURE_CLOSE: GATEWAY_CODES.VALIDATION_ERROR,
  FST_INVALID_MULTIPART_CONTENT_TYPE: GATEWAY_CODES.UNSUPPORTED_MEDIA_TYPE,
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
    // The TEST_MODE fence declining to authenticate: production verification is
    // not wired, so the surface refuses rather than accepting anyone (G-4,
    // AD-21). That is the fence working, and answering 500 reported a server
    // fault for a correct refusal — and invited a retry that cannot succeed.
    //
    // Matched by name rather than by importing the class, the same way
    // AbortError is: the error handler stays free of a dependency on the config
    // layer it exists to report on.
    if (name === 'TestModeFenceError') {
      return new GatewayError(GATEWAY_CODES.TEST_MODE_FORBIDDEN, { cause: error });
    }

    // #49 — the shared coordination store is unreachable.
    //
    // The store already fails closed and refuses the attempt, which is right.
    // What was wrong was the answer the client got: unmapped, this fell through
    // to `internal-error` — 500, recoverable:false, retryAfter 0 — telling a
    // user to give up on a request that will work as soon as Redis is back.
    //
    // `provider-unavailable` is a compromise and is worth naming as one: the
    // published taxonomy is frozen and has no code for "a dependency this
    // service needs is down". Of the codes that exist this is the only one whose
    // shape is right — 503, recoverable, retryAfter 60 — and the shape is what
    // a client acts on. The name points at the wrong subsystem; that is a
    // smaller inaccuracy than telling someone a transient failure is permanent.
    //
    // Matched by name, like the two above, so the error handler keeps no
    // dependency on the storage layer it reports on.
    if (name === 'RedisUnavailableError') {
      return new GatewayError(GATEWAY_CODES.PROVIDER_UNAVAILABLE, { cause: error });
    }
  }

  return new GatewayError(GATEWAY_CODES.INTERNAL_ERROR, { cause: error });
};

/**
 * The Product API has its own taxonomy (docs/06 §6.2), and a client branches on
 * it. A gateway code returned from `/api/v1` would be a code the product client
 * has never heard of, so the two surfaces map thrown errors separately.
 */
const FASTIFY_PRODUCT_CODE_MAP: Readonly<Record<string, ErrorCode>> = {
  FST_ERR_CTP_BODY_TOO_LARGE: ERROR_CODES.PAYLOAD_TOO_LARGE,
  FST_REQ_FILE_TOO_LARGE: ERROR_CODES.PAYLOAD_TOO_LARGE,
  FST_ERR_CTP_INVALID_MEDIA_TYPE: ERROR_CODES.UNSUPPORTED_MEDIA_TYPE,
  FST_ERR_CTP_EMPTY_JSON_BODY: ERROR_CODES.VALIDATION_FAILED,
  FST_ERR_VALIDATION: ERROR_CODES.VALIDATION_FAILED,
};

export const toProductError = (error: unknown): AppError => {
  if (isAppError(error)) return error;

  if (error !== null && typeof error === 'object') {
    const code = (error as { code?: unknown }).code;
    if (typeof code === 'string' && code in FASTIFY_PRODUCT_CODE_MAP) {
      return new AppError(
        FASTIFY_PRODUCT_CODE_MAP[code] as ErrorCode,
        'Request could not be accepted.',
        { cause: error },
      );
    }
    // Every other content-type-parser fault — an unparseable body, a content
    // length that disagrees with the payload — is the client's request being
    // wrong, not the server failing. Answering 500 would invite a retry of a
    // request that cannot succeed.
    if (typeof code === 'string' && code.startsWith('FST_ERR_CTP_')) {
      return AppError.validation('Request body could not be parsed.', [
        { field: 'body', issue: 'unparseable' },
      ]);
    }
    if ((error as { name?: unknown }).name === 'AbortError') {
      return new AppError(ERROR_CODES.CLIENT_CANCELLED, 'Client cancelled the request.', {
        cause: error,
      });
    }
    // Same refusal, thrown from the product surface's own `authenticate`. The
    // two taxonomies are mapped separately, so it has to be handled twice; the
    // message is deliberately generic, with the detail kept on `cause`.
    if ((error as { name?: unknown }).name === 'TestModeFenceError') {
      return new AppError(
        ERROR_CODES.TEST_MODE_FORBIDDEN,
        'This deployment cannot authenticate requests.',
        { cause: error },
      );
    }
  }

  // Nothing internal is serialized: the detail stays on `cause`, which is
  // logged and never sent.
  return AppError.internal('Internal error.', error);
};

export const mapError = (error: unknown, correlationId: string): MappedErrorResponse => {
  const gatewayError = toGatewayError(error);
  const headers: Record<string, string> = {
    'X-Correlation-Id': correlationId,
    // Headers the throwing site attached — the X-RateLimit-* trio a 429 must
    // carry (rate-limit.toml marks all three non-optional).
    ...gatewayError.headers,
  };

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
