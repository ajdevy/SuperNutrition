import { describe, it, expect } from 'vitest';
import { parseUuid, parseFoodRef, parseAttemptId } from './identifiers.js';
import { isOk } from '../result/result.js';

describe('identifiers', () => {
  it('accepts a well-formed uuid', () => {
    expect(isOk(parseUuid('018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071'))).toBe(true);
  });

  it('rejects empty and malformed uuids', () => {
    expect(parseUuid('')).toEqual({ ok: false, error: 'empty' });
    expect(parseUuid('not-a-uuid')).toEqual({ ok: false, error: 'malformed' });
  });

  // docs/08 §8.3: a bare integer becomes ambiguous the moment a second catalog
  // source exists.
  it('requires namespace-qualified food references', () => {
    expect(isOk(parseFoodRef('usda:fdc:171705'))).toBe(true);
    expect(isOk(parseFoodRef('off:barcode:3017620422003'))).toBe(true);
    expect(parseFoodRef('171705')).toEqual({ ok: false, error: 'malformed' });
    expect(parseFoodRef('unknown:fdc:1')).toEqual({ ok: false, error: 'malformed' });
    expect(parseFoodRef('')).toEqual({ ok: false, error: 'empty' });
  });

  it('requires opaque prefixed attempt ids', () => {
    expect(isOk(parseAttemptId('att_01J8X4Q2K7R9M3P5'))).toBe(true);
    expect(parseAttemptId('att_short')).toEqual({ ok: false, error: 'malformed' });
    expect(parseAttemptId('user-42')).toEqual({ ok: false, error: 'malformed' });
    expect(parseAttemptId('')).toEqual({ ok: false, error: 'empty' });
  });
});
