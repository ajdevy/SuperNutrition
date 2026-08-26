# Stage 1: Backend Architecture & Design

> Architecture spine: _bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md
> Solution design: _bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/solution-design.md
> Agent guide: _bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md
> Epics (E4): _bmad-output/planning-artifacts/epics.md (line 635)
> Product lifecycle: backend/mobile-api/product-lifecycle.toml
>
> Next stage: backend/02-skeleton.md
> Stage type: architecture-only (no code generation)

## Role

You are acting as a Principal Staff Engineer, Backend Architect, Domain Architect and AI Systems Designer.

Your task is NOT to generate random code.
Your task is to design and scaffold the backend foundation for SuperNutrition MVP v1 according to the architecture below.

You must challenge bad assumptions, prevent overengineering, and preserve long-term scalability.

## Project Context

SuperNutrition is an AI-powered nutrition companion.
The product goal is not calorie counting.
The product goal is building healthy eating habits through:
- meal tracking
- nutrition analysis
- coaching
- motivation
- achievements
- progress tracking

The primary KPI is: **Daily Meal Logging Retention**

The product is centered around **Meal Events**.
Photos are only one way to create a Meal Event.

## Core Product Flows

Users can create Meal Events via:
- Photo Scan
- Manual Entry
- Voice Entry

All three methods must converge into a single domain entity: **Meal Event**

### Product Screens

**Screen 1 — Meal Capture**
Features: photo, manual input, voice input
Result: identified foods, nutrition, coaching message, XP reward

**Screen 2 — Journey**
Features: meal history, achievements, streaks, failures

**Screen 3 — Progress**
Features: calories, protein, fats, carbs, goal adherence, trends

## Architectural Principles

**Principle 1:** Meal Event is the core entity. All domains revolve around Meal Events.

**Principle 2:** User is the authority.
Source of truth hierarchy: User Confirmation > USDA Nutrition Data > AI Inference.
AI suggestions are never final truth.

**Principle 3:** No durable raw photos.
After inference: store results, delete photo. Never store raw meal images.

**Principle 4:** AI providers are replaceable.
Business logic must never depend on OpenAI, Gemini, Claude, or OpenRouter directly. Use abstractions.

**Principle 5:** Nutrition data must be grounded.
AI identifies food. Nutrition comes from catalog providers.
MVP source: USDA. Future: USDA + Open Food Facts + Fallback Resolver.

## Technology Stack

| Layer | Choice |
|-------|--------|
| Runtime | Node.js + TypeScript |
| Database | PostgreSQL |
| ORM | Drizzle ORM |
| Cache | Redis |
| Architecture | DDD Modular Monolith |
| Transport | REST API |
| Auth | JWT + Refresh Tokens |

## AI Infrastructure

Models accessed through **OpenRouter**. Do NOT couple the system to any specific model.
Create `InferenceProvider` interface.

### AI Pipeline — Inference Orchestrator

Responsibilities: provider selection, confidence routing, retry strategy, telemetry.

**Pass 1:** Fast identification. Goal: identify foods, estimate confidence.

**Pass 2:** Only if confidence below threshold. Additional context: user goal, country, meal type, previous meals.

**Confidence Routing:**
- confidence >= threshold → accept
- confidence < threshold → second pass
- still low confidence → require confirmation

### OpenRouter Strategy

Design support for:
- `google/gemini-2.5-flash`
- `openai/gpt-4o-mini`
- `openai/gpt-4o`
- `anthropic/claude-sonnet`

Model selection must be configurable. No hardcoded models in business logic.

## Required Domains (DDD)

Create DDD boundaries for: Auth, Users, Goals, Meals, Inference, Nutrition, Catalog, Coach, Gamification, Progress.

### Meal Domain

Entities: `MealEvent`, `MealItem`, `MealEvidence`
Support sources: photo, manual, voice.

### Nutrition Domain

Contracts: `NutritionProvider` interface. MVP: `USDAProvider`. Extension points for `OpenFoodFactsProvider`.

### Coach Domain

Responsibilities: motivational messages, recommendations, habit reinforcement.
Coach never modifies facts. Coach interprets facts.

### Gamification Domain

MVP achievements: First Meal, 3 Day Streak, 7 Day Streak, Healthy Week, Protein Hero.
Create scalable achievement engine. Avoid hardcoded logic.

### Progress Domain

Support: Daily aggregates, Weekly aggregates, Goal adherence, Trend calculations, Dashboard queries.

## Database Design

Produce: ERD, Tables, Indexes, Relations, Migration strategy, Drizzle schemas.

Required tables:
- users
- user_goals
- meal_events
- meal_items
- nutrition_snapshots
- inference_evidence
- achievements
- user_achievements
- streaks
- daily_progress
- coach_messages
- inference_runs (AI telemetry)

### AI Telemetry: `inference_runs`

Store: model, confidence, duration, token usage, estimated cost, success, created_at.
Goal: Future model benchmarking.

## API Design

Generate REST contracts for: Auth, Goals, Meals, Journey, Progress, Achievements, Nutrition.

Provide: Request DTOs, Response DTOs, Validation strategy, Error format, Pagination strategy, Versioning strategy.

## Security

Design: JWT flow, Refresh flow, Rate limiting, Upload limits, Consent flow, Photo disposal flow, PII protection, Secrets management, OpenRouter key isolation.

## E4 Epic Mapping

Map the architecture to:
- E4.1 — Remote Consent and Transfer
- E4.2 — Remote Mobile Adapter
- E4.3 — First Party Gateway Boundary

Produce: Responsibilities, Components, Risks, Acceptance Criteria, Implementation Order.

## Architectural Alignment (existing project)

This backend runs at `backend/mobile-api/` (product root, currently in `preselection` lifecycle).
It integrates with the KMP mobile app through `contracts/mobile-api/` — versioned, language-neutral releases.

Key ADs that constrain backend design:
- **AD-13** (line 167): mobile calls only first-party gateway; provider secrets server-side
- **AD-21** (line 225): authenticated gateway with short-lived tokens, replay protection
- **AD-24** (line 244): production operations ownership, SLOs, kill switch
- **AD-8** (line 119): ephemeral image lifecycle, no raw photo retention
- **AD-14** (line 174): allowlisted telemetry, no sensitive data in logs

Mobile-side epics E4.1–E4.3 define consent, mobile adapter, and gateway boundary.
This backend implements the server side of those boundaries.

### Auth note (test mode)
The mobile client currently has no auth integration. The backend should include auth infrastructure (JWT, refresh) but run in **test mode** where sessions are handed out immediately without verification. The auth feature is present but gated behind a `TEST_MODE` flag.

## Deliverables

Generate the following documents (no application code):

1. System Architecture Overview
2. Domain Architecture (DDD boundaries)
3. Folder Structure (`backend/mobile-api/`)
4. DDD Module Design
5. Database Design (ERD, tables, indexes, Drizzle schemas)
6. REST API Specification
7. OpenRouter Integration Design
8. USDA Integration Design
9. Security Design
10. E4 Implementation Plan (E4.1–E4.3 mapping)
11. Technical Backlog
12. Future Scaling Strategy
13. Risks and Tradeoffs

## Constraints

- Do NOT generate application code
- Do NOT generate controllers, repositories, or implementations
- This stage is architecture and planning only
- When uncertain, choose: simplicity, maintainability, replaceability, observability over premature optimization
- Output goes to `backend/mobile-api/docs/` unless specified otherwise
- Next stage (02-skeleton.md) will generate the actual project skeleton from these artifacts
