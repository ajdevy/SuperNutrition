# ADR 0005 — Activation evidence for the AD-37 transition

**Status:** accepted (2026-07-29)
**Context:** AD-27, AD-29, AD-33, AD-37

## Context

`backend/02-skeleton.md` instructs Stage 2 to set the lifecycle to `active` when
the skeleton is complete. Stage 1's plan flagged that a skeleton alone cannot
satisfy AD-37, which requires a pinned toolchain tuple, frozen dependencies and
lock state, a schema-conforming nonempty inventory, a real read-only quality
entrypoint, **nonzero tests and coverage denominators**, an artifact, owners, and
a deploy/rollback skeleton. Interfaces and DTOs with no tests satisfy none of the
evidence requirements.

## Decision

Produce the evidence rather than assert the state. Stage 2 delivers the skeleton
_and_ the activation requirements together:

- Pinned tuple and frozen `package-lock.json` (ADR 0001)
- `product-inventory.toml` enumerating every deployable unit, its tests,
  coverage denominator, artifact, owner, and quality task
- `quality-check` rewritten as a read-only active-state gate: typecheck, lint,
  test, coverage thresholds, lock integrity, contract validation
- 278 tests with real coverage denominators, over the AD-33 85% line and branch
  floors
- Deploy/rollback skeleton under `deploy/`

The tests are not coverage padding. They assert the invariants the architecture
depends on: the `TEST_MODE` fence, the AD-14 telemetry allowlist against every
forbidden field, `inference_runs` having no user foreign key, ungrounded
nutrition being rejected at the boundary, consent receipt completeness, and the
layer boundaries themselves.

## What activation does NOT claim

`active` means the product has a real, reviewed, enforced quality gate — not
that it is feature-complete or production-ready. Business logic is Stage 3.
Production remote inference additionally requires G-4 and the AD-24 checklist in
`docs/09` §9.11, none of which this transition touches.
