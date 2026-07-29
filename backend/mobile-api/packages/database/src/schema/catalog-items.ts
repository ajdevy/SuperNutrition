import { pgTable, uuid, text, jsonb, timestamp, uniqueIndex } from 'drizzle-orm/pg-core';
import { catalogPackages } from './catalog-packages.js';

/**
 * `foodId` is namespace-qualified (`usda:fdc:171705`). A bare integer becomes
 * ambiguous the moment a second source exists (docs/08 §8.3).
 *
 * The GIN index on the search vector is added by migration; drizzle-kit does
 * not model tsvector generation, so it is applied as raw SQL there.
 */
export const catalogItems = pgTable(
  'catalog_items',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    packageId: uuid('package_id')
      .notNull()
      .references(() => catalogPackages.id, { onDelete: 'cascade' }),
    foodId: text('food_id').notNull(),
    displayName: text('display_name').notNull(),
    nutrientsPer100g: jsonb('nutrients_per_100g').notNull(),
    servingOptions: jsonb('serving_options').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('catalog_items_package_food_uq').on(t.packageId, t.foodId)],
);

export type CatalogItemRow = typeof catalogItems.$inferSelect;
export type NewCatalogItemRow = typeof catalogItems.$inferInsert;
