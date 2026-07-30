import { describe, it, expect } from 'vitest';
import { mapError, toGatewayError } from './error-handler.js';
import { GatewayError, GATEWAY_CODES } from '../security/gateway-errors.js';

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
