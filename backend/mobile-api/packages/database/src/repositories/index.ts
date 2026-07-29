/**
 * Repository adapters — the only Drizzle consumers in the product.
 *
 * Nothing in `application` or `domain` imports Drizzle, so replacing the ORM is
 * an adapter change rather than a rewrite (docs/05 §5.7). Port interfaces live
 * in each module's `domain/`; the classes here implement them.
 *
 * Stage 2 generates contracts and placeholders only. Every method throws
 * `NotImplemented` so an unimplemented path fails loudly at the call site
 * instead of silently returning a plausible empty result. Stage 3 supplies the
 * query bodies.
 */
export { DrizzleRepository, NotImplementedError } from './base.repository.js';
export { DrizzleMealEventRepository } from './meal-event.repository.js';
export { DrizzleUserRepository } from './user.repository.js';
export { DrizzleInferenceRunRecorder } from './inference-run.repository.js';
