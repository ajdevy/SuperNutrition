import {
  pgTable,
  uuid,
  integer,
  numeric,
  date,
  jsonb,
  timestamp,
  uniqueIndex,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { userGoals } from './user-goals.js';

/**
 * A derived, rebuildable projection over meal_events — not a source of truth.
 * `computedAt` is the watermark a maintenance job uses to detect staleness
 * (docs/04 §4.8).
 */
export const dailyProgress = pgTable(
  'daily_progress',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    localDate: date('local_date').notNull(),
    totalCalories: numeric('total_calories', { precision: 10, scale: 3 }).notNull(),
    totalProteinG: numeric('total_protein_g', { precision: 10, scale: 3 }),
    totalFatG: numeric('total_fat_g', { precision: 10, scale: 3 }),
    totalCarbG: numeric('total_carb_g', { precision: 10, scale: 3 }),
    totalFiberG: numeric('total_fiber_g', { precision: 10, scale: 3 }),
    mealCount: integer('meal_count').notNull().default(0),
    goalId: uuid('goal_id').references(() => userGoals.id),
    goalAdherence: jsonb('goal_adherence'),
    computedAt: timestamp('computed_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex('daily_progress_user_date_uq').on(t.userId, t.localDate),
    index('daily_progress_user_date_desc_idx').on(t.userId, t.localDate),
  ],
);

export type DailyProgressRow = typeof dailyProgress.$inferSelect;
export type NewDailyProgressRow = typeof dailyProgress.$inferInsert;
