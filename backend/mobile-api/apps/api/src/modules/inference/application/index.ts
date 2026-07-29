import type { Result } from '@supernutrition/shared';
import type {
  InferenceRequest,
  InferenceResult,
  InferenceFailure,
} from '../domain/inference.types.js';

/** Application-level use case. Orchestration detail stays in the orchestrator. */
export interface AnalyzeMealPhoto {
  execute(request: InferenceRequest): Promise<Result<InferenceResult, InferenceFailure>>;
}

export interface AnalyzeMealText {
  execute(request: InferenceRequest): Promise<Result<InferenceResult, InferenceFailure>>;
}
