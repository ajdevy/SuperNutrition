export const MEAL_SOURCES = ['photo', 'manual', 'voice'] as const;
export type MealSourceKind = (typeof MEAL_SOURCES)[number];

export const MEAL_STATUSES = ['pending', 'identified', 'confirmed', 'failed'] as const;
export type MealStatusKind = (typeof MEAL_STATUSES)[number];

export const MEAL_TYPES = ['breakfast', 'lunch', 'dinner', 'snack', 'unspecified'] as const;
export type MealTypeKind = (typeof MEAL_TYPES)[number];

export const INFERENCE_OUTCOMES = ['candidates', 'abstention', 'failure'] as const;
export type InferenceOutcomeKind = (typeof INFERENCE_OUTCOMES)[number];

export const TRANSFER_STATES = ['pre-transfer', 'transferring', 'transferred'] as const;
export type TransferState = (typeof TRANSFER_STATES)[number];

export const CATALOG_SOURCES = ['usda', 'off'] as const;
export type CatalogSourceKind = (typeof CATALOG_SOURCES)[number];

export const MODALITIES = ['photo', 'text'] as const;
export type Modality = (typeof MODALITIES)[number];

/** AD-41: every market defaults to `unresolved` and permits no remote inference. */
export const RESIDENCY_POSTURES = [
  'unresolved',
  'unrestricted',
  'restricted-in-region',
  'prohibited',
] as const;
export type ResidencyPosture = (typeof RESIDENCY_POSTURES)[number];

export const AUTH_MODES = ['attested', 'test-mode'] as const;
export type AuthMode = (typeof AUTH_MODES)[number];

export const AUTH_SCOPES = ['product', 'gateway', 'both'] as const;
export type AuthScope = (typeof AUTH_SCOPES)[number];

export const NUTRITION_ORIGINS = ['client-grounded', 'server-grounded'] as const;
export type NutritionOrigin = (typeof NUTRITION_ORIGINS)[number];
