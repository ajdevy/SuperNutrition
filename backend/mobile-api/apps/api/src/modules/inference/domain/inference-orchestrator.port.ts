import type { Result } from '@supernutrition/shared';
import type { InferenceFailure, InferenceRequest, InferenceResult } from './inference.types.js';

/**
 * Provider selection, confidence routing, retry, deadlines, telemetry, kill
 * switch. It does not know what a meal is.
 *
 * Pass 2 reuses the payload already received for the same attempt: it is not a
 * new transfer and requires no new consent receipt (docs/07 §7.5).
 */
export interface InferenceOrchestrator {
  analyze(request: InferenceRequest): Promise<Result<InferenceResult, InferenceFailure>>;
}

/** Telemetry row per pass. No user foreign key (docs/05 §5.4). */
export interface InferenceRunTelemetry {
  readonly attemptId: string;
  readonly passNumber: number;
  readonly provider: string;
  readonly modelId: string;
  readonly modelProfileVersion: string;
  readonly outcome: 'candidates' | 'abstention' | 'failure';
  readonly success: boolean;
  readonly confidence: number | null;
  readonly durationMs: number;
  readonly durationBucket: string;
  readonly promptTokens: number | null;
  readonly completionTokens: number | null;
  readonly estimatedCostMicros: bigint | null;
  readonly failureCode: string | null;
  readonly marketJurisdiction: string | null;
  readonly processingRegion: string | null;
  readonly environment: string;
}

export interface InferenceRunRecorder {
  record(run: InferenceRunTelemetry): Promise<void>;
}

export const DURATION_BUCKETS = ['under-1s', '1s-3s', '3s-10s', 'over-10s'] as const;
export type DurationBucket = (typeof DURATION_BUCKETS)[number];

/** Durations are bucketed before telemetry: AD-14 allows buckets, not raw traces. */
export const bucketDuration = (ms: number): DurationBucket => {
  if (ms < 1_000) return 'under-1s';
  if (ms < 3_000) return '1s-3s';
  if (ms < 10_000) return '3s-10s';
  return 'over-10s';
};
