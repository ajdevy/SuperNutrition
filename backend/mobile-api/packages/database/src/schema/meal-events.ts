import {
  pgTable,
  uuid,
  text,
  integer,
  date,
  timestamp,
  index,
  type AnyPgColumn,
} from 'drizzle-orm/pg-core';
import { relations } from 'drizzle-orm';
import { users } from './users.js';
import { mealSource, mealStatus, mealType } from './enums.js';
import { mealItems } from './meal-items.js';
import { nutritionSnapshots } from './nutrition-snapshots.js';

/**
 * The core aggregate root. `confirmed` is terminal: a correction creates a new
 * revision through `supersedesId` rather than mutating history, because
 * Progress and Gamification have already reacted to the original fact
 * (docs/02 §2.3).
 *
 * `localDate` and `timezone` are written at creation from the client's
 * configuration snapshot, never derived later from UTC (docs/05 §5.1).
 */
export const mealEvents = pgTable(
  'meal_events',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    source: mealSource('source').notNull(),
    status: mealStatus('status').notNull().default('pending'),
    occurredAt: timestamp('occurred_at', { withTimezone: true }).notNull(),
    localDate: date('local_date').notNull(),
    timezone: text('timezone').notNull(),
    mealType: mealType('meal_type').notNull().default('unspecified'),
    revision: integer('revision').notNull().default(1),
    supersedesId: uuid('supersedes_id').references((): AnyPgColumn => mealEvents.id),
    attemptId: text('attempt_id'),
    configurationVersion: text('configuration_version'),
    transcript: text('transcript'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    confirmedAt: timestamp('confirmed_at', { withTimezone: true }),
    deletedAt: timestamp('deleted_at', { withTimezone: true }),
  },
  (t) => [
    index('meal_events_user_occurred_idx').on(t.userId, t.occurredAt),
    index('meal_events_user_local_date_idx').on(t.userId, t.localDate),
    index('meal_events_user_status_idx').on(t.userId, t.status),
    index('meal_events_attempt_idx').on(t.attemptId),
  ],
);

export const mealEventsRelations = relations(mealEvents, ({ many, one }) => ({
  items: many(mealItems),
  snapshot: one(nutritionSnapshots, {
    fields: [mealEvents.id],
    references: [nutritionSnapshots.mealEventId],
  }),
}));

export type MealEventRow = typeof mealEvents.$inferSelect;
export type NewMealEventRow = typeof mealEvents.$inferInsert;
