import type { Modality, ResidencyPosture } from '@supernutrition/shared';
import type { MarketPolicyEntry, MarketResolution } from './index.js';

/**
 * AD-41 / MP-1..MP-8.
 *
 * Every market defaults to `unresolved` and permits no remote inference of any
 * modality. Absence, `unresolved`, and expiry are equivalent AT RESOLUTION
 * TIME, not at build time — an entry that expires between deploys stops
 * permitting transfers without anything being redeployed.
 *
 * Nothing here infers a posture from a neighbouring market, a shared language,
 * a currency, or a region grouping.
 */

const PERMISSIVE_POSTURES: readonly ResidencyPosture[] = ['unrestricted', 'restricted-in-region'];

/** The fail-closed answer, returned for absent, unresolved, expired, or prohibited. */
export const deniedResolution = (
  marketJurisdiction: string,
  posture: ResidencyPosture,
  acceptedInputLanguages: readonly string[] = [],
): MarketResolution => ({
  marketJurisdiction,
  processingRegion: null,
  residencyPosture: posture,
  remotePermitted: false,
  consentCopyVersion: null,
  acceptedInputLanguages,
});

export class StaticMarketPolicyResolver {
  readonly #entries: ReadonlyMap<string, MarketPolicyEntry>;
  readonly #policyVersion: string;

  constructor(entries: readonly MarketPolicyEntry[], policyVersion: string) {
    this.#entries = new Map(entries.map((entry) => [entry.marketId, entry]));
    this.#policyVersion = policyVersion;
  }

  get policyVersion(): string {
    return this.#policyVersion;
  }

  resolve(marketId: string, modality: Modality, now: Date): MarketResolution {
    const entry = this.#entries.get(marketId);

    // MP-1: absence is not an error, it is the default deny.
    if (!entry) return deniedResolution(marketId, 'unresolved');

    // MP-3: expiry is evaluated here, not at build time.
    const expiresAt = Date.parse(entry.expiresAt);
    if (Number.isNaN(expiresAt) || expiresAt <= now.getTime()) {
      return deniedResolution(marketId, 'unresolved', entry.acceptedInputLanguages);
    }

    if (!PERMISSIVE_POSTURES.includes(entry.residencyPosture)) {
      return deniedResolution(
        entry.marketJurisdiction,
        entry.residencyPosture,
        entry.acceptedInputLanguages,
      );
    }

    // MP-4: photo and text are decided independently.
    if (entry.remoteInference[modality] !== true) {
      return deniedResolution(
        entry.marketJurisdiction,
        entry.residencyPosture,
        entry.acceptedInputLanguages,
      );
    }

    const region = entry.permittedProcessingRegions[0];
    if (region === undefined) {
      // A permissive posture with no permitted region cannot be honoured.
      return deniedResolution(entry.marketJurisdiction, 'unresolved', entry.acceptedInputLanguages);
    }

    const copy = entry.consentCopyVersion[modality];
    if (!copy) {
      // Permission without reviewed disclosure copy is not usable permission.
      return deniedResolution(
        entry.marketJurisdiction,
        entry.residencyPosture,
        entry.acceptedInputLanguages,
      );
    }

    return {
      marketJurisdiction: entry.marketJurisdiction,
      processingRegion: region,
      residencyPosture: entry.residencyPosture,
      remotePermitted: true,
      consentCopyVersion: copy,
      acceptedInputLanguages: entry.acceptedInputLanguages,
    };
  }
}

/**
 * Fixture markets used only when TEST_MODE is on. ZZ is permissive so the whole
 * pipeline is exercisable; XX is prohibited so the deny path is exercisable.
 * No real ISO 3166-1 market appears here — a test-mode token can never reach a
 * real market's transfer path.
 */
export const FIXTURE_MARKET_POLICY: readonly MarketPolicyEntry[] = [
  {
    marketId: 'ZZ',
    marketJurisdiction: 'ZZ',
    residencyPosture: 'unrestricted',
    permittedProcessingRegions: ['fixture-region'],
    remoteInference: { photo: true, text: false },
    consentCopyVersion: { photo: 'photo-consent/fixture-1', text: null },
    acceptedInputLanguages: ['en'],
    legalReviewRef: 'fixture-no-legal-position',
    approvedBy: 'fixture',
    approvedAt: '2026-01-01T00:00:00Z',
    expiresAt: '2099-01-01T00:00:00Z',
  },
  {
    marketId: 'XX',
    marketJurisdiction: 'XX',
    residencyPosture: 'prohibited',
    permittedProcessingRegions: [],
    remoteInference: { photo: false, text: false },
    consentCopyVersion: { photo: null, text: null },
    acceptedInputLanguages: ['en'],
    legalReviewRef: 'fixture-no-legal-position',
    approvedBy: 'fixture',
    approvedAt: '2026-01-01T00:00:00Z',
    expiresAt: '2099-01-01T00:00:00Z',
  },
];

/**
 * Production policy data is a signed release owned by a principal independent
 * of this product (AD-41). No real market ships a posture here: G-1 and G-4
 * own which markets get one, so the shipped default is deny-everything.
 */
export const PRODUCTION_MARKET_POLICY: readonly MarketPolicyEntry[] = [];
