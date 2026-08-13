import type { Result } from '@supernutrition/shared';
import type { CatalogMatch, CatalogFailure } from '../domain/index.js';

export interface SearchCatalog {
  execute(
    term: string,
    catalogCountry: string,
    limit: number,
  ): Promise<Result<readonly CatalogMatch[], CatalogFailure>>;
}
