import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'smol-toml';
import { describe, it, expect } from 'vitest';
import {
  ConsentReceiptSchema,
  PhotoInferenceRequestSchema,
  TextInferenceRequestSchema,
} from './inference.schemas.js';

const here = dirname(fileURLToPath(import.meta.url));
// apps/api/src/modules/inference/presentation -> repo root is eight levels up.
const fixtureRoot = resolve(
  here,
  '..',
  '..',
  '..',
  '..',
  '..',
  '..',
  '..',
  '..',
  'contracts',
  'mobile-api',
  'v1',
  'fixtures',
);

/**
 * The receipt exactly as the frozen release carries it.
 *
 * These validators had drifted into a second, private definition of a shape the
 * release already fixes. They called the digest `payloadDigest` and the
 * timestamp `decidedAt`, and they had no `version`, `attemptId`, `decision`,
 * `retentionDays` or `expiresAt` at all — so a receipt bound to no attempt and
 * with no expiry passed as complete.
 *
 * Nothing caught it because nothing on the request path imports this module:
 * the gateway validates through `security/consent.ts`, and these schemas were
 * held alive only by a barrel export and their own tests. Tests written against
 * a hand-copied literal agree with whatever the schema says, which is how the
 * two definitions drifted apart while both looked covered.
 *
 * Reading the receipt out of the release removes the hand-copied literal. There
 * is now one description of a consent receipt in the repository, and the test
 * fails the moment this module stops matching it.
 */
const receiptFromRelease = (): Record<string, unknown> => {
  const fixture = parse(
    readFileSync(join(fixtureRoot, 'photo-analysis-success.toml'), 'utf8'),
  ) as Record<string, Record<string, Record<string, unknown>>>;
  return fixture['request']?.['context']?.['consentReceipt'] as Record<string, unknown>;
};

const receipt = receiptFromRelease();

// P-04/P-02 — this fixture was missing `modality` and
// `inferenceContext.configurationVersion`, both `optional = false` in
// gateway-request.toml, and the schema did not require them either. Two
// incomplete things agreeing is not a passing test, so the omission read as
// green. The receipt is already taken from the release; the request now names
// every required field too.
const request = {
  contractVersion: 'mobile-api/v1',
  attemptId: 'att_01J8X4Q2K7R9M3P5',
  effectId: 'eff_01J8X4Q2K7R9M3P5',
  modality: 'photo',
  consentReceipt: receipt,
  inferenceContext: {
    catalogCountry: 'DE',
    mealType: 'lunch',
    locale: 'de-DE',
    measurementSystem: 'metric',
    configurationVersion: receipt['configurationVersion'],
  },
};

describe('consent receipt validation (AD-8)', () => {
  it('accepts the receipt the release actually carries', () => {
    expect(ConsentReceiptSchema.safeParse(receipt)).toMatchObject({ success: true });
  });

  // A partial receipt fails closed: every binding field is required.
  it.each([
    'version',
    'attemptId',
    'digest',
    'modality',
    'purpose',
    'decision',
    'consentCopyVersion',
    'marketJurisdiction',
    'retentionVersion',
    'retentionDays',
    'configurationVersion',
    'recordedAt',
    'expiresAt',
  ])('rejects a receipt missing %s', (field) => {
    const partial: Record<string, unknown> = { ...receipt };
    delete partial[field];
    expect(ConsentReceiptSchema.safeParse(partial).success).toBe(false);
  });

  // AD-8 binds consent to the bytes and to one attempt. Without these two the
  // receipt is a general permission slip rather than a decision about this
  // photo, and it is exactly what the old shape could not express.
  it('binds the receipt to a payload digest and a single attempt', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, digest: 'md5:abc' }).success).toBe(false);
    expect(ConsentReceiptSchema.safeParse({ ...receipt, digest: 'sha256:xyz' }).success).toBe(
      false,
    );
    expect(ConsentReceiptSchema.safeParse({ ...receipt, attemptId: 'att_short' }).success).toBe(
      false,
    );
  });

  // An expiry that cannot be represented cannot be enforced.
  it('carries an expiry and a recorded time', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, expiresAt: 'soon' }).success).toBe(false);
    expect(ConsentReceiptSchema.safeParse({ ...receipt, recordedAt: 'yesterday' }).success).toBe(
      false,
    );
  });

  it('records the decision the user actually made', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, decision: 'granted' }).success).toBe(true);
    expect(ConsentReceiptSchema.safeParse({ ...receipt, decision: 'maybe' }).success).toBe(false);
  });

  // MP-4: photo and text permissions are decided independently.
  it('constrains modality to the closed set', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, modality: 'text' }).success).toBe(true);
    expect(ConsentReceiptSchema.safeParse({ ...receipt, modality: 'audio' }).success).toBe(false);
  });

  it('requires an ISO 3166-1 alpha-2 jurisdiction', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, marketJurisdiction: 'DEU' }).success).toBe(
      false,
    );
    expect(ConsentReceiptSchema.safeParse({ ...receipt, marketJurisdiction: 'de' }).success).toBe(
      false,
    );
  });

  // The release makes the region optional; the enforced shape allows null, so
  // this one must too or the two disagree again.
  it('allows an absent processing region, as the release does', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, processingRegion: null }).success).toBe(
      true,
    );
  });
});

describe('gateway request validation', () => {
  it('accepts a well-formed photo request', () => {
    expect(PhotoInferenceRequestSchema.safeParse(request)).toMatchObject({ success: true });
  });

  it('rejects an unsupported contract version', () => {
    expect(
      PhotoInferenceRequestSchema.safeParse({ ...request, contractVersion: 'mobile-api/v2' })
        .success,
    ).toBe(false);
  });

  it('requires attempt and effect ids for at-most-once effects (AD-18)', () => {
    const { attemptId: _a, ...noAttempt } = request;
    expect(PhotoInferenceRequestSchema.safeParse(noAttempt).success).toBe(false);
    const { effectId: _e, ...noEffect } = request;
    expect(PhotoInferenceRequestSchema.safeParse(noEffect).success).toBe(false);
  });

  it('requires a consent receipt before remote transfer', () => {
    const { consentReceipt: _c, ...noConsent } = request;
    expect(PhotoInferenceRequestSchema.safeParse(noConsent).success).toBe(false);
  });

  it('bounds the text description', () => {
    expect(
      TextInferenceRequestSchema.safeParse({ ...request, description: 'a banana' }).success,
    ).toBe(true);
    expect(
      TextInferenceRequestSchema.safeParse({ ...request, description: 'x'.repeat(501) }).success,
    ).toBe(false);
    expect(TextInferenceRequestSchema.safeParse({ ...request, description: '' }).success).toBe(
      false,
    );
  });
});
