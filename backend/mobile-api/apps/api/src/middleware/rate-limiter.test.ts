import { describe, it, expect } from 'vitest';
import { enforceRateLimits, rateLimitHeaders } from './rate-limiter.js';
import { InMemoryRateLimiter } from '../security/stores.js';
import { GatewayError } from '../security/gateway-errors.js';

const rules = [
  { layer: 'installation' as const, limit: 2, windowSeconds: 60 },
  { layer: 'ip' as const, limit: 5, windowSeconds: 60 },
];

describe('enforcing layered limits', () => {
  it('passes when every layer has headroom', async () => {
    const decision = await enforceRateLimits({
      limiter: new InMemoryRateLimiter(rules),
      keys: [
        { layer: 'installation', principal: 'i' },
        { layer: 'ip', principal: '203.0.113.5' },
      ],
    });
    expect(decision.allowed).toBe(true);
  });

  it('reports the binding limit, not the most generous one', async () => {
    const decision = await enforceRateLimits({
      limiter: new InMemoryRateLimiter(rules),
      keys: [
        { layer: 'installation', principal: 'i' },
        { layer: 'ip', principal: '203.0.113.5' },
      ],
    });
    // installation has 1 left, ip has 4 — the tighter one is reported.
    expect(decision.remaining).toBe(1);
  });

  it('throws the contract rate-limit code when a layer is exhausted', async () => {
    const limiter = new InMemoryRateLimiter([
      { layer: 'installation', limit: 0, windowSeconds: 30 },
    ]);
    await expect(
      enforceRateLimits({ limiter, keys: [{ layer: 'installation', principal: 'i' }] }),
    ).rejects.toMatchObject({ gatewayCode: 'rate-limited', retryAfter: 30 });
  });

  it('throws a GatewayError, so nothing reaches a client unmapped', async () => {
    const limiter = new InMemoryRateLimiter([{ layer: 'ip', limit: 0, windowSeconds: 60 }]);
    await expect(
      enforceRateLimits({ limiter, keys: [{ layer: 'ip', principal: 'x' }] }),
    ).rejects.toBeInstanceOf(GatewayError);
  });

  it('returns a permissive decision when no layers are checked', async () => {
    const decision = await enforceRateLimits({
      limiter: new InMemoryRateLimiter(rules),
      keys: [],
    });
    expect(decision.allowed).toBe(true);
  });
});

describe('headers', () => {
  it('renders the contract header set', () => {
    expect(
      rateLimitHeaders({
        allowed: false,
        limit: 20,
        remaining: 0,
        resetAtEpochSeconds: 1785072000,
        retryAfterSeconds: 30,
      }),
    ).toEqual({
      'X-RateLimit-Limit': '20',
      'X-RateLimit-Remaining': '0',
      'X-RateLimit-Reset': '1785072000',
    });
  });
});
