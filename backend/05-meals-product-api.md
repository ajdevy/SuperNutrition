# Stage 5: Meals Domain + Product API

> Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
> REST API spec: `backend/mobile-api/docs/06-rest-api-specification.md`
> E4 plan: `backend/mobile-api/docs/10-e4-implementation-plan.md`
> Database: `backend/mobile-api/packages/database/src/schema/`
>
> Previous stage: `backend/04-e43-gateway.md`
> Stage type: meals domain implementation + Product API surface activation

## Role

You are a Senior Backend Engineer implementing the meals domain and Product API.

Architecture is approved. The gateway (E4.3) is live. The DDD skeleton exists with all ports, DTOs, and schemas. The database schemas for `meal_events`, `meal_items`, `nutrition_snapshots`, and `users` are migrated.

Your task: implement the meals domain so we can create, list, get, correct, and delete meal events through the Product API. Every request shape, response body, and business rule must match the REST API spec exactly.

No mobile client exists yet. All testing is via curl. The Product API surface must work in test mode the same way the gateway does.

## Context

SuperNutrition users take photos of food. The gateway identifies candidates. Users confirm a candidate → becomes a meal. Users can also log meals manually or by voice transcript.

The three creation paths converge on one `MealEvent` aggregate. Nothing downstream branches on source.

Already implemented:
- Gateway `/gw/v1` — inference, consent, market policy, rate limiter, kill switch
- Auth — test mode sessions (unauthenticated requests work in dev)
- Database — all 19 tables migrated, Drizzle client working
- Shared kernel — `Result<T,E>`, error codes, value objects, constants
- Meals module — **ports only**: entity types, repository interface, application command/query interfaces, Zod request schemas, response DTOs, controller interface. **No real implementations.**
- Meals infrastructure — re-exports the repository port
- Users module — ports only, no implementation
- Product routes — route list declared, server returns 501 for all

Stubs to replace:
- `DrizzleMealEventRepository` — all methods throw `notImplemented`
- `DrizzleUserRepository` — all methods throw `notImplemented`
- Product routes in `server.ts` — return 501

## What to implement

### 1. User Repository — `packages/database/src/repositories/user.repository.ts`

Replace the stub with real Drizzle implementation:

- `findById(id)` — `SELECT ... FROM users WHERE id = ... AND deletedAt IS NULL`
- `findByExternalRef(ref)` — `SELECT ... FROM users WHERE external_ref = ...`
- `create(user)` — `INSERT INTO users ... RETURNING *`
- `softDelete(id)` — `UPDATE users SET deletedAt = NOW() WHERE id = ...`

The `externalRef` is the installation ID from auth context. In test mode, create a user on first access if none exists (upsert).

### 2. Meal Event Repository — `packages/database/src/repositories/meal-event.repository.ts`

Replace the stub with real Drizzle implementation:

- `findById(id, userId)` — single aggregate with items + nutrition snapshot joined. Ownership filtered by `userId`.
- `findPage(userId, page, filters)` — cursor-based pagination on `occurredAt DESC`. Support `from`, `to`, `mealType`, `source` filters. Return `{ data: [...], page: { nextCursor, hasMore } }`.
- `save(event, items, snapshot, tx?)` — transaction: `INSERT INTO meal_events` + `INSERT INTO meal_items` + `INSERT INTO nutrition_snapshots`. Use Drizzle transaction.
- `supersede(originalId, replacement, tx?)` — transaction: set `supersedes_id` on original, insert replacement.
- `softDelete(id, userId)` — `UPDATE meal_events SET deletedAt = NOW() WHERE id = ... AND userId = ...`

### 3. Use Cases — `apps/api/src/modules/meals/application/`

Implement `CreateMealEventHandler`, `CorrectMealEventHandler`, `GetMealEventHandler`, `ListMealEventsHandler`:

**CreateMealEvent:**
- Accept `CreatePhotoMealCommand | CreateManualMealCommand | CreateVoiceMealCommand`
- Validate: items non-empty, nutrition has `catalogSource` + `datasetVersion` (AD-7)
- Create `MealEvent` with `id` (generate via `crypto.randomUUID()`), `status: 'confirmed'`, `revision: 1`
- Save via repository
- Return the saved aggregate with `rewards: null`, `coachMessage: null` (best-effort, subscribers not implemented yet)
- On storage failure: return `storage-unavailable`

**CorrectMealEvent:**
- Accept `CorrectMealCommand`
- Load existing meal, verify ownership
- Create revision N+1: new event with `supersedesId: original.id`, `revision: original.revision + 1`
- Save via `repository.supersede()`
- Return new event

**GetMealEvent:**
- Accept `(id, userId)`
- Load via repository
- Return 404 `MEAL_NOT_FOUND` if not found or not owned

**ListMealEvents:**
- Accept `(userId, page, filters)`
- Return paginated results
- Empty list is not an error

### 4. Controller — `apps/api/src/modules/meals/presentation/`

Replace the interface with a real class `MealControllerImpl`:

- Each method: parse Zod schema → call use case → map to response DTO
- `createPhotoMeal`: parse `CreatePhotoMealRequestSchema`, build `CreatePhotoMealCommand`, execute
- `createManualMeal`: parse `CreateManualMealRequestSchema`, build `CreateManualMealCommand`, execute
- `createVoiceMeal`: parse `CreateVoiceMealRequestSchema`, build `CreateVoiceMealCommand`, execute
- `correctMeal`: parse `CorrectMealRequestSchema` + `MealIdParamSchema`, build `CorrectMealCommand`, execute
- `getMeal`: parse `MealIdParamSchema`, execute query
- `listMeals`: parse `ListMealsQuerySchema`, execute query
- `deleteMeal`: parse `MealIdParamSchema`, call repository.softDelete. Return 204.

Error handling: map `MealCommandFailure` to Product API error codes:
- `not-found` → 404 `MEAL_NOT_FOUND`
- `ungrounded-nutrition` → 400 `VALIDATION_FAILED`
- `storage-unavailable` → 500 `INTERNAL_ERROR`

### 5. Product Routes — `apps/api/src/routes/product/`

Replace the stub file with real route registration:

```
POST   /api/v1/meals/photo     → controller.createPhotoMeal
POST   /api/v1/meals/manual    → controller.createManualMeal
POST   /api/v1/meals/voice     → controller.createVoiceMeal
GET    /api/v1/meals           → controller.listMeals
GET    /api/v1/meals/:id       → controller.getMeal
PATCH  /api/v1/meals/:id       → controller.correctMeal (creates revision N+1)
DELETE /api/v1/meals/:id       → controller.deleteMeal
```

Non-meals routes (`/api/v1/goals`, `/api/v1/journey`, `/api/v1/progress`, etc.) remain 501 Not Implemented.

### 6. Wire in Composition Root — `apps/api/src/main.ts`

Add:
- `DrizzleMealEventRepository` bound to `MealEventRepository` port
- `DrizzleUserRepository` bound to `UserRepository` port
- Handler implementations bound to command/query ports
- `MealControllerImpl` bound to `MealController` interface
- Pass controller to product route registration

### 7. Product Route Registration in Server — `apps/api/src/server.ts`

Add `registerProductRoutes(app, deps)` call after gateway routes. The product server deps interface includes the meal controller.

Do NOT stub individual product routes in the server loop anymore — the route module handles registration itself.

### 8. Test expectations

Existing tests must remain green. Add:

- **Repository tests**: save + findById, save + findPage with filters, softDelete, supersede creates revision
- **Use case tests**: create photo meal, create manual meal, correct meal creates N+1, not-found returns error
- **HTTP tests**: POST/GET/GET:id/PATCH/DELETE against real Fastify instance, 404 for unknown meal, 204 for delete, pagination works, nutrition without catalogSource is rejected
- **Auth**: unauthenticated requests return 401 (test mode should still work via session endpoint)

## Constraints

- Do NOT implement Coach, Gamification, or Progress subscribers — `rewards` and `coachMessage` stay null
- Do NOT implement USDA integration — nutrition is stored verbatim as the client sends it
- Do NOT implement auth session management (refresh, logout, revocation) — request goes through test mode auth
- Do NOT implement catalog search — those routes stay 501
- Do NOT create new database tables — all schemas exist
- Do NOT implement evidence persistence — the `inference_evidence` table is separate and not implemented in this stage. Evidence is accepted in the request schema but ignored (`.default([])`)
- Every error must return a Product API error code (`MEAL_NOT_FOUND`, `VALIDATION_FAILED`, etc.), not a gateway code
- Photo bytes never enter the Product API — they went to the gateway
- `npm test` must pass after implementation

## Files to modify

```
packages/database/src/repositories/user.repository.ts           — real implementation
packages/database/src/repositories/meal-event.repository.ts     — real implementation

apps/api/src/modules/meals/application/commands.ts              — add CreateMealEventHandler, CorrectMealEventHandler
apps/api/src/modules/meals/application/queries.ts               — add GetMealEventHandler, ListMealEventsHandler
apps/api/src/modules/meals/application/index.ts                 — export new handlers
apps/api/src/modules/meals/presentation/meal.controller.ts      — real MealControllerImpl class
apps/api/src/modules/meals/presentation/index.ts                — export controller
apps/api/src/modules/meals/infrastructure/index.ts              — add infrastructure implementations
apps/api/src/modules/meals/index.ts                             — add infrastructure exports

apps/api/src/routes/product/index.ts                            — registerProductRoutes(app, deps) with real meals + 501 for rest
apps/api/src/server.ts                                          — call registerProductRoutes, remove 501 loop
apps/api/src/main.ts                                            — wire deps
```
