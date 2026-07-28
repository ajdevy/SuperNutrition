# Story 1.5: Establish the low-friction interaction policy

Status: review

## Story

As a product and UX owner, I want safe defaults to reduce routine interaction
without hiding consent, uncertainty, jurisdiction, or provenance.

## Acceptance Criteria

1. The immutable interaction-policy/v1 release defines the closed allowlist and
   lifecycle/compatibility rules.
2. Mobile consumes the release through a protected policy manifest.
3. Remote consent, provider routing, uncertain identity, and medical/allergen
   conclusions are never defaultable.

## Tasks

- [x] Add language-neutral v1 schema, compatibility metadata, and fixtures.
- [x] Add the read-only contract validator, content digest check, and negative
  forbidden-default fixture.
- [x] Add mobile interaction-policy manifest with visible/editable/reversible
  defaults and independent context axes.
- [x] Implement covered `mobile/core/config` proposal/decision types with
  idempotent session-scoped overrides and invalidation.
- [x] Bind journey-level tap/scroll/field/choice budgets and the Android/iOS
  accessibility contracts to the photo-analysis scenario; native execution
  remains pending until the platform gaps are attested.

## Evidence

- `contracts/interaction-policy/v1/**`
- `scripts/verify-interaction-policy-contract.sh`
- `scripts/interaction-policy-contract-negative-fixtures.sh`
- `mobile/config/quality/interaction-policy.toml`
- `mobile/config/quality/positive-scenarios.toml`
- `mobile/config/quality/journeys.toml`
- `mobile/config/quality/platform-contracts.toml`
- `mobile/core/config/src/commonMain/**/InteractionLifecycle.kt`

Production remote/default-resolution remains bounded by the policy and the
mobile lifecycle remains scaffold-pending; no durable preference is written.

The mobile manifest now declares provenance, independent context axes,
inline-override and session-undo paths, persistence scope, and forbidden
default tests for each allowlisted field. Journey evidence binds proposal and
decision IDs plus an explicit resolved-value-digest state.
