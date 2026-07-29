import { type Result, ok, err } from '../result/result.js';
import type { Brand } from '../types/branded.js';

/**
 * Branded identifiers. A `UserId` and a `MealEventId` are both strings at
 * runtime, and without brands they are interchangeable at every call site.
 */
export type EntityId = Brand<string, 'EntityId'>;
export type UserId = Brand<string, 'UserId'>;
export type MealEventId = Brand<string, 'MealEventId'>;
export type MealItemId = Brand<string, 'MealItemId'>;
export type AchievementId = Brand<string, 'AchievementId'>;
export type SessionId = Brand<string, 'SessionId'>;
export type InstallationId = Brand<string, 'InstallationId'>;

/** Opaque, random, per-inference-attempt. Never derived from user identity. */
export type AttemptId = Brand<string, 'AttemptId'>;
export type EffectId = Brand<string, 'EffectId'>;

/** Namespace-qualified catalog food reference, e.g. `usda:fdc:171705`. */
export type FoodRef = Brand<string, 'FoodRef'>;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export type IdError = 'empty' | 'malformed';

export const parseUuid = <T extends string>(value: string): Result<T, IdError> => {
  if (value.length === 0) return err('empty');
  if (!UUID_PATTERN.test(value)) return err('malformed');
  return ok(value as T);
};

/**
 * Catalog food references are namespace-qualified so a bare integer cannot
 * become ambiguous once a second catalog source exists (docs/08 §8.3).
 */
const FOOD_REF_PATTERN = /^(usda|off):[a-z0-9-]+:[A-Za-z0-9._-]+$/;

export const parseFoodRef = (value: string): Result<FoodRef, IdError> => {
  if (value.length === 0) return err('empty');
  if (!FOOD_REF_PATTERN.test(value)) return err('malformed');
  return ok(value as FoodRef);
};

/** Attempt IDs are opaque and prefixed so they are recognisable in evidence. */
const ATTEMPT_PATTERN = /^att_[A-Za-z0-9]{16,64}$/;

export const parseAttemptId = (value: string): Result<AttemptId, IdError> => {
  if (value.length === 0) return err('empty');
  if (!ATTEMPT_PATTERN.test(value)) return err('malformed');
  return ok(value as AttemptId);
};
