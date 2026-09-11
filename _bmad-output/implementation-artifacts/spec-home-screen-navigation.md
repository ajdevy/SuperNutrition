---
title: 'Open the photo screen first and keep Home behind the first tab'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** The photo-analysis surface is the primary action for the product and must be the first screen on launch. Home remains available as the first tab, while capture must remain focused and easy to dismiss.

**Approach:** Keep the small positive Home surface as the first tab, preserve Rewards as the second tab, and show the existing capture surface immediately on Android and iOS launch. Capture is a temporary full-screen mode with no bottom tab bar; a top-right close action and the system Back gesture reveal Home after initial launch or return to the tab that launched it. Initialize the analysis runtime after the first frame so model validation does not hold the system splash open. Keep the temporary Home copy simple until the final design is available.

## Boundaries & Constraints

**Always:** Keep copy supportive and positive; expose stable locale-neutral automation IDs; give tabs, camera, and capture-close controls explicit accessibility roles/state; preserve the existing photo-analysis and Rewards state/ledger boundaries; keep the first-tab compatibility ID stable for existing flows; hide the bottom tab bar while capture is open.

**Ask First:** Replace the temporary Home visual treatment or add ledger-backed daily content when the final Home design and content contract are approved.

**Never:** Add guilt, failure, or punitive language to rewards/Home; silently change photo-analysis privacy or consent behavior; treat a host compile as physical-device evidence.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fresh launch | No saved tab state | Photo-analysis capture surface is visible, the close action is available, and the bottom tab bar is hidden | Keep runtime/model initialization off the first UI frame; show Home after capture is closed |
| Rewards navigation | Rewards tab tapped, then Home tab tapped | Rewards list appears, then HomeScreen returns | Do not alter Rewards ledger state |
| Center camera | Center action tapped from Home or Rewards | Existing photo-analysis surface appears | Existing camera permission/recovery flow remains authoritative |
| Add a meal | Home Add a meal button tapped | Existing photo-analysis surface appears | Existing permission/recovery flow remains authoritative |
| Close capture | Top-right close action tapped while capture is open | Capture closes and the launching Home or Rewards surface returns | Preserve photo-analysis state and do not alter the Rewards ledger |
| Back from capture | Android system Back gesture/button while capture is open | Capture closes and the launching surface returns | Do not finish the activity while the capture mode is active |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt` -- temporary positive Home UI and model.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt` -- Android tab and capture-mode routing.
- `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift` -- iOS Home/capture routing and temporary Home renderer.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt` -- shared tab/camera semantics and layout.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisPocLayout.kt` -- shared capture header with the top-right close action.
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` -- stable Home identifiers and capture-tab compatibility alias.
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java` -- Android launch, tab, camera, and Add a meal contracts.
- `mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift` -- iOS Home, tab, camera, and Add a meal contracts.

## Tasks & Acceptance

**Execution:**
- [x] Add HomeScreen and positive resource-backed copy -- provide a testable shell before final design handoff.
- [x] Route Android and iOS first tabs to Home, show capture on launch, and keep center/Add a meal actions entering capture -- align host behavior.
- [x] Add role/selected semantics and shared automation IDs -- keep UI automation and accessibility stable.
- [x] Make capture a full-screen mode with no bottom tab bar and add close/Back dismissal -- keep the user oriented in the launching tab.
- [x] Extend Compose, Android, iOS, Maestro, and quality-catalog coverage -- make the full route testable.

**Acceptance Criteria:**
- Given a fresh app launch, when the root screen renders, then the photo-analysis capture surface is visible, the close action is available, and the bottom tab bar is hidden.
- Given the capture surface opened on launch, when close is used, then HomeScreen is visible and the first tab is selected.
- Given Rewards is visible, when the first tab is tapped, then HomeScreen is visible without changing the Rewards ledger.
- Given Home or Rewards is visible, when the centered camera action is tapped, then the existing photo-analysis surface is visible.
- Given Home is visible, when Add a meal is tapped, then the existing photo-analysis surface is visible.
- Given capture is visible, then the bottom tab bar is hidden and a top-right close action is available.
- Given capture is visible, when close or Android Back is used, then the launching Home or Rewards surface returns.
- Given a cold launch, when the first frame is rendered, then synchronous runtime/model validation does not block the system splash or the capture surface.
- Given any Home or reward copy is rendered, then the copy remains supportive and contains no punitive emotional framing.

## Verification

**Commands:**
- `./mobile/gradlew -p mobile :core:ui:jvmTest :feature:photo-analysis-ui:jvmTest :apps:sharedKit:jvmTest --no-configuration-cache --console=plain` -- expected: SUCCESS.
- `./mobile/gradlew -p mobile :apps:androidApp:assembleProductionDebug :apps:androidApp:assembleProductionDebugAndroidTest --no-configuration-cache --console=plain` -- expected: SUCCESS.
- `./mobile/gradlew -p mobile -Psupernutrition.enableIosDeviceTarget=true -Psupernutrition.enableIntelX64=true :apps:sharedKit:compileKotlinIosArm64 --no-configuration-cache --console=plain` -- expected: SUCCESS.
- `git diff --check` -- expected: no output.

**Manual checks (if no CLI):**
- Run the connected Android instrumentation suite when a physical device is available and unlocked; an installed APK or a connected-but-locked device is not live instrumentation evidence.
- Re-run `qualityCheckJvmAndroid` after refreshing the separately maintained native-gap evidence binding; the current repository record is stale for `bootstrap-android-host`.
