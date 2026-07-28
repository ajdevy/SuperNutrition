# Story 1.2: Enforce monorepo boundaries and contracts

Status: in-progress

## Story

As a repository-policy owner, I want root product boundaries and immutable
contract releases enforced, so mobile, landing, and backend remain independent.

## Acceptance Criteria

1. Only `mobile/`, `web/landing/`, `backend/mobile-api/`, and data-only
   `contracts/` are product roots.
2. Root and contract validation is read-only and fails on executable contract
   source or malformed product-root paths.
3. Mobile API v1 contains immutable compatibility metadata for producer/consumer
   skew without importing product source.

## Tasks

- [x] Add root-policy v1 and the read-only root boundary validator.
- [x] Add mobile-api v1 schema and compatibility matrix fixtures.
- [x] Add the read-only v1 schema/fixture/digest validator and negative skew
  fixture; CI validates both forward and backward producer/consumer pairs.
- [x] Add negative CI fixtures for cross-product source/build/cache edges.
- [ ] Reconcile the final protected ownership/ruleset evidence.

## Evidence

- `scripts/verify-root-boundaries.sh`
- `scripts/root-boundary-negative-fixtures.sh`
- `scripts/verify-ci-policy.sh`
- `scripts/ci-policy-negative-fixtures.sh`
- `contracts/root-policy/v1/**`
- `contracts/mobile-api/v1/**`
- `scripts/verify-mobile-api-contract.sh`
- `scripts/mobile-api-contract-negative-fixtures.sh`

The story remains in progress until the protected hosting/ruleset gate is
available; local boundary and CI policy fixture suites are green.

## Change Log

- 2026-07-28: Extended the read-only root-policy inventory with the CI
  fingerprint producer, verifier, and negative fixture. Protected hosting and
  CODEOWNERS/ruleset evidence remains external and unclaimed.
- 2026-07-28: Reconciled the protected executable inventory with the mobile
  Gradle wrapper and the preselection web/backend quality entrypoints. Added a
  distinct-principal ownership manifest and CODEOWNERS configuration; the
  manifest remains explicitly pending hosting/team provisioning, so no
  external enforcement claim is made.
