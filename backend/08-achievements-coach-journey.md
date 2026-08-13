# Stage 8: Achievements + Coach + Journey (Dashboard Screen)

> Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
> REST API spec: `backend/mobile-api/docs/06-rest-api-specification.md` (§6.6 Journey, Progress, Achievements)
> Database: `backend/mobile-api/packages/database/src/schema/`
>
> Previous stage: `backend/07-goals-progress.md`
> Stage type: gamification + coach + journey feed for the dashboard screen

## Role

You are a Senior Backend Engineer implementing the achievement engine, streak/XP ledger, coach message generation, and the journey feed.

The gateway (E4.3), meals (Stage 5), USDA (Stage 6), and goals/progress (Stage 7) are live. A user can photograph food, confirm a candidate, and see daily/weekly progress against goals.

Your task: implement achievements, streaks, XP, coach messages, and the merged journey feed so the dashboard screen works. Every request shape and response body must match the REST API spec exactly. Meal creation now returns real `rewards` and `coachMessage` instead of `null`.

No mobile client exists yet. All testing is via curl.

## Context

Already implemented:
- Meals domain (Stage 5) + USDA auto-nutrition (Stage 6). `CreateMealEventHandler` returns `rewards: null`, `coachMessage: null` and has a best-effort progress recompute hook (Stage 7).
- Goals + progress (Stage 7) — `user_goals`, `daily_progress` live, `GET /progress*` works, `DailyProgressProjector.recompute` is called after meal writes.
- Shared kernel — `Result<T,E>`, `UserId`, `LocalDate`, `Instant`, `AchievementId`.
- Gamification module — **ports only**: `Achievement`, `AchievementCriteria` (meal_count / streak / goal_adherence / goal_adherence_days), `UserAchievement`, `Streak`, `XpTransaction`, `AchievementRuleEngine`, `XpLedger`, `StreakRepository`, `EvaluateAchievements` port. No implementation.
- Coach module — **ports only**: `CoachMessage`, `CoachContext`, `CoachContextAssembler`, `MessageSelector`, `CoachMessageRepository`, `GenerateCoachMessage` port. No implementation.
- Achievement seed data — `packages/database/src/seeds/achievements.seed.ts` ships five declarative achievements with passing tests.
- Database — `achievements`, `user_achievements`, `streaks`, `xp_transactions`, `coach_messages` tables migrated.
- Product routes — `GET /api/v1/achievements`, `GET /api/v1/coach/messages`, `GET /api/v1/journey` declared but return 501. **There is no journey module.**

Stubs to replace:
- All gamification and coach infrastructure (no Drizzle implementations exist)
- `EvaluateAchievements` / `GenerateCoachMessage` ports (no handlers exist)
- Journey endpoint (501, no module)

## What to implement

### 1. Gamification infrastructure — `packages/database/src/repositories/`

**`achievement.repository.ts`** — over `achievements`:
- `listActive()` — all rows with `active = true`, ordered by `sortOrder`
- `findByKeys(keys)` — rows whose `key` is in the list

**`user-achievement.repository.ts`** — over `userAchievements`:
- `list(userId)` — joined to `achievements`, unlock state
- `unlock(userId, achievementId, evidence)` — idempotent insert; the unique `(userId, achievementId)` constraint makes a replay a no-op; detect via insert conflict (return `already-awarded` on conflict)

**`xp-ledger.repository.ts`** — over `xpTransactions`:
- `award(tx)` — idempotent on `(userId, reason, refId)`; conflict → no-op (a replay is not an error, it is a no-op)
- `balance(userId)` — `SUM(amount)`

**`streak.repository.ts`** — over `streaks`:
- `recompute(userId, type, asOf)` — recompute, never increment: query distinct `localDate` values from `meal_events` (non-deleted) for the user, walk back from `asOf` while dates are consecutive, count; update `currentCount`, `longestCount = max(longest, current)`, `lastLocalDate`. Upsert on `(userId, type)`.

### 2. Achievement rule engine — `apps/api/src/modules/gamification/`

Implement `AchievementRuleEngine.evaluate(criteria, userId, asOf)` per criterion type:
- `meal_count` — `COUNT(*)` non-deleted `meal_events` for the user
- `streak` — recompute the streak via `StreakRepository` and compare `currentCount`
- `goal_adherence` (window 1 day) — `daily_progress` for `asOf`: the named nutrient's adherence ratio meets the criterion
- `goal_adherence_days` (window N days) — count of the last N `daily_progress` rows where the nutrient's adherence meets the threshold

Implement `EvaluateAchievementsHandler` (`EvaluateAchievements` port — return type is `readonly string[]`, the newly unlocked achievement **keys**; keep the port signature exactly as declared):
- Recompute the `daily_logging` streak
- For each active achievement, evaluate its criteria; collect newly satisfied ones
- Unlock each (guarding `already-awarded`) and award its `xpReward` XP (`reason: 'achievement'`, `refId: achievementKey`)
- Award 10 base XP per confirmed meal (`reason: 'meal_logged'`, `refId: mealEventId`) — idempotent
- Return the newly unlocked achievement keys

### 3. Coach — `apps/api/src/modules/coach/`

**`CoachContextAssembler`** — read-only snapshot from Stage 7/8 data:
- `recentTotals` — last 7 days of `daily_progress` totals
- `adherenceStatus` — today's adherence status (or null)
- `streakCount` — current `daily_logging` streak
- `goalType` — current goal type (or null)

**`MessageSelector`** — rule-based message catalog, deterministic:
- streak >= 3 → `coach.streak.day_three`, params `{ days }`
- no goal set → `coach.goal.not_set`
- streak 0 (missed yesterday) → `coach.streak.restart`
- default → `coach.motivation.daily`
- The catalog is a single constant table (key + params builder); **no medical or allergen content, no accuracy claims** (AD-40)

**`CoachMessageRepository`** (Drizzle over `coachMessages`): `save`; `listPage(userId, cursor)`.

Implement `GenerateCoachMessageHandler` (`GenerateCoachMessage` port):
- Assemble context → select message → save → return the message
- `no-context` when context assembly fails (no recent data at all)

### 4. Wire rewards/coachMessage into meal responses

In `CreateMealEventHandler` (Stage 5 file), **replace** the `rewards: null, coachMessage: null` stub with best-effort subscribers alongside the Stage 7 progress hook:

- After the meal commit, call `EvaluateAchievementsHandler.execute(userId, localDate, mealEventId)` — the port returns the newly unlocked achievement keys. Build the `rewards` object per spec: `{ xpAwarded, achievementsUnlocked: string[], streak: { type: 'daily_logging', currentCount } }`. `xpAwarded` is the sum of base meal XP + newly unlocked achievements' `xpReward` (look them up via the achievement repository); `currentCount` comes from the streak recompute.
- Call `GenerateCoachMessageHandler.execute(userId, mealEventId)` — build `coachMessage` per spec: `{ type, messageKey, params }`
- Either subscriber failing (storage error, rule error) must NOT fail the meal — the field is `null` in that case. Log and continue. Logging a meal is the primary KPI and never depends on this engine.
- **Update the Stage 5 test that asserted `rewards`/`coachMessage` are always `null`** — it now asserts real values when subscribers succeed and `null` only when they fail.

### 5. Journey — new module `apps/api/src/modules/journey/`

Create the module skeleton (domain/application/presentation/contracts/index) and:

- `GetJourneyHandler.execute(userId, cursor, limit)` — merged reverse-chronological feed of:
  - meal events (from `DrizzleMealEventRepository`)
  - achievement unlocks (from `userAchievements` joined with `achievements`)
  - streak milestones (synthetic entries when the current streak reaches a milestone — derive from `streaks.longestCount` for `daily_logging`)
- Items are normalized to `{ type: 'meal' | 'achievement' | 'streak_milestone', id, occurredAt, ...payload }`
- Stable total order by `occurredAt DESC`; cursor-paged with an opaque cursor encoding `(occurredAt, type, id)`; `limit` 1–50, default 20
- No items → empty `data`, `hasMore: false` (not an error)

### 6. Controllers + Routes

Implement `GamificationController`, `CoachController`, `JourneyController` and register:

```
GET /api/v1/achievements     → achievementController.list          → AchievementResponse[] with progress
GET /api/v1/coach/messages   → coachController.list               → CoachMessageResponse[] cursor page
GET /api/v1/journey          → journeyController.list             → merged feed, cursor paged
```

`AchievementResponse.progress` is a 0–1 number derived from the same evaluation the rule engine uses (meal count ratio, streak ratio, adherence ratio, days-met ratio).

Remaining routes (`/api/v1/users/me*`, `/api/v1/catalog/items/:foodId`, `/api/v1/catalog/packages`) stay 501.

### 7. Wire in Composition Root — `apps/api/src/main.ts`

- Repositories bound to gamification/coach ports
- `AchievementRuleEngine`, `EvaluateAchievementsHandler`, `CoachContextAssembler`, `MessageSelector`, `GenerateCoachMessageHandler`, `GetJourneyHandler` bound
- Controllers registered; a startup/script step upserts `ACHIEVEMENT_SEEDS` into `achievements` (idempotent, keyed on `key`)

### 8. Test expectations

Existing tests must remain green (including the updated Stage 5 rewards test). Add:

- **Repository tests**: unlock idempotency, XP award no-op on replay, balance = sum, streak recompute across gap/multiple meals per day
- **Rule engine tests**: each criterion type true/false, healthy_week counts days, streak achievement triggers at 3/7
- **Subscriber tests**: meal creation awards XP + unlocks `first_meal`, replay (same eventId) awards nothing twice, subscriber failure leaves `rewards: null` and the meal still saved
- **Coach tests**: selector rules map context → key, catalog contains no prohibited claims, no-context when empty
- **HTTP tests**: GET /achievements with progress, GET /coach/messages pagination, GET /journey merge order + pagination, 501 unchanged for out-of-scope routes

## Constraints

- Do NOT implement user-rights endpoints (`/users/me`, export, erasure), catalog items/packages, or auth refresh/logout — separate later stages
- Do NOT implement a background/job engine — subscribers run in-process, best-effort, after commit
- Do NOT add conditional branching on achievement keys in application code — achievements are data, the rule engine evaluates criteria (docs/04 §4.8)
- Do NOT create new database tables — all five tables exist; journey has no table by design (it is a read-time merge)
- The unique constraints are the idempotency mechanism; do not bypass them with read-then-write
- Coach messages are `messageKey` + `params`, never rendered prose; the catalog is scanned for prohibited medical/allergen/accuracy claims (AD-40)
- Do NOT modify meal schemas or the progress projection behavior from Stage 7 — only the rewards/coachMessage fields in the meal response change
- `npm test` must pass after implementation

## Files to modify

```
packages/database/src/repositories/achievement.repository.ts         — new
packages/database/src/repositories/user-achievement.repository.ts     — new
packages/database/src/repositories/xp-ledger.repository.ts            — new
packages/database/src/repositories/streak.repository.ts               — new
packages/database/src/repositories/coach-message.repository.ts        — new

apps/api/src/modules/gamification/domain/index.ts                    — rule engine implementation
apps/api/src/modules/gamification/application/index.ts               — EvaluateAchievementsHandler
apps/api/src/modules/gamification/presentation/index.ts              — GamificationController, AchievementResponse with progress
apps/api/src/modules/gamification/infrastructure/index.ts            — repository re-exports
apps/api/src/modules/gamification/index.ts                           — export wiring

apps/api/src/modules/coach/domain/index.ts                           — context assembler + message selector implementations
apps/api/src/modules/coach/application/index.ts                      — GenerateCoachMessageHandler
apps/api/src/modules/coach/presentation/index.ts                     — CoachController
apps/api/src/modules/coach/infrastructure/index.ts                   — repository re-export
apps/api/src/modules/coach/index.ts                                  — export wiring

apps/api/src/modules/journey/domain/index.ts                         — new: journey item types
apps/api/src/modules/journey/application/index.ts                    — new: GetJourneyHandler
apps/api/src/modules/journey/presentation/index.ts                   — new: JourneyController, schemas
apps/api/src/modules/journey/contracts/index.ts                      — new
apps/api/src/modules/journey/index.ts                                — new

apps/api/src/modules/meals/application/commands.ts                  — rewards/coachMessage wiring (replaces null stub)
apps/api/src/modules/meals/presentation/meal.controller.ts          — response mapping for rewards/coachMessage
apps/api/src/routes/product/index.ts                                — register achievements, coach, journey routes
apps/api/src/main.ts                                                — wire deps + seed upsert step
```
