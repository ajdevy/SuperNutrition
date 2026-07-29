import type { Result } from '@supernutrition/shared';
import type {
  InferenceProvider,
  ProviderRequest,
  ProviderResponse,
} from '../../domain/inference-provider.port.js';
import type { InferenceFailure, ProviderType } from '../../domain/inference.types.js';
import type { ModelProfile } from '../../domain/model-profile.js';

/**
 * The ONLY place in the product that may know OpenRouter exists.
 *
 * The API key is read once into this adapter's closure at construction. It is
 * not placed on a config object other modules can reach, not stored in a
 * global, and not re-readable (docs/09 §9.8).
 *
 * Stage 2 provides the shape; no HTTP call, no SDK, no prompt text.
 */
export interface OpenRouterConfig {
  readonly baseUrl: string;
  readonly timeoutMs: number;
}

export class OpenRouterInferenceProvider implements InferenceProvider {
  readonly id: ProviderType = 'openrouter';

  readonly #apiKey: string;
  readonly #config: OpenRouterConfig;

  constructor(apiKey: string, config: OpenRouterConfig) {
    this.#apiKey = apiKey;
    this.#config = config;
  }

  supports(profile: ModelProfile): boolean {
    return profile.provider === 'openrouter' && profile.enabled;
  }

  async infer(_request: ProviderRequest): Promise<Result<ProviderResponse, InferenceFailure>> {
    void this.#apiKey;
    void this.#config;
    throw new Error('Not implemented in the Stage 2 skeleton: OpenRouterInferenceProvider.infer');
  }
}
