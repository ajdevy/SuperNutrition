import { pgTable, uuid, integer, text, timestamp, uniqueIndex, index } from 'drizzle-orm/pg-core';
import { users } from './users.js';

/**
 * A ledger, not a counter. Balance is SUM(amount). The unique constraint on
 * (userId, reason, refId) makes a replayed award a no-op rather than a
 * double-grant (docs/04 §4.8).
 */
export const xpTransactions = pgTable(
  'xp_transactions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    amount: integer('amount').notNull(),
    reason: text('reason').notNull(),
    refId: text('ref_id').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex('xp_transactions_idempotency_uq').on(t.userId, t.reason, t.refId),
    index('xp_transactions_user_created_idx').on(t.userId, t.createdAt),
  ],
);

export type XpTransactionRow = typeof xpTransactions.$inferSelect;
export type NewXpTransactionRow = typeof xpTransactions.$inferInsert;
