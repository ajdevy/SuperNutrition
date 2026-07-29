import { pgTable, uuid, integer, date, timestamp, uniqueIndex } from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { streakType } from './enums.js';

/**
 * Streaks advance on distinct local dates, not on meal counts, and are
 * recomputed rather than incremented: an increment-only streak cannot survive a
 * late-arriving or corrected meal (docs/04 §4.8).
 */
export const streaks = pgTable(
  'streaks',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    type: streakType('type').notNull(),
    currentCount: integer('current_count').notNull().default(0),
    longestCount: integer('longest_count').notNull().default(0),
    lastLocalDate: date('last_local_date'),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('streaks_user_type_uq').on(t.userId, t.type)],
);

export type StreakRow = typeof streaks.$inferSelect;
export type NewStreakRow = typeof streaks.$inferInsert;
