/**
 * Adapter bindings for the meals module.
 *
 * The Drizzle row-level implementation lives in packages/database; the adapter
 * here binds it to the `MealEventRepository` port so the composition root has
 * one import site per module and nothing above this layer sees a row.
 */
export type {
  MealEventRepository,
  MealRepositoryFailure,
} from '../domain/meal-event.repository.js';

export {
  DrizzleMealEventRepositoryAdapter,
  toDomainMealEvent,
  toWriteRows,
} from './meal-event.adapter.js';
