import { z } from 'zod';
import { LIMITS, MODALITIES, SUPPORTED_MOBILE_API_VERSIONS } from '@supernutrition/shared';

/** Gateway request validation (docs/06 §6.5). */

const attemptId = z.string().regex(/^att_[A-Za-z0-9]{16,64}$/);
const effectId = z.string().regex(/^eff_[A-Za-z0-9]{16,64}$/);
const sha256 = z.string().regex(/^sha256:[0-9a-f]{64}$/);

/**
 * AD-8: the receipt binds the digest of the scrubbed normalized payload to one
 * attempt, together with the purpose, decision, market jurisdiction, processing
 * region, retention terms and configuration version the user was shown. Every
 * field is required — a partial receipt fails closed.
 *
 * The field names are the release's, not this module's. An earlier shape here
 * used `payloadDigest`/`decidedAt` and omitted the attempt binding, the
 * decision, the retention window and the expiry, which made a receipt bound to
 * nothing and valid forever parse as complete. `inference.schemas.test.ts`
 * reads the canonical receipt out of contracts/mobile-api/v1 so the two cannot
 * drift apart again.
 */
export const ConsentReceiptSchema = z.object({
  version: z.string().min(1),
  attemptId,
  digest: sha256,
  modality: z.enum(MODALITIES),
  purpose: z.string().min(1).max(120),
  decision: z.enum(['granted', 'denied']),
  marketJurisdiction: z.string().regex(/^[A-Z]{2}$/),
  // The release permits absence: a market with no region commitment shows none
  // rather than inventing one.
  processingRegion: z.string().min(1).max(64).nullable(),
  consentCopyVersion: z.string().min(1).nullable(),
  retentionVersion: z.string().min(1),
  retentionDays: z.number().int().nonnegative(),
  configurationVersion: z.string().min(1),
  recordedAt: z.iso.datetime({ offset: true }),
  expiresAt: z.iso.datetime({ offset: true }),
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
