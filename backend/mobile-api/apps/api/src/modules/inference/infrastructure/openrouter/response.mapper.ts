import type { Result } from '@supernutrition/shared';
import type { Candidate, InferenceFailure } from '../../domain/inference.types.js';

/**
 * The sole translation point from a provider shape to contract-v1 evidence.
 *
 * No provider type is exported beyond this directory, so a provider schema
 * cannot become the mobile contract (AD-13). Provider error strings are mapped
 * to the closed `InferenceFailure` set and never propagated: a domain that can
 * branch on an upstream message is coupled to that upstream's release notes.
 */
export interface ProviderResponseMapper {
  toCandidates(raw: unknown): Result<readonly Candidate[], InferenceFailure>;
  toFailure(status: number, raw: unknown): InferenceFailure;
}
