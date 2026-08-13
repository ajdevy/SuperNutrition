import { describe, expect, it } from 'vitest';

import { assertStubFence, TestModeFenceError } from '../../../../config/index.js';
import { StubInferenceProvider } from './stub.provider.js';

/**
 * The stub answers with candidates no model produced. Two things therefore have
 * to hold: it cannot be reachable outside TEST_MODE, and what it returns has to
 * be a lawful gateway outcome rather than a shape the mapper happens to tolerate.
 */
describe('stub inference fence', () => {
  it('refuses to enable the stub when test mode is off', () => {
    expect(() => assertStubFence(true, false)).toThrow(TestModeFenceError);
  });

  it('enables the stub only when both the request and test mode agree', () => {
    expect(assertStubFence(true, true)).toBe(true);
  });

  it('stays disabled when unrequested, regardless of test mode', () => {
    expect(assertStubFence(false, true)).toBe(false);
    expect(assertStubFence(false, false)).toBe(false);
  });
});

describe('StubInferenceProvider', () => {
  const profile = {
    model: 'stub/model-1',
    enabled: true,
  } as unknown as Parameters<StubInferenceProvider['supports']>[0];

  it('supports only an enabled profile', () => {
    const provider = new StubInferenceProvider();
    expect(provider.supports(profile)).toBe(true);
    expect(provider.supports({ ...profile, enabled: false } as unknown as typeof profile)).toBe(
      false,
    );
  });

  it('returns ranked candidates with descending confidence', async () => {
    const provider = new StubInferenceProvider();

    const result = await provider.infer({
      profile,
    } as unknown as Parameters<StubInferenceProvider['infer']>[0]);

    expect(result.ok).toBe(true);
    if (!result.ok) return;

    const { candidates } = result.value;
    expect(candidates.length).toBeGreaterThan(0);

    // Rank is 1-based and dense, and confidence never increases with rank —
    // the client reads candidates[0] as the top answer.
    candidates.forEach((candidate, index) => {
      expect(candidate.rank).toBe(index + 1);
      if (index > 0) {
        expect(candidate.confidence.score).toBeLessThanOrEqual(
          candidates[index - 1]!.confidence.score,
        );
      }
    });
  });

  it('carries no nutrition field, which the gateway contract forbids', async () => {
    const provider = new StubInferenceProvider();

    const result = await provider.infer({
      profile,
    } as unknown as Parameters<StubInferenceProvider['infer']>[0]);

    expect(result.ok).toBe(true);
    if (!result.ok) return;

    for (const candidate of result.value.candidates) {
      const keys = Object.keys(candidate);
      expect(keys).not.toContain('nutrition');
      expect(keys).not.toContain('calories');
    }
  });

  it('names itself in provenance so a stub answer is never mistaken for a model', async () => {
    const provider = new StubInferenceProvider();

    const result = await provider.infer({
      profile,
    } as unknown as Parameters<StubInferenceProvider['infer']>[0]);

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    const { provenance } = result.value.candidates[0]!;
    expect(provenance.engineId).toContain('stub');
    expect(provenance.calibrationVersion).toContain('uncalibrated');
    expect(provenance.route).toBe('remote');
  });
});
