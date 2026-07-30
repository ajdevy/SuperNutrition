---
title: 'Resolve PR #24 fourth-pass review findings'
type: 'bugfix'
created: '2026-07-30'
status: 'done'
baseline_commit: '26e7c5dced5edf15de26149035ca55fec0e1ea43'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
  - '{project-root}/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PR #24's fourth-pass review found new correctness, lifecycle, localization, memory-safety, and governance weaknesses across the iOS bridge/hosts, shared presenter, Android camera flow, Xcode project, and build logic.

**Approach:** Repair the findings at their ownership boundaries, centralize duplicated host/configuration seams, preserve stable module paths and product policy, and add focused regression coverage for each failure mode.

## Boundaries & Constraints

**Always:** Keep Apple/Xcode execution optional while requiring canonical arm64 compile evidence when available; preserve the static SharedKit framework model; keep mobile domain pure KMP; use typed resource and policy parsing; keep photo bytes out of snapshots and logs; use stable identifiers and explicit recovery states.

**Ask First:** Any change that requires a module rename, a new product implementation root, a broad dependency-verification refresh, a CI policy expansion, or making Apple execution a required gate.

**Never:** Do not hide failures with task filters, defaults, broad lint baselines, raw English production fallbacks, unbounded image decoding, hard-coded generated Xcode GUIDs, or regex-only parsing where the repository already has a typed parser.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Large image | oversized or malicious Android image | bounded decode/scale and normalized output | typed poor-image recovery; no process crash |
| Permission recovery | Android rationale/permanent denial or iOS denied/restricted | localized recovery and settings/import actions | no repeated blind prompt |
| Missing copy | absent locale key | base-language product copy | never display a key or English bridge fallback |
| Recreated host | valid saved snapshot and same attempt | one feature instance restores state | malformed/foreign snapshot is rejected safely |
| Native failure | camera/picker/presentation/hash/normalization failure | typed cancellation or capture/poor-image recovery | callback completes once on main thread |

</frozen-after-approval>

## Code Map

- `mobile/platform/photo-ios/src/iosMain/**` and `src/nativeInterop/**` -- modern camera, permission, picker, crypto, nullability, and normalization seams.
- `mobile/apps/iosApp/**` and `SuperNutritionUIAcceptance.xcodeproj/project.pbxproj` -- shared Swift host support, localized plist/UI copy, identifiers, and incremental framework build phases.
- `mobile/apps/sharedKit/src/**` -- one iOS copy/session implementation across supported Apple targets.
- `mobile/feature/photo-analysis-presenter/**` and `mobile/feature/photo-analysis-ui/**` -- grouped copy model, shared fixtures, and surface-owned automation IDs.
- `mobile/platform/photo-android/**` and `mobile/feature/photo-analysis/src/androidMain/**` -- bounded decode, permission rationale, camera result, lifecycle-owned feature, and snapshot schema.
- `mobile/build-logic/src/**` and `scripts/**` -- structured policy parsing, boundary scanning, convention wiring, and identifier-independent governance checks.

## Tasks & Acceptance

**Execution:**
- [x] Refactor iOS bridge/normalizer/cinterop for modern APIs, safe ownership, nullability, main-thread completion, checked crypto, and safe image destination writes.
- [x] Extract shared Swift host/coordinator support, centralize attempt IDs and identifiers, make action routing exhaustive, localize Info.plist and camera copy, and correct static-framework/Xcode phase inputs.
- [x] Move shared Apple copy/session code to a common Apple seam, expose only the required copy API, and remove duplicated getters/tables.
- [x] Group presenter copy by responsibility, centralize test fixtures, and make UI automation IDs surface-owned and constant-driven.
- [x] Harden Android permission/rationale handling, bounded bitmap decoding, CameraX lifecycle/results, feature recreation, snapshot version/keys, and error mapping.
- [x] Replace brittle build/governance scans with shared structured parsing and convention helpers; add regression tests for multiline dependencies, policy values, malformed snapshots, and path edge cases.

**Acceptance Criteria:**
- Given any supported host and locale, when photo capture/import or recovery is attempted, then localized UI and typed recovery are delivered exactly once without leaked bytes or stale presenters.
- Given a large, malformed, or recreated Android input, when the host decodes or restores it, then memory use is bounded and invalid state is rejected without a crash.
- Given a Gradle/Xcode/policy change, when local quality entrypoints run, then only declared source/config inputs are scanned and valid multiline/structured values are interpreted correctly.
- Given canonical arm64 compilation, when Xcode builds with signing disabled, then the static SharedKit link succeeds; Apple execution remains optional.

## Design Notes

Keep the existing `mobile/`, `core`, `domain`, `platform`, `feature`, `apps`, and `testing` boundaries. Prefer one shared Apple host support file and one Apple source-set seam over copying code or renaming modules. Treat persisted snapshot keys as versioned wire data rather than Kotlin enum implementation names.

## Verification

**Commands:**
- `./mobile/gradlew -p mobile verifyMobilePolicy --dependency-verification=strict --configuration-cache --configuration-cache-problems=fail --no-daemon --max-workers=2` -- expected: success.
- Focused JVM tests plus strict arm64 Kotlin/Native compile/link -- expected: success.
- `xcodebuild ... -sdk iphoneos ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build` -- expected: compile-only success when Xcode is available.
- `./scripts/verify-product-identity.sh`, `./scripts/verify-root-boundaries.sh`, and `./scripts/verify-ci-policy.sh` -- expected: success.
- `git diff --check` and `graphify update .` -- expected: success/current graph.

## Suggested Review Order

**Host and platform boundaries**

- Start with the shared host/coordinator to see lifecycle, routing, and localized bridge ownership.
  [`PhotoAnalysisHostSupport.swift:24`](../../mobile/apps/iosApp/PhotoAnalysisHostSupport.swift#L24)

- Verify modern permission, presentation, delegate, and bounded picker behavior at the iOS seam.
  [`IosPhotoBridge.kt:51`](../../mobile/platform/photo-ios/src/iosMain/kotlin/com/supernutrition/platform/photoios/IosPhotoBridge.kt#L51)

- Follow Android operation tokens through launch, result acceptance, and typed recovery.
  [`PhotoAnalysisAndroidHostController.kt:16`](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAndroidHostController.kt#L16)

**Build and project correctness**

- Check the shell phase inputs and ordering that make static SharedKit linking deterministic.
  [`project.pbxproj:224`](../../mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj#L224)

- Confirm module scans exclude generated build trees while retaining multiline dependency detection.
  [`ModuleBoundaryVerifier.kt:15`](../../mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/ModuleBoundaryVerifier.kt#L15)

**State, copy, and tests**

- Review the versioned snapshot codec and foreign-attempt rejection before examining instrumentation coverage.
  [`PhotoAnalysisSnapshotBundleCodec.kt:17`](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisSnapshotBundleCodec.kt#L17)

- Inspect the grouped presenter copy and surface-owned automation registry supporting localized UI tests.
  [`PhotoAnalysisPresenter.kt:1`](../../mobile/feature/photo-analysis-presenter/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisPresenter.kt#L1)

- Finish with the acceptance tests that consume shared identifiers and avoid English UI assumptions.
  [`BootstrapLaunchTests.swift:12`](../../mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift#L12)
