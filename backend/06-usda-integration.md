# Stage 6: USDA Nutrition Provider

> Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
> USDA design: `backend/mobile-api/docs/08-usda-integration.md`
> REST API spec: `backend/mobile-api/docs/06-rest-api-specification.md`
> Database: `backend/mobile-api/packages/database/src/schema/`
>
> Previous stage: `backend/05-meals-product-api.md`
> Stage type: USDA provider + catalog search (facts only, no calculation)

## Role

You are a Senior Backend Engineer implementing the USDA FoodData Central provider.

The gateway (E4.3) is live. The meals domain (Stage 5) is implemented — meal events are created with client-supplied, client-grounded nutrition.

Your task: implement the USDA provider so the backend can return **catalog facts and serving options** — never calories computed by the server.

## Architecture constraint (do not deviate)

- **AD-7 / AD-19:** the AI never generates nutrition values; confirmation selects a canonical food; only the versioned **on-device** nutrition catalog plus deterministic serving math produces calories and nutrients.
- **docs/08 §8.4:** until the shared golden-fixtures contract release exists (G-2), **the server-side calculation path stays disabled**. The lookup endpoint returns catalog facts and serving options; **the client calculates**.
- **ARCHITECTURE-SPINE:** serving confirmation is the explicit boundary before nutrition calculation.
- The backend's job in this stage: implement role 2 (server-side catalog lookup) as **facts only** — `factsPer100g`, `servingOptions`, namespace-qualified food IDs, source + dataset version on every result.

This stage does NOT:
- Calculate nutrition for a meal.
- Auto-fill foodRef, serving, or nutrition on meal creation.
- Change any meal request schema.
- Implement or wire the `NutritionCalculator` (its server-side path stays disabled per §8.4).
- Record a deviation in docs/13 — there is none.

## Context

SuperNutrition users take photos of food. The gateway returns candidates with labels ("Banana, raw"). The user confirms a candidate, confirms the serving, and the **device** computes nutrition from the on-device catalog. When no local package covers a term, the backend's catalog lookup returns facts and serving options for the client to calculate from.

The `NutritionProvider` port and `USDAProvider` stub already exist. The `nutrition_snapshots` and `catalog_items` schemas are migrated. `USDA_API_KEY` is already in the env schema (optional).

The `search` method is missing from the `NutritionProvider` port even though docs/08 §8.2 declares it — add it. `NutritionCalculator` exists as a port and stays unimplemented (disabled path).

## What to implement

### 1. USDAProvider — real HTTP implementation

File: `apps/api/src/modules/nutrition/infrastructure/usda.provider.ts`

Replace the stub with a real HTTP client.

**`lookup(request)`** — look up by existing fdcId:

- Parse `foodRef` ("usda:fdc:171705") → extract fdcId
- GET `{baseUrl}/food/{fdcId}?api_key={apiKey}`
- Map USDA nutrients to `NutritionFact` fields using nutrientId mapping:
  - 1008 (Energy) → `calories`
  - 1003 (Protein) → `proteinG`
  - 1004 (Total lipid) → `fatG`
  - 1005 (Carbohydrate) → `carbG`
  - 1079 (Fiber) → `fiberG`
  - 2000 (Sugars) → `sugarG`
  - 1093 (Sodium) → `sodiumMg`
- All nutrient values are **decimal strings** with at most 3 fraction digits — never floats, never JavaScript numbers (docs/08 §8.4 precision discipline). A nutrient missing from the USDA response maps to `null` (absent is not zero, §8.7).
- `factsPer100g.servingBasis` is a marker basis `{ unit: 'g', amount: '100', gramWeight: '100' }` — the fact is per 100 g.
- Map `foodPortions` to `ServingOption[]` — skip portions without `gramWeight`; `gramWeight` is a decimal string (docs/08 §8.3).
- Return `FoodLookupResult`

**`search(query, country)`** — NEW method required for label → fdcId resolution:

- GET `{baseUrl}/foods/search?query={query}&dataType=Foundation,SR%20Legacy&pageSize=5&api_key={apiKey}`
- Map each result to a `CatalogMatch`
- An empty `foods` array is a **valid empty result**, not an error (the REST catalog route returns an empty `data` array)

**Add `search` to the `NutritionProvider` port** in `domain/index.ts` (mirrors docs/08 §8.2):

```typescript
export interface NutritionProvider {
  readonly id: CatalogSourceKind;
  readonly datasetVersion: string;
  lookup(request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>>;
  search(query: string, country: string): Promise<Result<readonly CatalogMatch[], NutritionFailure>>;
}
```

**`CatalogMatch`** type (add to `domain/index.ts`):

```typescript
export interface CatalogMatch {
  readonly foodRef: FoodRef;
  readonly displayName: string;
  readonly factsPer100g: NutritionFact;
  readonly servingOptions: readonly ServingOption[];
}
```

**Key isolation** (same pattern as OpenRouter):
- API key in `#apiKey` private field
- Not on the instance, not in config objects, not serialized by `JSON.stringify`

**Error handling:**
- HTTP 4xx from USDA → `not-found` (missing or unknown fdcId)
- HTTP 429 → `rate-limited`
- HTTP 5xx or network error → `provider-unavailable`
- Timeout (configurable `totalTimeoutMs`) → `timeout`
- Missing required nutrients (no calories) → `provider-unavailable` (malformed data)
- Circuit breaker: after 5 consecutive 5xx/timeout errors within 60s, return `provider-unavailable` for 30s without calling USDA

**Caching:**
- Lookup results in-memory `Map<string, {result, expiresAt}>` (Redis not required for MVP)
- Key: `{datasetVersion}:{foodRef}`, TTL 7 days (docs/08 §8.6)

### 2. Nutrition use cases — real implementations

File: `apps/api/src/modules/nutrition/application/index.ts`

Implement `LookupFoodHandler` (implements the existing `LookupFood` port):

- Accept `FoodLookupRequest`
- Call `provider.lookup()`
- Return `FoodLookupResult` (facts + serving options; the client calculates — §8.4)

Add `SearchFoodHandler` (new port):

- Accept `{ query: string; country: string; limit?: number }`
- Call `provider.search()`
- Return `readonly CatalogMatch[]`; an empty array is a valid result

**Do NOT implement `NutritionCalculator`.** The `forServing`/`aggregate` port stays as-is (interface only). The server-side calculation path is disabled until the golden-fixtures contract release exists (docs/08 §8.4).

### 3. Catalog search route — real implementation

File: `apps/api/src/routes/product/index.ts` (was 501 in Stage 5)

Implement `GET /api/v1/catalog/search?q=banana&country=US`:

- Parse query params: `q` (required, min 1 char), `country` (required, ISO 3166-1 alpha-2), `limit` (optional, default 5, max 25)
- Call `provider.search()`
- Return `{ data: readonly CatalogMatch[], query, country }`
- No matches is not an error — return empty `data` array
- Returns **matches, not verdicts** — never a single auto-selected food (docs/06 §6.6; auto-selecting food identity is a forbidden default under AD-40)

### 4. Wire in Composition Root — `apps/api/src/main.ts`

- Read `USDA_API_KEY` from env (like OpenRouter key — isolated, not in config)
- Instantiate `USDAProvider` with key, config, dataset version
- Pass `NutritionProvider` to `LookupFoodHandler`, `SearchFoodHandler`, and the catalog route

### 5. Test expectations

Existing tests must remain green. Add:

- **Provider tests**: lookup by valid fdcId (fake HTTP → use MockAgent/undici), search by query returns array, empty result is valid, key isolation, error mapping for each HTTP status, timeout, nutrient parsing (decimal strings, missing → null), serving options mapping, portions without gram weight are skipped
- **Use case tests**: lookup existing food, search returns matches, search returns empty array
- **HTTP tests**: catalog search returns matches with provenance, empty `data` on no match, validation errors for missing `q`/`country`
- **Negative test**: assert the create-meal flow is unchanged — nutrition is still required and persisted verbatim; no USDA call happens on meal creation (prove no calculation path exists)

## Constraints

- Do NOT calculate nutrition server-side — `NutritionCalculator` stays unimplemented, `nutrition` stays required on meal requests, meal schemas are untouched
- Do NOT auto-fill foodRef, serving, or nutrition on meal creation — the device owns calculation and serving confirmation (AD-19)
- Do NOT implement the catalog package curation pipeline (docs/08 §8.5) — separate post-MVP story
- Do NOT implement Redis caching — in-memory cache with TTL is sufficient for MVP
- Do NOT implement Open Food Facts — the port is ready for it, but this stage is USDA only
- Do NOT change existing database schemas
- Do NOT modify the gateway surface (`/gw/v1`) — this is product API only
- All nutrient values are decimal strings with ≤ 3 fraction digits, never numbers
- `npm test` must pass after implementation

## Files to modify

```
apps/api/src/modules/nutrition/domain/index.ts                        — add search to NutritionProvider, add CatalogMatch
apps/api/src/modules/nutrition/infrastructure/usda.provider.ts         — real HTTP implementation
apps/api/src/modules/nutrition/infrastructure/usda.provider.test.ts    — rewrite tests for real impl
apps/api/src/modules/nutrition/infrastructure/index.ts                 — update exports
apps/api/src/modules/nutrition/application/index.ts                   — LookupFoodHandler, SearchFoodHandler (+ port)
apps/api/src/modules/nutrition/index.ts                               — update exports

apps/api/src/routes/product/index.ts                                  — implement GET /api/v1/catalog/search
apps/api/src/main.ts                                                  — wire USDA provider and handlers
```
