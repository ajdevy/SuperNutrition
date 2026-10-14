---
title: 'Correct the brand accent and floating tab bar'
type: 'bugfix'
created: '2026-10-13'
status: 'done'
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="User explicitly requested these three corrections in a separate worktree">

## Intent

**Problem:** The Home wordmark still draws the ordinary dot under an incorrectly positioned sprout. Selecting or pressing a navigation tab recolors its label/background. Rewards reserves an opaque, full-width navigation strip, hiding content around the shaped pill.

**Approach:** Anchor the sprout over the first i and remove that ordinary dot. Give selected icons the existing accent while retaining fixed label colors and a transparent touch surface. Float navigation over Rewards, with sufficient scroll padding for the last card.

## Boundaries & Constraints

**Always:** Work only in the isolated checkout. Preserve current source/assets, navigation callbacks, accessibility tab roles and selection, large touch targets, reward order/progress, Home layout, and host-owned system insets. Keep the existing pill artwork. Keep changes reviewable separately from the imported parent WIP.

**Ask First:** Publishing or remote Git operations.

**Never:** Change the parent checkout, reward rules, app routes, or quality policy. Do not claim device or independent-agent evidence from host checks. Device installation and focused verification were authorized by the 2026-10-14 follow-up below.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Home brand | Normal, narrow, wide, or enlarged text | First i has the sprout in place of its dot; second i retains its dot | Position from measured text |
| Tab selection | Home or Rewards clicked/pressed | Only selected icon changes color; labels and touch surfaces stay constant | Keep tab roles and state |
| Rewards | Normal, short, or wide viewport with system insets | Content continues behind the shaped nav pill; no full-width light strip | Background covers insets |
| Last reward | Scroll to final row with normal or larger text | Full card clears the camera/pill and remains reachable | Reserve bottom scroll padding once |
| Home navigation | Home is selected | Existing reserved navigation layout and action clearance survive | Preserve existing measurements |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/.../HomeHeader.kt` — measured wordmark and sprout in physical glyph coordinates.
- `mobile/feature/photo-analysis-ui/.../PhotoAnalysisMainBottomBar.kt` — label/icon colors, press indication, shared canvas height.
- `mobile/feature/photo-analysis-ui/.../PhotoAnalysisMainLayout.kt` — tab-specific background, floating Rewards navigation, bottom inset provider.
- `mobile/feature/photo-analysis-ui/.../RewardsScreen.kt` — scroll padding beneath floating navigation.
- `mobile/feature/photo-analysis-ui/src/jvmTest/.../BrandNavigationVisualTest.kt` — targeted regressions and production composable screenshots.

## Tasks & Acceptance

**Execution:**
- [x] Record the observed findings and reproduce them with focused rendered checks.
- [x] Correct first-i glyph/accent placement across font scales and widths.
- [x] Keep labels/press surface constant while selected icons retain their accent.
- [x] Float the shaped bar over Rewards and retain final-card scroll clearance.
- [x] Run focused and full UI module tests, relevant compilation/lint, inspect fresh PNGs, update graphify, and check the diff.

**Acceptance Criteria:**
- Given the Home header, when text remeasures, then the accent follows the first i and replaces its ordinary dot.
- Given either tab, when clicked or held, then icon tint alone communicates selection without recoloring its label or rectangular touch area.
- Given Rewards, when rendered with insets or scrolled to its end, then its backdrop/content reach the bottom and its final card can clear navigation.
- Given the isolated worktree, when verification completes, then the original checkout remains untouched and the three corrections have their own reviewable patch.

## Verification

- `:feature:photo-analysis-ui:jvmTest` — inspect fresh XML per class and rendered screenshots.
- `:feature:photo-analysis-ui:compileAndroidMain` and `compileKotlinIosArm64` — compile affected shared source; Apple execution is optional.
- Module source-set Detekt tasks — inspect current reports without broad autocorrect.
- `graphify update .` and `git diff --check` — update only this checkout and inspect the correction delta.

## Review Notes

Subagents are prohibited in this side conversation. No independent subagent review will be claimed. The user's direct instruction authorizes implementation of these concrete corrections without another approval checkpoint.

## Worktree Baseline

The desktop created a managed checkout but could not attach it to this side conversation. Its Git metadata is outside the writable sandbox. A separate local repository and real worktree were therefore created under `/private/tmp` without modifying parent Git metadata. The index contains a byte-for-byte import of 558 existing parent WIP files; fixes remain unstaged so `git diff` isolates this task. The immutable import is under `/private/tmp/supernutrition-brand-tabs-baseline`, with a SHA-256 manifest in ignored `mobile/build/side-conversation-baseline.json`. No commit or remote write was made.

## Review Outcome

The three corrections and authorized follow-up are complete. Fresh UI evidence has 117 tests with zero failures/errors/skips, 99.5346% lines and 85.0498% branches, plus 100% coverage of the correction's 58 added executable lines and their branch outcomes. All current module source lint reports have zero findings. The production debug build was updated on the physical HONOR DNY_NX9 (API 36) and five focused Android checks passed, including native pixel comparisons and final-card clearance. Shared Android/iOS compilation from the initial correction is retained as separate evidence; this follow-up added tests only. Full product quality/merge readiness is outside this side task. See [the evidence report](brand-tabbar-rewards-2026-10-13.md).

## Suggested Review Order

- Float Rewards navigation while keeping system and scroll inset ownership explicit.
  [PhotoAnalysisMainLayout.kt:29](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt#L29)

- Keep the last card clear of the floating panel.
  [RewardsScreen.kt:33](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt#L33)

- Attach the sprout to the first dotless i in physical glyph coordinates.
  [HomeBrand.kt:41](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeBrand.kt#L41)

- Keep label and press-surface colors constant while icon tint follows selection.
  [PhotoAnalysisMainBottomBar.kt:131](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainBottomBar.kt#L131)

- Review failed-before/passed-after render regressions and live font/RTL coverage.
  [BrandNavigationVisualTest.kt:44](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/BrandNavigationVisualTest.kt#L44)

## Worktree Handoff

2026-10-14: The original repository worktree is now writable. The verified baseline and correction delta have been transferred to `/Users/REDACTED/.codex/worktrees/brand-tabs-rewards/SuperNutrition`, branch `codex/brand-tabs-rewards`. Source is identical to the prior tested checkout; earlier test execution paths remain documented truthfully in the evidence report.

## Authorized Follow-up — 2026-10-14

The user requested: “доделай покрытие и проверь на устройстве”. Complete the UI module's remaining branch coverage with executable state/interaction checks, then build/install the isolated correction and verify the three visual changes on the connected physical Android phone. This supersedes the earlier device restriction for this focused task. It does not authorize deleting installed app data, remote Git writes, changing quality policy, or resuming unrelated parent work.

- [x] Exercise previously missing live screen refresh, callback replacement, recovery, selection and research-surface paths with observable assertions.
- [x] Re-run the full UI module suite and produce fresh Kover XML with at least 85% lines/branches; retain 100% coverage of the correction's added executable lines/outcomes.
- [x] Build compatible Android app/test artifacts and check physical Home branding, icon-only tab selection/press feedback, Rewards backdrop and final-card clearance.
- [x] Record exact command, device/API, test counts and screenshot evidence, refresh the isolated patch, graph and diff check.
