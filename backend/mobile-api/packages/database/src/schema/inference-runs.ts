import {
  pgTable,
  uuid,
  text,
  integer,
  smallint,
  bigint,
  boolean,
  numeric,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';
import { inferenceOutcome } from './enums.js';

/**
 * AI telemetry for model benchmarking.
 *
 * There is deliberately NO `user_id` column, and there will not be one.
 *
 * AD-14 permits an opaque attempt ID and forbids telemetry that identifies
 * content or people. A user foreign key here would turn an operational metrics
 * table into a per-person record of what someone photographed and when,
 * joinable to `meal_events` by anyone with read access. Benchmarking needs
 * model, latency, cost, and outcome distributions — none of which require
 * knowing whose meal it was (docs/05 §5.4).
 *
 * Also absent by design: prompt text, raw provider responses, candidate labels,
 * image digests, headers, and free-form failure messages. `failureCode` is a
 * closed typed set.
 *
 * Per-user cost attribution, if ever genuinely needed, is a separate
 * G-6-gated design with its own consent and retention basis — not a column
 * added here.
 *
 * Retention: 90 days at full granularity, then rollups by
 * (modelId, outcome, environment).
 */
export const inferenceRuns = pgTable(
  'inference_runs',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    attemptId: text('attempt_id').notNull(),
    passNumber: smallint('pass_number').notNull(),
    provider: text('provider').notNull(),
    modelId: text('model_id').notNull(),
    modelProfileVersion: text('model_profile_version').notNull(),
    outcome: inferenceOutcome('outcome').notNull(),
    success: boolean('success').notNull(),
    confidence: numeric('confidence', { precision: 4, scale: 3 }),
    durationMs: integer('duration_ms').notNull(),
    durationBucket: text('duration_bucket').notNull(),
    promptTokens: integer('prompt_tokens'),
    completionTokens: integer('completion_tokens'),
    estimatedCostMicros: bigint('estimated_cost_micros', { mode: 'bigint' }),
    failureCode: text('failure_code'),
    marketJurisdiction: text('market_jurisdiction'),
    processingRegion: text('processing_region'),
    environment: text('environment').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index('inference_runs_created_idx').on(t.createdAt),
    index('inference_runs_model_created_idx').on(t.modelId, t.createdAt),
  ],
);

export type InferenceRunRow = typeof inferenceRuns.$inferSelect;
export type NewInferenceRunRow = typeof inferenceRuns.$inferInsert;
