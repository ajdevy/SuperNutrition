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
mobile lifecycle is `active` in `active-local` mode; no durable preference is
written. Native interaction execution remains separately device-gated on
Android, while Apple evidence is good-to-have.

The mobile manifest now declares provenance, independent context axes,
inline-override and session-undo paths, persistence scope, and forbidden
default tests for each allowlisted field. Journey evidence binds proposal and
decision IDs plus an explicit resolved-value-digest state.

The resolver now binds snapshot defaults to `bundled-safe-default`, requires
canonical-item confirmation before resolving either catalog proposal or atomic
standard serving, and the decision ledger rejects reuse of a decision ID with
a new revision. The contract fixture inventory and mobile validator cover the
complete forbidden-default set. The released provenance vocabulary no longer
contains an environment default path; CI executes the mobile JVM unit-test
task that covers the resolver and ledger.

## Independent Code Review

Claude CLI Opus/high review was rerun on 2026-07-29.

- [x] [Review][Patch] Local `qualityCheckJvmAndroid` and `qualityCheckApple`
  now execute `mobileUnitTests` and the read-only mobile policy plus contract
  validators, so interaction-policy coverage does not rely on hosted CI.
- [x] [Review][Patch] The interaction-policy contract negative fixture remains
  wired to the local validation path after removing hosted GitHub requirements.
- [ ] [Review][Defer] Included `build-logic` unit tests are not folded into the
  aggregate `mobileUnitTests` task; they remain available through the explicit
  `:build-logic:test` gate.
- [ ] [Review][Defer] Schema digest pinning and runtime provenance binding can
  be strengthened in a follow-up without changing the current fail-closed
  forbidden-default contract.
- [ ] [Review][Defer] Hosted pipeline tamper resistance is intentionally not a
  product gate because GitHub rulesets and trusted same-SHA evidence are
  optional hosting capabilities.
