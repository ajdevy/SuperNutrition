---
title: 'Resolve PR #24 sixth-pass review findings'
type: 'bugfix'
created: '2026-07-30'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'd9d200c6e2387996dcae371f2516c44b08f8e100'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-gh-24-fifth-pass-review-fixes.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The sixth-pass review of PR #24 still finds correctness and maintainability defects across the photo-analysis runtime seams, shared UI contracts, and quality/build policy. Several fixes also weakened existing invariants or left duplicate sources of truth.

**Approach:** Repair each finding at its owning boundary, restore regression coverage, and extract repeated build/policy responsibilities without changing stable module paths. Keep Apple/Xcode execution optional while making Apple project structure and arm64 compilation correct when the toolchain is available.

## Boundaries & Constraints

**Always:** Preserve pure KMP domain ownership, bounded photo memory, typed cross-platform contracts, localized user-visible copy, fail-closed policy validation, and the current `active-local` lifecycle interpretation. Keep state restoration safe across configuration change/process death. Use immutable product configuration for host identifiers and keep generated/build output untracked.

**Ask First:** Stop if a repair requires renaming a stable module, changing the active lifecycle state, changing an immutable contract digest, or adding a runtime dependency solely to replace the repository parser.

**Never:** Do not catch `Throwable` as normal recovery, use hard-coded English UI copy or raw automation/extra keys, embed static SharedKit, make quality checks autocorrect, reintroduce production test fixtures, or solve boundary enforcement by broad regex-only scanning.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|----------------------------|----------------|
| Permission recreation | Permission dialog or camera operation crosses Activity recreation/process death | State and pending operation remain truthful; result is accepted only for the matching operation | Unknown/missing result is typed cancellation/failure, never a hang |
| Native failure | Normalizer/bridge throws an expected preparation exception or unexpected `Exception` | Expected image failure maps to poor-image; unexpected platform failure maps to capture failure and remains observable | Never swallow `Error`/`Throwable` |
| Policy manifest | TOML contains comments, escaped strings, arrays, malformed values, or a non-TOML required file | Typed parser/validator rejects malformed input and scopes required files by format | Fail closed with a useful path/key error |
| UI contract | Presenter state, localized copy, and automation IDs are consumed by Android/iOS tests | One typed source of truth drives labels, IDs, and actions | Missing/blank copy or unknown action is rejected deterministically |

</frozen-after-approval>

## Code Map

- `mobile/domain/photo-analysis/src/commonMain/.../PhotoAnalysisDomain.kt` and `PhotoAnalysisReducer.kt` -- normalized-input invariants, sensitive-byte cleanup, in-memory session/lease state, and effect ordering.
- `mobile/feature/photo-analysis/src/androidMain/...` -- Android host lifecycle, permission, result, and normalization recovery boundaries.
- `mobile/platform/photo-android/src/androidMain/...` -- permission bridge, CameraX activity, and bounded result-store ownership.
- `mobile/feature/photo-analysis-presenter/src/commonMain/...` and `mobile/feature/photo-analysis-ui/src/commonMain/...` -- localized copy, presenter actions, surface construction, IDs, and Compose rendering.
- `mobile/platform/photo-ios/src/iosMain/...`, `mobile/apps/iosApp/...` -- AVFoundation/picker bridge, bounded NSData conversion, typed host dispatch, plist/Xcode integration, and duplicate bootstrap ownership.
- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/...` -- typed policy parsing, module-boundary scanning, validator decomposition, quality task wiring, and extracted evidence/coverage verifiers.
- `backend/mobile-api/quality-check`, `mobile/apps/iosApp/Info.plist`, and focused test sources -- portability, metadata, and regression fixtures.

## Tasks & Acceptance

**Execution:**
- [x] Restore domain invariants, secure cleanup, stable reducer transitions, and the removed feature/presenter regression coverage.
- [x] Make Android permission/result/camera state process-safe, contract-driven, weakly retained, bounded, and keyed only by shared constants.
- [x] Make iOS authorization, layout/session lifecycle, NSData/crypto handling, typed action dispatch, host identity, and Xcode target ownership deterministic; remove duplicate plist/bootstrap definitions.
- [x] Collapse duplicate presenter/surface/copy/automation contracts and replace raw English/test identifiers with typed/localized sources.
- [x] Remove non-TOML manifests from `PolicyToml`, eliminate residual regex parsing in common-manifest/import scanning, extract policy/coverage verifiers, and keep read-only quality gates free of autocorrect tasks.
- [x] Add focused tests for every repaired edge case and update only derived policy digests/metadata required by the validated source set.

**Acceptance Criteria:**
- Given a clean checkout and supported JVM/Android toolchain, focused tests, strict policy validation, and read-only quality gates pass without mutating sources.
- Given iOS arm64 compilation/linking or Xcode project validation is available, both production and acceptance targets resolve SharedKit after its build phase with correct static-framework handling; Apple runtime execution remains optional.
- Given malformed policy, unknown enum actions, missing operation state, or bridge failures, the system fails closed with typed recovery and no silent fallback/hang.
- Given a changed localized string, presenter field, automation ID, or result extra, there is one owning contract and tests do not duplicate raw literals.

## Verification

**Commands:**
- `./mobile/gradlew -p mobile :build-logic:test :verifyMobilePolicy --no-configuration-cache` -- expected: success.
- Focused presenter/UI/feature JVM tests and Android compile/instrumentation compile tasks -- expected: success.
- `./mobile/gradlew -p mobile :platform:photo-ios:compileKotlinIosArm64 :apps:sharedKit:linkDebugFrameworkIosArm64 -Psupernutrition.enableIosDeviceTarget=true` -- expected: success when Apple toolchain is present.
- Production/acceptance `xcodebuild` arm64 compile with signing disabled, governance scripts, `git diff --check`, and `graphify update .` -- expected: success; runtime/device execution is optional.

## Suggested Review Order

**Android lifecycle and result contract**

- Operation state, permission flow, and failure mapping are centralized behind typed host ports.
  [`PhotoAnalysisAndroidHostController.kt:16`](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAndroidHostController.kt#L16)

- CameraX callbacks persist only bounded bytes and weakly retain the Activity across recreation.
  [`AndroidCameraCaptureActivity.kt:27`](../../mobile/platform/photo-android/src/androidMain/kotlin/com/supernutrition/platform/photoandroid/AndroidCameraCaptureActivity.kt#L27)

**Apple bridge and host seam**

- Shared bootstrap and typed ordinal dispatch remove duplicated entry-point behavior and unsafe fallback identity.
  [`PhotoAnalysisHostSupport.swift:8`](../../mobile/apps/iosApp/PhotoAnalysisHostSupport.swift#L8)

- AVFoundation session work is serialized while delegate ownership is cleared on every dismissal path.
  [`IosPhotoBridge.kt:69`](../../mobile/platform/photo-ios/src/iosMain/kotlin/com/supernutrition/platform/photoios/IosPhotoBridge.kt#L69)

- The SharedKit facade exposes one localized copy contract instead of parallel label getters.
  [`SharedKitPhotoAnalysisSession.kt:19`](../../mobile/apps/sharedKit/src/iosMain/kotlin/com/supernutrition/sharedkit/SharedKitPhotoAnalysisSession.kt#L19)

**UI contract and automation**

- Compose receives one typed surface contract, keeping localized copy and automation IDs owned together.
  [`PhotoAnalysisCaptureScreen.kt:20`](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCaptureScreen.kt#L20)

**Policy and quality enforcement**

- Structured manifest validation and generated-directory exclusions keep policy gates fail-closed and read-only.
  [`MobilePolicyValidator.kt:27`](../../mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/MobilePolicyValidator.kt#L27)

- Boundary scanning handles production/test scopes and multiline project dependencies without regex-only parsing.
  [`ModuleBoundaryVerifier.kt:11`](../../mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/ModuleBoundaryVerifier.kt#L11)

- Coverage and native-equivalent evidence checks now have dedicated verifier ownership.
  [`CoverageVerifiers.kt:15`](../../mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/CoverageVerifiers.kt#L15)

**Regression evidence**

- A multiline dependency fixture protects the boundary scanner against a common bypass shape.
  [`ModuleBoundaryVerifierTest.kt:39`](../../mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/ModuleBoundaryVerifierTest.kt#L39)
