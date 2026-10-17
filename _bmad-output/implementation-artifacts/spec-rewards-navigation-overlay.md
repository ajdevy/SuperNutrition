---
title: 'Rewards content behind floating navigation'
type: 'bugfix'
created: '2026-10-17'
status: 'done'
route: 'one-shot'
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
---

# Rewards content behind floating navigation

## Intent and observed finding N-30

The user still sees an empty light band behind the floating Rewards navigation. The previous backdrop fix only matched the band to the screen color. `PhotoAnalysisMainLayout` places content and the 83 dp navigation canvas in a Column, so Rewards cannot draw or receive scrolling gestures in that lower canvas. Matching colors did not meet the requested behavior.

Render Rewards throughout the safe content viewport and place the existing floating navigation above it. Preserve the illustrated pill and its buttons. Reserve navigation clearance at the end of the scrollable list so the last reward can be raised entirely above the controls. Home retains its existing reserved content viewport. Keep all completed camera, review, food assets and unrelated worktree changes.

## Scope and code map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt`: overlay the existing bar for Rewards; consume host safe padding once; provide the list's navigation overlap through an internal composition local.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt`: share the existing canvas height with layout and list clearance, avoiding duplicated constants.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt`: apply the provided overlap to LazyColumn end padding, without shortening or painting over its viewport.
- `mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/RewardsNavigationOverlayTest.kt`: witness the viewport and rendering regression before changing production code; check scrolling outside the pill and navigation actions.

This is shared Compose presentation. It introduces no domain, persistence, permission, privacy, or platform routing changes. A standalone Rewards screen without the navigation shell keeps its current end padding. Existing Home and short/large-text Rewards tests remain applicable.

## Acceptance conditions

1. Given Rewards in the main shell with host safe insets, when displayed, then its list reaches the bottom safe boundary instead of ending at the navigation canvas's top. Insets are applied once and controls remain inside the safe viewport.
2. Given a reward card passing through the navigation region, when viewed outside the floating pill, then its content remains visible; a swipe there scrolls the list. The bar's buttons remain above the list and trigger their existing actions.
3. Given the final reward on compact, normal, large or tablet viewports, including enlarged text, when scrolled to the end, then its title, progress and state can be fully raised above all navigation controls.
4. Given Home or a standalone Rewards screen, when displayed, then the existing Home geometry and standalone list padding are preserved.

## Verification plan

Archive the three current production files before the patch because this worktree contains prior uncommitted work. Run the focused regression and preserve its failing XML; then implement, rerun the focused suite and the complete shared UI JVM suite. Check all six relevant UI lint tasks with `--continue`, compile `iosArm64` explicitly on this Intel host, and rebuild all Android flavors and instrumentation APKs in a single Gradle invocation.

Use one isolated API 35 x86_64 guest at a time for current-build screenshots and complete Android instrumentation. Confirm source/artifact hashes and exact fresh XML counts. Check Rewards navigation overlap and last-card clearance at multiple sizes and font scales. Stop guests before heavyweight independent work and remove only task-created temporary AVDs after saving evidence. Review only this patch against its saved dirty-worktree baseline; do not stage, commit, push or merge.

## Evidence

Focused red witnessed: both regressions fail on the preserved production baseline. Rewards ends at y=709 instead of the safe y=792 boundary; a passing reward is replaced by RewardsPaper inside the actual centered card beside the pill. Red XML and the exact run are archived under `mobile/feature/photo-analysis-ui/build/reports/rewards-navigation-overlay/corrected-red/`. Green and current-build emulator verification are pending. The previous N-29 build/native evidence remains historical after this patch.

The initial wide-window sample was outside the centered 430 dp content cap, so it was not accepted as rendering evidence. After correcting the sample/gesture using actual card, list and pill bounds, both tests were witnessed failing again on the exact archived production baseline. An unsuccessful diagnostic screenshot helper compilation executed no tests; it was removed and archived separately.

Corrected focused green: 2 tests / 0 failures / 0 errors / 0 skipped. Includes actual navigation pointer taps, rendered card pixels outside the pill, and scrolling initiated in that region. Full UI/lint/explicit Apple compilation, current artifacts and emulator replay are running next.

Full relevant build completed with exit 0 (6m08s): shared UI 114/0/0/0 across 20 fresh XML suites; six clean scoped lint reports; explicit `:feature:photo-analysis-ui:compileKotlinIosArm64` executed; all six Android app/test APKs built and hashed. Production SHA-256: `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Prior Domain 115/0/0/0 and Feature 25/0/0/0 evidence is retained only after verifying those source hashes unchanged; those modules were not rerun in this patch. Blind Hunter and Edge Case Hunter static reviews of the exact scoped patch found no actionable introduced defects. Current native and manual matrix checks remain pending.

## Final closure evidence

Current shared UI 114/0/0/0 and canonical Android 40/0/0/0 for each of POC, Production and UI Acceptance pass on production digest `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Current Large/tablet gallery and real-IME checks preserve banana/custom choice and `apple`, keep the full field clear of Close and expose both actions; compact N-29 actual-IME evidence concerns unchanged review sources. Rewards N-30 overlay and end clearance are verified at all four planned sizes and both font scales. Temporary guest cleanup and scoped limitations are in [the matrix report](emulator-screen-matrix-2026-10-17.md). Existing red/partial history is retained.

## Suggested Review Order

- Overlay Rewards while preserving Home and applying safe insets once.
  [PhotoAnalysisMainLayout.kt:24](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt#L24)

- Share the existing canvas height across drawing and list clearance.
  [PhotoAnalysisMainBottomBar.kt:48](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt#L48)

- Keep end clearance inside scrolling content.
  [RewardsScreen.kt:33](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt#L33)

- Witness visible cards, safe bounds, real control taps and corner scrolling.
  [RewardsNavigationOverlayTest.kt:25](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/RewardsNavigationOverlayTest.kt#L25)

Static reviews: [Blind Hunter](rewards-navigation-overlay-blind-2026-10-17.md), [Edge Case Hunter](rewards-navigation-overlay-edge-2026-10-17.md). No actionable introduced defect remains in their scoped final patch.
