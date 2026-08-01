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

interface UsdaNutrient {
  readonly nutrient: { readonly id: number };
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

const NUTRIENT_MAP: ReadonlyMap<number, keyof Pick<
  NutritionFact,
  'calories' | 'proteinG' | 'fatG' | 'carbG' | 'fiberG' | 'sugarG' | 'sodiumMg'
>> = new Map([
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
): Pick<NutritionFact, 'calories' | 'proteinG' | 'fatG' | 'carbG' | 'fiberG' | 'sugarG' | 'sodiumMg'> | null => {
  const byId = new Map<number, number>();
  for (const n of raw) {
    if (n.amount !== undefined && n.amount !== null) byId.set(n.nutrient.id, n.amount);
  }

  const cal = byId.get(1008);
  if (cal === undefined) return null; // calories required

  return {
    calories: toDecimal(cal),
    proteinG: byId.has(1003) ? toDecimal(byId.get(1003)!) : null,
    fatG: byId.has(1004) ? toDecimal(byId.get(1004)!) : null,
    carbG: byId.has(1005) ? toDecimal(byId.get(1005)!) : null,
    fiberG: byId.has(1079) ? toDecimal(byId.get(1079)!) : null,
    sugarG: byId.has(2000) ? toDecimal(byId.get(2000)!) : null,
    sodiumMg: byId.has(1093) ? toDecimal(byId.get(1093)!) : null,
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

  constructor(
    apiKey: string,
    config: UsdaConfig,
    datasetVersion: string,
    fetchImpl?: FetchLike,
  ) {
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

    const url = `${this.#config.baseUrl}/food/${fdcId}?api_key=${this.#apiKey}`;
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

  async search(query: string, _country: string): Promise<Result<readonly CatalogMatch[], NutritionFailure>> {
    const blocked = this.#checkCircuit();
    if (blocked) return blocked;

    const params = new URLSearchParams({
      query,
      dataType: 'Foundation,SR Legacy',
      pageSize: '5',
      api_key: this.#apiKey,
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
          signal: AbortSignal.timeout(this.#config.totalTimeoutMs),
        });

        if (response.status === 429) return err('rate-limited');
        if (response.status >= 400 && response.status < 500) return err('not-found');
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
