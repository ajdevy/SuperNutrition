# Story 1.2: Enforce monorepo boundaries and contracts

Status: review

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
- [x] Reconcile the local ownership manifest and CODEOWNERS declaration;
  external GitHub hosting enforcement is optional and is not a product gate.

## Evidence

- `scripts/verify-root-boundaries.sh`
- `scripts/root-boundary-negative-fixtures.sh`
- `scripts/verify-ci-policy.sh`
- `scripts/ci-policy-negative-fixtures.sh`
- `contracts/root-policy/v1/**`
- `contracts/mobile-api/v1/**`
- `scripts/verify-mobile-api-contract.sh`
- `scripts/mobile-api-contract-negative-fixtures.sh`

The local ownership and boundary policy is complete. GitHub CODEOWNERS/ruleset
enforcement remains an optional hosting configuration and does not block this
story or mobile lifecycle activation.

## Change Log

- 2026-07-28: Extended the read-only root-policy inventory with the CI
  fingerprint producer, verifier, and negative fixture. Protected hosting and
  CODEOWNERS/ruleset evidence remains external and unclaimed.
- 2026-07-28: Reconciled the protected executable inventory with the mobile
  Gradle wrapper and the preselection web/backend quality entrypoints. Added a
  distinct-principal ownership manifest and CODEOWNERS configuration.
- 2026-07-29: Removed the unavailable GitHub ruleset/protected-hosting gate from
  the product lifecycle. The ownership manifest remains a local policy
  declaration; GitHub enforcement is explicitly optional.
