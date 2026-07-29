import { pgTable, uuid, jsonb, timestamp, uniqueIndex } from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { achievements } from './achievements.js';

/** The unique constraint is what makes replayed events safe to re-handle. */
export const userAchievements = pgTable(
  'user_achievements',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    achievementId: uuid('achievement_id')
      .notNull()
      .references(() => achievements.id, { onDelete: 'cascade' }),
    unlockedAt: timestamp('unlocked_at', { withTimezone: true }).notNull().defaultNow(),
    evidence: jsonb('evidence'),
  },
  (t) => [uniqueIndex('user_achievements_uq').on(t.userId, t.achievementId)],
);

export type UserAchievementRow = typeof userAchievements.$inferSelect;
export type NewUserAchievementRow = typeof userAchievements.$inferInsert;
