# 4. DDD Module Design

Each section states what the module owns, its domain model, its ports, its
published contract, and the rule most likely to be violated under delivery
pressure. Signatures are illustrative shapes for Stage 2, not code.

## 4.1 Auth

**Owns:** installation attestation, token issuance/rotation/revocation, refresh
lineage, replay and nonce state, `TEST_MODE` fencing.

**Domain**

- `Session` — id, userId, installationId, environment, issuedAt, expiresAt, revokedAt
- `TokenPair` — accessToken, refreshToken, accessExpiresAt, refreshExpiresAt
- `RefreshTokenFamily` — lineage for reuse detection
- `AuthScope` — `product` \| `gateway` \| `both`
- `AuthMode` — `attested` \| `test-mode`
- `InstallationAttestation` — platform, attestationToken, verifiedAt

**Ports**

```
SessionRepository        find, create, revoke, revokeFamily
AttestationVerifier      verify(platform, token) -> Result<InstallationId, AuthFailure>
NonceStore               claimOnce(nonce, ttl) -> boolean     (Redis)
TokenIssuer              issue(subject, scope, mode) -> TokenPair
```

**Published contract:** `AuthContext { userId, installationId, scope, authMode, environment }`.

**Rule:** Auth is the only module that mints or parses tokens. A module needing
identity receives `AuthContext`; it never sees a JWT. `AttestationVerifier` has
two adapters — a real platform verifier and a fixture verifier — and the fixture
adapter is bound only when the `TEST_MODE` boot assertion has passed.

## 4.2 Users

**Owns:** user record, profile, preferences, consent receipts, erasure.

**Domain**

- `User` — id, externalRef, createdAt, status, deletedAt
- `UserProfile` — displayName, locale (BCP 47), measurementSystem, timezone (IANA)
- `ConsentReceipt` — id, userId, purpose, modality, payloadDigest, marketJurisdiction, processingRegion, retentionVersion, configurationVersion, consentCopyVersion, decidedAt, decision

**Ports**

```
UserRepository            find, create, update, softDelete
ConsentReceiptRepository  record, findByDigest, findByUser
ErasurePort               erase(userId) -> Result<ErasureReport, ...>   (one per domain)
```

**Rule:** Users orchestrates erasure by fanning out over every registered
`ErasurePort` and producing an auditable `ErasureReport`. A new domain with
user-scoped data registers a port or the erasure test fails. This is what makes
"user rights evidence" (AD-24) a build property.

`ConsentReceipt` stores the **digest** of the scrubbed payload, never the
payload. It is the durable record proving AD-8's precondition was met.

## 4.3 Goals

**Owns:** versioned nutrition targets.

**Domain**

- `UserGoal` — id, userId, goalType, targetCalories, targetProteinG, targetFatG, targetCarbG, activityLevel, effectiveFrom, effectiveTo
- `GoalType` — `lose` \| `maintain` \| `gain` \| `custom`

**Ports:** `UserGoalRepository — findActiveAt(userId, date), findCurrent, openNewVersion`

**Rule:** `PATCH /goals` **closes** the current version and **opens** a new one.
It never updates a row in place. Progress always resolves targets via
`findActiveAt(userId, localDate)`, so last month's adherence is computed against
last month's goal.

## 4.4 Meals — core

**Owns:** the `MealEvent` aggregate.

**Domain**

- `MealEvent` (root) — id, userId, source, status, occurredAt, localDate, timezone, revision, createdAt, confirmedAt
- `MealItem` — id, mealEventId, foodRef, servingUnit, servingAmount, confidence, position
- `MealEvidence` — id, mealItemId, type, payload, provenance
- `NutritionSnapshot` — value object of the confirmed event; totals plus per-item facts, each with catalog provenance
- `MealSource` — `PhotoSource | ManualSource | VoiceSource` (discriminated union)
- `MealStatus` — `pending | identified | confirmed | failed`
- `MealEventRevision` — supersedes-chain for post-confirmation corrections

**Ports**

```
MealEventRepository   findById, findPage(userId, cursor, filters), save, supersede
MealEventFactory      fromPhotoAttempt | fromManualEntry | fromVoiceEntry
```

**Domain events:** `MealEventCreated`, `MealEventIdentified`, `MealEventConfirmed`,
`MealEventFailed`, `MealEventSuperseded`.

**Published contract**

```
MealFactsQuery
  totalsForLocalDate(userId, localDate)        -> DailyTotals
  totalsForRange(userId, from, to)             -> RangeTotals
  confirmedCountSince(userId, instant)         -> number
  distinctLoggedLocalDates(userId, from, to)   -> LocalDate[]
```

Gamification and Progress consume exactly this. Neither reads `meal_events`.

**Rules**

- One `MealEvent` and its children are one transaction.
- `confirmed` is terminal. Corrections create a new revision and emit
  `MealEventSuperseded`; the original row is retained.
- `localDate` and `timezone` are written at creation from the client's
  configuration snapshot. Never derived later from UTC.
- The three sources converge at `MealEventFactory`. Downstream code branching on
  `source` beyond presentation is a design smell and is called out in review.

## 4.5 Inference

**Owns:** provider abstraction, orchestration, confidence routing, telemetry.

**Domain**

- `InferenceRequest` — attemptId, payloadRef, context (goal, catalogCountry, mealType, priorMealSummary), contractVersion
- `InferenceResult` — candidates, confidence, modelId, durationMs, outcome
- `Candidate` — candidateId, label, rank, confidence, unresolvedAssumptions, provenance
- `ConfidenceScore` — value object, 0..1, plus `band`
- `InferenceEvidence` — provenance, rawResponseRef (transient), timing
- `ProviderType` — `openrouter` (extension point: `google`, `openai`, `anthropic-direct`)
- `InferenceOutcome` — `candidates | abstention | failure`

**Ports**

```
InferenceProvider
  id: ProviderType
  supports(profile: ModelProfile): boolean
  infer(req: ProviderRequest): Promise<Result<ProviderResponse, InferenceFailure>>

InferenceOrchestrator
  analyze(req: InferenceRequest): Promise<Result<InferenceResult, InferenceFailure>>

ModelProfileSource     profiles(): ModelProfile[]        (configuration, not code)
InferenceRunRecorder   record(run: InferenceRunTelemetry): Promise<void>
```

**Rules**

- `InferenceResult` carries **no calories, macros, or micronutrients**. This
  mirrors `contracts/inference/v1` (`numeric_nutrition = false`) and is asserted
  by a schema test, because the cheapest way for a model to be wrong is to be
  asked for a number.
- No module outside `infrastructure/openrouter` may name a provider or model.
- Pass 2 reuses the **already-transferred** payload for the same `attemptId`. It
  is not a new transfer and does not require a new consent receipt.
- `InferenceRunRecorder` writes `inference_runs`, which holds **no user foreign
  key** — see document 5 §5.4.

## 4.6 Nutrition

**Owns:** nutrient facts, conversion, rounding, snapshot construction.

**Domain**

- `NutritionFact` — per basis: calories, proteinG, fatG, carbG, fiberG, sugarG, sodiumMg, plus `catalogSource`, `datasetVersion`, `servingBasis`
- `FoodReference` — namespace-qualified canonical food ID plus display metadata
- `NutritionSnapshot` — per-item facts and meal totals, frozen at confirmation
- `ServingBasis` — unit, amount, gramWeight

**Ports**

```
NutritionProvider
  id: 'usda' | 'off'
  lookup(req: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>>

NutritionCalculator
  forServing(fact: NutritionFact, basis: ServingBasis): NutritionFact
  aggregate(items: NutritionFact[]): NutritionTotals
```

**Rules**

- A `NutritionFact` without `catalogSource` and `datasetVersion` fails
  serialization. Provenance is not optional metadata; it is what makes the
  number admissible (AD-7).
- Conversion is SI-normalized decimal; source precision, calculation precision,
  and display rounding are three distinct settings (AD-19). Never float
  arithmetic on nutrients — decimal throughout, `numeric` in Postgres.
- For a client-created meal the backend **persists the client's snapshot
  verbatim**. `NutritionCalculator` runs only for server-originated lookups, and
  both sides are pinned to shared golden fixtures. See document 8 §8.4.

## 4.7 Catalog

**Owns:** food lookup and canonical identity.

**Domain**

- `CatalogMatch` — foodId, source, confidence, displayName, servingOptions
- `CatalogSource` — `usda` \| `off`
- `CatalogPackage` — packageId, catalogCountry, datasetVersion, checksum, license, activatedAt, rollbackTarget

**Ports**

```
CatalogSearchPort   search(term, catalogCountry, limit): Promise<CatalogMatch[]>
CatalogItemPort     byId(foodId): Promise<Result<CatalogItem, CatalogFailure>>
CatalogPackagePort  activePackage(catalogCountry): Promise<CatalogPackage>
```

**Rules**

- Food IDs are namespace-qualified (`usda:fdc:171705`), never bare integers. A
  bare ID becomes ambiguous the moment a second source exists.
- Search results carry `CatalogSource` and `datasetVersion` so a match is
  reproducible after a package upgrade.
- Catalog never guesses. No fuzzy match is promoted to a confirmed identity
  without explicit user confirmation (AD-40).

## 4.8 Coach, Gamification, Progress

### Coach

**Domain:** `CoachMessage` (text, type, context, mealEventId?, createdAt),
`CoachRecommendation`, `CoachContext` (recent totals, adherence, streak state,
goal — all read-only).

**Ports:** `CoachMessageRepository`, `CoachContextAssembler`, `MessageSelector`.

**Rules:** Coach has **no write access to any other domain**. Enforced by giving
its module no dependency on any other repository port. It reads through
published contracts and writes only `coach_messages`. Message content is drawn
from a reviewed catalog with no medical or allergen claims (AD-40); a lint test
scans the catalog for prohibited claim patterns.

### Gamification

**Domain:** `Achievement` (id, key, name, description, `criteria`, active),
`UserAchievement` (userId, achievementId, unlockedAt, evidence),
`Streak` (userId, type, currentCount, longest, lastLocalDate),
`XpTransaction` (userId, amount, reason, refId, createdAt).

**Ports:** `AchievementRepository`, `UserAchievementRepository`, `StreakRepository`,
`XpLedger`, `AchievementRuleEngine`.

**Rules**

- `criteria` is a declarative JSON expression evaluated by
  `AchievementRuleEngine` against a `MealFactsQuery` projection. The five MVP
  achievements are seed rows. **No achievement key appears in a conditional in
  application code.**
- Streaks advance on **distinct local dates**, not on meal counts, and are
  recomputed from `distinctLoggedLocalDates` rather than incremented blindly —
  an increment-only streak cannot survive a late-arriving or corrected meal.
- XP is a **ledger**. Balance is the sum of transactions, never a mutable
  counter. `(userId, reason, refId)` is unique, which makes replay safe.

### Progress

**Domain:** `DailyProgress` (userId, localDate, totals, goalAdherence,
mealCount, computedAt), `WeeklyProgress` (weekStartLocalDate, totals, trend),
`GoalProgress` (goalId, current, target, status).

**Ports:** `DailyProgressRepository`, `ProgressProjector`, `TrendCalculator`.

**Rules**

- `daily_progress` is a **rebuildable projection**. `ProgressProjector` exposes
  `rebuild(userId, from, to)` and a maintenance command invokes it. A projection
  that cannot be rebuilt has quietly become a source of truth.
- Adherence resolves the goal in force on that local date via
  `UserGoalRepository.findActiveAt`.
- Weekly aggregates are computed from daily rows, not re-derived from meals, so
  one definition of a day exists.

## 4.9 Enforcing the rules

Every rule above is worth exactly as much as its enforcement. Stage 2 must land
these alongside the skeleton:

| Rule | Enforcement |
| --- | --- |
| Layer direction (AD-1) | `eslint-plugin-boundaries` zones per §3.4, error severity |
| No cross-module repository access | Boundary rule: `modules/*/infrastructure` importable only by its own module and the composition root |
| Gateway cannot persist | Boundary rule: `routes/gateway/**` may not import `packages/database` or any `*Repository` |
| Domain purity | Boundary rule: `modules/*/domain` may import only `packages/shared` |
| No provider names outside adapter | `no-restricted-imports` plus a grep test for model ID strings outside `config/model-profiles.toml` |
| No nutrient fields in inference types | Schema test over `InferenceResult` asserting the absence of nutrient keys |
| Coach writes nothing | Coach module has no other repository port in its dependency graph; asserted by a graph test |
| Product boundary (AD-36) | `no-restricted-imports` on `../../mobile`, `../../web/landing`, and runtime imports of root `contracts/` |
| Telemetry allowlist (AD-14) | Logger serializer built from `config/telemetry-allowlist.toml`; test asserts unknown fields are dropped |

These are not style preferences. Each one corresponds to an invariant that is
otherwise unprovable, and document 11 schedules them as part of activation
rather than as follow-up hardening.
