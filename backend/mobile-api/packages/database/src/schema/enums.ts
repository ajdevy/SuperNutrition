import { pgEnum } from 'drizzle-orm/pg-core';

// Closed sets get a Postgres enum; sets expected to grow stay text + check so
// that growth is not a migration (docs/05 §5.1).

export const userStatus = pgEnum('user_status', ['active', 'suspended', 'deleted']);

export const measurementSystem = pgEnum('measurement_system', ['metric', 'imperial']);

export const authMode = pgEnum('auth_mode', ['attested', 'test-mode']);

export const authScope = pgEnum('auth_scope', ['product', 'gateway', 'both']);

export const consentModality = pgEnum('consent_modality', ['photo', 'text']);

export const consentDecision = pgEnum('consent_decision', ['granted', 'declined']);

export const goalType = pgEnum('goal_type', ['lose', 'maintain', 'gain', 'custom']);

export const activityLevel = pgEnum('activity_level', [
  'sedentary',
  'light',
  'moderate',
  'active',
  'very_active',
]);

export const mealSource = pgEnum('meal_source', ['photo', 'manual', 'voice']);

export const mealStatus = pgEnum('meal_status', ['pending', 'identified', 'confirmed', 'failed']);

export const mealType = pgEnum('meal_type', [
  'breakfast',
  'lunch',
  'dinner',
  'snack',
  'unspecified',
]);

/**
 * `client-grounded` rows are persisted verbatim from the device's catalog
 * calculation and are never recomputed here (docs/01 §1.5). The distinction is
 * permanent so a future divergence is measurable rather than invisible.
 */
export const nutritionOrigin = pgEnum('nutrition_origin', ['client-grounded', 'server-grounded']);

export const evidenceType = pgEnum('evidence_type', [
  'candidate',
  'assumption',
  'abstention',
  'correction',
]);

export const inferenceOutcome = pgEnum('inference_outcome', [
  'candidates',
  'abstention',
  'failure',
]);

export const streakType = pgEnum('streak_type', ['daily_logging', 'goal_adherence']);

export const coachMessageType = pgEnum('coach_message_type', [
  'motivation',
  'recommendation',
  'habit',
]);

export const catalogSource = pgEnum('catalog_source', ['usda', 'off']);

export const catalogPackageStatus = pgEnum('catalog_package_status', [
  'staged',
  'active',
  'rolled-back',
]);
