# 3. Folder Structure

## 3.1 Product root

Everything below lives under `backend/mobile-api/`. Nothing outside it is
written, imported, or depended on, other than pinned immutable contract releases
consumed as data (AD-36).

```text
backend/mobile-api/
├── apps/
│   └── api/                        REST entrypoint — the only deployable
│       ├── src/
│       │   ├── main.ts             composition root; the only place adapters are chosen
│       │   ├── server.ts           HTTP server assembly, middleware order
│       │   ├── config/             typed env schema and accessors
│       │   ├── middleware/         correlation ID, auth, rate limit, error mapper, logging
│       │   ├── routes/             surface mounting: /gw/v1 and /api/v1
│       │   └── modules/            the ten DDD modules — see §3.2
│       ├── test/                   cross-module integration and contract tests
│       ├── package.json
│       └── tsconfig.json
├── packages/
│   ├── database/                   Drizzle schema, migrations, seeds, repository impls
│   │   ├── src/
│   │   │   ├── schema/             one file per table
│   │   │   ├── migrations/         drizzle-kit output, checked in, forward-only
│   │   │   ├── seeds/              achievements catalog, fixture markets, dev data
│   │   │   ├── repositories/       Drizzle implementations of domain ports
│   │   │   └── client.ts           pool, transaction helper
│   │   └── package.json
│   ├── shared/                     shared kernel — see §3.3
│   │   └── src/{errors,events,result,value-objects,types,constants}/
│   └── contracts/                  cross-module boundary interfaces (internal)
│       └── src/{meals,progress,gamification,nutrition,inference,users}/
├── contracts-generated/            product-local bindings from pinned contract releases
│   └── mobile-api/v1/              generated; never edited; never written back
├── config/                         runtime and deploy policy (added at activation)
│   ├── model-profiles.toml         OpenRouter model roster and routing thresholds
│   └── telemetry-allowlist.toml    AD-14 field allowlist
├── docs/                           this directory — non-executable Markdown only
│   └── adr/                        ADRs, added in Stage 2
├── docker-compose.yml              local Postgres + Redis
├── package.json                    workspace root for THIS product only
├── tsconfig.base.json
├── .env.example
├── quality-check                   lifecycle-aware quality entrypoint
├── product-lifecycle.toml          preselection → active at the atomic AD-37 story
└── product-inventory.toml          created at activation; nonempty only when active
```

### Notes on specific choices

**`apps/api` is the only deployable.** Workers, if they arrive, are separate
entries under `apps/` with their own inventory rows — not a flag on this one.

**`contracts-generated/` is generated, never authored.** It holds product-local
TypeScript bindings derived from the pinned `contracts/mobile-api` release.
Generation reads the release as data; it never writes back and never publishes a
replacement (AD-36). The directory is checked in so builds are reproducible
without network access, and a CI check re-generates and diffs it.

**`packages/contracts` vs root `contracts/`.** Different things, and the naming
collision is worth stating once: `packages/contracts` holds *internal*
module-boundary interfaces for this service. Root `contracts/` holds the
*cross-product* immutable releases. The former is ours to change freely; the
latter is owned by an independent principal.

**No `src/` at the product root.** The spine's structural seed sketches
`backend/mobile-api/src/`. The workspace layout above supersedes it, because a
monorepo-within-a-product-root needs the `apps` / `packages` split to keep the
database package's Drizzle dependency out of the domain layer's dependency
graph. This is a refinement, not a boundary change — `product-inventory.toml`
enumerates the real paths at activation.

## 3.2 Module layout

Every module under `apps/api/src/modules/` has the same five directories. The
uniformity is the point: a reader who knows one module knows all ten.

```text
modules/<module>/
├── application/       commands, queries, use cases, application DTOs, validators
├── domain/            entities, value objects, port interfaces, domain events
├── infrastructure/    adapters implementing domain ports
├── presentation/      routes, controllers, request/response DTOs, Zod schemas
├── contracts/         this module's published boundary interface
└── index.ts           the module's ONLY public export surface
```

The ten modules:

```text
modules/
├── auth/           JWT, refresh lineage, attestation, session, TEST_MODE fence
├── users/          profile, preferences, consent receipts, erasure orchestration
├── goals/          versioned targets and validity periods
├── meals/          MealEvent aggregate — the core
├── inference/      InferenceProvider port, orchestrator, telemetry
├── nutrition/      NutritionProvider port, facts, snapshot construction
├── catalog/        food lookup, canonical IDs, package metadata
├── coach/          messages and recommendations — read-only over facts
├── gamification/   achievements, streaks, XP; data-driven rule engine
└── progress/       daily and weekly aggregates, adherence, trends
```

### Gateway placement

Surface A is not an eleventh module. It is a route group under
`apps/api/src/routes/gateway/` that composes `auth`, `inference`, and the market
policy resolver. This placement carries a property worth having: **the gateway
route group imports no repository and no database package**, so its inability to
durably store user content is a dependency-graph fact rather than a review
convention. Document 4 §4.9 specifies the lint rule that enforces it.

## 3.3 Shared kernel

```text
packages/shared/src/
├── errors/          AppError hierarchy, error codes, HTTP mapping table
├── events/          DomainEvent, EventBus port, handler registration
├── result/          Result<T, E> — explicit failure without exceptions
├── value-objects/   EntityId, Email, LocalDate, Instant, Money-free quantities
├── types/           branded primitives, utility types
└── constants/       enums, contract version strings, telemetry field names
```

**Admission rule.** `shared` is for things with no domain meaning. A helper that
knows what a meal is belongs in `meals`, not here. Shared kernels rot by
accretion, and the only defence is a rule applied at review time: *if removing
this from `shared` would require it to know a domain concept, it was never
shared.*

`shared` may not import from `packages/database`, from any module, or from
`contracts-generated/`.

## 3.4 Dependency rules

Enforced mechanically — see document 4 §4.9 for the tooling.

| From | May import |
| --- | --- |
| `modules/*/domain` | `shared` only |
| `modules/*/application` | own `domain`, `shared`, other modules' `contracts` |
| `modules/*/infrastructure` | own `domain`, own `application`, `shared`, `packages/database`, `contracts-generated` |
| `modules/*/presentation` | own `application`, `shared`, own `contracts` |
| `modules/*/contracts` | `shared` only |
| `routes/gateway/**` | `auth`, `inference`, `shared` — **never** `packages/database` |
| `packages/shared` | nothing internal |
| `packages/database` | `packages/shared` |
| `apps/api/src/main.ts` | everything (composition root) |
| anything | **never** `../../../mobile`, `../../../web/landing`, or root `contracts/` at runtime |

The last row is the AD-36 product boundary. Root `contracts/` is read at build
time by the binding generator and at no other moment.

## 3.5 Naming conventions

| Kind | Convention | Example |
| --- | --- | --- |
| Directories | kebab-case | `meal-events/` |
| Source files | kebab-case | `meal-event.repository.ts` |
| Types, classes | PascalCase | `MealEvent`, `InferenceOrchestrator` |
| Functions, variables | camelCase | `confirmMealEvent` |
| Constants, enum members | SCREAMING_SNAKE_CASE | `MAX_UPLOAD_BYTES` |
| Database tables | plural snake_case | `meal_events` |
| Database columns | snake_case | `created_at` |
| Ports (interfaces) | noun ending in role | `MealEventRepository`, `InferenceProvider` |
| Adapters | implementation prefix + port | `DrizzleMealEventRepository`, `OpenRouterInferenceProvider` |
| Use cases | verb phrase | `ConfirmMealEvent`, `GetDailyProgress` |
| Domain events | past tense | `MealEventConfirmed`, `AchievementUnlocked` |
| Result types | suffix `Result` | `InferenceResult` |
| Failure types | suffix `Failure` | `TransferFailure` |
| Zod schemas | suffix `Schema` | `CreateMealRequestSchema` |
| Test files | mirror source, `.test.ts` | `meal-event.repository.test.ts` |

Port and result naming deliberately mirrors the spine's convention table so a
reader crossing the client/server boundary meets the same vocabulary.

## 3.6 What is absent at Stage 1, and why

Stage 1 produces design only, so the tree above is a specification, not a state.
At the end of this stage the product root contains `product-lifecycle.toml`,
`quality-check`, and `docs/`. Nothing else.

Stage 2 creates the skeleton. Note a constraint that Stage 2's brief does not
mention: **the skeleton alone cannot flip the lifecycle to `active`.** AD-37
requires a pinned toolchain tuple, frozen dependencies and lock state, a
schema-conforming nonempty inventory, a real read-only quality entrypoint,
*nonzero tests and coverage denominators*, an artifact, owners, and a
deploy/rollback skeleton. Interfaces and DTOs with no tests satisfy none of the
evidence requirements. Document 11 sequences activation as its own atomic story.
