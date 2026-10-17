# Final edge-case review — 2026-10-12

Reviewed the frozen full diff `/tmp/supernutrition-final-review.patch` and code
diff `/tmp/supernutrition-final-review-code.patch` against
`5ee5de077866feed70b418ee2760af24322b1d22`, in worktree
`/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`, branch
`codex/figma-camera-flow`.

Applied `bmad-review-edge-case-hunter`: received the text diffs, traced changed
control-flow and domain boundaries, revisited their implicit alternatives, and
performed the required secondary deletion check. Existing referenced functions
were read for context. This was a static review; no production changes, builds,
tests, device commands, or Git writes were performed.

## Findings

```json
[
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt:35-37",
    "trigger_condition": "Nonempty earned summary in a short window leaves no height after the fixed Rewards header",
    "guard_snippet": "LazyColumn { item { RewardsHeader(model) }; itemsIndexed(model.cards.chunked(columnCount)) { index, row -> renderRow(index, row) } }",
    "potential_consequence": "The rewards list receives zero height; users cannot reach any cards by scrolling."
  }
]
```

## Evidence and reproduction boundary

`RewardsScreen.kt:35` measures `RewardsHeader` as an unweighted child of the
outer `Column`; only the subsequent `LazyColumn` receives remaining height
through `weight(1f)` at line 37. The production presenter supplies a nonblank
earned summary (`RewardsPresenter.kt:48-50`). With the default English title at
font scale 1, the new header requests at least 176 dp: 16 dp top padding,
32 sp title line, 12 dp spacing, and a 116 dp minimum summary panel
(`RewardsHeader.kt:29-48`). The outer column adds another 12 dp before the list.
At font scale 1.5, the corresponding minimum is approximately 192 dp before
the outer spacing.

A 320 × 240 dp shell reserves 83 dp for its bottom bar, leaving at most 157 dp
for Rewards even before platform insets (`PhotoAnalysisMainLayout.kt:30-34`,
`PhotoAnalysisMainBottomBar.kt:50`). The header consumes that available height,
and the list receives zero. The header itself has no scrolling mechanism.
The previous title-only header left list space under the same constraints.

Acceptance condition: render the production model, including its earned
summary, in a constrained 320 × 240 dp shell; the final reward and its progress
must remain reachable through user scrolling. A scrollable header or explicit
height fallback closes this boundary. The existing short-height tests construct
`layoutRewardsModel()` with empty `earnedSummary`; the new summary test uses a
361 × 782 dp viewport, so neither exercises this combination.

Both native apps request portrait orientation. Ordinary phone rotation is
therefore not claimed as a trigger. Short Android multi-window or another
constrained shared renderer is the relevant boundary. Reproduction and native
consequence remain for root verification.

No additional deletion findings were retained. The final odd Rewards row is
explicitly documented and tested as centered; its full-width rendering was not
treated as an unhandled boundary or a new design requirement.
