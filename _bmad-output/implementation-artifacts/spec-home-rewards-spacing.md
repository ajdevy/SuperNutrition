---
title: 'Home and Rewards spacing and alignment'
type: 'bugfix'
created: '2026-10-10'
status: 'done'
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="User requested this layout correction as the next task">

## Intent

**Problem:** Home scales a fixed 375×812 canvas to cover the viewport, cropping cards on tall phones and making lower content unreachable on short phones. Android overlays its navigation on Rewards without reserving scroll space, and the tab centers and card gutters differ slightly.

**Approach:** Keep the existing illustrations, typography, colors, content, and actions while arranging foreground content with symmetric gutters and natural vertical flow. Reserve navigation space once in each host and make the shared bottom bar center its artwork and mirror its tab positions.

## Boundaries & Constraints

**Always:** Preserve the current camera/review changes, all automation identifiers, callbacks, positive reward copy, and reward ordering. Use the shared Compose module for Android product layout. Keep platform hosts responsible for platform insets; preserve iOS's existing bottom safe-area reservation. Let content scroll when available height or larger fonts require it. Use existing assets and avoid stretching text or touch targets with viewport height.

**Ask First:** Publishing, committing, pushing, or changing reward/business policy requires a separate user request.

**Never:** Introduce new reward behavior, invented progress, a replacement visual design, new navigation destinations, baseline suppression, broad lint/coverage exclusions, or destructive worktree operations. Do not replace the iOS Home placeholder with a new architecture as part of a spacing correction.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Regular phone | 375×812; Home or Rewards | Equal card gutters, aligned navigation, visible primary actions | Natural measurement |
| Tall phone | 375×932 | Cards remain centered with equal gutters and no horizontal crop | No cover scaling |
| Short phone | 320×480 or 320×568 | Home's lower actions and the final reward remain reachable by scrolling | No clipping or hidden controls |
| Larger text | Font scale 1.3–1.5 | Content grows/wraps; sections and actions do not overlap | Scroll overflow vertically |
| Wide viewport | Width 600 | Content and bottom bar stay centered at a comfortable bounded width | Symmetric surrounding space |
| Navigation | Either tab; camera; goals/profile | Existing selected semantics and callbacks remain intact; camera hides the bar | Keep existing session behavior |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/.../HomeScreen.kt` — fixed canvas and absolute positions; Home art and fonts.
- `mobile/feature/photo-analysis-ui/.../RewardsScreen.kt` — seven-card LazyColumn with 16dp outer gutters and trailing padding.
- `mobile/feature/photo-analysis-ui/.../PhotoAnalysisMainBottomBar.kt` — artwork includes its shadow; tab centers are asymmetric.
- `mobile/feature/photo-analysis/src/androidMain/.../PhotoAnalysisComposeActivity.kt` — tab state, camera handoff, overlay navigation without content reservation.
- `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift` — separate Swift Home, native bar, Intel Rewards fallback; bottom space already reserved by safeAreaInset.
- `mobile/feature/photo-analysis-ui/src/{commonTest,jvmTest}/...` — semantic contracts, geometry regressions, production-rendered screenshots.

## Tasks & Acceptance

**Execution:**
- [x] `HomeScreen.kt` and focused layout helpers — remove height-driven cover scaling and absolute foreground positioning; unify card gutters/padding and align the metric grid.
- [x] `PhotoAnalysisMainBottomBar.kt` — mirror equal tab slots around the camera, center the shadowed pill, keep icon/label baselines consistent, and bound width.
- [x] `RewardsScreen.kt` and Android tab host — use matching outer gutters and reserve the actual bar/inset area outside scrollable content.
- [x] `PhotoAnalysisHostSupport.swift` — apply consistent outer/top/bottom content padding and group related text in existing native Home/Intel Rewards; keep safe-area ownership singular.
- [x] UI tests — demonstrate the old crop/scroll/alignment defects before implementation, then cover responsive dimensions, larger text, last reward visibility, and existing interactions.
- [x] Evidence — inspect rendered Home/Rewards screens, compile/package Android and common iOS UI, run quality gates with continue, record genuine toolchain limits, update graphify, and check the diff.

**Acceptance Criteria:**
- Given either tab, the content uses one consistent gutter, section/card spacing is deliberate, and corresponding controls/columns align.
- Given navigation is visible, the final visible/scrollable content ends above it and system insets are applied once; capture remains full-screen.
- Given existing models and callbacks, all Home, Rewards, goals, profile, tab, and camera interactions retain their current behavior and identifiers.
- Given the current branch, the prior camera/review work survives without unrelated edits or remote mutations.

## Spec Change Log

2026-10-11 follow-up: center the icon and label together in “Add a meal” and
“View goals”; keep their equal button widths and center wrapped label lines.
The focused regression reproduces the excess right-side space before the fix.

2026-10-11 follow-up: draw the complete capybara in front of the panel so its
paw tips hang slightly over the edge, per the user's latest preference.
Preserve the existing measured overlap and responsive size. This supersedes
the earlier foreground-panel interpretation; the original PNG remains intact.

2026-10-10 follow-up: restore the Home design's capybara/panel overlap after the
user identified the floating mascot. Keep the responsive mascot size and group
it with the rhythm panel so their overlap scales together. The new visual
regression covers regular, tall, short, wide, and larger-text viewports.

## Design Notes

The exported pill is 347×86 including a 14dp horizontal shadow margin around its 319×58 body. Center the whole asset rather than treating the transparent extent as the visible pill. iOS already reserves 92pt below content; its Home is a separate Swift surface. Correct native spacing without duplicating the Android reservation inside shared Rewards.

## Verification

**Commands:**
- `./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --no-configuration-cache --console=plain` — all fresh UI XML results pass, including camera regressions.
- `./mobile/gradlew -p mobile :apps:androidApp:assembleProductionDebug --no-configuration-cache --console=plain` — package succeeds.
- `./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --no-configuration-cache --console=plain` — shared UI compiles on this Intel host.
- `./mobile/gradlew -p mobile qualityCheckJvmAndroid qualityCheckApple --continue --no-configuration-cache --console=plain` — inspect fresh reports, report any remaining failures accurately.
- `graphify update .` and `git diff --check` — updated graph and clean whitespace.

**Visual checks:** Production composable PNGs at regular, tall, short, wide, and larger-text sizes; compare equal margins, card flow, icon baselines, and visible last reward after scrolling. Native execution requires the unavailable full Xcode/device tooling and must be reported as unverified.

## Review Outcome

Independent full-diff reviews produced 11 implementation patches and one pre-existing iOS navigation deferral. All patches were verified by the relevant shared tests, Android quality/package checks, or explicitly limited Apple source/syntax checks. No product-intent loopback or remote mutation was needed. Final evidence is in [home-rewards-spacing-2026-10-10.md](home-rewards-spacing-2026-10-10.md).

The subsequent foreground-feet correction and its verification are recorded
in [home-mascot-foreground-2026-10-11.md](home-mascot-foreground-2026-10-11.md).

The care button centering correction and its verification are recorded in
[home-care-button-centering-2026-10-11.md](home-care-button-centering-2026-10-11.md).

## Suggested Review Order

**Shared layout and insets**

- Reserve navigation once while the backdrop covers the viewport.
  [PhotoAnalysisMainLayout.kt:21](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt#L21)

- Pass platform insets into the shared tab shell.
  [PhotoAnalysisComposeActivity.kt:517](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt#L517)

**Measured Home content**

- Keep foreground content measured and scrollable with symmetric gutters.
  [HomeScreen.kt:45](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L45)

- Keep the capybara's feet in front of the panel while retaining the measured overlap.
  [HomeScreen.kt:114](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L114)

- Center care button icons and labels while retaining equal button widths.
  [HomeCarePrompt.kt:99](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeCarePrompt.kt#L99)

- Align metric rows and value/unit baselines as text grows.
  [HomeRhythmCard.kt:48](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeRhythmCard.kt#L48)

- Stack the next action when width or text size requires it.
  [HomeNextStep.kt:38](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeNextStep.kt#L38)

**Rewards and navigation**

- Match card gutters and reserve space below the final reward.
  [RewardsScreen.kt:28](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt#L28)

- Mirror tab centers and keep navigation labels within the pill.
  [PhotoAnalysisMainBottomBar.kt:50](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt#L50)

**Camera state and input**

- Retain explicit choices and reset only after accepted photo transitions.
  [PhotoAnalysisFeature.kt:59](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisFeature.kt#L59)

- Hit visible sticker pixels while allowing ancestor scrolling.
  [FoodStickerHitTesting.kt:43](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/FoodStickerHitTesting.kt#L43)

- Let review content grow without changing the default Figma arrangement.
  [PhotoAnalysisMealReview.kt:61](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt#L61)

**Native integration**

- Keep successful review completion separate from cancellation cleanup.
  [PhotoAnalysisViewController.kt:18](../../mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisViewController.kt#L18)

- Apply consistent spacing to the existing Swift Home surface.
  [PhotoAnalysisHostSupport.swift:101](../../mobile/apps/iosApp/PhotoAnalysisHostSupport.swift#L101)

**Verification**

- Exercise mascot overlap, responsive geometry, larger text, navigation, insets, and progress.
  [PhotoAnalysisLayoutVisualTest.kt:36](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLayoutVisualTest.kt#L36)

- Cover lifecycle restoration, pointer routing, scroll reachability, and touch targets.
  [PhotoAnalysisCameraEdgeCasesTest.kt:38](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraEdgeCasesTest.kt#L38)

- Verify degradation, full evidence semantics, and localized confirmation labels.
  [PhotoAnalysisReviewEvidenceTest.kt:20](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisReviewEvidenceTest.kt#L20)
