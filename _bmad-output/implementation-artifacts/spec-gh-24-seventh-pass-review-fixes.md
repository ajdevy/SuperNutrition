---
title: 'Resolve PR #24 seventh-pass review findings'
type: 'bugfix'
created: '2026-07-30'
status: 'in-progress'
review_loop_iteration: 7
baseline_commit: '835472133eb18384c800c3490baa2683bad421a1'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-gh-24-sixth-pass-review-fixes.md'
---

# PR #24 seventh-pass fixes

## Frozen intent

Remove the remaining lifecycle, ownership, parser, and Xcode-project defects reported against `765c75e7`, without renaming stable modules or making Apple execution an activation gate. Preserve `active-local`, the pure KMP domain seam, bounded image processing, typed platform ports, and fail-closed policy validation. Do not add a runtime TOML dependency, alter an immutable contract digest, or invent product translations; adding supported locales is a product decision.

## Code map

- Android coordination: `mobile/feature/photo-analysis/.../PhotoAnalysisAndroidHostController.kt`, `PhotoAnalysisComposeActivity.kt`; CameraX and normalizing: `mobile/platform/photo-android/...`.
- Apple presentation: `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift`; native port: `mobile/platform/photo-ios/.../IosPhotoBridge.kt`; project metadata: `mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj`.
- Test copy ownership: the three `PhotoAnalysisPresenterCopyFixtures.kt` source sets.
- Policy: `mobile/build-logic/.../PolicyToml.kt`, `MobilePolicyValidator.kt`, `CoverageVerifiers.kt`, `ModuleBoundaryVerifier.kt`; backend gate: `backend/mobile-api/quality-check`.

## Tasks and acceptance

1. Make Android operation and permission state one-way and lifecycle-safe: clear an interrupted permission request on recreation, cancel/complete only matching camera operations, dismiss rationale dialogs with the Activity lifecycle, and ignore CameraX errors after teardown. Remove redundant normalizer API and idiomatically model capture-failure wire values. Add focused controller/activity tests.
2. Extract one injected iOS photo-presentation coordinator shared by Compose and Intel hosts. Ensure all port completion delivery reaches the main queue, replace manual button frames with constraints, and retain resource-localized strings. Keep both Xcode targets with their own pre-link SharedKit phase; make project settings/resource declarations structurally correct and verify both schemes sequentially.
3. Move the non-English presenter-copy fixture to one test-only owner and consume it from dependent tests, preserving no production dependency on testing.
4. Parse each policy document once into typed root/table values; remove string round-tripping, split validation responsibilities, and share XML/digest checks. Recognize nested Gradle wrappers such as `platform(project(...))`. Strengthen parser and validator tests.
5. Make the backend preselection checker prefer Python `tomllib` when present and use a stateful fail-closed Python 3.10 fallback for its declared manifest grammar. Test comments, quoted `#`, arrays, and unsupported values.

## Verification

- `./gradlew :build-logic:test :feature:photo-analysis:test --no-configuration-cache` plus Android compilation/instrumentation tasks available locally.
- `backend/mobile-api/quality-check`, `./gradlew :verifyMobilePolicy`, and targeted policy/parser tests.
- Sequential unsigned `xcodebuild` for production and acceptance schemes when Xcode is present; failure is reported as optional Apple evidence, never as activation state.
- `graphify update .`, `git diff --check`, and PR-head-specific result reporting.
