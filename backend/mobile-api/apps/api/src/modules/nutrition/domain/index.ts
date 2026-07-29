import type { Result, FoodRef, CatalogSourceKind } from '@supernutrition/shared';

export interface ServingBasis {
  readonly unit: string;
  readonly amount: string;
  readonly gramWeight: string | null;
}

/**
 * AD-7: a fact without `catalogSource` and `datasetVersion` is not a fact.
 * Both are required, and serialization of a snapshot missing them fails —
 * provenance is what makes the number admissible (docs/04 §4.6).
 *
 * Nutrients are decimal strings, never numbers: float arithmetic produces
 * totals that do not match their parts.
 */
export interface NutritionFact {
  readonly catalogSource: CatalogSourceKind;
  readonly datasetVersion: string;
  readonly servingBasis: ServingBasis;
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  readonly fiberG: string | null;
  readonly sugarG: string | null;
  readonly sodiumMg: string | null;
}

export interface FoodReference {
  readonly foodRef: FoodRef;
  readonly displayName: string;
  readonly catalogSource: CatalogSourceKind;
  readonly datasetVersion: string;
}

export interface ServingOption {
  readonly unit: string;
  readonly amount: string;
  /** An option without a gram weight is not offered — "1 cup" alone is unusable. */
  readonly gramWeight: string;
}

export interface FoodLookupRequest {
  readonly foodRef: FoodRef;
  readonly catalogCountry: string;
}

export interface FoodLookupResult {
  readonly foodRef: FoodReference;
  readonly factsPer100g: NutritionFact;
  readonly servingOptions: readonly ServingOption[];
}

export type NutritionFailure =
  'not-found' | 'provider-unavailable' | 'timeout' | 'rate-limited' | 'unsupported-country';

/** `USDAProvider` and a future `OpenFoodFactsProvider` both implement this. */
export interface NutritionProvider {
  readonly id: CatalogSourceKind;
  readonly datasetVersion: string;
  lookup(request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>>;
}

export interface NutritionTotals {
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  readonly fiberG: string | null;
  /** True when any component was unknown. Absent is never summed as zero. */
  readonly partial: boolean;
}

/**
 * Runs only for server-originated lookups. Client-created meals are persisted
 * verbatim (docs/01 §1.5). Both sides are pinned to shared golden fixtures;
 * until that contract release exists this path stays disabled (docs/08 §8.4).
 */
export interface NutritionCalculator {
  forServing(fact: NutritionFact, basis: ServingBasis): Result<NutritionFact, NutritionFailure>;
  aggregate(facts: readonly NutritionFact[]): NutritionTotals;
}
