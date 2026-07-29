import type {
  AttemptId,
  ConfidenceScore,
  InferenceOutcomeKind,
  Modality,
} from '@supernutrition/shared';

/** Extension point. Adding a provider does not change any type here. */
export const PROVIDER_TYPES = ['openrouter'] as const;
export type ProviderType = (typeof PROVIDER_TYPES)[number];

/** Opaque handle to the in-flight buffer. The orchestrator never holds bytes. */
export interface PayloadRef {
  readonly attemptId: AttemptId;
  readonly byteLength: number;
  readonly contentType: string;
  readonly digest: string;
}

export interface InferenceContext {
  readonly catalogCountry: string;
  readonly mealType: string;
  readonly locale: string;
  readonly measurementSystem: string;
  readonly goalSummary?: string;
  readonly priorMealSummary?: string;
}

export interface Candidate {
  readonly candidateId: string;
  readonly label: string;
  readonly rank: number;
  readonly confidence: ConfidenceScore;
  readonly unresolvedAssumptions: readonly Assumption[];
  readonly provenance: Provenance;
}

export interface Assumption {
  readonly code: string;
  readonly userMessageKey: string;
}

export interface Provenance {
  readonly route: 'local' | 'remote';
  readonly engineId: string;
  readonly modelVersion: string;
  readonly calibrationVersion: string;
  readonly contractVersion: string;
}

/**
 * Identification evidence only.
 *
 * There are no calorie, macro, or micronutrient fields on this type, and there
 * will not be. `contracts/inference/v1` sets `numeric_nutrition = false`, and a
 * schema test asserts the absence — the cheapest way for a model to be wrong is
 * to be asked for a number (docs/04 §4.5).
 */
export interface InferenceResult {
  readonly attemptId: AttemptId;
  readonly outcome: InferenceOutcomeKind;
  readonly candidates: readonly Candidate[];
  readonly confidence: ConfidenceScore | null;
  readonly modelId: string;
  readonly durationMs: number;
  readonly passesRun: number;
  readonly requiresConfirmation: boolean;
}

/** Closed typed set. Provider error strings are mapped here and never propagated. */
export type InferenceFailure =
  | 'timeout'
  | 'rate-limited'
  | 'provider-unavailable'
  | 'content-rejected'
  | 'malformed-response'
  | 'cancelled'
  | 'budget-exceeded'
  | 'kill-switch';

export const RETRYABLE_FAILURES: readonly InferenceFailure[] = [
  'timeout',
  'rate-limited',
  'provider-unavailable',
];

export const isRetryable = (failure: InferenceFailure): boolean =>
  RETRYABLE_FAILURES.includes(failure);

export interface InferenceRequest {
  readonly attemptId: AttemptId;
  readonly modality: Modality;
  readonly payload: PayloadRef;
  readonly context: InferenceContext;
  readonly contractVersion: string;
}
