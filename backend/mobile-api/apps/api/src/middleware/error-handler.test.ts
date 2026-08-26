import { describe, it, expect } from 'vitest';
import { AppError, ERROR_STATUS } from '@supernutrition/shared';
import { mapError, toGatewayError, toProductError } from './error-handler.js';
import { GatewayError, GATEWAY_CODES, GATEWAY_ERRORS } from '../security/gateway-errors.js';

describe('mapping to the B-5 taxonomy', () => {
  it('passes a GatewayError through unchanged', () => {
    const error = new GatewayError(GATEWAY_CODES.CONSENT_EXPIRED);
    expect(toGatewayError(error)).toBe(error);
  });

  it.each([
    ['FST_ERR_CTP_BODY_TOO_LARGE', 'content-too-large'],
    ['FST_REQ_FILE_TOO_LARGE', 'content-too-large'],
    ['FST_ERR_CTP_INVALID_MEDIA_TYPE', 'unsupported-media-type'],
    ['FST_ERR_CTP_EMPTY_JSON_BODY', 'validation-error'],
    ['FST_ERR_VALIDATION', 'validation-error'],
  ])('maps Fastify %s to %s', (code, expected) => {
    expect(toGatewayError(Object.assign(new Error('x'), { code })).gatewayCode).toBe(expected);
  });

  it('maps an abort to client-cancelled', () => {
    expect(toGatewayError(Object.assign(new Error('x'), { name: 'AbortError' })).gatewayCode).toBe(
      'client-cancelled',
    );
  });

  // Nothing reaches a client unmapped.
  it.each([
    ['a plain error', new Error('boom')],
    ['a string', 'boom'],
    ['null', null],
    ['an unknown fastify code', Object.assign(new Error('x'), { code: 'FST_UNKNOWN' })],
  ])('maps %s to internal-error', (_name, thrown) => {
    expect(toGatewayError(thrown).gatewayCode).toBe('internal-error');
  });
});

describe('response shaping', () => {
  it('carries the correlation id in body and header', () => {
    const mapped = mapError(new GatewayError(GATEWAY_CODES.VALIDATION_ERROR), 'corr-1');
    expect(mapped.body.error.correlationId).toBe('corr-1');
    expect(mapped.headers['X-Correlation-Id']).toBe('corr-1');
  });

  it('advertises Retry-After only where the taxonomy permits a retry', () => {
    expect(mapError(new GatewayError(GATEWAY_CODES.KILL_SWITCH), 'c').headers['Retry-After']).toBe(
      '300',
    );
    expect(mapError(new GatewayError(GATEWAY_CODES.RATE_LIMITED), 'c').headers['Retry-After']).toBe(
      '30',
    );
  });

  it('omits Retry-After on a terminal failure', () => {
    // Advertising a retry on something that can never succeed invites a loop.
    expect(
      mapError(new GatewayError(GATEWAY_CODES.CONSENT_EXPIRED), 'c').headers['Retry-After'],
    ).toBeUndefined();
    expect(
      mapError(new GatewayError(GATEWAY_CODES.INTERNAL_ERROR), 'c').headers['Retry-After'],
    ).toBeUndefined();
  });

  it('omits Retry-After when a recoverable code has no wait', () => {
    expect(
      mapError(new GatewayError(GATEWAY_CODES.SESSION_EXPIRED), 'c').headers['Retry-After'],
    ).toBeUndefined();
  });

  // rate-limit.toml: "Retry-After ... Authoritative; equals the body
  // retryAfter." A client that backs off on the header and a server that meant
  // the body would disagree about when a retry is allowed.
  it('keeps the Retry-After header and the body retryAfter in agreement', () => {
    const mapped = mapError(
      new GatewayError(GATEWAY_CODES.RATE_LIMITED, { retryAfterOverride: 3600 }),
      'c',
    );
    expect(mapped.headers['Retry-After']).toBe('3600');
    expect(mapped.body.error.retryAfter).toBe(3600);
  });

  // The X-RateLimit-* trio belongs to the layer that rejected, which the
  // taxonomy entry cannot know — so the throwing site attaches it.
  it('emits headers the throwing site attached', () => {
    const mapped = mapError(
      new GatewayError(GATEWAY_CODES.RATE_LIMITED, {
        headers: { 'X-RateLimit-Limit': '20', 'X-RateLimit-Remaining': '0' },
      }),
      'c',
    );
    expect(mapped.headers).toMatchObject({
      'X-RateLimit-Limit': '20',
      'X-RateLimit-Remaining': '0',
      'X-Correlation-Id': 'c',
    });
  });

  it('never serializes the cause', () => {
    const mapped = mapError(
      new GatewayError(GATEWAY_CODES.INTERNAL_ERROR, { cause: { secret: 'do-not-leak' } }),
      'c',
    );
    expect(JSON.stringify(mapped.body)).not.toContain('do-not-leak');
    // It is still available for logging.
    expect(mapped.cause).toEqual({ secret: 'do-not-leak' });
  });

  it('carries validation details', () => {
    const mapped = mapError(
      new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
        details: [{ field: 'attemptId', issue: 'malformed' }],
      }),
      'c',
    );
    expect(mapped.body.error.details).toEqual([{ field: 'attemptId', issue: 'malformed' }]);
  });

  it('reports the taxonomy status', () => {
    expect(mapError(new GatewayError(GATEWAY_CODES.RATE_LIMITED), 'c').status).toBe(429);
    expect(mapError(new GatewayError(GATEWAY_CODES.KILL_SWITCH), 'c').status).toBe(503);
  });
});

/**
 * The two surfaces map thrown errors separately. A gateway code returned from
 * `/api/v1` would be a code the product client has never heard of.
 */
describe('mapping to the Product API taxonomy', () => {
  it('passes an AppError through unchanged', () => {
    const error = AppError.unauthenticated();
    expect(toProductError(error)).toBe(error);
  });

  it('maps a Redis outage to a recoverable failure on the product path too (DS-F6)', () => {
    // The gateway mapper has answered 503 for this since the shared stores
    // landed; the product mapper had no branch at all, so the SAME transient
    // outage on a keyed POST fell through to `internal` — a non-retryable 500
    // for a condition that clears on its own. One error, two subsystems, two
    // contradictory instructions to the client.
    const outage = Object.assign(new Error("Stream isn't writeable"), {
      name: 'RedisUnavailableError',
    });

    const mapped = toProductError(outage);

    expect(mapped.code, 'a transient outage was reported as permanent').toBe(
      'PROVIDER_UNAVAILABLE',
    );
    expect(ERROR_STATUS[mapped.code], 'the client is told not to retry').toBeLessThan(503);
    expect(ERROR_STATUS[mapped.code]).toBeGreaterThanOrEqual(500);
    expect(mapped.cause, 'the original fault was dropped instead of logged').toBe(outage);
  });

  it.each([
    ['FST_ERR_CTP_BODY_TOO_LARGE', 'PAYLOAD_TOO_LARGE', 413],
    ['FST_REQ_FILE_TOO_LARGE', 'PAYLOAD_TOO_LARGE', 413],
    ['FST_ERR_CTP_INVALID_MEDIA_TYPE', 'UNSUPPORTED_MEDIA_TYPE', 415],
    ['FST_ERR_CTP_EMPTY_JSON_BODY', 'VALIDATION_FAILED', 400],
    ['FST_ERR_VALIDATION', 'VALIDATION_FAILED', 400],
  ])('maps Fastify %s to %s', (code, expected, status) => {
    const mapped = toProductError(Object.assign(new Error('x'), { code }));
    expect([mapped.code, mapped.status]).toEqual([expected, status]);
  });

  // A body the client sent wrong is the client's fault. Answering 500 would
  // invite a retry of a request that cannot succeed.
  it('maps any other content-type-parser fault to VALIDATION_FAILED', () => {
    const mapped = toProductError(
      Object.assign(new SyntaxError('Unexpected end of JSON input'), {
        code: 'FST_ERR_CTP_INVALID_JSON',
      }),
    );
    expect(mapped.code).toBe('VALIDATION_FAILED');
    expect(mapped.details).toEqual([{ field: 'body', issue: 'unparseable' }]);
  });

  it('maps a client abort to CLIENT_CANCELLED', () => {
    const mapped = toProductError(Object.assign(new Error('aborted'), { name: 'AbortError' }));
    expect([mapped.code, mapped.status]).toEqual(['CLIENT_CANCELLED', 499]);
  });

  it.each([
    ['an unrecognised throwable', new Error('boom')],
    ['a thrown string', 'boom'],
    ['null', null],
  ])('maps %s to INTERNAL_ERROR and serializes nothing internal', (_name, thrown) => {
    const mapped = toProductError(thrown);
    expect(mapped.code).toBe('INTERNAL_ERROR');
    expect(JSON.stringify(mapped.toResponse('c1'))).not.toContain('boom');
  });
});

/**
 * Low group — multipart errors that answered 500.
 *
 * Only two of @fastify/multipart's codes were mapped. The rest fell through to
 * internal-error, so a client that sent too many parts or a malformed
 * content-type was told the server had broken and that retrying might help. It
 * would not: the same request fails the same way every time.
 */
describe('multipart failures are client errors, not server faults', () => {
  it.each([
    ['FST_FILES_LIMIT', 413],
    ['FST_FIELDS_LIMIT', 413],
    ['FST_PARTS_LIMIT', 413],
    ['FST_MP_PREMATURE_CLOSE', 400],
    ['FST_INVALID_MULTIPART_CONTENT_TYPE', 415],
  ])('maps %s to %i rather than 500', (code, expected) => {
    const mapped = toGatewayError(Object.assign(new Error('multipart'), { code }));
    expect(GATEWAY_ERRORS[mapped.gatewayCode].httpStatus, code).toBe(expected);
  });

  it('still sends anything genuinely unrecognised to internal-error', () => {
    // The fallback has to stay: an unmapped throwable must not leak, and
    // guessing a 4xx for something we do not understand would be worse.
    const mapped = toGatewayError(Object.assign(new Error('?'), { code: 'FST_SOMETHING_NEW' }));
    expect(mapped.gatewayCode).toBe('internal-error');
  });
});

describe('the TEST_MODE fence refusing is not a server fault', () => {
  // Production authentication is not wired: the attestation service does not
  // exist yet, so `authenticate` throws TestModeFenceError rather than
  // silently accepting anyone (G-4, AD-21). That refusal is the fence working.
  //
  // Nothing mapped it, so it fell through to internal-error: a 500 telling the
  // client the server broke, when the server had in fact correctly declined.
  // A 500 also invites a retry, and no number of retries wires an attestation
  // verifier.
  //
  // test-mode-forbidden is what the fence firing is called, and it is the only
  // entry in the frozen taxonomy whose client action is
  // `no-client-action-configuration-fault` — which is exactly the situation.
  // It is not recoverable, so the client falls back instead of retrying.
  const fenceError = () =>
    Object.assign(new Error('production authentication requires an attestation verifier (G-4)'), {
      name: 'TestModeFenceError',
    });

  it('answers 403 test-mode-forbidden on the gateway surface', () => {
    const mapped = toGatewayError(fenceError());

    expect(mapped.gatewayCode).toBe(GATEWAY_CODES.TEST_MODE_FORBIDDEN);
    expect(GATEWAY_ERRORS[mapped.gatewayCode].httpStatus).toBe(403);
    expect(GATEWAY_ERRORS[mapped.gatewayCode].recoverable).toBe(false);
  });

  it('answers 403 TEST_MODE_FORBIDDEN on the product surface', () => {
    // The product routes throw the same error from their own `authenticate`,
    // and the two surfaces map thrown errors separately, so both need it.
    const mapped = toProductError(fenceError());

    expect([mapped.code, mapped.status]).toEqual(['TEST_MODE_FORBIDDEN', 403]);
  });

  it('never puts the fence message in the response body', () => {
    // The message names an internal control and the gate it belongs to. It
    // belongs in the log, on `cause`, not in something a client receives.
    const mapped = mapError(fenceError(), 'corr_1');

    expect(JSON.stringify(mapped.body)).not.toMatch(/attestation|G-4|TestModeFence/i);
    expect(mapped.status).toBe(403);
  });
});
