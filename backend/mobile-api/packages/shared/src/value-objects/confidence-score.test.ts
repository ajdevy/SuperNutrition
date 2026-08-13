import { describe, it, expect } from 'vitest';
import { createConfidenceScore, bandFor, compareWithinEngine } from './confidence-score.js';
import { isOk, isErr } from '../result/result.js';

describe('ConfidenceScore', () => {
  it('bands by threshold', () => {
    expect(bandFor(0.9)).toBe('high');
    expect(bandFor(0.75)).toBe('high');
    expect(bandFor(0.6)).toBe('medium');
    expect(bandFor(0.5)).toBe('medium');
    expect(bandFor(0.2)).toBe('low');
  });

  it('accepts the closed unit interval', () => {
    expect(isOk(createConfidenceScore(0, 'e', 'c'))).toBe(true);
    expect(isOk(createConfidenceScore(1, 'e', 'c'))).toBe(true);
  });

  it('rejects out-of-range and non-finite scores', () => {
    expect(createConfidenceScore(1.1, 'e', 'c')).toEqual({ ok: false, error: 'out-of-range' });
    expect(createConfidenceScore(-0.1, 'e', 'c')).toEqual({ ok: false, error: 'out-of-range' });
    expect(createConfidenceScore(Number.NaN, 'e', 'c')).toEqual({ ok: false, error: 'not-finite' });
    expect(createConfidenceScore(Infinity, 'e', 'c')).toEqual({ ok: false, error: 'not-finite' });
  });

  // Spine convention: confidence is calibrated within one engine/model version
  // and is never compared across engines without a G-5 calibration transform.
  it('refuses to compare across engines', () => {
    const a = createConfidenceScore(0.9, 'engine-a', 'cal-1');
    const b = createConfidenceScore(0.8, 'engine-b', 'cal-1');
    if (!isOk(a) || !isOk(b)) throw new Error('setup');
    expect(compareWithinEngine(a.value, b.value)).toEqual({ ok: false, error: 'incomparable' });
  });

  it('refuses to compare across calibration versions', () => {
    const a = createConfidenceScore(0.9, 'engine-a', 'cal-1');
    const b = createConfidenceScore(0.8, 'engine-a', 'cal-2');
    if (!isOk(a) || !isOk(b)) throw new Error('setup');
    expect(isErr(compareWithinEngine(a.value, b.value))).toBe(true);
  });

  it('compares within one engine and calibration', () => {
    const a = createConfidenceScore(0.9, 'engine-a', 'cal-1');
    const b = createConfidenceScore(0.8, 'engine-a', 'cal-1');
    if (!isOk(a) || !isOk(b)) throw new Error('setup');
    const cmp = compareWithinEngine(a.value, b.value);
    expect(isOk(cmp)).toBe(true);
    if (isOk(cmp)) expect(cmp.value).toBeCloseTo(0.1, 5);
  });
});
