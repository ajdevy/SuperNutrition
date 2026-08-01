import { describe, it, expect, afterEach, vi } from 'vitest';
import type { FoodRef } from '@supernutrition/shared';
import { USDAProvider, type FetchLike, type UsdaConfig } from './usda.provider.js';

/**
 * The provider is exercised through an injected transport — the same pattern
 * the OpenRouter adapter uses — so no test ever reaches the network. What is
 * asserted is the contract the domain depends on: facts with provenance,
 * decimal strings rather than floats (docs/08 §8.4), absent nutrients that stay
 * absent (§8.7), and a failure taxonomy a caller can branch on.
 */

const KEY = 'usda-secret-key';
const DATASET = 'SR Legacy 2021-10';

const config = (over: Partial<UsdaConfig> = {}): UsdaConfig => ({
  baseUrl: 'https://example.invalid/fdc/v1',
  connectTimeoutMs: 3000,
  totalTimeoutMs: 5000,
  // Retries are opt-in per test: an unnecessary backoff is a second of wall
  // clock in every unrelated assertion.
  maxRetries: 0,
  circuitBreakerThreshold: 5,
  ...over,
});

const ref = (value: string): FoodRef => value as FoodRef;

const jsonResponse = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

const reply =
  (body: unknown, status = 200) =>
  (): Response =>
    jsonResponse(body, status);

const httpStatus = (code: number) => (): Response => jsonResponse({ error: 'usda' }, code);

const networkError = (): Response => {
  throw new TypeError('fetch failed');
};

const timeout = (): Response => {
  const error = new Error('The operation was aborted due to timeout');
  error.name = 'TimeoutError';
  throw error;
};

/** Scripted transport: call N consumes reply N; the last reply repeats. */
const transport = (...replies: ReadonlyArray<() => Response>) => {
  const urls: string[] = [];
  const fetchImpl: FetchLike = async (url) => {
    urls.push(url);
    return replies[Math.min(urls.length - 1, replies.length - 1)]!();
  };
  return { urls, fetch: fetchImpl };
};

const build = (fetchImpl: FetchLike, over: Partial<UsdaConfig> = {}) =>
  new USDAProvider(KEY, config(over), DATASET, fetchImpl);

const nutrient = (id: number, amount: number) => ({ nutrient: { id }, amount });

const BANANA = {
  fdcId: 171705,
  description: 'Bananas, raw',
  foodNutrients: [
    nutrient(1008, 89),
    nutrient(1003, 1.09),
    nutrient(1004, 0.33),
    nutrient(1005, 22.84),
    nutrient(1079, 2.6),
    nutrient(2000, 12.23),
    nutrient(1093, 1),
  ],
  foodPortions: [
    { amount: 1, gramWeight: 118, measureUnit: { name: 'medium' } },
    { amount: 1, gramWeight: 150, measureUnit: { name: 'cup, sliced' } },
  ],
};

const lookupBanana = (provider: USDAProvider) =>
  provider.lookup({ foodRef: ref('usda:fdc:171705'), catalogCountry: 'US' });

afterEach(() => {
  vi.useRealTimers();
});

describe('provider identity and key isolation', () => {
  const provider = build(transport(reply(BANANA)).fetch);

  it('identifies its catalog source', () => {
    expect(provider.id).toBe('usda');
  });

  // Cache keys include the dataset version, so a package upgrade invalidates by
  // construction (docs/08 §8.6).
  it('reports the dataset version it is pinned to', () => {
    expect(provider.datasetVersion).toBe(DATASET);
  });

  it('does not expose the API key on the instance', () => {
    expect(JSON.stringify(provider)).not.toContain(KEY);
    expect(Object.keys(provider)).not.toContain('apiKey');
    expect(Object.values(provider)).not.toContain(KEY);
  });

  it('sends the key to USDA without letting it into a result', async () => {
    const http = transport(reply(BANANA));
    const withKey = build(http.fetch);

    const result = await lookupBanana(withKey);

    expect(http.urls[0]).toContain(`api_key=${KEY}`);
    expect(JSON.stringify(result)).not.toContain(KEY);
  });
});

describe('lookup', () => {
  it('returns catalog facts for a known fdcId', async () => {
    const http = transport(reply(BANANA));
    const result = await lookupBanana(build(http.fetch));

    expect(http.urls).toEqual(['https://example.invalid/fdc/v1/food/171705?api_key=' + KEY]);
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.foodRef).toEqual({
      foodRef: 'usda:fdc:171705',
      displayName: 'Bananas, raw',
      catalogSource: 'usda',
      datasetVersion: DATASET,
    });
  });

  // AD-7: a fact without source and dataset version is not a fact.
  it('stamps every fact with its source and dataset version', async () => {
    const result = await lookupBanana(build(transport(reply(BANANA)).fetch));

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.factsPer100g.catalogSource).toBe('usda');
    expect(result.value.factsPer100g.datasetVersion).toBe(DATASET);
  });

  // The fact is per 100 g; the marker basis says so rather than leaving the
  // client to assume it.
  it('reports facts on a per-100-g marker basis', async () => {
    const result = await lookupBanana(build(transport(reply(BANANA)).fetch));

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.factsPer100g.servingBasis).toEqual({
      unit: 'g',
      amount: '100',
      gramWeight: '100',
    });
  });

  it('answers not-found for an unknown fdcId without inventing a food', async () => {
    const http = transport(httpStatus(404));
    const result = await build(http.fetch).lookup({
      foodRef: ref('usda:fdc:99999999'),
      catalogCountry: 'US',
    });

    expect(result).toEqual({ ok: false, error: 'not-found' });
  });

  // A reference the provider does not own is answered locally: asking USDA
  // about an Open Food Facts barcode cannot succeed.
  it.each([
    ['a foreign namespace', 'off:barcode:5000112637922'],
    ['a truncated reference', 'usda:fdc:'],
    ['a reference that is not namespaced', '171705'],
  ])('rejects %s without calling USDA', async (_case, value) => {
    const http = transport(reply(BANANA));
    const result = await build(http.fetch).lookup({
      foodRef: ref(value),
      catalogCountry: 'US',
    });

    expect(result).toEqual({ ok: false, error: 'not-found' });
    expect(http.urls).toEqual([]);
  });
});

describe('nutrient parsing', () => {
  const factsOf = async (foodNutrients: unknown[]) => {
    const result = await lookupBanana(build(transport(reply({ ...BANANA, foodNutrients })).fetch));
    return result.ok ? result.value.factsPer100g : null;
  };

  it('maps every documented nutrient id to its field', async () => {
    const facts = await factsOf(BANANA.foodNutrients);

    expect(facts).toMatchObject({
      calories: '89',
      proteinG: '1.09',
      fatG: '0.33',
      carbG: '22.84',
      fiberG: '2.6',
      sugarG: '12.23',
      sodiumMg: '1',
    });
  });

  // §8.4: float arithmetic produces totals that do not match their parts, so
  // nothing crosses the boundary as a number.
  it('emits decimal strings with at most three fraction digits, never floats', async () => {
    const facts = await factsOf([nutrient(1008, 88.5), nutrient(1003, 1.23456)]);

    expect(facts?.calories).toBe('88.5');
    expect(facts?.proteinG).toBe('1.235');
    for (const value of Object.values(facts ?? {})) {
      expect(typeof value).not.toBe('number');
    }
  });

  // §8.7: absent is not zero. A missing fibre value must not be summed as 0 g.
  it('maps a nutrient USDA does not report to null rather than zero', async () => {
    const facts = await factsOf([nutrient(1008, 89), nutrient(1003, 1.09)]);

    expect(facts).toMatchObject({ calories: '89', proteinG: '1.09' });
    expect(facts?.fiberG).toBeNull();
    expect(facts?.sugarG).toBeNull();
    expect(facts?.sodiumMg).toBeNull();
    expect(facts?.fatG).toBeNull();
    expect(facts?.carbG).toBeNull();
  });

  it('treats a nutrient entry without an amount as absent', async () => {
    const facts = await factsOf([nutrient(1008, 89), { nutrient: { id: 1003 } }]);

    expect(facts?.proteinG).toBeNull();
  });

  it('keeps a reported zero as a measured zero', async () => {
    const facts = await factsOf([nutrient(1008, 89), nutrient(1004, 0)]);

    expect(facts?.fatG).toBe('0');
  });

  // Energy is the one field the client cannot compute around; a food without it
  // is malformed data, not a food with unknown calories.
  it('treats a food without energy as malformed provider data', async () => {
    const result = await lookupBanana(
      build(transport(reply({ ...BANANA, foodNutrients: [nutrient(1003, 1.09)] })).fetch),
    );

    expect(result).toEqual({ ok: false, error: 'provider-unavailable' });
  });
});

describe('serving options', () => {
  const optionsOf = async (foodPortions: unknown) => {
    const result = await lookupBanana(build(transport(reply({ ...BANANA, foodPortions })).fetch));
    return result.ok ? result.value.servingOptions : null;
  };

  it('maps portions to serving options with gram weights', async () => {
    expect(await optionsOf(BANANA.foodPortions)).toEqual([
      { unit: 'medium', amount: '1', gramWeight: '118' },
      { unit: 'cup, sliced', amount: '1', gramWeight: '150' },
    ]);
  });

  // "1 cup" without a gram weight is unusable: the device cannot turn it into
  // grams, so offering it would be offering a serving nobody can calculate.
  it.each([
    ['no gram weight at all', { amount: 1, measureUnit: { name: 'cup' } }],
    ['a zero gram weight', { amount: 1, gramWeight: 0, measureUnit: { name: 'pinch' } }],
  ])('skips a portion with %s', async (_case, portion) => {
    expect(await optionsOf([portion, BANANA.foodPortions[0]])).toEqual([
      { unit: 'medium', amount: '1', gramWeight: '118' },
    ]);
  });

  it('falls back to the portion description when USDA names no measure unit', async () => {
    expect(await optionsOf([{ amount: 2, gramWeight: 30, description: 'slice' }])).toEqual([
      { unit: 'slice', amount: '2', gramWeight: '30' },
    ]);
  });

  it('offers no servings for a food USDA lists no portions for', async () => {
    expect(await optionsOf(undefined)).toEqual([]);
  });
});

describe('search', () => {
  const searchResponse = { foods: [BANANA] };

  it('asks USDA for the documented datasets and page size', async () => {
    const http = transport(reply(searchResponse));
    await build(http.fetch).search('banana', 'US');

    const url = new URL(http.urls[0]!);
    expect(url.pathname).toBe('/fdc/v1/foods/search');
    expect(url.searchParams.get('query')).toBe('banana');
    expect(url.searchParams.get('dataType')).toBe('Foundation,SR Legacy');
    expect(url.searchParams.get('pageSize')).toBe('5');
  });

  it('returns matches carrying provenance and serving options', async () => {
    const result = await build(transport(reply(searchResponse)).fetch).search('banana', 'US');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value).toEqual([
      {
        foodRef: 'usda:fdc:171705',
        displayName: 'Bananas, raw',
        factsPer100g: {
          catalogSource: 'usda',
          datasetVersion: DATASET,
          servingBasis: { unit: 'g', amount: '100', gramWeight: '100' },
          calories: '89',
          proteinG: '1.09',
          fatG: '0.33',
          carbG: '22.84',
          fiberG: '2.6',
          sugarG: '12.23',
          sodiumMg: '1',
        },
        servingOptions: [
          { unit: 'medium', amount: '1', gramWeight: '118' },
          { unit: 'cup, sliced', amount: '1', gramWeight: '150' },
        ],
      },
    ]);
  });

  // "USDA knows nothing about that term" is an answer, not a failure — the
  // route turns it into an empty `data` array.
  it('treats an empty result set as a valid empty answer', async () => {
    const result = await build(transport(reply({ foods: [] })).fetch).search('quux', 'US');

    expect(result).toEqual({ ok: true, value: [] });
  });

  it('treats a response without a foods array as an empty answer', async () => {
    const result = await build(transport(reply({})).fetch).search('quux', 'US');

    expect(result).toEqual({ ok: true, value: [] });
  });

  // One malformed row must not delete the rows around it — the user still gets
  // the candidates USDA described completely.
  it('drops a malformed candidate and keeps the rest', async () => {
    const malformed = { fdcId: 2, description: 'Mystery', foodNutrients: [nutrient(1003, 1)] };
    const result = await build(transport(reply({ foods: [malformed, BANANA] })).fetch).search(
      'banana',
      'US',
    );

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.map((match) => match.foodRef)).toEqual(['usda:fdc:171705']);
  });

  it('maps a USDA failure rather than returning an empty result', async () => {
    const result = await build(transport(httpStatus(500)).fetch).search('banana', 'US');

    expect(result).toEqual({ ok: false, error: 'provider-unavailable' });
  });
});

describe('error mapping', () => {
  it.each([
    ['400 bad request', httpStatus(400), 'not-found'],
    ['403 an invalid key', httpStatus(403), 'not-found'],
    ['404 no such food', httpStatus(404), 'not-found'],
    ['429 over quota', httpStatus(429), 'rate-limited'],
    ['500 upstream fault', httpStatus(500), 'provider-unavailable'],
    ['503 upstream outage', httpStatus(503), 'provider-unavailable'],
    ['a network error', networkError, 'provider-unavailable'],
    ['a timeout', timeout, 'timeout'],
  ])('maps %s to %s', async (_case, replyWith, expected) => {
    const result = await lookupBanana(build(transport(replyWith).fetch));

    expect(result).toEqual({ ok: false, error: expected });
  });
});

describe('retry', () => {
  it('retries a 5xx and returns the answer the retry got', async () => {
    const http = transport(httpStatus(500), reply(BANANA));
    const result = await lookupBanana(build(http.fetch, { maxRetries: 1 }));

    expect(result.ok).toBe(true);
    expect(http.urls).toHaveLength(2);
  });

  it('retries a timeout', async () => {
    const http = transport(timeout, reply(BANANA));
    const result = await lookupBanana(build(http.fetch, { maxRetries: 1 }));

    expect(result.ok).toBe(true);
    expect(http.urls).toHaveLength(2);
  });

  it('gives up with the last failure once the retries are spent', async () => {
    const http = transport(httpStatus(503));
    const result = await lookupBanana(build(http.fetch, { maxRetries: 1 }));

    expect(result).toEqual({ ok: false, error: 'provider-unavailable' });
    expect(http.urls).toHaveLength(2);
  });

  // A 4xx and a 429 are answers, not faults: repeating them burns the quota
  // and cannot change the outcome.
  it.each([
    ['a 4xx', httpStatus(404)],
    ['a rate limit', httpStatus(429)],
  ])('does not retry %s', async (_case, replyWith) => {
    const http = transport(replyWith);
    await lookupBanana(build(http.fetch, { maxRetries: 2 }));

    expect(http.urls).toHaveLength(1);
  });

  it('does not retry malformed data', async () => {
    const http = transport(reply({ ...BANANA, foodNutrients: [] }));
    const result = await lookupBanana(build(http.fetch, { maxRetries: 2 }));

    expect(result).toEqual({ ok: false, error: 'provider-unavailable' });
    expect(http.urls).toHaveLength(1);
  });
});

describe('circuit breaker', () => {
  it('stops calling USDA after consecutive failures', async () => {
    const http = transport(httpStatus(500));
    const provider = build(http.fetch, { circuitBreakerThreshold: 2 });

    await lookupBanana(provider);
    await lookupBanana(provider);
    const blocked = await lookupBanana(provider);

    expect(blocked).toEqual({ ok: false, error: 'provider-unavailable' });
    expect(http.urls).toHaveLength(2);
  });

  it('blocks search while the circuit is open', async () => {
    const http = transport(httpStatus(500));
    const provider = build(http.fetch, { circuitBreakerThreshold: 1 });

    await provider.search('banana', 'US');
    const blocked = await provider.search('banana', 'US');

    expect(blocked).toEqual({ ok: false, error: 'provider-unavailable' });
    expect(http.urls).toHaveLength(1);
  });

  it('probes again once the open window has passed', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-08-01T10:00:00Z'));

    const http = transport(httpStatus(500), reply(BANANA));
    const provider = build(http.fetch, { circuitBreakerThreshold: 1 });
    await lookupBanana(provider);
    expect((await lookupBanana(provider)).ok).toBe(false);

    vi.setSystemTime(new Date('2026-08-01T10:00:31Z'));
    const probe = await lookupBanana(provider);

    expect(probe.ok).toBe(true);
    expect(http.urls).toHaveLength(2);
  });

  it('forgets earlier failures once a call succeeds', async () => {
    const http = transport(httpStatus(500), reply(BANANA), httpStatus(500));
    const provider = build(http.fetch, { circuitBreakerThreshold: 2 });

    await lookupBanana(provider);
    await provider.lookup({ foodRef: ref('usda:fdc:2'), catalogCountry: 'US' });
    await provider.lookup({ foodRef: ref('usda:fdc:3'), catalogCountry: 'US' });
    const afterOneFailure = await provider.lookup({
      foodRef: ref('usda:fdc:4'),
      catalogCountry: 'US',
    });

    // The success reset the counter, so the single later failure did not open
    // the circuit and the fourth call still reached USDA.
    expect(afterOneFailure).toEqual({ ok: false, error: 'provider-unavailable' });
    expect(http.urls).toHaveLength(4);
  });
});

describe('lookup cache', () => {
  it('serves a repeated lookup without calling USDA again', async () => {
    const http = transport(reply(BANANA));
    const provider = build(http.fetch);

    const first = await lookupBanana(provider);
    const second = await lookupBanana(provider);

    expect(second).toEqual(first);
    expect(http.urls).toHaveLength(1);
  });

  it('caches per food, not globally', async () => {
    const http = transport(reply(BANANA));
    const provider = build(http.fetch);

    await lookupBanana(provider);
    await provider.lookup({ foodRef: ref('usda:fdc:169910'), catalogCountry: 'US' });

    expect(http.urls).toHaveLength(2);
  });

  it('does not cache a failure', async () => {
    const http = transport(httpStatus(500), reply(BANANA));
    const provider = build(http.fetch);

    expect((await lookupBanana(provider)).ok).toBe(false);
    expect((await lookupBanana(provider)).ok).toBe(true);
    expect(http.urls).toHaveLength(2);
  });

  // §8.6: a cached fact is a fact from a dataset, and it expires.
  it('refetches once the entry has outlived its TTL', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-08-01T10:00:00Z'));

    const http = transport(reply(BANANA));
    const provider = build(http.fetch);
    await lookupBanana(provider);

    vi.setSystemTime(new Date('2026-08-09T10:00:00Z'));
    await lookupBanana(provider);

    expect(http.urls).toHaveLength(2);
  });
});
