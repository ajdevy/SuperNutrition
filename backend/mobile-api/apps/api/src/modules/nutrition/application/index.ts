import type { Result } from '@supernutrition/shared';
import type {
  CatalogMatch,
  FoodLookupRequest,
  FoodLookupResult,
  NutritionFailure,
  NutritionProvider,
} from '../domain/index.js';

export interface LookupFood {
  execute(request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>>;
}

export interface SearchFood {
  execute(request: {
    query: string;
    country: string;
    limit?: number;
  }): Promise<Result<readonly CatalogMatch[], NutritionFailure>>;
}

/** Delegates to the provider — the client calculates, not the server (§8.4). */
export class LookupFoodHandler implements LookupFood {
  readonly #provider: NutritionProvider;
  constructor(provider: NutritionProvider) {
    this.#provider = provider;
  }

  execute(request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>> {
    return this.#provider.lookup(request);
  }
}

/** Delegates to the provider; an empty array is a valid result. */
export class SearchFoodHandler implements SearchFood {
  readonly #provider: NutritionProvider;
  constructor(provider: NutritionProvider) {
    this.#provider = provider;
  }

  /**
   * M-02 — the parameter type used to omit `limit` and the body dropped it, so
   * a validated request for 25 results silently became the provider's default
   * of 5. The port declares it and the route passes it; only this link lost it,
   * and TypeScript's parameter bivariance meant narrowing the object type here
   * compiled without complaint.
   */
  execute(request: {
    query: string;
    country: string;
    limit?: number;
  }): Promise<Result<readonly CatalogMatch[], NutritionFailure>> {
    return this.#provider.search(request.query, request.country, request.limit);
  }
}
