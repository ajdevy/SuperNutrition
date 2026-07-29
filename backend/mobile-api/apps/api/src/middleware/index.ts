import type { AppError } from '@supernutrition/shared';

/**
 * Middleware contracts. Order is load-bearing (docs/06 §6.3):
 *
 *   correlation → content-length → content-type → auth → rate limit → schema
 *
 * Rejecting an oversized body before parsing it is the difference between a
 * cheap 413 and an expensive one.
 */
export const MIDDLEWARE_ORDER = [
  'correlation-id',
  'request-logging',
  'content-length-guard',
  'content-type-guard',
  'authentication',
  'rate-limit',
  'idempotency',
  'schema-validation',
  'error-mapper',
] as const;

export type MiddlewareName = (typeof MIDDLEWARE_ORDER)[number];

export interface ErrorMapper {
  toResponse(
    error: unknown,
    correlationId: string,
  ): {
    status: number;
    body: ReturnType<AppError['toResponse']>;
  };
}

export interface CorrelationIdProvider {
  next(): string;
}
