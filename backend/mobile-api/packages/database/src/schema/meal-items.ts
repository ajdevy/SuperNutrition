import {
  pgTable,
  uuid,
  text,
  integer,
  numeric,
  boolean,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';
import { relations } from 'drizzle-orm';
import { mealEvents } from './meal-events.js';

/**
 * `confirmedByUser` records Principle 2 in the data: user confirmation outranks
 * catalog data, which outranks AI inference.
 */
export const mealItems = pgTable(
  'meal_items',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    mealEventId: uuid('meal_event_id')
      .notNull()
      .references(() => mealEvents.id, { onDelete: 'cascade' }),
    position: integer('position').notNull(),
    foodRef: text('food_ref').notNull(),
    displayName: text('display_name').notNull(),
    servingUnit: text('serving_unit').notNull(),
    servingAmount: numeric('serving_amount', { precision: 10, scale: 3 }).notNull(),
    gramWeight: numeric('gram_weight', { precision: 10, scale: 3 }),
    confidence: numeric('confidence', { precision: 4, scale: 3 }),
    confirmedByUser: boolean('confirmed_by_user').notNull().default(false),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index('meal_items_event_position_idx').on(t.mealEventId, t.position),
    index('meal_items_food_ref_idx').on(t.foodRef),
  ],
);

export const mealItemsRelations = relations(mealItems, ({ one }) => ({
  mealEvent: one(mealEvents, {
    fields: [mealItems.mealEventId],
    references: [mealEvents.id],
  }),
}));

export type MealItemRow = typeof mealItems.$inferSelect;
export type NewMealItemRow = typeof mealItems.$inferInsert;
