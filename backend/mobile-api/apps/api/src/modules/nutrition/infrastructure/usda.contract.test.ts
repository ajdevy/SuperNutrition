import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import type { FoodRef } from '@supernutrition/shared';
import { USDAProvider, type FetchLike, type UsdaConfig } from './usda.provider.js';

/**
 * Contract tests against snapshots of the LIVE USDA FoodData Central API
 * (captured 2026-08-02, dataset "SR Legacy 2021-10"):
 *   - usda-search.apple.json  : GET /fdc/v1/foods/search?query=apple&dataType=Foundation,SR Legacy&pageSize=2
 *   - usda-food-174988.json   : GET /fdc/v1/food/174988
 * The search endpoint is flat ({ nutrientId, value }), the detail endpoint
 * nests ({ nutrient: { id }, amount }) — both shapes must keep parsing.
 * Hard-coded values below are the recorded reality; a change in either the
 * fixture capture or the parser will fail loudly here.
 */

const DATASET = 'SR Legacy 2021-10';
const KEY = 'usda-secret-key';

const SEARCH_FIXTURE: unknown = JSON.parse(
  readFileSync(new URL('./__fixtures__/usda-search.apple.json', import.meta.url), 'utf8'),
);
const DETAIL_FIXTURE: unknown = JSON.parse(
  readFileSync(new URL('./__fixtures__/usda-food-174988.json', import.meta.url), 'utf8'),
);

const providerOf = (body: unknown): USDAProvider => {
  const fetchImpl: FetchLike = async () =>
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    });
  const cfg: UsdaConfig = {
    baseUrl: 'https://example.invalid/fdc/v1',
    connectTimeoutMs: 3000,
    totalTimeoutMs: 5000,
    maxRetries: 0,
    circuitBreakerThreshold: 5,
  };
  return new USDAProvider(KEY, cfg, DATASET, fetchImpl);
};

const ref = (value: string): FoodRef => value as FoodRef;

describe('USDA provider contract (live-API snapshots)', () => {
  it('parses the real /foods/search response shape', async () => {
    const result = await providerOf(SEARCH_FIXTURE).search('apple', 'US');

    expect(result.ok).toBe(true);
    if (!result.ok) return;

    expect(result.value).toHaveLength(2);

    const [croissants, strudel] = result.value;
    expect(croissants?.displayName).toBe('Croissants, apple');
    expect(croissants?.factsPer100g).toMatchObject({
      calories: '254',
      proteinG: '7.4',
      fatG: '8.7',
      carbG: '37.1',
      fiberG: '2.5',
      sodiumMg: '274',
    });
    expect(croissants?.servingOptions).toEqual([]); // search has no portions

    expect(strudel?.displayName).toBe('Strudel, apple');
    expect(strudel?.factsPer100g).toMatchObject({ calories: '274', sugarG: '25.8' });
  });

  it('parses the real /food/{id} response shape (nested nutrients + portions)', async () => {
    const result = await providerOf(DETAIL_FIXTURE).lookup({
      foodRef: ref('usda:fdc:174988'),
      catalogCountry: 'US',
    });

    expect(result.ok).toBe(true);
    if (!result.ok) return;

    expect(result.value.foodRef.displayName).toBe('Croissants, apple');
    expect(result.value.factsPer100g).toMatchObject({
      calories: '254',
      proteinG: '7.4',
      fatG: '8.7',
      carbG: '37.1',
      fiberG: '2.5',
      sodiumMg: '274',
    });
    expect(result.value.servingOptions).toEqual([
      { unit: 'undetermined', amount: '1', gramWeight: '57' },
      { unit: 'undetermined', amount: '1', gramWeight: '28.35' },
    ]);
  });
});
