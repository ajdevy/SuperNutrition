import { pgTable, uuid, integer, numeric, date, timestamp, index } from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { goalType, activityLevel } from './enums.js';

/**
 * Goals are versioned, not mutated (docs/04 §4.3). Changing a target closes the
 * current period and opens a new one, so historical adherence is computed
 * against the goal that was actually in force.
 */
export const userGoals = pgTable(
  'user_goals',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    goalType: goalType('goal_type').notNull(),
    targetCalories: integer('target_calories').notNull(),
    targetProteinG: numeric('target_protein_g', { precision: 10, scale: 3 }).notNull(),
    targetFatG: numeric('target_fat_g', { precision: 10, scale: 3 }).notNull(),
    targetCarbG: numeric('target_carb_g', { precision: 10, scale: 3 }).notNull(),
    activityLevel: activityLevel('activity_level').notNull(),
    effectiveFrom: date('effective_from').notNull(),
    effectiveTo: date('effective_to'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index('user_goals_user_effective_idx').on(t.userId, t.effectiveFrom)],
);

export type UserGoalRow = typeof userGoals.$inferSelect;
export type NewUserGoalRow = typeof userGoals.$inferInsert;
