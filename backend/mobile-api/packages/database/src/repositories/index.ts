/**
 * Repository adapters — the only Drizzle consumers in the product.
 *
 * Nothing in `application` or `domain` imports Drizzle, so replacing the ORM is
 * an adapter change rather than a rewrite (docs/05 §5.7). Port interfaces live
 * in each module's `domain/`; the classes here implement them, and each module's
 * `infrastructure/` binds one to the other.
 *
 * A method that is not implemented yet throws `NotImplemented` rather than
 * returning a plausible empty result, so an unimplemented path fails loudly at
 * the call site.
 */
export { DrizzleRepository, NotImplementedError } from './base.repository.js';
export {
  DrizzleMealEventRepository,
  mealEventByIdConditions,
  mealEventPageConditions,
} from './meal-event.repository.js';
export type {
  MealEventAggregateRow,
  MealEventPageQuery,
  MealEventPageRows,
} from './meal-event.repository.js';
export { DrizzleUserRepository } from './user.repository.js';
export { DrizzleInferenceRunRecorder } from './inference-run.repository.js';
