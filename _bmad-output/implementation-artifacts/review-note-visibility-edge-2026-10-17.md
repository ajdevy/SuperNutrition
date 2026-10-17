# N-26 Edge Case Hunter review

Reviewed `mobile/build/review-focused-resize-scoped.diff` only, with referenced
layout/relocation functions inspected to validate the reachable path. Scoped
diff SHA-256: `9e3b94fe572fe1df57d195ec6ee2d119a4294ac62cdd2c5c9b913723df9f9d60`.
Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`; branch:
`codex/figma-camera-flow`; starting HEAD:
`5ee5de077866feed70b418ee2760af24322b1d22`. The checkout contained unrelated
tracked and untracked WIP.

## Findings

```json
[
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt:379-380",
    "trigger_condition": "Focused oversized footer in a viewport that fits the note row but not footer",
    "guard_snippet": "Row(Modifier.height(ReviewNoteHeight).bringIntoViewRequester(noteFocus.fieldRequester)) { ... }",
    "potential_consequence": "The note border and hit container remain partly below the viewport"
  }
]
```

## Evidence for the unhandled path

`KeepFocusedReviewNoteVisible` selects `fieldRequester` whenever the measured
footer exceeds the viewport (`PhotoAnalysisReviewNoteFocus.kt:31-34`). The new
attachment is on `BasicTextField`, whose modifier has no height or
`fillMaxHeight`, at `PhotoAnalysisMealReview.kt:379-380`. Its parent note Row
has a fixed `45.2.dp` height and vertically centers the child at lines 368-373.
Thus a single-line text child can be shorter than the visible note box.

For a footer below the current viewport, Compose's default relocation moves
the requested child's trailing edge to the viewport's trailing edge. If the
child height is `h < 45.2.dp`, the surrounding Row still extends approximately
`(45.2.dp - h) / 2` below that edge. For example, a 20 dp child in a 100 dp
viewport leaves about 12.6 dp of the note box below the viewport, even though
that viewport can accommodate the entire 45.2 dp note. The footer is about
146.9 dp at the minimum action height, so this example reaches the new fallback
branch. A fully visible child satisfies its relocation request; no subsequent
request targets the Row.

The oversized-footer regression at
`PhotoReviewCompactViewportTest.kt:64-89` asserts visibility only on the text
child and then scrolls the actions. It does not inspect the note Row bounds,
so it cannot reject this path. The minimal change is to attach the fallback
requester to the note Row while keeping `focusRequester` and focus observation
on `BasicTextField`.

The framework behavior was inspected in the published
[Compose Foundation 1.11.1 source archive](https://repo.maven.apache.org/maven2/org/jetbrains/compose/foundation/foundation/1.11.1/foundation-1.11.1-sources.jar):
`gestures/BringIntoViewSpec.kt:76-96`, `gestures/ContentInViewNode.kt:127-140`,
and `text/CoreTextField.kt:549-601`.

## Verification limits

This finding follows from the layout and default relocation geometry. The
actual text-child height and clipping have not been measured in a running
Compose test or Android guest during this review. If acceptance deliberately
requires only text visibility rather than the surrounding note box, the
remaining clipping is an acceptance-interpretation question for triage.

The required ordered path pass, completeness pass, and deletion check were
performed. No separate deletion finding was identified. No build, device,
emulator, production/test edit, or staging operation was performed. Dynamic
font/width-only reflow without a footer-height change was not runtime-replayed
and is not asserted here as an introduced defect.
