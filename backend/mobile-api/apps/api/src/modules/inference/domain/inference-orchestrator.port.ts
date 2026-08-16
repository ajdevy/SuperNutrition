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
  analyze(
    request: InferenceRequest,
    options: InferenceAttemptOptions,
  ): Promise<Result<InferenceResult, InferenceFailure>>;
}

/**
 * Attempt-scoped runtime concerns, separate from the request payload.
 *
 * The signal is not optional: AD-6 requires cancellation to abort transport and
 * downstream work, and a port that cannot be cancelled cannot honour that.
 * The resolved market values travel here rather than in the request because
 * they are server-resolved, never client-supplied (AD-41 MP-7).
 */
export interface InferenceAttemptOptions {
  readonly signal: AbortSignal;
  readonly marketJurisdiction: string | null;
  readonly processingRegion: string | null;
  /**
   * #48 — fired when the payload actually goes on the wire.
   *
   * The pipeline cannot know this: it cannot tell a configured provider from an
   * absent one, and marking the transfer before `analyze()` reported bytes in
   * flight for a deployment with no provider at all. The adapter fires it.
   */
  readonly onTransferStarted?: () => void;
  /**
   * #48 — fired when a provider ANSWERED, which is when it is known to have
   * received the payload. Any HTTP status counts; only a request that never got
   * a response leaves it unfired.
   *
   * Not `response.ok`: a provider that received the whole image and replied 400,
   * 422, or 200-with-an-unusable-body has admitted it just as much as one that
   * replied usefully. Inferring admission from a USABLE answer understated where
   * the photo went.
   */
  readonly onProviderAdmitted?: () => void;
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
