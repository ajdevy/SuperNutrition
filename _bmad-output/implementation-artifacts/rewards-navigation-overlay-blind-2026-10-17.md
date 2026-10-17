# Rewards navigation overlay — Blind Hunter review

Reviewed on 2026-10-17 using `.agents/skills/bmad-review-adversarial-general/SKILL.md` as the `bmad-quick-dev` step-04 Blind Hunter pass. The requested review scope takes precedence over the skill's numerical findings quota: report actionable defects introduced by this patch; do not manufacture findings.

## Scope and identity

- Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`.
- Branch: `codex/figma-camera-flow`.
- HEAD: `5ee5de077866feed70b418ee2760af24322b1d22`.
- Checkout state: extensive pre-existing tracked and untracked WIP. That WIP is outside this review except where current surrounding source explains the scoped patch.
- Patch: `mobile/build/rewards-navigation-overlay.diff`.
- SHA-256 verified: `356f7ee962a73ed57bb9208efcc6b02cc37a19bd2de605f2117e150d845dc7bb`.
- Acceptance reference: `_bmad-output/implementation-artifacts/spec-rewards-navigation-overlay.md`.
- Architectural references: the architecture spine's presentation, scoped ownership, accessibility, and UI-evidence rules (AD-4, AD-15, AD-23, AD-34).

## Findings

No actionable introduced defect found in the scoped patch after a second static pass. This is a bounded static-review result, not a claim that the UI or full worktree passes execution checks.

## Grounded review checks

- `PhotoAnalysisMainLayout.kt:33-45`: the host padding is applied and consumed on one enclosing safe-content Box. On Rewards, the content and navigation are siblings; navigation is composed after content and bottom-aligned, so the patch removes the reserved lower content boundary while retaining the controls above the list.
- `PhotoAnalysisMainLayout.kt:46-49`: Home still uses a weighted content Box followed by the navigation bar. The additional enclosing safe-content Box does not change the reserved-content calculation. The current Android caller passes `WindowInsets.safeDrawing.asPaddingValues()` at `PhotoAnalysisComposeActivity.kt:569-583` and selects Rewards with tab value `1`; the new branch matches that caller.
- `PhotoAnalysisMainBottomBar.kt:48,57-74,134-143`: the shared height remains the existing 83 dp. The bar's bounded width, positions, click callbacks, and semantic roles are unchanged. Its outer containers and decorative Image have no newly introduced pointer handler; only the existing button regions are clickable. No new full-width gesture-catching or painted rectangle is added over Rewards.
- `RewardsScreen.kt:30-34`: adding the same 83 dp to the existing 16 dp end padding allows the last row to scroll above the navigation canvas. In an ordinary viewport, its final bottom at the scroll limit is 16 dp above the canvas's top. The list itself retains the full safe-content height instead of subtracting that padding from the viewport.
- `PhotoAnalysisMainLayout.kt:19-20,35-37` and `RewardsViewController.kt:11-13`: the overlap defaults to zero and is provided only by the main shell's Rewards branch. The standalone iOS Rewards controller therefore retains its original 16 dp end padding.
- `RewardsNavigationOverlayTest.kt:27-79`: the two new regressions exercise the safe list boundary, actual control taps, a rendered card pixel beside the pill, and a scroll gesture initiated there. Existing large-text and landscape end-clearance cases remain in `PhotoAnalysisLayoutVisualTest.kt:314-345` and `AdditionalInteractionLayoutsTest.kt:188-207`. Their source was inspected; their runtime results were not claimed here.

Source locations above are repository-relative, one-based locations in the current surrounding source inspected during this review.

## Method and limitations

Read the exact patch, its spec, relevant architecture rules, and current callers/tests. Used the existing graphify index for navigation with expanded graph-vocabulary terms `[rewards, main, layout, navigation]` and `graphify query "rewards main layout navigation" --budget 1800`; its bounded output contained the layout, bar, Rewards, host, and existing layout-test nodes. Graph locations predate the patch, so current source supplied the locations above. Graph query token budget was 1800; no model token charge was reported by the CLI.

Rechecked likely regression paths: safe padding, sibling drawing and hit-testing, unchanged navigation actions, Home geometry, tab branch selection, standalone Rewards, and end clearance. No build, Gradle task, emulator, device, or test execution was performed by this reviewer. Actual rendering, native accessibility, and execution at the required viewport/font-size matrix remain the parent agent's verification responsibility. Historical rewards failures from another worktree were used only to orient the review and were not treated as evidence against this patch.

Only this findings report was written. Production, tests, graph sidecars, unrelated WIP, staging, and Git history were left untouched.
