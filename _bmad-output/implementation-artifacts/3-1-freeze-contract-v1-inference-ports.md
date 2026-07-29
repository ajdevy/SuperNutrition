---
baseline_commit: 91c8f59f6963c3ba91e3970baabb960c668f8688
---

# Story 3.1: Freeze contract-v1 inference ports

Status: done

## Story

As the contract owner,
I want versioned request, capability, candidate, abstention, and failure types,
so that local, remote, gateway, and evaluation implementations are replaceable.

## Acceptance Criteria

1. Given the contract module is pure KMP, when contract-v1 is published, then it
   contains no platform, UI, DI, provider, or nutrition-catalog dependency.
2. Canonical JSON, in-memory fixtures, unknown-field handling, and compatibility
   tests are published under `contracts/`.
3. No result shape contains numeric nutrition from an Engine.
4. The shared `PhotoRef`/attempt seam is owned by the inference contract and the
   photo-analysis domain consumes it through an inward dependency.

## Tasks / Subtasks

- [x] Add the pure KMP `domain:inference` module with the contract-v1 request,
  capability, evidence, abstention, failure, provenance, and engine port types.
- [x] Add canonical JSON fixtures and immutable v1 compatibility metadata under
  `contracts/inference/v1`, including a release digest and negative validation.
- [x] Add the versioned, allowlisted operational telemetry schema and golden
  fixture without raw photo, user-label, or transport-body fields.
- [x] Move the opaque `PhotoRef`/`AttemptId` seam into the contract owner while
  preserving photo-analysis source compatibility through typealiases.
- [x] Register the module and coverage denominator, validate the contract in
  focused JVM tests and read-only repository gates, then record evidence.

## Dev Notes

- Authority: E3.1 in `_bmad-output/planning-artifacts/epics.md`, AD-2, AD-3,
  AD-17, AD-21, and the implementation-agent guide.
- `domain/inference` owns only Engine contract/evidence vocabulary,
  serialization, compatibility, canonical fixtures, and telemetry schema. It
  must not own routing, consent, provider adapters, UI, or nutrition math.
- Use the checked-in Kotlin/KMP and serialization catalog versions. Keep the
  result model free of calories, macros, micronutrients, and nutrition values.
- Contract releases under `contracts/` are language-neutral data only; source
  and executable validators remain under the repository scripts boundary.

## Dev Agent Record

### Implementation Plan

1. Write red contract and compatibility tests.
2. Implement the pure KMP port and serialized evidence model.
3. Publish and validate the language-neutral v1 release, then update mobile
   inventory/ownership and CI catalog fingerprints.
4. Run focused tests, contract negative fixtures, policy gates, and graphify.

### Debug Log

- 2026-07-28: E3.1 existed only in `epics.md`; no story file, domain module,
  inference release, or inference tests were present in the isolated worktree.
- 2026-07-28: Full quality initially exposed the public typealias seam and
  missing downstream lock entries; changed the contract dependency to `api`
  and regenerated only the affected Gradle lock states.

### Completion Notes

Implemented contract-v1 as pure KMP and published the immutable language-neutral
release under `contracts/inference/v1`. The release contains typed capability,
candidate, abstention, failure, provenance, request, engine, and telemetry
allowlist models; it has no numeric nutrition fields or raw payload telemetry.

Validation evidence:

- `:domain:inference:jvmTest`: 13 tests passed, including validation negative
  paths and canonical fixture round trips.
- `mobileUnitCoverage`: passed with 97.51% line and 85.39% branch coverage
  across the host production modules.
- `qualityCheckJvmAndroid`: passed; static analysis and host tests passed while
  the scaffold remained fail-closed without a production quality claim.
- `verifyMobilePolicy --dependency-verification=strict`: passed.
- Inference, root-boundary, CI-fingerprint, and interaction-policy
  positive/negative fixture suites: passed.
- `graphify update .`: completed.

The final base-aware review also recorded that `scripts/verify-ci-policy.sh`
still requires the former six-job workflow and `merge_group`, while live
`origin/main` intentionally carries the reduced `mobile-unit-coverage`
workflow. This pre-existing base mismatch is outside E3.1; the PR adds only
the inference contract checks to the live workflow.

### Review Findings

- [x] [Review][Patch] Restore `epic-3` and the story status beneath the
  `development_status` YAML mapping [sprint-status.yaml:48-49].
- [x] [Review][Patch] Reject duplicate candidate IDs, non-finite confidence,
  blank assumption message keys, and incomplete ready capability metadata
  [InferenceContract.kt:75-90, 147-155, 201-205, 221-225].
- [x] [Review][Patch] Keep cancellation as telemetry/reducer state rather
  than a terminal Engine failure code, matching the cancellation contract
  [InferenceContract.kt:302-315].
- [x] [Review][Patch] Validate every canonical fixture's exact fields, enum
  values, nested candidate/provenance structure, duplicate keys, finite
  numbers, and minified JSON [verify-inference-contract.sh:78-220].
- [x] [Review][Patch] Add capability JSON codec, golden fixture, and unknown
  field compatibility coverage [InferenceContract.kt:377-390,
  InferenceContractTest.kt:111-142].

The reviewers also raised ABI, lockfile, architecture-inventory, and CI
fingerprint concerns. Those were dismissed after checking the actual diff and
repository policy: the story requires source-compatible Kotlin typealiases,
affected lockfiles are present, architecture boundaries are group-based, and
same-SHA fingerprints already bind changed validator source through the commit
identity. The ownership-slice observation is a process note; this story's
required contract release and inventory/policy registration remain one PR.

### File List

- `_bmad-output/implementation-artifacts/3-1-freeze-contract-v1-inference-ports.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `contracts/inference/v1/{schema.toml,compatibility-matrix.toml,RELEASE.md}`
- `contracts/inference/v1/fixtures/{capability-ready.json,candidate-result.json,abstention-result.json,failure-result.json,telemetry-event.json}`
- `mobile/domain/inference/{build.gradle.kts,gradle.lockfile,src/commonMain/**,src/commonTest/**}`
- `mobile/domain/photo-analysis/{build.gradle.kts,gradle.lockfile,src/commonMain/**}`
- Affected downstream Gradle locks for photo-analysis feature/platform and app
  consumers, plus `mobile/gradle/verification-metadata.xml`.
- `mobile/config/quality/{module-matrix.toml,coverage.toml,evidence-bindings.toml}`
- `contracts/root-policy/v1/boundaries.toml`
- `.github/workflows/ci-required.yml`
- `scripts/{verify-inference-contract.sh,inference-contract-negative-fixtures.sh,emit-ci-fingerprint.sh}`

### Change Log

- 2026-07-28: Created the implementation story from the authoritative E3.1
  epic and architecture requirements.
- 2026-07-28: Implemented and validated the contract-v1 inference ports,
  immutable fixtures, telemetry allowlist, compatibility digest, ownership
  seam, module inventory, CI validation, and lock state.
