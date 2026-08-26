import { describe, it, expect } from 'vitest';
import { GATEWAY_ROUTES, GATEWAY_PIPELINE, PRODUCT_ROUTES } from './index.js';

describe('route surfaces (docs/01 §1.2)', () => {
  it('keeps the two surfaces on distinct base paths', () => {
    expect(GATEWAY_ROUTES.every((r) => r.path.startsWith('/gw/v1'))).toBe(true);
    expect(PRODUCT_ROUTES.every((r) => r.path.startsWith('/api/v1'))).toBe(true);
  });

  it('declares no duplicate route', () => {
    const all = [...GATEWAY_ROUTES, ...PRODUCT_ROUTES].map((r) => `${r.method} ${r.path}`);
    expect(new Set(all).size).toBe(all.length);
  });

  it('exposes the endpoints the skeleton brief requires', () => {
    const product = PRODUCT_ROUTES.map((r) => `${r.method} ${r.path}`);
    for (const required of [
      'POST /api/v1/meals/photo',
      'POST /api/v1/meals/manual',
      'POST /api/v1/meals/voice',
      'GET /api/v1/meals',
      'GET /api/v1/meals/:id',
      'GET /api/v1/progress',
      'GET /api/v1/journey',
      'GET /api/v1/achievements',
      'PATCH /api/v1/goals',
    ]) {
      expect(product, `missing ${required}`).toContain(required);
    }
  });

  it('exposes the market policy endpoint before any consent decision (MP-7)', () => {
    expect(GATEWAY_ROUTES.map((r) => r.path)).toContain('/gw/v1/policy/market');
  });
});

describe('gateway pipeline order (docs/10 §10.4)', () => {
  // Every step before orchestration must be able to reject the request WITHOUT
  // having called a provider: a provider call is the expensive, externally
  // visible, privacy-relevant act.
  it('places every gate before provider invocation', () => {
    const orchestrate = GATEWAY_PIPELINE.indexOf('orchestrate-inference');
    for (const gate of [
      'validate-size-and-type',
      'authenticate-and-replay-check',
      'rate-limit',
      'consent-and-market-gate',
      'effect-deduplication',
    ] as const) {
      expect(GATEWAY_PIPELINE.indexOf(gate), `${gate} runs too late`).toBeLessThan(orchestrate);
    }
  });

  it('validates size before authenticating, so a huge body is rejected cheaply', () => {
    expect(GATEWAY_PIPELINE.indexOf('validate-size-and-type')).toBeLessThan(
      GATEWAY_PIPELINE.indexOf('authenticate-and-replay-check'),
    );
  });

  it('disposes the payload after orchestration on every path', () => {
    expect(GATEWAY_PIPELINE.indexOf('dispose-payload')).toBeGreaterThan(
      GATEWAY_PIPELINE.indexOf('orchestrate-inference'),
    );
  });
});
