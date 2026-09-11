---
title: 'Make HomeScreen the first tab and keep capture behind the center action'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** The photo-analysis surface currently appears as the primary screen, while the product shell needs a Home landing surface. Photo capture must remain easy to reach from the centered camera action in the bottom bar.

**Approach:** Add a small positive Home surface, make it the default first tab on Android and iOS, preserve Rewards as the second tab, and make both the center camera action and Home's Add a meal action enter the existing capture surface. Keep the temporary Home copy simple until the final design is available.

## Boundaries & Constraints

**Always:** Keep copy supportive and positive; expose stable locale-neutral automation IDs; give tabs and camera controls explicit accessibility roles/state; preserve the existing photo-analysis and Rewards state/ledger boundaries; keep the first-tab compatibility ID stable for existing flows.

**Ask First:** Replace the temporary Home visual treatment or add ledger-backed daily content when the final Home design and content contract are approved.

**Never:** Add guilt, failure, or punitive language to rewards/Home; silently change photo-analysis privacy or consent behavior; treat a host compile as physical-device evidence.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fresh launch | No saved tab state | HomeScreen is visible and Home tab is selected | Keep capture hidden until an explicit capture action |
| Rewards navigation | Rewards tab tapped, then Home tab tapped | Rewards list appears, then HomeScreen returns | Do not alter Rewards ledger state |
| Center camera | Center action tapped from Home or Rewards | Existing photo-analysis surface appears | Existing camera permission/recovery flow remains authoritative |
| Add a meal | Home Add a meal button tapped | Existing photo-analysis surface appears | Existing permission/recovery flow remains authoritative |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt` -- temporary positive Home UI and model.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt` -- Android tab and capture-mode routing.
- `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift` -- iOS Home/capture routing and temporary Home renderer.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt` -- shared tab/camera semantics and layout.
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` -- stable Home identifiers and capture-tab compatibility alias.
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java` -- Android launch, tab, camera, and Add a meal contracts.
- `mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift` -- iOS Home, tab, camera, and Add a meal contracts.

## Tasks & Acceptance

**Execution:**
- [x] Add HomeScreen and positive resource-backed copy -- provide a testable shell before final design handoff.
- [x] Route Android and iOS first tabs to Home and center/Add a meal actions to capture -- align host behavior.
- [x] Add role/selected semantics and shared automation IDs -- keep UI automation and accessibility stable.
- [x] Extend Compose, Android, iOS, Maestro, and quality-catalog coverage -- make the full route testable.

**Acceptance Criteria:**
- Given a fresh app launch, when the root screen renders, then HomeScreen is visible and the first tab is selected.
- Given Rewards is visible, when the first tab is tapped, then HomeScreen is visible without changing the Rewards ledger.
- Given Home or Rewards is visible, when the centered camera action is tapped, then the existing photo-analysis surface is visible.
- Given Home is visible, when Add a meal is tapped, then the existing photo-analysis surface is visible.
- Given any Home or reward copy is rendered, then the copy remains supportive and contains no punitive emotional framing.

## Verification

**Commands:**
- `./mobile/gradlew -p mobile :core:ui:jvmTest :feature:photo-analysis-ui:jvmTest :apps:sharedKit:jvmTest --no-configuration-cache --console=plain` -- expected: SUCCESS.
- `./mobile/gradlew -p mobile :apps:androidApp:assembleProductionDebug :apps:androidApp:assembleProductionDebugAndroidTest --no-configuration-cache --console=plain` -- expected: SUCCESS.
- `./mobile/gradlew -p mobile -Psupernutrition.enableIosDeviceTarget=true -Psupernutrition.enableIntelX64=true :apps:sharedKit:compileKotlinIosArm64 --no-configuration-cache --console=plain` -- expected: SUCCESS.
- `git diff --check` -- expected: no output.

**Manual checks (if no CLI):**
- Run the connected Android instrumentation suite when a physical device is available; current ADB inventory is empty, so no live-device result is claimed.
- Re-run `qualityCheckJvmAndroid` after refreshing the separately maintained native-gap evidence binding; the current repository record is stale for `bootstrap-android-host`.
