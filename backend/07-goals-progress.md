# Stage 7: Goals + Progress (Calendar Screen)

> Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
> REST API spec: `backend/mobile-api/docs/06-rest-api-specification.md` (§6.6 Goals, Journey/Progress)
> Database: `backend/mobile-api/packages/database/src/schema/`
>
> Previous stage: `backend/06-usda-integration.md`
> Stage type: goals domain + progress projection + calendar-screen API surface

## Role

You are a Senior Backend Engineer implementing the goals domain and the daily/weekly progress projection.

The gateway (E4.3), the meals domain (Stage 5), and the USDA provider (Stage 6) are live. A user can photograph food, confirm a candidate, and have nutrition computed on-device from the catalog; the backend catalog search returns facts when no local package covers a term.

Your task: implement goals and progress so the calendar screen works — the user sees per-day totals against the goal in force, weekly aggregates, and trends. Every request shape, response body, and business rule must match the REST API spec exactly.

No mobile client exists yet. All testing is via curl.

## Context

Already implemented:
- Meals domain + Product API (Stage 5) — create/list/get/correct/delete meal events, `DrizzleMealEventRepository`, `DrizzleUserRepository`. Meal events are persisted with nutrition snapshots and `localDate` + `timezone`.
- USDA provider + catalog search (Stage 6) — `GET /api/v1/catalog/search` works and returns catalog facts + serving options. Meal nutrition stays client-grounded; the server never calculates (docs/08 §8.4).
- Shared kernel — `Result<T,E>`, value objects (`UserId`, `LocalDate`, `Instant`), constants.
- Goals module — **ports only**: `UserGoal`, `UserGoalRepository`, `GoalFailure`, `UpdateGoal` port, `UpdateGoalRequestSchema`, `GetGoalQuerySchema`, `GoalResponse`. No implementation.
- Progress module — **ports only**: `DailyProgress`, `WeeklyProgress`, `ProgressTotals`, `GoalAdherence`, `Trend`, `ProgressProjector`, `TrendCalculator`, `DailyProgressRepository`, `GetDailyProgress`, `GetWeeklyProgress`, query schemas. No implementation.
- Database — `user_goals` and `daily_progress` tables migrated (`packages/database/src/schema/user-goals.ts`, `daily-progress.ts`).
- Product routes — `GET/PATCH /api/v1/goals`, `GET /api/v1/progress`, `GET /api/v1/progress/weekly`, `GET /api/v1/progress/trends` declared but return 501.

Stubs to replace:
- `UserGoalRepository` / `DailyProgressRepository` — no Drizzle implementation exists
- `ProgressProjector` / `TrendCalculator` — no implementation exists
- Goals/progress routes — return 501

## What to implement

### 1. Goal Repository — `packages/database/src/repositories/user-goal.repository.ts`

Real Drizzle implementation over `userGoals`:

- `findCurrent(userId)` — the version with `effectiveTo IS NULL` (if multiple, the one with the latest `effectiveFrom`; else the one with the latest `effectiveTo`). Return `not-found` if none.
- `findActiveAt(userId, localDate)` — `effectiveFrom <= localDate AND (effectiveTo IS NULL OR effectiveTo >= localDate)`. Return `not-found` if none.
- `openNewVersion(goal)` — transaction: `UPDATE user_goals SET effectiveTo = <goal.effectiveFrom - 1 day> WHERE userId = ... AND effectiveTo IS NULL`, then `INSERT` the new row. Return the new version.

### 2. Goals Use Cases — `apps/api/src/modules/goals/application/`

Implement `UpdateGoalHandler` and `GetGoalHandler`:

**UpdateGoalHandler:**
- Accept `(userId, patch)` where `patch` is the parsed `UpdateGoalRequestSchema`
- Load current version; if none exists, create version 1 with `effectiveFrom` = patch.effectiveFrom or today (server UTC), `effectiveTo: null`
- If one exists: close it at `effectiveFrom - 1 day` and open the next version; a `patch.effectiveFrom` before the current version's `effectiveFrom` is `overlapping-period`
- Persist via `repository.openNewVersion`
- Return the new version
- On storage failure: `storage-unavailable`

**GetGoalHandler:**
- Accept `(userId, at?: LocalDate)`
- No `at`: `findCurrent`; with `at`: `findActiveAt`
- `not-found` → 404 `GOAL_NOT_FOUND`

### 3. Goal Controller — `apps/api/src/modules/goals/presentation/`

Implement a `GoalController` with `updateGoal` (parse schema → use case → `GoalResponse`) and `getGoal` (parse `GetGoalQuerySchema` → use case → `GoalResponse`).

### 4. Progress Projection — `apps/api/src/modules/progress/`

**`DailyProgressProjector.recompute(userId, localDate)`:**
- Aggregate `meal_events` (non-deleted, `localDate` match) joined to `nutrition_snapshots`: sum calories/protein/fat/carb/fiber; `partial: true` when any contributing snapshot has a null macro
- `mealCount` = number of events that day
- Load goal in force via `UserGoalRepository.findActiveAt`; compute `GoalAdherence` when a goal exists:
  - ratio = total / target per nutrient (calories, protein, fat, carb)
  - status: ratio < 0.85 → `under`, ratio > 1.15 → `over`, else `on_track`
  - no goal → `goalId: null`, `goalAdherence: null`
- Upsert `daily_progress` row (unique `(userId, localDate)`)
- Return `DailyProgress`

**`DailyProgressProjector.rebuild(userId, from, to)`:** loop `recompute` per local date in range; return count.

**`TrendCalculator.compute(days, metric)`:** `direction` = sign of change between the first and last non-null value; `changeRatio` = (last - first) / |first| (0 when first is 0); `flat` when equal.

**`DailyProgressRepository` (Drizzle):** `find`, `findRange` over `dailyProgress` with the unique index.

### 5. Progress Use Cases — `apps/api/src/modules/progress/application/`

- **`GetDailyProgressHandler`**: recompute the requested date on the fly (the projection is always fresh at read), then return it. If no meals exist that day, return a zeroed `DailyProgress` (totals zero, mealCount 0, goal in force, adherence computed against zero or null) — empty day is not an error.
- **`GetWeeklyProgressHandler`**: recompute the 7 days from `weekStart` (inclusive; `weekStart` is treated as-is, the client sends the Monday), aggregate `WeeklyProgress`, compute `Trend` for `calories`.
- **`GetTrendsHandler`**: for each local date in `[from, to]`, recompute and emit `{ localDate, value }` for the requested metric; missing values are `null`. Response: `{ metric, from, to, points }`.

### 6. Wire into meal writes

In `CreateMealEventHandler` and the delete path (Stage 5 files, meals module): after the meal save/soft-delete commits, call `DailyProgressProjector.recompute(userId, localDate)` as a **best-effort** step — it runs after commit, failures are logged and never fail the meal. On `GET /progress`, the handler also recomputes, so a failed write-time recompute cannot produce a stale dashboard.

### 7. Progress Controller + Routes

Implement `ProgressController` (daily/weekly/trends) and register:

```
GET   /api/v1/goals             → goalController.getGoal
PATCH /api/v1/goals             → goalController.updateGoal
GET   /api/v1/progress          → progressController.getDaily
GET   /api/v1/progress/weekly   → progressController.getWeekly
GET   /api/v1/progress/trends   → progressController.getTrends
```

Non-goals/progress routes stay 501.

### 8. Wire in Composition Root — `apps/api/src/main.ts`

- `DrizzleUserGoalRepository` bound to `UserGoalRepository`
- `DrizzleDailyProgressRepository` bound to `DailyProgressRepository`
- `DailyProgressProjector` implementation bound to `ProgressProjector`
- Handlers bound to ports; controllers registered

### 9. Test expectations

Existing tests must remain green. Add:

- **Repository tests**: openNewVersion closes current and opens next, findActiveAt returns version in force, findCurrent returns the open version
- **Projection tests**: totals sum correctly, `partial: true` with a null macro, adherence ratios and status bands, no-goal day yields null adherence, rebuild returns count
- **Use case tests**: update creates version 1, update closes + reopens, overlapping-period rejected, get with/without `at`
- **HTTP tests**: PATCH/GET goals, GET progress for a day with meals, empty day returns zeroed totals, weekly aggregates + trend, trends time series, 404 `GOAL_NOT_FOUND`
- **Integration**: create meal → GET /progress reflects it; delete meal → GET /progress reflects the rebuild

## Constraints

- Do NOT implement achievements, XP, streaks, coach, or journey — those are Stage 8
- Do NOT modify any Stage 5/6 file's public behavior that tests already assert (meal schemas, meal response shape, nutrition origin) — the projection hooks are additive
- Do NOT create new database tables or migrations — `user_goals` and `daily_progress` exist
- `daily_progress` is a derived, rebuildable projection, never a source of truth; read paths must recompute, not trust staleness
- Do NOT modify auth — requests go through the existing test-mode token from `/gw/v1/session`
- `npm test` must pass after implementation

## Files to modify

```
packages/database/src/repositories/user-goal.repository.ts        — new, real implementation
packages/database/src/repositories/daily-progress.repository.ts    — new, real implementation

apps/api/src/modules/goals/application/index.ts                   — UpdateGoalHandler, GetGoalHandler
apps/api/src/modules/goals/presentation/index.ts                  — GoalController impl
apps/api/src/modules/goals/index.ts                               — export handler/controller

apps/api/src/modules/progress/application/index.ts                — GetDailyProgressHandler, GetWeeklyProgressHandler, GetTrendsHandler
apps/api/src/modules/progress/presentation/index.ts               — ProgressController impl, TrendsResponse
apps/api/src/modules/progress/domain/index.ts                     — DailyProgressProjector + TrendCalculator implementations
apps/api/src/modules/progress/infrastructure/index.ts             — Drizzle repositories re-export
apps/api/src/modules/progress/index.ts                            — export wiring

apps/api/src/modules/meals/application/commands.ts               — best-effort recompute hook after create/delete (additive)
apps/api/src/routes/product/index.ts                             — register goals + progress routes
apps/api/src/main.ts                                             — wire deps
```
