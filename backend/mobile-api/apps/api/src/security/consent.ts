import { createHash, timingSafeEqual } from 'node:crypto';
import { GatewayError, GATEWAY_CODES } from './gateway-errors.js';
import type { MarketResolution } from './index.js';

/**
 * Consent receipt verification (docs/09 §9.5, contract consent-receipt.toml).
 *
 * Every condition must hold. Any one absent means ZERO provider calls — the
 * checks run before the orchestrator is ever reached, and each maps to exactly
 * one B-5 code so a rejection is explainable to the client without leaking why
 * internally.
 */

export interface ConsentReceiptInput {
  readonly version: string;
  readonly attemptId: string;
  readonly digest: string;
  readonly modality: string;
  readonly purpose: string;
  readonly decision: string;
  readonly marketJurisdiction: string;
  readonly processingRegion: string | null;
  readonly consentCopyVersion: string | null;
  readonly retentionVersion: string;
  readonly retentionDays: number;
  readonly configurationVersion: string;
  readonly recordedAt: string;
  readonly expiresAt: string;
}

export interface ConsentVerificationInput {
  readonly receipt: ConsentReceiptInput;
  readonly payload: Uint8Array;
  readonly attemptId: string;
  readonly modality: string;
  readonly market: MarketResolution;
  readonly now: Date;
  readonly knownConsentCopyVersions: readonly string[];
  readonly supportedRetentionVersions: readonly string[];
  readonly supportedConfigurationVersions: readonly string[] | null;
}

/** Maximum receipt lifetime, mirroring the contract's `max_validity_seconds`. */
export const MAX_CONSENT_VALIDITY_SECONDS = 900;

/**
 * How far ahead of the gateway a client's clock may be before its receipt is
 * refused. Ordinary device drift, not a window a caller gets to choose.
 */
export const CONSENT_CLOCK_SKEW_SECONDS = 60;

export const computePayloadDigest = (payload: Uint8Array): string =>
  `sha256:${createHash('sha256').update(payload).digest('hex')}`;

/** Constant-time compare so a digest check cannot be probed byte by byte. */
const digestsEqual = (a: string, b: string): boolean => {
  const left = Buffer.from(a, 'utf8');
  const right = Buffer.from(b, 'utf8');
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
};

const parseInstant = (value: string): number | null => {
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? null : parsed;
};

/**
 * Throws a `GatewayError` on the first failed condition. The order matters:
 * structural faults are reported as malformed-consent, policy faults as
 * consent-required, and expiry separately — so a client can tell "your receipt
 * is broken" from "you may not do this here".
 */
export const verifyConsent = (input: ConsentVerificationInput): void => {
  const { receipt, payload, attemptId, modality, market, now } = input;

  // --- structural ---------------------------------------------------------
  if (receipt.version !== '1') {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.version', issue: 'unsupported' }],
    });
  }

  if (receipt.attemptId !== attemptId) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.attemptId', issue: 'does_not_match_request' }],
    });
  }

  // The gateway recomputes the digest over what it ACTUALLY received. This is
  // what makes the AD-8 binding real rather than a client-side promise.
  const computed = computePayloadDigest(payload);
  if (!digestsEqual(computed, receipt.digest)) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.digest', issue: 'does_not_match_payload' }],
    });
  }

  const recordedAt = parseInstant(receipt.recordedAt);
  const expiresAt = parseInstant(receipt.expiresAt);
  if (recordedAt === null || expiresAt === null) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.recordedAt', issue: 'unparseable_timestamp' }],
    });
  }
  if (expiresAt <= recordedAt) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.expiresAt', issue: 'not_after_recorded_at' }],
    });
  }
  if (expiresAt - recordedAt > MAX_CONSENT_VALIDITY_SECONDS * 1000) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.expiresAt', issue: 'exceeds_max_validity' }],
    });
  }

  // The window has to have OPENED, not merely be short.
  //
  // Every rule above is relative: expiresAt after recordedAt, and the two no
  // more than MAX_CONSENT_VALIDITY_SECONDS apart. A receipt stamped a year ahead
  // with a fifteen-minute window satisfies all of them, and the expiry check
  // below passes too, because that expiry is also a year away. What the contract
  // bounds is how long ago the user decided, so the decision has to be in the
  // past. The tolerance is for device clock drift, not for a window the caller
  // gets to choose.
  if (recordedAt > now.getTime() + CONSENT_CLOCK_SKEW_SECONDS * 1000) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.recordedAt', issue: 'recorded_in_the_future' }],
    });
  }

  // AD-8 / G-4: durable provider-side retention cannot be expressed at all.
  if (receipt.retentionDays !== 0) {
    throw new GatewayError(GATEWAY_CODES.MALFORMED_CONSENT, {
      details: [{ field: 'consentReceipt.retentionDays', issue: 'must_be_zero' }],
    });
  }

  // --- expiry, evaluated at RESOLUTION time (AD-41 MP-3) -------------------
  if (expiresAt <= now.getTime()) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_EXPIRED, {
      details: [{ field: 'consentReceipt.expiresAt', issue: 'expired' }],
    });
  }

  // --- permission ---------------------------------------------------------
  if (receipt.decision !== 'granted') {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.decision', issue: 'not_granted' }],
    });
  }

  if (receipt.modality !== modality) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.modality', issue: 'does_not_match_request' }],
    });
  }

  if (receipt.purpose !== 'food-identification') {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.purpose', issue: 'not_permitted' }],
    });
  }

  // Market posture is checked BEFORE the disclosure fields. A denied market
  // has no consent copy version at all, so checking copy first would report
  // "unknown consent copy" for a market that simply does not permit remote
  // inference — the less accurate and less actionable of the two answers.
  if (!market.remotePermitted) {
    throw new GatewayError(GATEWAY_CODES.MARKET_NOT_PERMITTED, {
      details: [{ field: 'marketJurisdiction', issue: market.residencyPosture }],
    });
  }

  // A granted receipt must name the disclosure the user actually saw, for THIS
  // modality. Presenting another modality's copy fails closed (AD-41 MP-4).
  if (
    receipt.consentCopyVersion === null ||
    !input.knownConsentCopyVersions.includes(receipt.consentCopyVersion)
  ) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.consentCopyVersion', issue: 'unknown_for_modality' }],
    });
  }

  if (!input.supportedRetentionVersions.includes(receipt.retentionVersion)) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.retentionVersion', issue: 'unsupported' }],
    });
  }

  if (
    input.supportedConfigurationVersions !== null &&
    !input.supportedConfigurationVersions.includes(receipt.configurationVersion)
  ) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.configurationVersion', issue: 'unsupported' }],
    });
  }

  // The receipt records what the user was SHOWN. If the resolved policy no
  // longer matches, the disclosure is stale and consent does not carry over.
  if (receipt.marketJurisdiction !== market.marketJurisdiction) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.marketJurisdiction', issue: 'stale_disclosure' }],
    });
  }

  if (receipt.processingRegion !== market.processingRegion) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.processingRegion', issue: 'stale_disclosure' }],
    });
  }

  if (receipt.consentCopyVersion !== market.consentCopyVersion) {
    throw new GatewayError(GATEWAY_CODES.CONSENT_REQUIRED, {
      details: [{ field: 'consentReceipt.consentCopyVersion', issue: 'stale_disclosure' }],
    });
  }
};
