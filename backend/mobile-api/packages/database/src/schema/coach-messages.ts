import { pgTable, uuid, text, jsonb, timestamp, index } from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { mealEvents } from './meal-events.js';
import { coachMessageType } from './enums.js';

/**
 * Stores a message key plus params, not rendered prose. Localization is the
 * client's concern, and a stored English sentence cannot be re-rendered in
 * another locale (docs/05 §5.3).
 */
export const coachMessages = pgTable(
  'coach_messages',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    mealEventId: uuid('meal_event_id').references(() => mealEvents.id, { onDelete: 'set null' }),
    type: coachMessageType('type').notNull(),
    messageKey: text('message_key').notNull(),
    params: jsonb('params'),
    locale: text('locale'),
    deliveredAt: timestamp('delivered_at', { withTimezone: true }),
    readAt: timestamp('read_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index('coach_messages_user_created_idx').on(t.userId, t.createdAt)],
);

export type CoachMessageRow = typeof coachMessages.$inferSelect;
export type NewCoachMessageRow = typeof coachMessages.$inferInsert;
