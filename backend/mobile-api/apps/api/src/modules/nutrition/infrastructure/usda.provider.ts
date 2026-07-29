import type { Result } from '@supernutrition/shared';
import type {
  NutritionProvider,
  FoodLookupRequest,
  FoodLookupResult,
  NutritionFailure,
} from '../domain/index.js';

/**
 * Skeleton only. No HTTP call, no SDK.
 *
 * The backend's primary USDA role is catalog package curation and publication,
 * not per-request lookup (docs/08 §8.1). This live path is gated on G-2 and on
 * its own contract release.
 *
 * Cache keys include `datasetVersion`, so a package upgrade invalidates by
 * construction rather than by an invalidation step that can be forgotten.
 */
export interface UsdaConfig {
  readonly baseUrl: string;
  readonly connectTimeoutMs: number;
  readonly totalTimeoutMs: number;
  readonly maxRetries: number;
  readonly circuitBreakerThreshold: number;
}

export class USDAProvider implements NutritionProvider {
  readonly id = 'usda' as const;
  readonly datasetVersion: string;

  readonly #apiKey: string;
  readonly #config: UsdaConfig;

  constructor(apiKey: string, config: UsdaConfig, datasetVersion: string) {
    this.#apiKey = apiKey;
    this.#config = config;
    this.datasetVersion = datasetVersion;
  }

  async lookup(_request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>> {
    void this.#apiKey;
    void this.#config;
    throw new Error('Not implemented in the Stage 2 skeleton: USDAProvider.lookup');
  }
}
