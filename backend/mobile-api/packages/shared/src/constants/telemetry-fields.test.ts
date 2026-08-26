import { describe, it, expect } from 'vitest';
import {
  TELEMETRY_ALLOWLIST,
  FORBIDDEN_TELEMETRY_FIELDS,
  isAllowedTelemetryField,
  applyTelemetryAllowlist,
} from './telemetry-fields.js';

describe('AD-14 telemetry allowlist', () => {
  it('keeps allowlisted fields', () => {
    const out = applyTelemetryAllowlist({
      attemptId: 'att_abc',
      route: '/gw/v1/inference/photo',
      durationBucket: 'under-1s',
    });
    expect(out).toEqual({
      attemptId: 'att_abc',
      route: '/gw/v1/inference/photo',
      durationBucket: 'under-1s',
    });
  });

  // The negative test AD-31 requires: feed every forbidden field and assert
  // none survives (docs/09 §9.10).
  it('drops every forbidden field', () => {
    const hostile = Object.fromEntries(
      FORBIDDEN_TELEMETRY_FIELDS.map((f) => [f, `sensitive-${f}`]),
    );
    const out = applyTelemetryAllowlist({ ...hostile, attemptId: 'att_ok' });

    for (const field of FORBIDDEN_TELEMETRY_FIELDS) {
      expect(out, `leaked ${field}`).not.toHaveProperty(field);
    }
    expect(JSON.stringify(out)).not.toContain('sensitive-');
    expect(out).toEqual({ attemptId: 'att_ok' });
  });

  it('drops unknown fields rather than passing them through', () => {
    expect(applyTelemetryAllowlist({ somethingNew: 1 })).toEqual({});
  });

  it('never allows a user identifier', () => {
    expect(isAllowedTelemetryField('userId')).toBe(false);
    expect(isAllowedTelemetryField('email')).toBe(false);
  });

  it('allowlist and forbidden list do not intersect', () => {
    const allowed = new Set<string>(TELEMETRY_ALLOWLIST);
    for (const field of FORBIDDEN_TELEMETRY_FIELDS) {
      expect(allowed.has(field), `${field} is on both lists`).toBe(false);
    }
  });
});

describe('an allowlisted name does not license a structured value', () => {
  it('drops an object hiding under an allowed key', () => {
    // The filter decided on the KEY and passed the value through whatever it
    // was, so an allowlisted name carrying an object emitted every field inside
    // it. `outcome` is on the list; a transcript is not, and this is how one
    // would have travelled.
    const out = applyTelemetryAllowlist({
      attemptId: 'att_abc',
      outcome: { transcript: 'a banana and two eggs' },
    });

    expect(out, 'user content survived inside an allowlisted field').toEqual({
      attemptId: 'att_abc',
    });
  });

  it('drops an array of objects for the same reason', () => {
    expect(applyTelemetryAllowlist({ outcome: [{ prompt: 'secret' }] })).toEqual({});
  });

  it('keeps an array of scalars, because a list of codes is still codes', () => {
    expect(applyTelemetryAllowlist({ failureCode: ['a', 'b'] })).toEqual({
      failureCode: ['a', 'b'],
    });
  });

  it('keeps ordinary scalar fields untouched', () => {
    expect(applyTelemetryAllowlist({ route: '/gw/v1/inference/photo', statusCode: 200 })).toEqual({
      route: '/gw/v1/inference/photo',
      statusCode: 200,
    });
  });
});
