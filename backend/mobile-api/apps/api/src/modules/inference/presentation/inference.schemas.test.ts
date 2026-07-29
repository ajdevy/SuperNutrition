import { describe, it, expect } from 'vitest';
import {
  ConsentReceiptSchema,
  PhotoInferenceRequestSchema,
  TextInferenceRequestSchema,
} from './inference.schemas.js';

const receipt = {
  payloadDigest: `sha256:${'9'.repeat(64)}`,
  modality: 'photo',
  purpose: 'food-identification',
  consentCopyVersion: 'photo-consent/3',
  marketJurisdiction: 'DE',
  processingRegion: 'eu-central',
  retentionVersion: 'retention/2',
  configurationVersion: 'cfg/2026-07-26.4',
  decidedAt: '2026-07-29T10:14:02Z',
};

const request = {
  contractVersion: 'mobile-api/v1',
  attemptId: 'att_01J8X4Q2K7R9M3P5',
  effectId: 'eff_01J8X4Q2K7R9M3P5',
  consentReceipt: receipt,
  inferenceContext: {
    catalogCountry: 'DE',
    mealType: 'lunch',
    locale: 'de-DE',
    measurementSystem: 'metric',
  },
};

describe('consent receipt validation (AD-8)', () => {
  it('accepts a complete receipt', () => {
    expect(ConsentReceiptSchema.safeParse(receipt).success).toBe(true);
  });

  // A partial receipt fails closed: every binding field is required.
  it.each([
    'payloadDigest',
    'modality',
    'consentCopyVersion',
    'marketJurisdiction',
    'processingRegion',
    'retentionVersion',
    'configurationVersion',
  ])('rejects a receipt missing %s', (field) => {
    const partial: Record<string, unknown> = { ...receipt };
    delete partial[field];
    expect(ConsentReceiptSchema.safeParse(partial).success).toBe(false);
  });

  it('requires a sha256 payload digest', () => {
    expect(ConsentReceiptSchema.safeParse({ ...receipt, payloadDigest: 'md5:abc' }).success).toBe(
      false,
    );
    expect(
      ConsentReceiptSchema.safeParse({ ...receipt, payloadDigest: 'sha256:xyz' }).success,
    ).toBe(false);
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
});

describe('gateway request validation', () => {
  it('accepts a well-formed photo request', () => {
    expect(PhotoInferenceRequestSchema.safeParse(request).success).toBe(true);
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
