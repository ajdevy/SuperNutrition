# Rewards tab backdrop review — 2026-10-16

Review performed with the project `bmad-review-adversarial-general` skill, restricted to the requested two changed slices. No production or test files were edited. No Gradle build, emulator, or physical-device interaction was performed by this reviewer.

Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`; branch `codex/figma-camera-flow`; HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. The worktree contains unrelated modified and untracked work, which was excluded from this review.

## Scope and acceptance

Compared `mobile/feature/photo-analysis-ui/build/reports/rewards-backdrop/PhotoAnalysisMainLayout.before.kt` with the current `PhotoAnalysisMainLayout.kt`. The production diff adds one conditional selecting `RewardsPaper` for tab 1 instead of the theme background. Also inspected only the added single-capture/pixel-continuity slice of `rewards cards and title share symmetric gutters` in `PhotoAnalysisLayoutVisualTest.kt:201–208`.

Acceptance is recorded in `rewards-tab-backdrop-2026-10-16.md`: one continuous Rewards background behind the floating pill and through safe-area padding, with unchanged navigation positions, actions, illustration, scroll clearance, and Home backdrop.

Read the relevant architecture-spine rules for shared presentation and platform/localized accessibility. Queried the existing graph with vocabulary-confirmed tokens `rewards main layout background bottom bar`, then verified the relevant code directly; the graph is navigation context, not evidence of test execution. Graph mutations were excluded from this read-only review.

## Findings

- No substantiated user-visible regression was found in the requested production or test changes. `PhotoAnalysisMainBottomBar.kt:88` and the Android host at `PhotoAnalysisComposeActivity.kt:578` identify tab 1 as Rewards. `RewardsScreen.kt:28` uses the same `RewardsPaper` declaration as the changed outer backdrop. This removes the seam without introducing a second color definition.

## Optional improvement

- The new assertion covers the navigation boundary at the fixed 375×812, density-1 viewport with zero `contentPadding`; it does not directly sample Rewards with nonzero host safe-area padding. If stronger regression coverage is desired, add Rewards to the existing inset test (`PhotoAnalysisLayoutVisualTest.kt:329`) and sample the top/bottom inset pixels against the content background. This is a test-coverage improvement, not evidence of a defect: the production background remains outside the padded foreground column and fills the whole viewport.

## Review checks

Re-examined the small diff across ten concrete risk avenues rather than inventing additional findings to meet the skill's numeric target:

- The Rewards tab index agrees between the visual tab selection and the Android host.
- The full-viewport Rewards color and content color share the same constant.
- The Home branch and illustrated backdrop are unchanged.
- The fallback theme color for other indices is unchanged.
- The background still fills the outer Box before foreground padding is applied.
- Content weight, height reservation, and scroll-clearance modifiers are unchanged.
- Pill illustration, camera/tab positions, sizes, and touch handlers are unchanged.
- No new semantics, locale text, interaction state, or platform dependency is introduced.
- At height 812, the unchanged 83dp navigation reservation starts at y=729; samples y=700 and y=740 straddle that boundary. The pill starts at x=14 in this fixture, so x=2 lies outside it.
- The assertion and output screenshot use the same captured frame; no second capture can diverge from the checked pixels.

## Evidence and limits

The saved red report `mobile/feature/photo-analysis-ui/build/reports/rewards-backdrop/red/TEST-com.supernutrition.feature.photoanalysis.PhotoAnalysisLayoutVisualTest.xml` records timestamp `2026-10-16T06:20:15.463Z`, **1 test, 1 failure, 0 errors, 0 skipped**, at the new continuity assertion. It shows paper RGB `(251,252,248)` above navigation versus the prior theme RGB `(254,247,255)` below it. This grounds the regression assertion in the observed old defect.

This reviewer read that report and did not execute the failing run. No green test, Android APK, native compilation, emulator, or physical-device result is claimed here. Those remain the implementing agent's verification responsibility. Earlier main-worktree redesign notes were used only to avoid importing stale assumptions; this review is grounded in the current requested worktree and its explicit before/current comparison.
