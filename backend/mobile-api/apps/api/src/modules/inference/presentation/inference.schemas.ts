import { z } from 'zod';
import { LIMITS, MODALITIES, SUPPORTED_MOBILE_API_VERSIONS } from '@supernutrition/shared';

/** Gateway request validation (docs/06 §6.5). */

const attemptId = z.string().regex(/^att_[A-Za-z0-9]{16,64}$/);
const effectId = z.string().regex(/^eff_[A-Za-z0-9]{16,64}$/);
const sha256 = z.string().regex(/^sha256:[0-9a-f]{64}$/);

/**
 * AD-8: the receipt binds the digest of the scrubbed normalized payload, the
 * purpose, market jurisdiction, processing region, retention version, and
 * configuration version. Every field is required — a partial receipt fails
 * closed.
 */
export const ConsentReceiptSchema = z.object({
  payloadDigest: sha256,
  modality: z.enum(MODALITIES),
  purpose: z.string().min(1).max(120),
  consentCopyVersion: z.string().min(1),
  marketJurisdiction: z.string().regex(/^[A-Z]{2}$/),
  processingRegion: z.string().min(1).max(64),
  retentionVersion: z.string().min(1),
  configurationVersion: z.string().min(1),
  decidedAt: z.iso.datetime({ offset: true }),
});

export const InferenceContextSchema = z.object({
  catalogCountry: z.string().regex(/^[A-Z]{2}$/),
  mealType: z.enum(['breakfast', 'lunch', 'dinner', 'snack', 'unspecified']),
  locale: z.string().min(2).max(35),
  measurementSystem: z.enum(['metric', 'imperial']),
});

export const PhotoInferenceRequestSchema = z.object({
  contractVersion: z.enum(SUPPORTED_MOBILE_API_VERSIONS),
  attemptId,
  effectId,
  consentReceipt: ConsentReceiptSchema,
  inferenceContext: InferenceContextSchema,
});

export const TextInferenceRequestSchema = z.object({
  contractVersion: z.enum(SUPPORTED_MOBILE_API_VERSIONS),
  attemptId,
  effectId,
  consentReceipt: ConsentReceiptSchema,
  inferenceContext: InferenceContextSchema,
  description: z.string().trim().min(1).max(LIMITS.MAX_TEXT_DESCRIPTION_CHARS),
});

export const CancelInferenceParamsSchema = z.object({ attemptId });

export const MarketPolicyQuerySchema = z.object({
  marketId: z.string().regex(/^[A-Z]{2}$/),
  modality: z.enum(MODALITIES),
});

export type PhotoInferenceRequest = z.infer<typeof PhotoInferenceRequestSchema>;
export type TextInferenceRequest = z.infer<typeof TextInferenceRequestSchema>;
export type ConsentReceiptInput = z.infer<typeof ConsentReceiptSchema>;
