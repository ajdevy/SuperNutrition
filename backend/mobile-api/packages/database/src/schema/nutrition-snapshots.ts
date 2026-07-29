import { pgTable, uuid, text, numeric, jsonb, timestamp, uniqueIndex } from 'drizzle-orm/pg-core';
import { mealEvents } from './meal-events.js';
import { nutritionOrigin } from './enums.js';

/**
 * Nutrition frozen at confirmation.
 *
 * AD-7: every number carries catalog source, dataset version, and serving
 * basis. For a client-created meal this row is persisted verbatim from the
 * device and is never recomputed here (docs/01 §1.5).
 *
 * Nutrients are `numeric`, never float: float totals do not match their parts.
 */
export const nutritionSnapshots = pgTable(
  'nutrition_snapshots',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    mealEventId: uuid('meal_event_id')
      .notNull()
      .references(() => mealEvents.id, { onDelete: 'cascade' }),
    catalogSource: text('catalog_source').notNull(),
    datasetVersion: text('dataset_version').notNull(),
    servingBasis: jsonb('serving_basis').notNull(),
    origin: nutritionOrigin('origin').notNull(),
    calories: numeric('calories', { precision: 10, scale: 3 }).notNull(),
    proteinG: numeric('protein_g', { precision: 10, scale: 3 }),
    fatG: numeric('fat_g', { precision: 10, scale: 3 }),
    carbG: numeric('carb_g', { precision: 10, scale: 3 }),
    fiberG: numeric('fiber_g', { precision: 10, scale: 3 }),
    sugarG: numeric('sugar_g', { precision: 10, scale: 3 }),
    sodiumMg: numeric('sodium_mg', { precision: 10, scale: 3 }),
    perItem: jsonb('per_item').notNull(),
    computedAt: timestamp('computed_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('nutrition_snapshots_meal_event_uq').on(t.mealEventId)],
);

export type NutritionSnapshotRow = typeof nutritionSnapshots.$inferSelect;
export type NewNutritionSnapshotRow = typeof nutritionSnapshots.$inferInsert;
