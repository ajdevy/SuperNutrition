import { describe, it, expect } from 'vitest';
import { AppError, isAppError } from './app-error.js';
import { ERROR_CODES, ERROR_STATUS } from './error-codes.js';

describe('AppError', () => {
  it('derives status from the code table', () => {
    expect(new AppError('MEAL_NOT_FOUND', 'gone').status).toBe(404);
    expect(new AppError('RATE_LIMITED', 'slow down').status).toBe(429);
    expect(new AppError('MARKET_NOT_PERMITTED', 'closed').status).toBe(403);
  });

  it('maps every declared code to a status', () => {
    for (const code of Object.values(ERROR_CODES)) {
      expect(ERROR_STATUS[code], `missing status for ${code}`).toBeTypeOf('number');
    }
  });

  it('omits cause from the wire response', () => {
    const error = AppError.internal('failed', { secret: 'do-not-serialize' });
    const body = error.toResponse('corr-1');
    expect(JSON.stringify(body)).not.toContain('do-not-serialize');
    expect(body.error.correlationId).toBe('corr-1');
    expect(body.error.code).toBe('INTERNAL_ERROR');
  });

  it('carries validation details', () => {
    const error = AppError.validation('bad', [{ field: 'items', issue: 'required' }]);
    expect(error.status).toBe(400);
    expect(error.details).toHaveLength(1);
  });

  it('exposes the factory helpers', () => {
    expect(AppError.unauthenticated().status).toBe(401);
    expect(AppError.forbidden().status).toBe(403);
    expect(AppError.conflict('dupe').status).toBe(409);
    expect(AppError.notFound('USER_NOT_FOUND', 'nope').status).toBe(404);
  });

  it('identifies its own instances', () => {
    expect(isAppError(AppError.forbidden())).toBe(true);
    expect(isAppError(new Error('plain'))).toBe(false);
  });
});
