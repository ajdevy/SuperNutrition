import { pgTable, uuid, text, timestamp, index } from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { consentModality, consentDecision } from './enums.js';

/**
 * AD-8: the receipt binds the digest of the scrubbed normalized payload. The
 * payload itself is never stored, here or anywhere else. This row is the
 * durable proof the transfer precondition was met.
 */
export const consentReceipts = pgTable(
  'consent_receipts',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    attemptId: text('attempt_id').notNull(),
    purpose: text('purpose').notNull(),
    modality: consentModality('modality').notNull(),
    payloadDigest: text('payload_digest').notNull(),
    marketJurisdiction: text('market_jurisdiction').notNull(),
    processingRegion: text('processing_region').notNull(),
    retentionVersion: text('retention_version').notNull(),
    configurationVersion: text('configuration_version').notNull(),
    consentCopyVersion: text('consent_copy_version').notNull(),
    decision: consentDecision('decision').notNull(),
    decidedAt: timestamp('decided_at', { withTimezone: true }).notNull(),
  },
  (t) => [
    index('consent_receipts_user_idx').on(t.userId, t.decidedAt),
    index('consent_receipts_digest_idx').on(t.payloadDigest),
  ],
);

export type ConsentReceiptRow = typeof consentReceipts.$inferSelect;
export type NewConsentReceiptRow = typeof consentReceipts.$inferInsert;
