/**
 * Adapter bindings for the meals module.
 *
 * The Drizzle implementation of `MealEventRepository` lives in
 * packages/database/src/repositories. This file re-exports the port so the
 * composition root has one import site per module.
 */
export type {
  MealEventRepository,
  MealRepositoryFailure,
} from '../domain/meal-event.repository.js';
