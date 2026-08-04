import { type Result, ok, createConfidenceScore, isOk } from '@supernutrition/shared';
import type {
  InferenceProvider,
  ProviderRequest,
  ProviderResponse,
} from '../../domain/inference-provider.port.js';
import type { InferenceFailure, ProviderType } from '../../domain/inference.types.js';
import type { ModelProfile } from '../../domain/model-profile.js';

/**
 * Deterministic stand-in for a real provider.
 *
 * Enabled ONLY by STUB_INFERENCE=true, and the composition root refuses to bind
 * it unless TEST_MODE is on — so it can never serve a production environment.
 * It exists so the client↔gateway path can be exercised end-to-end without an
 * OpenRouter key; every gate, the envelope, and the disposal contract are the
 * real ones.
 *
 * It returns identity evidence only. Like every provider, it cannot express a
 * nutrient: the response type has nowhere to put one (AD-7).
 */
export class StubInferenceProvider implements InferenceProvider {
  readonly id: ProviderType = 'openrouter';

  supports(profile: ModelProfile): boolean {
    return profile.enabled;
  }

  async infer(request: ProviderRequest): Promise<Result<ProviderResponse, InferenceFailure>> {
    const engineId = 'gateway-stub';
    const calibration = 'stub-uncalibrated';
    const build = (label: string, score: number, rank: number) => {
      const confidence = createConfidenceScore(score, engineId, calibration);
      if (!isOk(confidence)) throw new Error('stub confidence out of range');
      return {
        candidateId: `cand_0${rank}`,
        label,
        rank,
        confidence: confidence.value,
        unresolvedAssumptions:
          rank === 1
            ? [{ code: 'portion-unknown', userMessageKey: 'inference.assumption.portion_unknown' }]
            : [],
        provenance: {
          route: 'remote' as const,
          engineId,
          modelVersion: request.profile.model,
          calibrationVersion: calibration,
          contractVersion: 'photo-inference-engine/v1',
        },
      };
    };

    return ok({
      candidates: [build('Mixed salad bowl', 0.93, 1), build('Garden salad', 0.61, 2)],
      modelId: request.profile.model,
      durationMs: 12,
      usage: { promptTokens: 0, completionTokens: 0 },
      outcome: 'candidates',
    });
  }
}
