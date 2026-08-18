import { describe, it, expect, vi } from 'vitest';
import { ok, err, type FoodRef } from '@supernutrition/shared';
import { LookupFoodHandler, SearchFoodHandler } from './index.js';
import type {
  CatalogMatch,
  FoodLookupResult,
  NutritionFailure,
  NutritionProvider,
} from '../domain/index.js';

/**
 * The use cases are deliberately thin: they hand the request to the catalog
 * provider and hand the answer back. What is asserted here is that they add
 * nothing — no calculation, no re-ranking, no turning "USDA knows nothing about
 * that term" into an error. Server-side nutrition calculation stays disabled
 * until the golden-fixtures contract release exists (docs/08 §8.4).
 */

const ref = (value: string): FoodRef => value as FoodRef;

const facts = {
  catalogSource: 'usda',
  datasetVersion: 'SR Legacy 2021-10',
  servingBasis: { unit: 'g', amount: '100', gramWeight: '100' },
  calories: '89',
  proteinG: '1.09',
  fatG: '0.33',
  carbG: '22.84',
  fiberG: '2.6',
  sugarG: '12.23',
  sodiumMg: '1',
} as const;

const match: CatalogMatch = {
  foodRef: ref('usda:fdc:171705'),
  displayName: 'Bananas, raw',
  factsPer100g: facts,
  servingOptions: [{ unit: 'medium', amount: '1', gramWeight: '118' }],
};

const lookupResult: FoodLookupResult = {
  foodRef: {
    foodRef: ref('usda:fdc:171705'),
    displayName: 'Bananas, raw',
    catalogSource: 'usda',
    datasetVersion: 'SR Legacy 2021-10',
  },
  factsPer100g: facts,
  servingOptions: [{ unit: 'medium', amount: '1', gramWeight: '118' }],
};

const provider = (over: Partial<NutritionProvider> = {}): NutritionProvider => ({
  id: 'usda',
  datasetVersion: 'SR Legacy 2021-10',
  lookup: vi.fn(async () => ok(lookupResult)),
  search: vi.fn(async () => ok([match] as readonly CatalogMatch[])),
  ...over,
});

describe('LookupFoodHandler', () => {
  it('returns the catalog facts and serving options for an existing food', async () => {
    const catalog = provider();
    const result = await new LookupFoodHandler(catalog).execute({
      foodRef: ref('usda:fdc:171705'),
      catalogCountry: 'US',
    });

    expect(result).toEqual(ok(lookupResult));
    expect(catalog.lookup).toHaveBeenCalledWith({
      foodRef: 'usda:fdc:171705',
      catalogCountry: 'US',
    });
  });

  // The handler returns facts, not calories for a meal: the device owns the
  // serving math (AD-7 / AD-19).
  it('returns facts on a per-100-g basis without computing a serving', async () => {
    const result = await new LookupFoodHandler(provider()).execute({
      foodRef: ref('usda:fdc:171705'),
      catalogCountry: 'US',
    });

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.factsPer100g.servingBasis).toEqual({
      unit: 'g',
      amount: '100',
      gramWeight: '100',
    });
    expect(result.value.servingOptions).toEqual([
      { unit: 'medium', amount: '1', gramWeight: '118' },
    ]);
  });

  it('passes a provider failure through rather than masking it', async () => {
    const catalog = provider({
      lookup: vi.fn(async () => err('not-found' as NutritionFailure)),
    });

    expect(
      await new LookupFoodHandler(catalog).execute({
        foodRef: ref('usda:fdc:99999999'),
        catalogCountry: 'US',
      }),
    ).toEqual(err('not-found'));
  });
});

describe('SearchFoodHandler', () => {
  it('returns the matches the catalog found', async () => {
    const catalog = provider();
    const result = await new SearchFoodHandler(catalog).execute({ query: 'banana', country: 'US' });

    expect(result).toEqual(ok([match]));
    // M-02 — the limit is now forwarded, so the provider sees a third argument.
    // Undefined here because this caller did not ask for a size; the provider
    // owns the default.
    expect(catalog.search).toHaveBeenCalledWith('banana', 'US', undefined);
  });

  // Matches, not verdicts: auto-selecting a food identity is a forbidden
  // default under AD-40, so the handler never collapses a list to one answer.
  it('keeps every candidate rather than selecting one', async () => {
    const second: CatalogMatch = {
      ...match,
      foodRef: ref('usda:fdc:173944'),
      displayName: 'Banana chips',
    };
    const catalog = provider({
      search: vi.fn(async () => ok([match, second] as readonly CatalogMatch[])),
    });

    const result = await new SearchFoodHandler(catalog).execute({ query: 'banana', country: 'US' });

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value).toHaveLength(2);
  });

  // "Nothing matched" is an answer the client can act on, not a failure.
  it('returns an empty array as a valid result', async () => {
    const catalog = provider({
      search: vi.fn(async () => ok([] as readonly CatalogMatch[])),
    });

    expect(await new SearchFoodHandler(catalog).execute({ query: 'quux', country: 'US' })).toEqual(
      ok([]),
    );
  });

  it('passes a provider failure through rather than answering with an empty list', async () => {
    const catalog = provider({
      search: vi.fn(async () => err('provider-unavailable' as NutritionFailure)),
    });

    expect(
      await new SearchFoodHandler(catalog).execute({ query: 'banana', country: 'US' }),
    ).toEqual(err('provider-unavailable'));
  });
});

describe('M-02 — the validated limit reaches the provider', () => {
  /**
   * The route validates `limit` to 1..25 and passes it; the port declares it;
   * the provider bounds it. This handler sat in the middle, narrowed its own
   * parameter type to drop it, and silently gave every caller the provider's
   * default of 5. TypeScript's parameter bivariance let that compile against an
   * interface that does declare it.
   */
  const capturing = () => {
    const seen: (number | undefined)[] = [];
    return {
      seen,
      provider: {
        search: async (_q: string, _c: string, limit?: number) => {
          seen.push(limit);
          return ok([] as never);
        },
        lookup: async () => ok(null as never),
      },
    };
  };

  it.each([1, 5, 25])('forwards limit=%i unchanged', async (limit) => {
    const { seen, provider } = capturing();
    await new SearchFoodHandler(provider as never).execute({
      query: 'banana',
      country: 'DE',
      limit,
    });
    expect(seen).toEqual([limit]);
  });

  it('forwards undefined when the caller did not ask, leaving the default to the provider', async () => {
    const { seen, provider } = capturing();
    await new SearchFoodHandler(provider as never).execute({ query: 'banana', country: 'DE' });
    expect(seen).toEqual([undefined]);
  });
});
