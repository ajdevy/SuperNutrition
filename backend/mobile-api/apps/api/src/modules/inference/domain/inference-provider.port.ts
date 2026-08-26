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
  /**
   * Fired immediately before the payload goes on the wire (#48, audit F-03).
   *
   * Only the adapter knows this. The pipeline used to mark the transfer
   * `transferring` unconditionally before calling the orchestrator, so a
   * deployment with no provider configured — a supported configuration —
   * reported that bytes were in flight when nothing had left the process, and
   * the published `pre-transfer` guarantee ("Nothing was sent. This is a
   * complete guarantee.") became unreachable through the API.
   */
  readonly onTransferStarted?: () => void;
  /**
   * Fired when the provider ANSWERED — any HTTP status — which is the moment it
   * is known to have received the payload (#48, audit F-02).
   *
   * Deliberately not `response.ok`. A provider that received the whole image,
   * ran inference and replied 200 with a body the mapper cannot use, or replied
   * 400/422, has admitted the payload just as much as one that replied usefully.
   * Inferring admission from a usable answer understated where the photo went,
   * which is the same class of false statement #48 set out to remove, pointing
   * the other way.
   */
  readonly onProviderAdmitted?: () => void;
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
