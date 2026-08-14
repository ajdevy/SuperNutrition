import { ok, err, type Result, type FoodRef } from '@supernutrition/shared';
import type {
  NutritionProvider,
  FoodLookupRequest,
  FoodLookupResult,
  NutritionFailure,
  CatalogMatch,
  NutritionFact,
  ServingOption,
} from '../domain/index.js';

/**
 * Injectable fetch so tests never hit the network (same pattern as
 * OpenRouterInferenceProvider — see inference/infrastructure/openrouter).
 */
export type FetchLike = (input: string, init: RequestInit) => Promise<Response>;

export interface UsdaConfig {
  readonly baseUrl: string;
  readonly connectTimeoutMs: number;
  readonly totalTimeoutMs: number;
  readonly maxRetries: number;
  readonly circuitBreakerThreshold: number;
}

// ── USDA FoodData Central response shapes (internal) ───────────────────────

// The foods/search endpoint is flat (nutrientId/value); the food/{id}
// endpoint nests ({ nutrient: { id }, amount }). Snapshot of the live API:
// __fixtures__/usda-search.apple.json and __fixtures__/usda-food-174988.json.
interface UsdaNutrient {
  readonly nutrientId?: number;
  readonly value?: number;
  readonly nutrient?: { readonly id: number };
  readonly amount?: number;
}

interface UsdaPortion {
  readonly description?: string;
  readonly gramWeight?: number;
  readonly amount?: number;
  readonly measureUnit?: { readonly name?: string };
}

interface UsdaFoodDetail {
  readonly fdcId: number;
  readonly description: string;
  readonly foodNutrients: readonly UsdaNutrient[];
  readonly foodPortions?: readonly UsdaPortion[];
}

interface UsdaSearchResponse {
  readonly foods: readonly UsdaFoodDetail[];
}

// ── Nutrient-ID → NutritionFact field mapping ──────────────────────────────

type NutrientField = keyof Pick<
  NutritionFact,
  'calories' | 'proteinG' | 'fatG' | 'carbG' | 'fiberG' | 'sugarG' | 'sodiumMg'
>;

const NUTRIENT_MAP: ReadonlyMap<number, NutrientField> = new Map([
  [1008, 'calories'],
  [1003, 'proteinG'],
  [1004, 'fatG'],
  [1005, 'carbG'],
  [1079, 'fiberG'],
  [2000, 'sugarG'],
  [1093, 'sodiumMg'],
]);

// ── Helpers ────────────────────────────────────────────────────────────────

/** Decimal string with at most 3 fraction digits — never a float (§8.4). */
const toDecimal = (n: number): string => {
  const s = n.toFixed(3);
  // strip trailing zeros but keep at least one digit after the dot if it has decimals
  return s.replace(/\.?0+$/, '') || '0';
};

const buildFoodRef = (fdcId: number): FoodRef => `usda:fdc:${fdcId}` as FoodRef;

const parseFdcId = (ref: string): string | null => {
  const parts = ref.split(':');
  if (parts.length === 3 && parts[0] === 'usda' && parts[1] === 'fdc' && parts[2]) {
    return parts[2];
  }
  return null;
};

/**
 * Extract the nutrient fields from the USDA response.  Returns `null` when
 * calories (nutrient 1008) are missing — that is malformed data (§8.7).
 */
const extractNutrients = (
  raw: readonly UsdaNutrient[],
): Pick<NutritionFact, NutrientField> | null => {
  const found: Partial<Record<NutrientField, string>> = {};
  for (const n of raw) {
    const id = n.nutrientId ?? n.nutrient?.id;
    if (id === undefined) continue;
    const amount = n.value ?? n.amount;
    const field = NUTRIENT_MAP.get(id);
    // A nutrient USDA lists without an amount is absent, not zero (§8.7).
    if (field === undefined || amount === undefined || amount === null) continue;
    found[field] = toDecimal(amount);
  }

  if (found.calories === undefined) return null; // calories required

  return {
    calories: found.calories,
    proteinG: found.proteinG ?? null,
    fatG: found.fatG ?? null,
    carbG: found.carbG ?? null,
    fiberG: found.fiberG ?? null,
    sugarG: found.sugarG ?? null,
    sodiumMg: found.sodiumMg ?? null,
  };
};

/** Skip portions without gramWeight — "1 cup" alone is unusable (§8.7). */
const mapPortions = (raw: readonly UsdaPortion[] | undefined): readonly ServingOption[] => {
  if (!raw) return [];
  const out: ServingOption[] = [];
  for (const p of raw) {
    if (p.gramWeight === undefined || p.gramWeight === null || p.gramWeight <= 0) continue;
    out.push({
      unit: p.measureUnit?.name ?? p.description ?? 'serving',
      amount: String(p.amount ?? 1),
      gramWeight: toDecimal(p.gramWeight),
    });
  }
  return out;
};

const CIRCUIT_OPEN_MS = 30_000;
const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

/**
 * USDA FoodData Central provider — facts only, no server-side nutrition
 * calculation (docs/08 §8.4).
 *
 * API key is in a `#private` field and is never serialised (key isolation,
 * same pattern as OpenRouter).  Cache keys include `datasetVersion` so a
 * package upgrade invalidates by construction (§8.6).
 */
/**
 * The page size actually sent to USDA.
 *
 * Bounded here as well as at the route because this port has more than one
 * caller, and an unbounded page size is a request the provider may refuse or
 * bill for. 5 is the documented default when the caller expresses no preference.
 */
const DEFAULT_SEARCH_LIMIT = 5;
const MAX_SEARCH_LIMIT = 25;
const boundedLimit = (limit?: number): number => {
  if (limit === undefined || !Number.isInteger(limit)) return DEFAULT_SEARCH_LIMIT;
  return Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
};

export class USDAProvider implements NutritionProvider {
  readonly id = 'usda' as const;
  readonly datasetVersion: string;

  readonly #apiKey: string;
  readonly #config: UsdaConfig;
  readonly #fetch: FetchLike;

  // In-memory lookup cache (§8.6).  Key: `{datasetVersion}:{foodRef}`.
  readonly #cache = new Map<string, { result: FoodLookupResult; expiresAt: number }>();

  // Circuit breaker: opens after N consecutive 5xx/timeout errors.
  #consecutiveFailures = 0;
  #circuitOpenedAt: number | null = null;

  constructor(apiKey: string, config: UsdaConfig, datasetVersion: string, fetchImpl?: FetchLike) {
    this.#apiKey = apiKey;
    this.#config = config;
    this.datasetVersion = datasetVersion;
    this.#fetch = fetchImpl ?? ((input, init) => fetch(input, init));
  }

  // ── lookup ─────────────────────────────────────────────────────────────

  async lookup(request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>> {
    const blocked = this.#checkCircuit();
    if (blocked) return blocked;

    const fdcId = parseFdcId(request.foodRef as string);
    if (!fdcId) return err('not-found');

    // Cache hit?
    const cacheKey = `${this.datasetVersion}:${request.foodRef}`;
    const cached = this.#cache.get(cacheKey);
    if (cached && Date.now() < cached.expiresAt) return ok(cached.result);

    const url = `${this.#config.baseUrl}/food/${fdcId}`;
    return this.#fetchWithRetry<FoodLookupResult>(url, (data: UsdaFoodDetail) => {
      const nutrients = extractNutrients(data.foodNutrients);
      if (!nutrients) return null; // malformed

      const result: FoodLookupResult = {
        foodRef: {
          foodRef: buildFoodRef(data.fdcId),
          displayName: data.description,
          catalogSource: 'usda',
          datasetVersion: this.datasetVersion,
        },
        factsPer100g: {
          catalogSource: 'usda',
          datasetVersion: this.datasetVersion,
          servingBasis: { unit: 'g', amount: '100', gramWeight: '100' },
          ...nutrients,
        },
        servingOptions: mapPortions(data.foodPortions),
      };
      this.#cache.set(cacheKey, { result, expiresAt: Date.now() + CACHE_TTL_MS });
      return result;
    });
  }

  // ── search ─────────────────────────────────────────────────────────────

  async search(
    query: string,
    _country: string,
    limit?: number,
  ): Promise<Result<readonly CatalogMatch[], NutritionFailure>> {
    const blocked = this.#checkCircuit();
    if (blocked) return blocked;

    // The key travels in a header, not the query string. A query parameter is
    // recorded by every proxy, gateway access log and error report between here
    // and USDA, and a leaked key is not something a log rotation undoes.
    const params = new URLSearchParams({
      query,
      dataType: 'Foundation,SR Legacy',
      // M-02 — the route validates 1..25 and this was hardcoded to 5, so a
      // caller asking for 25 got 5 with no way to tell that apart from a
      // catalog that only held 5. Re-bounded here rather than trusted: this
      // port is reachable from more than one caller.
      pageSize: String(boundedLimit(limit)),
    });
    const url = `${this.#config.baseUrl}/foods/search?${params.toString()}`;

    return this.#fetchWithRetry<readonly CatalogMatch[]>(url, (data: UsdaSearchResponse) => {
      if (!data.foods || data.foods.length === 0) return [] as CatalogMatch[];

      const matches: CatalogMatch[] = [];
      for (const food of data.foods) {
        const nutrients = extractNutrients(food.foodNutrients);
        if (!nutrients) continue; // skip malformed, not an error

        matches.push({
          foodRef: buildFoodRef(food.fdcId),
          displayName: food.description,
          factsPer100g: {
            catalogSource: 'usda',
            datasetVersion: this.datasetVersion,
            servingBasis: { unit: 'g', amount: '100', gramWeight: '100' },
            ...nutrients,
          },
          servingOptions: mapPortions(food.foodPortions),
        });
      }
      return matches;
    });
  }

  // ── internals ──────────────────────────────────────────────────────────

  /**
   * Fetch with retry (on 5xx/timeout only) and circuit breaker recording.
   * The `mapBody` callback transforms the parsed JSON into the domain
   * result; it returns `null` if the data is malformed.
   */
  async #fetchWithRetry<T>(
    url: string,
    mapBody: (data: never) => T | null,
  ): Promise<Result<T, NutritionFailure>> {
    let lastFailure: NutritionFailure = 'provider-unavailable';

    for (let attempt = 0; attempt <= this.#config.maxRetries; attempt++) {
      if (attempt > 0) {
        const delay = Math.min(1000 * 2 ** (attempt - 1), 4000) + Math.random() * 200;
        await new Promise((r) => setTimeout(r, delay));
      }

      try {
        const response = await this.#fetch(url, {
          headers: { 'X-Api-Key': this.#apiKey },
          signal: AbortSignal.timeout(this.#config.totalTimeoutMs),
        });

        if (response.status === 429) return err('rate-limited');
        // 404 means the food is not there. Every other 4xx is about US — a wrong
        // or revoked key, a malformed request — and reporting those as
        // "not-found" told the user their search had no results while the
        // catalog was in fact unreachable. They then retype the same food and
        // get the same silence.
        if (response.status === 404) return err('not-found');
        if (response.status >= 400 && response.status < 500) return err('provider-unavailable');
        if (response.status >= 500) {
          this.#recordFailure();
          lastFailure = 'provider-unavailable';
          continue;
        }

        const body = await response.json();
        const mapped = mapBody(body as never);
        if (mapped === null) {
          // malformed data — missing required nutrients
          this.#recordFailure();
          return err('provider-unavailable');
        }

        this.#recordSuccess();
        return ok(mapped);
      } catch (error: unknown) {
        this.#recordFailure();
        if (error instanceof Error && error.name === 'TimeoutError') {
          lastFailure = 'timeout';
          continue; // retry on timeout
        }
        lastFailure = 'provider-unavailable';
        continue; // retry on network error
      }
    }

    return err(lastFailure);
  }

  #checkCircuit(): Result<never, NutritionFailure> | null {
    if (this.#circuitOpenedAt === null) return null;
    if (Date.now() - this.#circuitOpenedAt < CIRCUIT_OPEN_MS) {
      return err('provider-unavailable');
    }
    // Half-open: allow a probe.
    this.#circuitOpenedAt = null;
    return null;
  }

  #recordFailure(): void {
    this.#consecutiveFailures++;
    if (this.#consecutiveFailures >= this.#config.circuitBreakerThreshold) {
      this.#circuitOpenedAt = Date.now();
    }
  }

  #recordSuccess(): void {
    this.#consecutiveFailures = 0;
    this.#circuitOpenedAt = null;
  }
}
