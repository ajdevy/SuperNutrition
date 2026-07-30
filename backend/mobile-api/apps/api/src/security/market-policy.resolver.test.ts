import { describe, it, expect } from 'vitest';
import {
  StaticMarketPolicyResolver,
  FIXTURE_MARKET_POLICY,
  PRODUCTION_MARKET_POLICY,
} from './market-policy.resolver.js';
import type { MarketPolicyEntry } from './index.js';

const NOW = new Date('2026-07-29T12:00:00Z');

const entry = (over: Partial<MarketPolicyEntry> = {}): MarketPolicyEntry => ({
  marketId: 'DE',
  marketJurisdiction: 'DE',
  residencyPosture: 'unrestricted',
  permittedProcessingRegions: ['eu-central'],
  remoteInference: { photo: true, text: false },
  consentCopyVersion: { photo: 'photo-consent/3', text: null },
  acceptedInputLanguages: ['de', 'en'],
  legalReviewRef: 'review-1',
  approvedBy: 'legal',
  approvedAt: '2026-01-01T00:00:00Z',
  expiresAt: '2027-01-01T00:00:00Z',
  ...over,
});

const resolve = (entries: MarketPolicyEntry[], id = 'DE', modality: 'photo' | 'text' = 'photo') =>
  new StaticMarketPolicyResolver(entries, 'test/1').resolve(id, modality, NOW);

describe('fail-closed defaults (AD-41 MP-1)', () => {
  it('an absent market permits nothing', () => {
    const result = resolve([], 'FR');
    expect(result.residencyPosture).toBe('unresolved');
    expect(result.remotePermitted).toBe(false);
    expect(result.processingRegion).toBeNull();
    expect(result.consentCopyVersion).toBeNull();
  });

  it('production ships no market with a posture', () => {
    expect(PRODUCTION_MARKET_POLICY).toEqual([]);
    expect(resolve([...PRODUCTION_MARKET_POLICY], 'DE').remotePermitted).toBe(false);
  });

  it('infers nothing from a neighbouring market', () => {
    expect(
      resolve([entry({ marketId: 'AT', marketJurisdiction: 'AT' })], 'DE').remotePermitted,
    ).toBe(false);
  });
});

describe('expiry is evaluated at resolution time (MP-3)', () => {
  it('an expired posture behaves as unresolved', () => {
    const result = resolve([entry({ expiresAt: '2026-07-29T11:59:59Z' })]);
    expect(result.residencyPosture).toBe('unresolved');
    expect(result.remotePermitted).toBe(false);
  });

  it('an unparseable expiry fails closed', () => {
    expect(resolve([entry({ expiresAt: 'not-a-date' })]).remotePermitted).toBe(false);
  });

  it('a live posture permits', () => {
    expect(resolve([entry()]).remotePermitted).toBe(true);
  });
});

describe('per-modality independence (MP-4)', () => {
  it('photo permitted does not imply text permitted', () => {
    expect(resolve([entry()], 'DE', 'photo').remotePermitted).toBe(true);
    expect(resolve([entry()], 'DE', 'text').remotePermitted).toBe(false);
  });

  it('returns the consent copy for the requested modality only', () => {
    expect(resolve([entry()], 'DE', 'photo').consentCopyVersion).toBe('photo-consent/3');
    expect(resolve([entry()], 'DE', 'text').consentCopyVersion).toBeNull();
  });
});

describe('prohibited and unresolved postures', () => {
  it.each(['prohibited', 'unresolved'] as const)('%s permits nothing', (posture) => {
    expect(resolve([entry({ residencyPosture: posture })]).remotePermitted).toBe(false);
  });
});

describe('incomplete permissive entries fail closed', () => {
  it('a permissive posture with no permitted region cannot be honoured', () => {
    const result = resolve([entry({ permittedProcessingRegions: [] })]);
    expect(result.remotePermitted).toBe(false);
    expect(result.residencyPosture).toBe('unresolved');
  });

  it('permission without reviewed disclosure copy is not usable permission', () => {
    const result = resolve([entry({ consentCopyVersion: { photo: null, text: null } })]);
    expect(result.remotePermitted).toBe(false);
  });
});

describe('language independence (MP-5)', () => {
  it('reports accepted languages even while denied', () => {
    const result = resolve([entry({ residencyPosture: 'prohibited' })]);
    expect(result.remotePermitted).toBe(false);
    expect(result.acceptedInputLanguages).toEqual(['de', 'en']);
  });
});

describe('fixture markets exist only for test mode', () => {
  it('exposes one permissive and one prohibited fixture, and no real market', () => {
    const ids = FIXTURE_MARKET_POLICY.map((e) => e.marketId);
    expect(ids).toEqual(['ZZ', 'XX']);
    expect(resolve([...FIXTURE_MARKET_POLICY], 'ZZ').remotePermitted).toBe(true);
    expect(resolve([...FIXTURE_MARKET_POLICY], 'XX').remotePermitted).toBe(false);
  });
});
