import { describe, it, expect } from 'vitest';
import { createHash } from 'node:crypto';
import { verifyConsent, computePayloadDigest, type ConsentReceiptInput } from './consent.js';
import type { MarketResolution } from './index.js';

const BYTES = new Uint8Array([1, 2, 3, 4]);
const NOW = new Date('2026-07-29T10:20:00Z');

const market: MarketResolution = {
  marketJurisdiction: 'ZZ',
  processingRegion: 'fixture-region',
  residencyPosture: 'unrestricted',
  remotePermitted: true,
  consentCopyVersion: 'photo-consent/1',
  acceptedInputLanguages: ['en'],
};

const receipt = (over: Partial<ConsentReceiptInput> = {}): ConsentReceiptInput => ({
  version: '1',
  attemptId: 'att_0000000000000000',
  digest: computePayloadDigest(BYTES),
  modality: 'photo',
  purpose: 'food-identification',
  decision: 'granted',
  marketJurisdiction: 'ZZ',
  processingRegion: 'fixture-region',
  consentCopyVersion: 'photo-consent/1',
  retentionVersion: 'retention/2',
  retentionDays: 0,
  configurationVersion: 'cfg/1',
  recordedAt: '2026-07-29T10:15:00Z',
  expiresAt: '2026-07-29T10:30:00Z',
  ...over,
});

const verify = (
  over: Partial<ConsentReceiptInput> = {},
  overrides: Partial<Parameters<typeof verifyConsent>[0]> = {},
) =>
  verifyConsent({
    receipt: receipt(over),
    payload: BYTES,
    attemptId: 'att_0000000000000000',
    modality: 'photo',
    market,
    now: NOW,
    knownConsentCopyVersions: ['photo-consent/1'],
    supportedRetentionVersions: ['retention/2'],
    supportedConfigurationVersions: null,
    ...overrides,
  });

const expectCode = (code: string, over: Partial<ConsentReceiptInput> = {}, overrides = {}) => {
  expect(() => verify(over, overrides)).toThrowError(
    expect.objectContaining({ gatewayCode: code }),
  );
};

describe('a complete receipt passes', () => {
  it('accepts the happy path', () => {
    expect(() => verify()).not.toThrow();
  });

  it('computes the digest over the received bytes', () => {
    expect(computePayloadDigest(BYTES)).toBe(
      `sha256:${createHash('sha256').update(BYTES).digest('hex')}`,
    );
  });
});

describe('structural faults are malformed-consent', () => {
  it.each([
    ['unsupported version', { version: '2' }],
    ['attempt id mismatch', { attemptId: 'att_1111111111111111' }],
    ['digest mismatch', { digest: `sha256:${'0'.repeat(64)}` }],
    ['unparseable recordedAt', { recordedAt: 'nope' }],
    ['unparseable expiresAt', { expiresAt: 'nope' }],
    [
      'expiry before issue',
      { recordedAt: '2026-07-29T10:20:00Z', expiresAt: '2026-07-29T10:10:00Z' },
    ],
    [
      'validity beyond the cap',
      { recordedAt: '2026-07-29T10:00:00Z', expiresAt: '2026-07-29T12:00:00Z' },
    ],
    ['nonzero retention', { retentionDays: 30 }],
  ])('%s', (_name, over) => {
    expectCode('malformed-consent', over as Partial<ConsentReceiptInput>);
  });
});

describe('expiry is separate from structure', () => {
  it('reports consent-expired for a receipt past its window', () => {
    expectCode('consent-expired', {
      recordedAt: '2026-07-29T09:00:00Z',
      expiresAt: '2026-07-29T09:15:00Z',
    });
  });

  // Evaluated at resolution time, not at issue time (AD-41 MP-3).
  it('accepts a receipt that is still live at resolution time', () => {
    expect(() => verify({ expiresAt: '2026-07-29T10:20:01Z' })).not.toThrow();
  });
});

describe('permission faults are consent-required', () => {
  it.each([
    ['declined', { decision: 'declined' }],
    ['modality mismatch', { modality: 'text' }],
    ['unpermitted purpose', { purpose: 'training' }],
    ['null consent copy', { consentCopyVersion: null }],
    ['unknown consent copy', { consentCopyVersion: 'photo-consent/99' }],
    ['unsupported retention version', { retentionVersion: 'retention/99' }],
    ['stale jurisdiction disclosure', { marketJurisdiction: 'AA' }],
    ['stale region disclosure', { processingRegion: 'elsewhere' }],
  ])('%s', (_name, over) => {
    expectCode('consent-required', over as Partial<ConsentReceiptInput>);
  });

  it('rejects an unsupported configuration version when a list is supplied', () => {
    expectCode('consent-required', {}, { supportedConfigurationVersions: ['cfg/2'] });
  });

  it('accepts a supported configuration version', () => {
    expect(() => verify({}, { supportedConfigurationVersions: ['cfg/1'] })).not.toThrow();
  });
});

/**
 * Posture is checked BEFORE the disclosure fields: a denied market has no
 * consent copy at all, so checking copy first would report "unknown consent
 * copy" for a market that simply does not permit remote inference.
 */
describe('market posture is reported accurately', () => {
  const denied: MarketResolution = {
    marketJurisdiction: 'ZZ',
    processingRegion: null,
    residencyPosture: 'unresolved',
    remotePermitted: false,
    consentCopyVersion: null,
    acceptedInputLanguages: [],
  };

  it('reports market-not-permitted rather than a disclosure fault', () => {
    expect(() =>
      verifyConsent({
        receipt: receipt(),
        payload: BYTES,
        attemptId: 'att_0000000000000000',
        modality: 'photo',
        market: denied,
        now: NOW,
        knownConsentCopyVersions: [],
        supportedRetentionVersions: ['retention/2'],
        supportedConfigurationVersions: null,
      }),
    ).toThrowError(expect.objectContaining({ gatewayCode: 'market-not-permitted' }));
  });

  it('carries the posture in the error detail', () => {
    try {
      verifyConsent({
        receipt: receipt(),
        payload: BYTES,
        attemptId: 'att_0000000000000000',
        modality: 'photo',
        market: { ...denied, residencyPosture: 'prohibited' },
        now: NOW,
        knownConsentCopyVersions: [],
        supportedRetentionVersions: ['retention/2'],
        supportedConfigurationVersions: null,
      });
      throw new Error('expected a rejection');
    } catch (error) {
      expect((error as { details: { issue: string }[] }).details[0]?.issue).toBe('prohibited');
    }
  });
});

describe('digest binding', () => {
  it('rejects when the payload differs from the consented bytes', () => {
    expect(() =>
      verifyConsent({
        receipt: receipt(),
        payload: new Uint8Array([9, 9, 9]),
        attemptId: 'att_0000000000000000',
        modality: 'photo',
        market,
        now: NOW,
        knownConsentCopyVersions: ['photo-consent/1'],
        supportedRetentionVersions: ['retention/2'],
        supportedConfigurationVersions: null,
      }),
    ).toThrowError(expect.objectContaining({ gatewayCode: 'malformed-consent' }));
  });

  it('rejects a digest of the wrong length without throwing a comparison error', () => {
    expectCode('malformed-consent', { digest: 'sha256:short' });
  });
});
