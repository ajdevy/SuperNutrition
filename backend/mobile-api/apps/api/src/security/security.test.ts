import { describe, it, expect } from 'vitest';
import { consentSatisfied, type ConsentVerification } from './index.js';

const allTrue: ConsentVerification = {
  digestMatches: true,
  modalityMatches: true,
  copyVersionKnown: true,
  posturePermits: true,
  regionPermitted: true,
  versionsSupported: true,
};

/**
 * docs/09 §9.5: a remote transfer requires ALL six conditions. Any one absent
 * fails closed.
 */
describe('consent gate fails closed (AD-8, AD-41)', () => {
  it('permits transfer only when every condition holds', () => {
    expect(consentSatisfied(allTrue)).toBe(true);
  });

  it.each(Object.keys(allTrue) as (keyof ConsentVerification)[])(
    'refuses transfer when %s is false',
    (field) => {
      expect(consentSatisfied({ ...allTrue, [field]: false })).toBe(false);
    },
  );

  it('refuses transfer when nothing holds', () => {
    const allFalse = Object.fromEntries(
      Object.keys(allTrue).map((k) => [k, false]),
    ) as unknown as ConsentVerification;
    expect(consentSatisfied(allFalse)).toBe(false);
  });
});
