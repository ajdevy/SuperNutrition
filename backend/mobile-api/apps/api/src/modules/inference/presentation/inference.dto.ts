/** Contract-v1 response envelope (docs/06 §6.5). */
export interface GatewayEnvelope<T> {
  readonly contractVersion: string;
  readonly requestId: string;
  readonly attemptId: string;
  readonly outcome: T;
}

export interface CandidatesOutcome {
  readonly outcomeType: 'candidates';
  readonly candidates: readonly CandidateDto[];
  readonly requiresConfirmation: boolean;
}

export interface AbstentionOutcome {
  readonly outcomeType: 'abstention';
  readonly reasonCode: string;
  readonly userMessageKey: string;
}

export interface FailureOutcome {
  readonly outcomeType: 'failure';
  readonly failureCode: string;
  readonly recoverable: boolean;
}

export type InferenceOutcomeDto = CandidatesOutcome | AbstentionOutcome | FailureOutcome;

/**
 * No calorie, macro, or micronutrient field appears on this type or anything
 * it references. Asserted by a schema test (docs/06 §6.5).
 */
export interface CandidateDto {
  readonly candidateId: string;
  readonly label: string;
  readonly rank: number;
  readonly confidence: { score: number; band: string };
  readonly unresolvedAssumptions: readonly { code: string; userMessageKey: string }[];
  readonly provenance: {
    route: string;
    engineId: string;
    modelVersion: string;
    calibrationVersion: string;
    contractVersion: string;
  };
}

/**
 * Cancellation response. `recallClaimed` is always false after transfer:
 * reporting a deletion request as a deletion guarantee is a claim the gateway
 * cannot keep (docs/06 §6.5, AD-8).
 */
export interface CancelInferenceResponse {
  readonly attemptId: string;
  readonly transferState: 'pre-transfer' | 'transferring' | 'transferred';
  readonly bytesTransferred: number;
  readonly recallClaimed: false;
  readonly downstreamDeletionRequested: boolean;
}

export interface MarketPolicyResponse {
  readonly marketJurisdiction: string;
  readonly processingRegion: string | null;
  readonly residencyPosture: string;
  readonly remotePermitted: boolean;
  readonly consentCopyVersion: string | null;
  readonly acceptedInputLanguages: readonly string[];
}
