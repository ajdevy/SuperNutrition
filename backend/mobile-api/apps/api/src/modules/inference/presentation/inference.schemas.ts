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

/**
 * The `inferenceContext` object from `gateway-request.toml [inference_context]`.
 *
 * P-02 — `configurationVersion` and `priorMealSummary` were absent here while
 * the pipeline consumed both. `priorMealSummary` is the one that mattered: it is
 * forwarded to the provider, and the release types it a string bounded at 280
 * characters, so an object or a 281-character value went out unexamined.
 */
export const InferenceContextSchema = z.object({
  catalogCountry: z.string().regex(/^[A-Z]{2}$/),
  mealType: z.enum(['breakfast', 'lunch', 'dinner', 'snack', 'unspecified']),
  locale: z.string().min(2).max(35),
  measurementSystem: z.enum(['metric', 'imperial']),
  /** Signed ConfigurationSnapshot version this attempt captured (AD-20). */
  configurationVersion: z.string().min(1),
  /**
   * Free-form user text, passed to the provider as delimited data and never as
   * instructions. `max_length = 280` in the release.
   */
  priorMealSummary: z.string().max(LIMITS.MAX_PRIOR_MEAL_SUMMARY_CHARS).nullable().optional(),
});

/**
 * `processingRegion` is not part of the request envelope, and the release is
 * emphatic about the difference between "not accepted" and "ignored":
 *
 *   > A client-supplied preferred region is not accepted, is not honoured, and
 *   > is not silently ignored either — it is simply not part of the envelope.
 *
 * Users consent to a transfer; they do not select provider routing (AD-41 MP-7).
 * Dropping it quietly would let a client believe it had chosen a region, so it
 * is refused by name. Every *other* unknown field is ignored, as the release
 * requires ("Unknown fields are ignored").
 */
export const FORBIDDEN_CONTEXT_FIELDS = ['processingRegion'] as const;

export const PhotoInferenceRequestSchema = z.object({
  contractVersion: z.enum(SUPPORTED_MOBILE_API_VERSIONS),
  attemptId,
  effectId,
  /**
   * `enum = ["photo"]` with `reserved_values = ["text"]`. Reserved is not
   * accepted: permissions and consent copy are per-modality and are never
   * inferred across modalities, so a `text` body on the photo endpoint is a
   * refusal rather than a shortcut.
   */
  modality: z.literal('photo'),
  /**
   * ADVISORY ONLY, and optional. The gateway resolves the authoritative
   * jurisdiction itself, so a mismatch is not an error — but a malformed claim
   * is, because it is a syntactically invalid market that would otherwise be
   * carried into policy lookup.
   */
  claimedMarketJurisdiction: z
    .string()
    .regex(/^[A-Z]{2}$/)
    .nullable()
    .optional(),
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
