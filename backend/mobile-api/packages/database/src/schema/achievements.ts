import {
  pgTable,
  uuid,
  text,
  integer,
  boolean,
  jsonb,
  timestamp,
  uniqueIndex,
} from 'drizzle-orm/pg-core';

/**
 * Achievements are DATA, not code.
 *
 * `criteria` is a declarative expression the rule engine evaluates against the
 * meal-facts projection. Adding the sixth achievement is a seed row plus a
 * fixture, never a conditional in application code (docs/04 §4.8).
 */
export const achievements = pgTable(
  'achievements',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    key: text('key').notNull(),
    name: text('name').notNull(),
    description: text('description').notNull(),
    iconRef: text('icon_ref'),
    criteria: jsonb('criteria').notNull(),
    xpReward: integer('xp_reward').notNull().default(0),
    active: boolean('active').notNull().default(true),
    sortOrder: integer('sort_order').notNull().default(0),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('achievements_key_uq').on(t.key)],
);

export type AchievementRow = typeof achievements.$inferSelect;
export type NewAchievementRow = typeof achievements.$inferInsert;
