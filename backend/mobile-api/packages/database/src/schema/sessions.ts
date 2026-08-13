import { pgTable, uuid, text, timestamp, index } from 'drizzle-orm/pg-core';
import { users } from './users.js';
import { authMode, authScope } from './enums.js';

/**
 * Refresh tokens are stored hashed. `refreshFamilyId` groups a rotation
 * lineage: presenting an already-rotated token revokes the whole family, which
 * is the standard detection for a stolen refresh token (docs/09 §9.2).
 */
export const sessions = pgTable(
  'sessions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    installationId: text('installation_id').notNull(),
    environment: text('environment').notNull(),
    authMode: authMode('auth_mode').notNull(),
    scope: authScope('scope').notNull(),
    refreshTokenHash: text('refresh_token_hash').notNull(),
    refreshFamilyId: uuid('refresh_family_id').notNull(),
    issuedAt: timestamp('issued_at', { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    revokedAt: timestamp('revoked_at', { withTimezone: true }),
    revokedReason: text('revoked_reason'),
  },
  (t) => [
    index('sessions_refresh_hash_idx').on(t.refreshTokenHash),
    index('sessions_family_idx').on(t.refreshFamilyId),
    index('sessions_user_active_idx').on(t.userId, t.revokedAt),
  ],
);

export type SessionRow = typeof sessions.$inferSelect;
export type NewSessionRow = typeof sessions.$inferInsert;
