import { pgTable, uuid, text, jsonb, timestamp, index } from 'drizzle-orm/pg-core';
import { mealEvents } from './meal-events.js';
import { mealItems } from './meal-items.js';
import { evidenceType } from './enums.js';

/**
 * Identity evidence and provenance.
 *
 * `payload` holds candidate labels, ranks, and confidences. It never holds
 * image bytes, image digests joinable to a user, prompt text, or raw provider
 * responses (AD-14).
 */
export const inferenceEvidence = pgTable(
  'inference_evidence',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    mealEventId: uuid('meal_event_id')
      .notNull()
      .references(() => mealEvents.id, { onDelete: 'cascade' }),
    mealItemId: uuid('meal_item_id').references(() => mealItems.id, { onDelete: 'cascade' }),
    attemptId: text('attempt_id').notNull(),
    type: evidenceType('type').notNull(),
    payload: jsonb('payload').notNull(),
    provenance: jsonb('provenance').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index('inference_evidence_meal_idx').on(t.mealEventId),
    index('inference_evidence_attempt_idx').on(t.attemptId),
  ],
);

export type InferenceEvidenceRow = typeof inferenceEvidence.$inferSelect;
export type NewInferenceEvidenceRow = typeof inferenceEvidence.$inferInsert;
