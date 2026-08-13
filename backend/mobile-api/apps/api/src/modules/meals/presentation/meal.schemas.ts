import { z } from 'zod';
import { LIMITS, MEAL_TYPES, CATALOG_SOURCES, NUTRITION_ORIGINS } from '@supernutrition/shared';

/**
 * Zod at the boundary. The parsed output is the only thing passed inward; raw
 * request bodies never cross into application code (docs/06 §6.3).
 *
 * Unknown keys are stripped, not rejected — the contract's compatibility rule
 * is ignore-unknown, reject-missing-required.
 */

// Non-negative. The `-?` accepted "-250" calories and "-1" servings, which the
// goals module already refuses — two surfaces disagreeing about whether a meal
// can weigh less than nothing, and the permissive one is the write path.
const decimalString = z
  .string()
  .regex(/^\d{1,7}(\.\d{1,3})?$/, 'must be a non-negative decimal with at most 3 fraction digits');

const foodRef = z
  .string()
  .regex(/^(usda|off):[a-z0-9-]+:[A-Za-z0-9._-]+$/, 'must be a namespace-qualified food id');

// Shape AND existence. The regex alone accepted 2026-02-30 and 2026-13-01,
// which reached the insert and failed there — a 500 for a request the client
// could have been told about immediately.
const localDate = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'must be YYYY-MM-DD')
  .refine((value) => {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(Date.UTC(year as number, (month as number) - 1, day as number));
    return (
      date.getUTCFullYear() === year &&
      date.getUTCMonth() === (month as number) - 1 &&
      date.getUTCDate() === day
    );
  }, 'must be a real calendar date');

const timezone = z.string().refine(
  (v) => {
    try {
      new Intl.DateTimeFormat('en-US', { timeZone: v });
      return true;
    } catch {
      return false;
    }
  },
  { message: 'must be a valid IANA timezone' },
);

export const MealItemInputSchema = z.object({
  foodRef,
  displayName: z.string().trim().min(1).max(LIMITS.MAX_DISPLAY_NAME_CHARS),
  servingUnit: z.string().trim().min(1).max(32),
  servingAmount: decimalString,
  gramWeight: decimalString.optional(),
  confidence: z
    .string()
    .regex(/^0(\.\d{1,3})?$|^1(\.0{1,3})?$/)
    .optional(),
  confirmedByUser: z.boolean(),
});

export const PerItemNutritionSchema = z.object({
  foodRef,
  calories: decimalString,
  proteinG: decimalString.nullable(),
  fatG: decimalString.nullable(),
  carbG: decimalString.nullable(),
  partial: z.boolean(),
});

/**
 * A snapshot without `catalogSource` and `datasetVersion` is rejected here.
 * An ungrounded number must not enter the database, and the boundary is the
 * last place to stop it (AD-7, docs/06 §6.6).
 */
export const NutritionSnapshotSchema = z.object({
  origin: z.enum(NUTRITION_ORIGINS),
  catalogSource: z.enum(CATALOG_SOURCES),
  datasetVersion: z.string().trim().min(1),
  servingBasis: z.object({
    unit: z.string().trim().min(1),
    amount: decimalString,
    gramWeight: decimalString.nullable(),
  }),
  calories: decimalString,
  proteinG: decimalString.nullable(),
  fatG: decimalString.nullable(),
  carbG: decimalString.nullable(),
  fiberG: decimalString.nullable(),
  sugarG: decimalString.nullable(),
  sodiumMg: decimalString.nullable(),
  perItem: z.array(PerItemNutritionSchema).max(LIMITS.MAX_MEAL_ITEMS),
});

const mealBase = {
  occurredAt: z.iso.datetime({ offset: true }),
  localDate,
  timezone,
  mealType: z.enum(MEAL_TYPES).default('unspecified'),
  items: z.array(MealItemInputSchema).min(1).max(LIMITS.MAX_MEAL_ITEMS),
  nutrition: NutritionSnapshotSchema,
};

const attemptId = z.string().regex(/^att_[A-Za-z0-9]{16,64}$/);

export const EvidenceSchema = z.object({
  mealItemId: z.uuid().nullable(),
  attemptId,
  type: z.enum(['candidate', 'assumption', 'abstention', 'correction']),
  payload: z.record(z.string(), z.unknown()),
  provenance: z.object({
    route: z.enum(['local', 'remote', 'manual']),
    engineId: z.string().min(1),
    modelVersion: z.string().min(1),
    calibrationVersion: z.string().min(1),
    contractVersion: z.string().min(1),
  }),
});

/** POST /api/v1/meals/photo — carries evidence, never image bytes. */
export const CreatePhotoMealRequestSchema = z.object({
  ...mealBase,
  attemptId,
  configurationVersion: z.string().min(1),
  evidence: z.array(EvidenceSchema).max(200).default([]),
});

/** POST /api/v1/meals/manual */
export const CreateManualMealRequestSchema = z.object(mealBase);

/** POST /api/v1/meals/voice — transcript is user text: stored, never logged. */
export const CreateVoiceMealRequestSchema = z.object({
  ...mealBase,
  transcript: z.string().trim().min(1).max(LIMITS.MAX_TEXT_DESCRIPTION_CHARS),
  attemptId: attemptId.optional(),
  evidence: z.array(EvidenceSchema).max(200).default([]),
});

/** PATCH /api/v1/meals/:id — creates revision N+1. */
export const CorrectMealRequestSchema = z.object({
  items: z.array(MealItemInputSchema).min(1).max(LIMITS.MAX_MEAL_ITEMS),
  nutrition: NutritionSnapshotSchema,
});

export const ListMealsQuerySchema = z.object({
  limit: z.coerce
    .number()
    .int()
    .min(1)
    .max(LIMITS.MAX_PAGE_LIMIT)
    .default(LIMITS.DEFAULT_PAGE_LIMIT),
  cursor: z.string().max(512).optional(),
  from: localDate.optional(),
  to: localDate.optional(),
  mealType: z.enum(MEAL_TYPES).optional(),
  source: z.enum(['photo', 'manual', 'voice']).optional(),
});

export const MealIdParamSchema = z.object({ id: z.uuid() });

export type CreatePhotoMealRequest = z.infer<typeof CreatePhotoMealRequestSchema>;
export type CreateManualMealRequest = z.infer<typeof CreateManualMealRequestSchema>;
export type CreateVoiceMealRequest = z.infer<typeof CreateVoiceMealRequestSchema>;
export type CorrectMealRequest = z.infer<typeof CorrectMealRequestSchema>;
export type ListMealsQuery = z.infer<typeof ListMealsQuerySchema>;
