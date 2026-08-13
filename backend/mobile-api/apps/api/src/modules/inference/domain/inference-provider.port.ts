import type { Result } from '@supernutrition/shared';
import type {
  Candidate,
  InferenceContext,
  InferenceFailure,
  PayloadRef,
  ProviderType,
} from './inference.types.js';
import type { ModelProfile } from './model-profile.js';

export interface TokenUsage {
  readonly promptTokens: number | null;
  readonly completionTokens: number | null;
}

export interface ProviderRequest {
  readonly attemptId: string;
  readonly modality: 'photo' | 'text';
  readonly payload: PayloadRef;
  readonly context: InferenceContext;
  readonly profile: ModelProfile;
  readonly deadlineMs: number;
  readonly signal: AbortSignal;
}

export interface ProviderResponse {
  readonly candidates: readonly Candidate[];
  readonly modelId: string;
  readonly durationMs: number;
  readonly usage: TokenUsage;
  readonly outcome: 'candidates' | 'abstention';
}

/**
 * Principle 4 / AD-9: no business module imports a provider SDK. Modules depend
 * on this port; the composition root binds the adapter. Adding a provider is
 * five steps and touches no domain, application, or presentation file
 * (docs/07 §7.9).
 */
export interface InferenceProvider {
  readonly id: ProviderType;
  supports(profile: ModelProfile): boolean;
  infer(request: ProviderRequest): Promise<Result<ProviderResponse, InferenceFailure>>;
}
