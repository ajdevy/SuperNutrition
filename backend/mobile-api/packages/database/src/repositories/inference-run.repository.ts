import { lt } from 'drizzle-orm';
import { DrizzleRepository } from './base.repository.js';
import type { Database } from '../client.js';
import { inferenceRuns, type NewInferenceRunRow } from '../schema/inference-runs.js';

/**
 * Implements the `InferenceRunRecorder` port.
 *
 * The row type carries no user foreign key by construction (docs/05 §5.4), so
 * this adapter cannot record one even if a caller supplied it. The insert is
 * built field by field from an allowlist rather than spread from the input —
 * a spread would silently persist whatever a future caller decided to attach,
 * which is exactly how a telemetry table acquires a user identifier.
 */
export class DrizzleInferenceRunRecorder extends DrizzleRepository {
  constructor(db: Database) {
    super(db);
  }

  async record(run: NewInferenceRunRow): Promise<void> {
    await this.db.insert(inferenceRuns).values({
      attemptId: run.attemptId,
      passNumber: run.passNumber,
      provider: run.provider,
      modelId: run.modelId,
      modelProfileVersion: run.modelProfileVersion,
      outcome: run.outcome,
      success: run.success,
      confidence: run.confidence ?? null,
      durationMs: run.durationMs,
      durationBucket: run.durationBucket,
      promptTokens: run.promptTokens ?? null,
      completionTokens: run.completionTokens ?? null,
      estimatedCostMicros: run.estimatedCostMicros ?? null,
      failureCode: run.failureCode ?? null,
      marketJurisdiction: run.marketJurisdiction ?? null,
      processingRegion: run.processingRegion ?? null,
      environment: run.environment,
    });
  }

  /**
   * Retention sweep. 90 days at full granularity, then rollups (docs/05 §5.4).
   * `inference_runs` is the only table that grows with operations rather than
   * with user-owned history, and its value decays.
   */
  async pruneOlderThan(cutoff: Date): Promise<number> {
    const deleted = await this.db
      .delete(inferenceRuns)
      .where(lt(inferenceRuns.createdAt, cutoff))
      .returning({ id: inferenceRuns.id });
    return deleted.length;
  }
}
