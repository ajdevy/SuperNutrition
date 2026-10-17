---
title: 'Home content behind floating navigation'
type: 'bugfix'
created: '2026-10-17'
status: 'done'
review_loop_iteration: 0
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
context: []
---

<frozen-after-approval reason="Direct user instruction authorizes this scoped Home follow-up">

## Intent

**Problem:** The user sees a background strip behind Home navigation that hides the lower cards. The shared main layout still reserves a separate 83 dp canvas for Home, even though Rewards already renders beneath the floating navigation. This cuts the Home scroll viewport off above the controls.

**Approach:** Give Home the full safe content viewport and overlay the existing floating navigation. Add equivalent clearance at the end of Home's scrollable content so the final card and its action can be raised completely above the controls. Keep the existing layout dimensions and compact-screen spacing decisions stable.

## Boundaries & Constraints

**Always:** Apply and consume host insets once. Share the existing navigation height rather than duplicating it. Preserve Rewards overlay behavior, navigation accessibility and real pointer actions, Home artwork, symmetric gutters, mascot overlap, and readable card contents. Continue within the user's existing dirty worktree and keep all unrelated completed work intact. Run one Gradle build at a time and operate only the owned emulator.

**Ask First:** No additional user decision is needed within this scope. An unrelated published contract, routing or privacy change requires separate authorization.

**Never:** Remove the illustrated floating pill itself, shorten the content viewport to avoid an overlap, introduce another opaque navigation backdrop, weaken quality gates, alter the camera/food-recognition pipeline, stage or commit unrelated changes, push, merge or interact with a physical phone.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected behavior | Failure handling |
|----------|---------------|-------------------|------------------|
| Safe phone viewport | Home in main shell with top and bottom host padding | Home extends to the bottom safe edge; floating controls stay inside the safe viewport | A viewport ending at the canvas top fails the regression |
| Compact or large-text Home | Smaller height, narrow width or 200% font | Existing measured content remains readable, scrollable and end-reachable | Do not clip or shrink text to force a pass |
| Content in navigation region | A card passing outside the pill | Card pixels remain visible and a swipe outside control targets scrolls Home | A blank strip or intercepted swipe fails the regression |
| End of scroll | Final helpful-step card | Whole card and Do it are clear of the camera/control tops | Add scroll clearance, not a fixed content crop |
| Standalone Home or Rewards | No navigation local / Rewards selected | Standalone Home keeps existing padding; Rewards remains unchanged | Existing full relevant UI tests must pass |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt` — main safe viewport and existing Rewards overlay provider; expand overlay to Home.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt` — scrollable foreground and compact breakpoint; consume navigation overlap only for end clearance and preserve its former usable-height compact decision.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt` — read-only shared height and floating control geometry.
- `mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/HomeNavigationOverlayTest.kt` — new safe-viewport, pointer/scroll and end-clearance regression.
- `mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLayoutVisualTest.kt` — revise only the old reserved-Home-viewport assertion to the newly requested overlay contract; retain system-edge pixel assertions.

## Tasks & Acceptance

**Execution:**
- [x] Archive the exact pre-change dirty production source and write a failing Home overlay regression before editing it.
- [x] Extend the existing overlay/local mechanism to Home; reserve clearance inside Home scrolling while preserving its compact layout policy.
- [x] Update the explicit legacy viewport assertion; verify standalone Home, Rewards and navigation actions with the complete UI JVM suite.
- [x] Run scoped UI lint and explicit iOS Arm64 compile; assemble the current production APK, install it on the retained owned emulator, inspect Home top/end screenshots and verify the installed hash.
- [x] Review the exact scoped patch independently, record results, run diff check and update the code graph. Leave the current application open for the user's review.

**Acceptance Criteria:**
- Given Home with host safe padding, when composed, then its scroll viewport reaches the safe bottom edge and each inset is applied once.
- Given the existing floating controls, when tapped using actual pointer input, then Home, Rewards and Camera each invoke their original action.
- Given Home content passing beside navigation, when drawn and swiped outside controls, then content is visible and scrolling advances.
- Given compact, ordinary, large and tablet-sized viewports at normal or enlarged font, when scrolled to the end, then the complete helpful-step card is above the navigation controls with its action reachable.
- Given the installed current build, when opened on the owned baseline emulator, then the blank Home strip is absent and no app failure blocks the screen.

## Spec Change Log

## Design Notes

The layout's existing composition local defaults to zero outside the main shell. Home must use the provided 83 dp overlap as scroll-end padding. Its compact predicate should keep the former available-height decision by subtracting that overlap from the now full-height viewport. This avoids moving the header, increasing card gaps or changing mascot size as a side effect of extending the drawing area. The bar itself already has transparent pixels outside its illustrated pill and control targets.

## Verification

Run the new focused regression against the archived baseline and preserve fresh failing XML. Then execute focused green, full `:feature:photo-analysis-ui:jvmTest`, six existing scoped UI detekt tasks with `--continue`, `:feature:photo-analysis-ui:compileKotlinIosArm64` with the explicit Intel device-target flag and `:apps:androidApp:assembleProductionDebug` sequentially. Capture exact commands, exits and per-suite XML counts. Earlier native-suite results belong to the preceding production digest and must not be quoted as new-build execution.

Use the existing API35 x86_64 baseline guest on isolated ADB 5041; verify AVD identity before mutation. Read the installed APK hash after updating. Inspect normal Home, passing content, the final card and Rewards navigation on the new APK. Record emulator evidence honestly, retaining the known unrelated root-policy expiry limitation. The user's prior instruction to keep the emulator open for review remains in effect.

## Evidence

The exact dirty production baseline and the existing visual test are SHA-256 archived under `mobile/build/home-navigation-overlay-baseline/`. The new regression was run before any production edit. Fresh focused XML records **4 tests / 2 failures / 0 errors / 0 skipped**: Home ends at y=709 instead of the safe y=792 edge; the passing rhythm card ends at y=157 while the pill starts at y=167. The standalone compact-geometry and existing end-clearance checks pass on the baseline. Run exited 1 after 1m39s; full unfiltered log, command and fresh XML are preserved under UI `build/reports/home-navigation-overlay/red/`. These are product-regression failures, not compilation or harness failures.

Focused green then records **4 / 0 / 0 / 0** in fresh XML, exit 0 after 1m18s. It executes the card pixel/swipe probe, actual navigation/action pointer clicks, all eight viewport/font combinations, and the standalone compact-geometry guard. The subsequent shared-layout KDoc correction changes documentation only. The final scoped patch hash is `ec1ba56178b8138e821f8bc32c22379ff4c7a524a74c98907883a56d88a71521`; full-module, lint, explicit iOS compile and production assembly are running next. Earlier full Android-suite evidence is not a current-build result.

The first full UI replay executed 118 tests with 3 failures, 0 errors and 0 skipped. The affected short-phone, enlarged-text and landscape tests use the Compose test helper `performScrollTo`, which brings a node inside the newly full-height rectangular viewport but does not account for floating siblings. This is insufficient to exercise the scenarios' end-reachability condition: e.g. the short-phone action stops at y=420–468 while camera occupies y=400–450. The new maximum-scroll plus real-pointer end-clearance matrix passes. Preserve all three existing strict above-camera/readability assertions and update only their scroll setup to reach the actual scroll end; do not remove or relax assertions. Archive this first full attempt before the corrected full replay. Review must assess this harness distinction independently.

Final full replay exited 0 after 2m46s: **118 / 0 / 0 / 0** across 21 fresh UI JVM XML suites. All six scoped UI lint reports contain zero issues. The explicit Intel UI `iosArm64` compile executed in the preserved first full attempt and is legitimately UP-TO-DATE for unchanged production inputs in the final full run; no Swift/Xcode or Apple device execution is claimed. Exact commands, XML, lint reports, source hashes and cache lineage are in UI `build/reports/home-navigation-overlay/full-green/verification.json` and its sibling run/log files. The initial verification helper expected only two production changes, while the preceding manifest also included the two intentionally changed JVM test sources; that inventory assumption was corrected before install and is recorded separately.

Current production APK SHA-256 is `0969aa8fa690470aa9889a22fd41a805bedc12a1a1927a1bb204489c8a014abd`, verified again from the installed APK on `SuperNutrition_API_35_x86_64`, serial `emulator-5554`, isolated ADB 5041. This is emulator evidence: actual 720×1560 pixels at 320 dpi, font scale 1.0. Home now reaches `[0,136][720,1464]`. A real native swipe begun beside the pill moves Home to its scroll end; the complete last card `[48,1132][672,1282]` is **22 px** above the first floating control at y=1304, with the full Do it button inside the card. Visually inspected screenshots and fresh XML are archived under `emulator/`. Rewards still reaches the same full safe viewport after a real navigation tap. Home has been reopened for the user's review. The preceding 120-test native-suite evidence belongs to the older APK, not this follow-up build.

## Closure

Independent Blind Hunter and Edge Case Hunter reviews of final scoped diff `f68d96a229ab43b925e2e608c81e4bbfb4d3be204406cf0a7033a6b846335774` report zero actionable introduced findings. The three corrected scroll preparations preserve strict assertions and are independently assessed as end-reachability setup changes. These are static reviews, not native accessibility execution. Reports: [Blind Hunter](home-navigation-overlay-blind-2026-10-17.md), [Edge Case Hunter](home-navigation-overlay-edge-2026-10-17.md).

`git diff --check` passes. `graphify update .` exits 0 and produces 20,774 nodes / 27,818 edges / 1,992 communities; it covers code AST, skips two SQL files for missing `tree_sitter_sql`, retries 253 zero-node JSON/asset files and does not run semantic document extraction or HTML above its 5,000-node limit. All 104 final recorded source hashes still match after review. The retained baseline emulator remains running on Home; no physical device or additional temporary emulator was used. No local commit, staging, push or merge was performed: the exact slice is archived relative to the preceding dirty baseline rather than bundling unrelated prerequisites into a commit.

## Suggested Review Order

**Shared floating navigation**

- Keep safe viewport intact and draw the existing controls afterward.
  [PhotoAnalysisMainLayout.kt:34](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt#L34)

**Home layout preservation**

- Subtract the provided overlap to preserve the former compact breakpoint.
  [HomeScreen.kt:60](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L60)

- Put navigation clearance inside scrolling so the final card remains reachable.
  [HomeScreen.kt:110](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L110)

**Regression evidence**

- Check full viewport, real gestures, whole-card clearance and compact geometry.
  [HomeNavigationOverlayTest.kt:32](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/HomeNavigationOverlayTest.kt#L32)

- Preserve strict end visibility assertions while reaching the actual scroll end.
  [PhotoAnalysisLayoutVisualTest.kt:111](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLayoutVisualTest.kt#L111)

- Keep landscape Home and Rewards content reachable at double text size.
  [AdditionalInteractionLayoutsTest.kt:189](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/AdditionalInteractionLayoutsTest.kt#L189)
