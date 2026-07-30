import { GatewayError, GATEWAY_CODES } from '../security/gateway-errors.js';
import type { RateLimitDecision, RateLimitKey, RateLimiter } from '../security/index.js';

/**
 * Rate-limit middleware.
 *
 * One code (`rate-limited`) is returned regardless of which layer rejected, so
 * a caller probing for headroom learns nothing about the limiter topology
 * (contract rate-limit.toml).
 */

export interface RateLimitCheckInput {
  readonly limiter: RateLimiter;
  readonly keys: readonly RateLimitKey[];
}

export interface RateLimitHeaders {
  readonly 'X-RateLimit-Limit': string;
  readonly 'X-RateLimit-Remaining': string;
  readonly 'X-RateLimit-Reset': string;
}

export const rateLimitHeaders = (decision: RateLimitDecision): RateLimitHeaders => ({
  'X-RateLimit-Limit': String(decision.limit),
  'X-RateLimit-Remaining': String(decision.remaining),
  'X-RateLimit-Reset': String(decision.resetAtEpochSeconds),
});

/**
 * Checks every layer and throws on the first rejection. Returns the decision
 * with the least headroom so the response advertises the binding limit rather
 * than a more generous one.
 */
export const enforceRateLimits = async (input: RateLimitCheckInput): Promise<RateLimitDecision> => {
  let tightest: RateLimitDecision | null = null;

  for (const key of input.keys) {
    const decision = await input.limiter.check(key);
    if (!decision.allowed) {
      throw new GatewayError(GATEWAY_CODES.RATE_LIMITED, {
        ...(decision.retryAfterSeconds !== null
          ? { retryAfterOverride: decision.retryAfterSeconds }
          : {}),
        details: [{ field: 'rateLimit', issue: 'exceeded' }],
      });
    }
    if (tightest === null || decision.remaining < tightest.remaining) tightest = decision;
  }

  return (
    tightest ?? {
      allowed: true,
      limit: 0,
      remaining: 0,
      resetAtEpochSeconds: 0,
      retryAfterSeconds: null,
    }
  );
};
