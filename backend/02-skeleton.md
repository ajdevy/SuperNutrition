# Stage 2: Backend Skeleton

> Architecture spine: _bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md
> Solution design: _bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/solution-design.md
> Agent guide: _bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md
> Epics (E4): _bmad-output/planning-artifacts/epics.md (line 635)
> Product lifecycle: backend/mobile-api/product-lifecycle.toml
>
> Previous stage: backend/01-architecture.md
> Next stage: backend/03-database.md
> Stage type: skeleton generation (interfaces, DTOs, schemas, folder structure — no business logic)

## Role

You are a Senior Backend Engineer working under an approved architecture.
Architecture decisions have already been made in `backend/01-architecture.md` and the project architecture spine.
Do NOT redesign the system. Do NOT challenge architecture.
Your task is to generate the initial backend skeleton exactly according to the approved architecture.

## Context

This is the second stage of backend development. Stage 1 (architecture) produced the system design.
Stage 2 creates the concrete project structure, interfaces, DTOs, schemas, and validation contracts.
Stage 3 (database) will add migrations and repositories on top of this skeleton.

The backend lives at `backend/mobile-api/` in the monorepo (currently in `preselection` lifecycle).
It must remain independent from `mobile/`, `web/landing/`, and `contracts/` — no cross-root imports.

## Goal

Create a production-ready backend foundation for SuperNutrition MVP.
Generate only:
- folder structure
- domain boundaries
- interfaces
- DTOs
- schemas
- validation contracts
- infrastructure skeleton

Do not implement business logic.
Do not implement AI prompts.
Do not implement nutrition calculations.
Do not implement gamification rules.

## Technology Stack

| Layer | Choice |
|-------|--------|
| Runtime | Node.js + TypeScript |
| Database | PostgreSQL |
| ORM | Drizzle ORM |
| Cache | Redis |
| Architecture | DDD Modular Monolith |
| Transport | REST API |
| Auth | JWT + Refresh Tokens (test mode gated) |

## Monorepo Structure (within `backend/mobile-api/`)

```
backend/mobile-api/
├── apps/
│   └── api/                 ← REST API entrypoint
├── packages/
│   ├── database/            ← Drizzle schema, migrations, seeds, repositories
│   ├── shared/              ← errors, events, result, value-objects, types, constants
│   └── contracts/           ← cross-module DTOs and interfaces
├── docker-compose.yml
├── package.json
├── tsconfig.json
├── .env.example
└── product-lifecycle.toml   ← existing, update lifecycle to "active" when ready
```

### API Module Structure

Generate under `apps/api/src/modules/`:

```
modules/
├── auth/         ← JWT, refresh, session contracts
├── users/        ← user entity, profile
├── goals/        ← user goals
├── meals/        ← MealEvent, MealItem, MealEvidence — core domain
├── inference/    ← InferenceProvider, InferenceOrchestrator
├── nutrition/    ← NutritionProvider, USDAProvider skeleton
├── catalog/      ← food catalog lookup
├── coach/        ← CoachMessage, recommendations
├── gamification/ ← Achievement, Streak, XP
└── progress/     ← DailyProgress, WeeklyProgress
```

Every module must contain:
```
  application/   ← Commands, Queries, Use Cases, DTOs, Validators
  domain/        ← Entities, Value Objects, Repository Interfaces, Domain Events
  infrastructure/ ← Repository contracts, provider contracts, adapters
  presentation/  ← REST controllers, Request/Response DTOs, validation schemas
  contracts/     ← module-boundary interfaces
```

Generate **placeholders only** (no implementation logic).

## What to Generate

### 1. Domain Layer — in each module

Entities, Value Objects, Repository Interfaces, Domain Events.
No implementation.

### 2. Application Layer — in each module

Commands, Queries, Use Cases, DTOs, Validators.
No implementation.

### 3. Infrastructure Layer — in each module

Repository contracts, Provider contracts, Database adapters, Redis adapters.
No implementation.

### 4. Presentation Layer — in each module

REST controllers, Request DTOs, Response DTOs, Validation schemas (Zod).
No implementation.

### 5. Shared Kernel — `packages/shared/`

```
shared/
├── errors/        ← AppError types
├── events/        ← Domain event bus interface
├── result/        ← Result<T, E> monad
├── value-objects/ ← common VOs (EntityId, Email, etc.)
├── types/         ← global TS types
└── constants/     ← magic strings, enums
```

### 6. Database Package — `packages/database/`

Drizzle structure:

```
database/
├── schema/        ← one file per table
├── migrations/    ← drizzle-kit output
├── seeds/         ← test data
└── repositories/  ← repository interface + drizzle impl skeleton
```

Required schema files: users, user_goals, meal_events, meal_items, nutrition_snapshots, inference_evidence, inference_runs, achievements, user_achievements, streaks, daily_progress, coach_messages.

### 7. Domain Entities (interfaces only)

**Meal Domain**
- `MealEvent` — id, userId, source (photo/manual/voice), status, createdAt
- `MealItem` — id, mealEventId, foodRef, serving, confidence
- `MealEvidence` — id, mealItemId, type, payload, provenance
- `MealSource` — union: PhotoSource | ManualSource | VoiceSource
- `MealStatus` — pending, identified, confirmed, failed

**Inference Domain**
- `InferenceProvider` — interface for provider abstraction
- `InferenceOrchestrator` — interface for pipeline orchestration
- `InferenceResult` — candidates, confidence, model, duration
- `InferenceEvidence` — provenance, raw response ref, timing
- `ConfidenceScore` — value object (0–1)
- `ProviderType` — enum: openrouter, usda (future: google, openai, etc.)

**Nutrition Domain**
- `NutritionProvider` — interface
- `NutritionSnapshot` — calculated nutrients at confirmation time
- `NutritionFact` — calories, protein, fats, carbs, fiber, etc.
- `FoodReference` — catalog identifier + metadata
- `CatalogMatch` — source (USDA/OFF), confidence, foodId

**Coach Domain**
- `CoachMessage` — text, type (motivation/recommendation/habit), context
- `CoachRecommendation` — action suggestion
- `CoachContext` — user state snapshot for coaching decisions

**Gamification Domain**
- `Achievement` — id, key, name, description, criteria
- `UserAchievement` — userId, achievementId, unlockedAt
- `Streak` — userId, type, currentCount, longest, lastDate
- `XpTransaction` — userId, amount, reason, refId

**Progress Domain**
- `DailyProgress` — date, totals, goal adherence
- `WeeklyProgress` — weekStart, totals, trend
- `GoalProgress` — goalId, current, target, status

### 8. OpenRouter Integration (interfaces only)

```
inference/
├── InferenceProvider     ← interface
├── InferenceRequest      ← DTO
├── InferenceResponse     ← DTO
├── ModelProfile          ← config object
```

No SDK implementation. No provider-specific logic.

### 9. USDA Integration (interfaces only)

```
nutrition/
├── NutritionProvider     ← interface
├── USDAProvider          ← skeleton class (implements NutritionProvider)
├── FoodLookupRequest     ← DTO
├── FoodLookupResult      ← DTO
```

No implementation. No HTTP calls.

### 10. API Contracts (DTOs)

Generate request/response DTOs for:
- `POST /meals/photo`
- `POST /meals/manual`
- `POST /meals/voice`
- `GET /meals`
- `GET /meals/:id`
- `GET /progress`
- `GET /journey`
- `GET /achievements`
- `PATCH /goals`

### 11. Validation (Zod)

Generate Zod schemas only — one per endpoint.

### 12. Security Contracts (interfaces only)

- JWT token pair (access + refresh) contracts
- User session contract
- Rate limit contract
- Consent receipt contract

### 13. Configuration

- `.env` schema (typed env accessors)
- Config module
- Typed config accessors

### 14. Logging

- Pino integration skeleton
- Request logging middleware
- Correlation ID support (no implementation)

## Auth Note (test mode)

The mobile client has no auth yet. Generate auth infrastructure (JWT, refresh, session)
but the API must boot in **test mode** where sessions are handed out immediately
without verification. Gate with `TEST_MODE` flag in config. Auth feature is present
but gated — ready for production but inactive by default.

## Output Requirements

Deliver the following as generated files under `backend/mobile-api/`:

1. Complete folder tree
2. Complete file tree
3. Drizzle schema structure (type definitions, no migrations yet)
4. Module boundaries
5. Interface definitions
6. DTO definitions
7. Validation structure (Zod schemas)
8. Infrastructure contracts
9. Dependency rules
10. Import rules
11. Naming conventions
12. ADR decisions (`docs/adr/`)

## Constraints

- Do NOT generate business logic
- Do NOT generate business logic implementations
- Do NOT generate AI prompts or nutrition calculations
- Output must be architecture skeleton only — interfaces, types, DTOs, schemas, placeholders
- All output goes under `backend/mobile-api/`
- No cross-root imports into `mobile/`, `web/landing/`, or `contracts/`
- Node.js + TypeScript + Drizzle ORM + Zod + Pino + Redis
- Update `product-lifecycle.toml` lifecycle from `preselection` to `active` when skeleton is complete
