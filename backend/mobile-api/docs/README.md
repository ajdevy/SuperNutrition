# SuperNutrition Mobile API — Architecture (Stage 1)

Design artifacts produced by `backend/01-architecture.md`. Architecture and
planning only: this directory contains no application code, and the product
remains in `preselection` lifecycle.

## Documents

| # | Document | Purpose |
| --- | --- | --- |
| 1 | [System Architecture Overview](01-system-architecture-overview.md) | Two-surface topology, request paths, deployment shape |
| 2 | [Domain Architecture](02-domain-architecture.md) | DDD boundaries, ownership, cross-domain rules |
| 3 | [Folder Structure](03-folder-structure.md) | Physical layout of the product root |
| 4 | [DDD Module Design](04-ddd-module-design.md) | Per-module layering, ports, dependency rules |
| 5 | [Database Design](05-database-design.md) | ERD, tables, indexes, migrations, Drizzle schema plan |
| 6 | [REST API Specification](06-rest-api-specification.md) | Both surfaces, DTOs, errors, pagination, versioning |
| 7 | [OpenRouter Integration Design](07-openrouter-integration.md) | Provider abstraction, orchestrator, two-pass routing |
| 8 | [USDA Integration Design](08-usda-integration.md) | Catalog grounding, package curation, authority limits |
| 9 | [Security Design](09-security-design.md) | Auth, test mode, rate limits, consent, photo disposal, secrets |
| 10 | [E4 Implementation Plan](10-e4-implementation-plan.md) | E4.1–E4.3 mapping, order, acceptance criteria |
| 11 | [Technical Backlog](11-technical-backlog.md) | Sequenced work, including approvals this stage requires |
| 12 | [Future Scaling Strategy](12-future-scaling-strategy.md) | Growth stages and their triggers |
| 13 | [Risks and Tradeoffs](13-risks-and-tradeoffs.md) | Accepted risks, rejected alternatives, open questions |

## Read this first

Three decisions shape everything else. They are argued in document 1 and
restated wherever they bind.

1. **This product root serves two distinct API surfaces**, not one. The
   contract-bound inference gateway and the product REST API have different
   consumers, versioning rules, and threat models.
2. **This backend is not the authority on nutrition numbers.** AD-7 and AD-19
   place that authority in the versioned on-device catalog. The backend records
   grounded results; it does not originate or overwrite them.
3. **`TEST_MODE` is a fenced development capability**, not a deployment mode. It
   cannot start in production and cannot authorize a real remote transfer.

## Governing sources

These documents are subordinate to, and cite, the following. Where this stage's
brief and the architecture spine disagree, the conflict is named explicitly in
document 13 rather than silently resolved.

- Architecture spine — `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
- Solution design — same directory, `solution-design.md`
- Market processing policy — same directory, `market-processing-policy.md`
- Epics E4 — `_bmad-output/planning-artifacts/epics.md`
- Contract releases — `contracts/mobile-api/v1`, `contracts/inference/v1`, `contracts/interaction-policy/v1`
- Stage brief — `backend/01-architecture.md`; next stage — `backend/02-skeleton.md`

## Status

Stage 1 is complete as design. No production source, dependency manifest, lock
state, runtime, or deployable artifact exists yet, and `product-lifecycle.toml`
correctly still reads `preselection`. Activation is an atomic AD-37 story
scheduled in document 11, not a side effect of Stage 2.
