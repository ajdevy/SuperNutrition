---
baseline_commit: 84fc6d7e2128b01a45a9b7cec8b37755d04120e6
---

# Story 3.3: Implement local iOS inference

Status: in-progress

## Story

As an iOS local-inference owner,
I want an iOS-specific Local Engine behind the same contract,
so that iOS devices receive the same user-facing evidence vocabulary without
an implicit remote transfer.

## Acceptance Criteria

1. `mobile/inference/local-ios` implements `PhotoInferenceEngine` contract-v1
   and depends only on `domain:inference`, `domain:photo-analysis`, and approved
   core ports. It never imports Android, Compose, Koin, remote inference, or
   nutrition catalog code.
2. Ready, unavailable, downloading, unsupported, resource-budget, and
   temporarily-unavailable capability states map to the shared vocabulary;
   unavailable analysis fails closed without reading photo bytes.
3. A ready attempt reads normalized content through the existing
   `PhotoSessionStorePort`, invokes only an injected iOS runtime, returns local
   provenance and evidence, and performs no network transfer.
4. Model provenance records immutable model version, integrity identity,
   compatibility tier, calibration version, ownership, and rollback semantics.
   App-managed packages are checksum/rollback aware; platform-managed models
   never claim app rollback. No concrete runtime or model is selected before
   the signed G-3 device benchmark.
5. Cancellation is rethrown, runtime resources are released, and a late result
   after cancellation or replacement cannot publish a terminal result.
6. Common/JVM tests cover mapping, no-network behavior, input lease/digest,
   provenance, cancellation, late results, and invalid model/resource state.
   Apple compilation and the approved physical iOS fixture/host lanes are
   executed when signing/device prerequisites are available; simulator output
   is never reported as physical-device evidence.

## Tasks / Subtasks

- [x] Create `mobile/inference/local-ios` with KMP/Apple targets, lock state,
  inward dependencies, module inventory, and coverage denominator.
- [x] Define an injectable iOS runtime/model boundary with fail-closed
  capability and ownership semantics; defer runtime/model selection to G-3.
- [x] Implement `LocalIosInferenceEngine` over contract-v1 with normalized
  photo lease validation, provenance, typed outcomes, cancellation, cleanup,
  and late-result suppression.
- [x] Add deterministic tests first and implementation second; cover all
  contract outcomes, capability faults, integrity/compatibility/resource
  rejection, and rollback ownership.
- [x] Add explicit forbidden-artifact/log assertions for raw photo bytes,
  filenames, paths, labels, prompts, responses, secrets, and headers.
- [x] Run focused JVM/Apple tests, Detekt, policy/lock validation, and the
  `iosArm64TestBinaries` device-target compile. The Intel x64 compatibility
  lane is blocked by an untrusted Kotlin serialization klib; the signed
  physical Xcode and arm64 fixture lanes are now recorded separately. Record
  G-3 honestly as the remaining production gate.

## Dev Notes

### Boundaries and reuse

- `mobile/domain/inference` is the sole owner of contract-v1 types and canonical
  fixtures. Do not add iOS-specific DTOs to that module or edit its fixtures.
- `mobile/domain/photo-analysis` owns the existing attempt-scoped
  `PhotoSessionStorePort` and normalized `NormalizedPhotoInput`; do not create
  a second content store or accept `UIImage`, `NSURL`, paths, or raw platform
  objects in common code.
- `mobile/inference/local-ios` is independent from local Android and remote
  adapters. Constructor injection is required; Koin selection belongs only in
  the app composition root.

### Architecture and safety

- Follow AD-2, AD-3, AD-6, AD-9, AD-10, AD-11, AD-22, and G-3 in
  `ARCHITECTURE-SPINE.md`. Local inference is identification evidence only;
  numeric nutrition never appears in an engine result.
- Normalized input invariants are enforced by `NormalizedPhotoInput`; the
  adapter must additionally verify the opaque photo ref and content digest
  before invoking the runtime.
- No photo bytes, labels, prompts, paths, model responses, headers, secrets,
  or transport bodies may appear in logs, telemetry, exceptions, or fixtures.
- A signed G-3 artifact must select OS/device tiers, runtime, model,
  download policy, and latency/memory/thermal/battery budgets before any
  production runtime dependency or model asset is added.

### Validation

- On this Apple-silicon host, `iosSimulatorArm64Test` is the canonical Apple
  test lane; `iosX64Test` is compatibility-only when explicitly enabled.
- Physical iOS evidence requires both `iosArm64TestBinaries` executed through
  `scripts/run-physical-ios-fixtures.sh` and the signed Xcode acceptance scheme
  on the real iPhone. Use `mobile/REAL_DEVICE_TESTING.md` and emit evidence
  only from a clean, digest-bound run.
- Aggregate `qualityCheckJvmAndroid` has been rerun and passes locally; the
  remaining iOS-specific gaps are the signed G-3 artifact and physical
  Xcode/device prerequisites.

## Interaction-budget attestation

Non-UI story. No taps, scrolls, fields, choices, defaults, consent, or
navigation behavior changes.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E3.3]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-2, AD-3, AD-6, AD-9, AD-10, AD-11, AD-22, G-3]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceContract.kt`]
- [Source: `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisDomain.kt`]
- [Source: `mobile/REAL_DEVICE_TESTING.md`]

## Dev Agent Record

### Agent Model Used

GPT-5 / Codex

### Debug Log References

### Completion Notes List

- Added the independent `local-ios` KMP adapter with contract-v1 mapping,
  immutable app/platform model provenance, digest-bound photo lease reads,
  cancellation-safe generation tokens, and runtime cleanup.
- JVM tests pass with 98.6111% line and 86.8421% branch coverage; Detekt and
  strict lock/policy validation pass. `iosArm64TestBinaries` completes.
- Physical iOS evidence now passes on the paired iPhone 14 Pro using the local
  Xcode account/team: the signed Xcode acceptance scheme passed 5/5 tests and
  the arm64 fixture runner passed 5/5 tests (3 normalization, 2 bridge).
  Evidence is bound to commit `1a22bff1`, the current framework/app and fixture
  digests, device `00008120-0012295934E0C01E`, Xcode 26.0.1, and Gradle 9.5.0.
  G-3 remains open because these are bridge/normalization correctness lanes,
  not a signed runtime/model latency-memory-thermal-battery benchmark.
- 2026-07-31 preliminary G-3 spike: the separate research harness completed
  50/50 inferences on the paired iPhone 14 Pro and recorded latency, resident
  memory, and thermal evidence. iOS battery/energy evidence and owner
  acceptance remain open in
  `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`.

### File List

- `mobile/inference/local-ios/build.gradle.kts`
- `mobile/inference/local-ios/gradle.lockfile`
- `mobile/inference/local-ios/src/commonMain/kotlin/com/supernutrition/inference/localios/LocalIosInference.kt`
- `mobile/inference/local-ios/src/commonTest/kotlin/com/supernutrition/inference/localios/LocalIosInferenceEngineTest.kt`
- `mobile/config/quality/module-matrix.toml`
- `mobile/config/quality/coverage.toml`
- `mobile/config/quality/evidence-bindings.toml`

### Change Log

- 2026-07-31: Created implementation-ready E3.3 context with G-3 and
  physical-device guardrails.

### Review Findings

- [x] [Review][Patch] Lease validation, native cancellation, malformed-output
  handling, and exception sanitization are now fail-closed in the iOS adapter
  [mobile/inference/local-ios/src/commonMain/kotlin/com/supernutrition/inference/localios/LocalIosInference.kt].
- [x] [Review][Patch] Ready model provenance now requires explicit verified
  runtime evidence and cannot claim app rollback for platform-managed models
  [mobile/inference/local-ios/src/commonMain/kotlin/com/supernutrition/inference/localios/LocalIosInference.kt].
- [x] [Review][Patch] Runtime diagnostic assertions reject raw bytes, paths,
  labels, prompts, responses, secrets, and authorization headers from the
  typed failure surface
  [mobile/inference/local-ios/src/commonTest/kotlin/com/supernutrition/inference/localios/LocalIosInferenceEngineTest.kt].
- [x] [Review][Patch] Physical Xcode UI acceptance and binary-bound iOS
  fixtures now execute on the paired device with the configured development
  team; runtime selection remains gated by signed G-3 evidence.
