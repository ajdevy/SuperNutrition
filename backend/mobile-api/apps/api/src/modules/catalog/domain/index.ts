import type { Result, FoodRef, CatalogSourceKind, Instant } from '@supernutrition/shared';

export interface CatalogMatch {
  readonly foodRef: FoodRef;
  readonly displayName: string;
  readonly source: CatalogSourceKind;
  readonly datasetVersion: string;
  readonly confidence: number;
}

export interface CatalogPackage {
  readonly packageId: string;
  readonly catalogCountry: string;
  readonly datasetVersion: string;
  readonly source: CatalogSourceKind;
  readonly checksum: string;
  readonly licenseId: string;
  readonly status: 'staged' | 'active' | 'rolled-back';
  readonly activatedAt: Instant | null;
  readonly rollbackTargetId: string | null;
}

export type CatalogFailure = 'not-found' | 'no-active-package' | 'storage-unavailable';

/**
 * Search returns matches, not verdicts. No fuzzy match is promoted to a
 * confirmed identity without explicit user confirmation — auto-selecting a food
 * identity is a forbidden default under AD-40 (docs/04 §4.7).
 */
export interface CatalogSearchPort {
  search(
    term: string,
    catalogCountry: string,
    limit: number,
  ): Promise<Result<readonly CatalogMatch[], CatalogFailure>>;
}

export interface CatalogItemPort {
  byId(foodRef: FoodRef): Promise<Result<CatalogMatch, CatalogFailure>>;
}

/** Activation is atomic; a failed verification leaves the previous package active. */
export interface CatalogPackagePort {
  activePackage(catalogCountry: string): Promise<Result<CatalogPackage, CatalogFailure>>;
}
