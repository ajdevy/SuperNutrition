---
title: 'Align existing mobile screens with current MVP Figma'
type: 'feature'
created: '2026-10-23'
status: 'in-review'
baseline_commit: '7a141facbd0ec0b4fc86efce4948fdfd3b2ce660'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="Direct user instruction authorizes implementation and verification; no intermediate permission required">

## Intent

**Problem:** Existing mobile screens still render older Home, Rewards and camera/review designs. The user requests comparison with the current Figma file and implementation of its changes across screens already present in the application.

**Approach:** Map reachable screens to current MVP frames, record differences, update shared Compose presentation, and verify rendered screens and existing interactions. Preserve the completed Profile and Meal history flow.

## Boundaries & Constraints

**Always:** Use current high-fidelity context and screenshots from file `08zQiW2IIHfQUBN3JDRUcZ`; preserve domain state, grounded nutrition authority, photo lifetime and consent. Reuse exact exported assets, Fraunces/Nunito, accessible contrast and 48dp touch targets. Keep readable 200% text, RTL, compact phones, IME and navigation insets. Home should fit without scrolling where natural text allows; readable fallback scrolling is preferable to clipping. Values must retain their source; unavailable data stays unavailable.

**Ask First:** New privacy/storage/provider contracts or destructive changes outside the requested presentation scope.

**Never:** Invent meal photos, nutrition, earned rewards or saved-meal storage. Do not add unimplemented meal/reward detail screens, onboarding or barcode flows. Do not infer iOS production Home support from its acceptance host. Do not push or publish website files from this work.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Home | Existing host model | New logo/bubble/mascot, Fiber/Colors/Protein/Fats grid; existing profile and camera entry remain reachable | Unknown metric displays unavailable rather than borrowed water/movement values |
| Rewards | Seven real reward cards | Daily four and Weekly three carousels, actual counts/progress/checks and new artwork | Empty/missing cards do not create earned states |
| Camera / review | Live or retained photo; actual candidates | Current typography, colors, controls, dish chips, sticker placement and footer; real photo stays dynamic | Existing recovery/permission/cancel actions remain accessible |
| Name / note editing | Existing session editing | Current styled input and native keyboard, cancel/Done behavior retained | No new persistence claim or unsupported toggle |
| Accessibility | 320/360/375dp, 200% text, RTL and keyboard | All content/actions reachable with no overlap or hidden text | Scroll natural content when it cannot fit |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/` — Home, Rewards, navigation, camera/review, Profile editing; sole presentation owner.
- `mobile/feature/photo-analysis-ui/src/commonMain/composeResources/` — bundled fonts and exact Figma assets.
- `mobile/feature/photo-analysis-ui/src/jvmTest/` and `src/commonTest/` — rendered layout and interaction verification.
- `mobile/feature/photo-analysis/src/androidMain/` — Android host copy/model wiring only if the UI contract needs it.
- `mobile/feature/photo-analysis-presenter/src/commonMain/` — existing reward IDs, copy and progress; retain computation.
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` — distinct new metric IDs and their inventory.
- `mobile/apps/iosApp/Localizable.strings` — existing production review copy, kept consistent with Android.
- `mobile/apps/androidApp/src/androidTest/` — existing native navigation and semantics coverage.

## Tasks & Acceptance

**Execution:**
- [x] `_bmad-output/implementation-artifacts/existing-screens-figma-audit-2026-10-23.md` — record inventory and concrete differences before fixes.
- [x] `mobile/feature/photo-analysis-ui/` — align Home, Rewards, shared navigation, capture/review and existing name editor with current frames; bundle exact assets and preserve interactions.
- [x] `mobile/feature/photo-analysis/src/androidMain/` — align host strings/model fields where necessary without changing domain behavior.
- [x] `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` — register distinct Colors/Fats selectors without reusing Water/Movement identities.
- [x] `mobile/apps/iosApp/Localizable.strings` — align existing review and reward copy without changing Apple route reachability.
- [x] `mobile/feature/photo-analysis-ui/src/` — update superseded layout assertions and add meaningful regression checks; observe new checks fail before fixes.
- [x] `_bmad-output/implementation-artifacts/existing-screens-verification-2026-10-23/` — record current renders, tests, independent review findings and limitations.

**Acceptance Criteria:**
- Given each reachable screen, when audited against the listed current frame, then every observed in-scope mismatch is fixed or explicitly identified as a source-data/platform constraint.
- Given actual rewards, when rendered, then there is exactly one card per reward ID with actual counts and progress in its correct daily/weekly group.
- Given Home or Rewards, when camera/profile/back navigation is used, then existing destinations and return state continue to work.
- Given changed shared UI, when module and emulator tests execute, then reports are fresh and exact counts are recorded; physical-device evidence remains explicitly unavailable.

## Spec Change Log

- 2026-10-23: Native camera verification exposed a pre-existing blocking CameraX future read during permission/lifecycle startup. Extend the Android host correction to `PhotoAnalysisLiveCameraPreview.kt` and add pending/completed/failed/cancelled provider checks. Preserve CameraX lifecycle ownership and all recovery callbacks; add no storage/provider contract.

## Design Notes

Current frames: Home `604:294`, Rewards `604:860`, Capture `604:819`, Review `572:17720`, custom review `604:1445`, typing `604:1485`, Profile/name `604:921`/`604:1056`, history `604:893`. Add-by-text `632:503` and dedicated detail screens are not reachable product screens at baseline; they are inventory gaps, not silently fabricated functionality. Current camera has manual ingredient/note editing but no meal-name/portion/save-to-library contract.

Home motion is a 71-frame sprite, 12fps, 5916.667ms loop from `get_motion_context`; use a single timeline and respect reduced motion. New metric targets are design/demo copy, not personalized prescriptions. Preserve honest unavailable states for fields the host does not supply.

## Verification

- Run focused new layout/interaction checks red, then full `:feature:photo-analysis-ui:jvmTest` and affected host/presenter tests.
- Run Android production assembly and full `:apps:androidApp:connectedAndroidTest --no-configuration-cache --console=plain` on `emulator-5554`; inspect each variant XML.
- Run `qualityCheckJvmAndroid --continue`; record stale physical-evidence/typed-analysis limits honestly.
- Compile changed Apple source explicitly if applicable; shared UI compile `:feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true`.
- Inspect actual rendered normal/200% screens, exported asset geometry, `git diff --check`, and `graphify update . --no-cluster`.

## Remaining Verification

The final230-test shared UI run and a fresh150-test native Android run on final UI source `3d54a716` are complete. Production, POC and UI Acceptance each have50 tests with0 failures/errors/skipped in fresh XML. Installed-APK hashes match; current normal/200% runtime images, native IME and Home → Profile → Meal history are inspected. At200% text, Home remains scrollable rather than a no-scroll layout. See [latest runtime evidence](emulator-verification-2026-10-23/README.md). Earlier launch failures remain [diagnostic history](existing-screens-verification-2026-10-23/resume-native-diagnostics.md). Keep status in-review for required physical policy evidence, which remains unavailable under the user’s accepted emulator-only constraint. iOS runtime execution is separately unavailable on Intel because Compose1.11.1 has no `ios_x64` variant; the user explicitly accepted that limitation.

## Suggested Review Order

**Screen composition**

- Start with the current Home composition and its existing navigation callbacks.
  [HomeScreen.kt:31](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L31)

- Render seven real rewards with localized summaries and actual progress.
  [CurrentRewardsScreen.kt:84](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/CurrentRewardsScreen.kt#L84)

- Shared navigation retains native touch targets and current artwork.
  [PhotoAnalysisMainBottomBar.kt:46](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt#L46)

**State and native boundaries**

- Preserve the published Profile API while sharing controlled session editing state.
  [ProfileScreen.kt:50](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileScreen.kt#L50)

- Native Back cancels name editing before leaving Profile.
  [PhotoAnalysisComposeActivity.kt:649](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt#L649)

- Avoid blocking the UI thread during CameraX provider initialization.
  [PhotoAnalysisLiveCameraPreview.kt:235](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLiveCameraPreview.kt#L235)

**Regression evidence**

- Verify RTL, localized summaries, progress counts and accessible inline editing.
  [ReviewAcceptanceRegressionTest.kt:22](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/ReviewAcceptanceRegressionTest.kt#L22)

- Native regression protects draft cancellation and Profile return state.
  [ProfileNameEditingInstrumentationTest.java:28](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/ProfileNameEditingInstrumentationTest.java#L28)
