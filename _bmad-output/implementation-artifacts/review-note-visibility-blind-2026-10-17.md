# N-26 Blind Hunter review — 2026-10-17

Reviewed `mobile/build/review-focused-resize-scoped.diff` only, with current source and the architecture/spec used for context. No conversation history or other reviewer results were consulted. Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`; branch: `codex/figma-camera-flow`; HEAD: `5ee5de077866feed70b418ee2760af24322b1d22`. The checkout contained unrelated WIP, which this review preserved.

Scoped diff SHA-256: `9e3b94fe572fe1df57d195ec6ee2d119a4294ac62cdd2c5c9b913723df9f9d60`.

## Findings

- No introduced user-visible production defect was confirmed from the scoped change. This is a source review result, not a native execution result.

## Verified test limitations

- The newly added focused-note checks use `assertIsDisplayed()` at [PhotoReviewCompactViewportTest.kt:54](/Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:54) and [PhotoReviewCompactViewportTest.kt:86](/Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:86), without comparing the field's clipped and unclipped bounds. In the installed Compose UI test 1.11.1 implementation, `isDisplayed()` calls `checkIsDisplayed(..., false)`; it accepts partial visibility. A regression that leaves only part of the note visible could pass these new assertions. The older 414 dp test includes a full-bounds comparison, but does not exercise the new 206/100 dp resizing cases. This verifies a test-oracle gap; it does not establish that current production code clips the note.

- The new tests simulate resize by assigning a `Box.height` at [PhotoReviewCompactViewportTest.kt:53](/Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:53) and [PhotoReviewCompactViewportTest.kt:85](/Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:85). They do not drive real IME/safe-drawing insets or an animated IME transition. Therefore they cannot establish native timing behavior of the one-frame delay at [PhotoAnalysisReviewNoteFocus.kt:30](/Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisReviewNoteFocus.kt:30). Native execution remains necessary before claiming that behavior verified. No timing defect was reproduced by this reviewer.

## Re-analysis of failure candidates

- Footer clipping from the padding move: the footer requester and `onSizeChanged` precede the bottom-padding modifier, so their measured/requested rectangle includes that padding.
- Loss of the 48 dp action hit height: the action implementation and width-aware height calculation remain unchanged; the pinned threshold still includes the same footer dimensions.
- Loss of focus when scroll policy changes: the note remains under a single footer call and stable composition parent; the new state/requesters are remembered outside the conditional modifier.
- Relocation to a removed scrolling ancestor: the new effect waits a frame after focus/viewport/size changes before requesting relocation; no source-level stale-node failure was established.
- Initial zero footer measurement selecting the wrong requester permanently: measured size is an effect key, so a nonzero size invalidates the initial decision.
- Treating physical screen height as the usable viewport: the height comes from the `BoxWithConstraints` inside `safeDrawingPadding().imePadding()`.
- Oversized footer hiding the focused note: the branch explicitly requests the field instead of the entire footer when the measured footer exceeds the viewport.
- Automatic snapping after every manual scroll: scroll position is absent from the effect keys. User scrolling alone does not restart this effect.
- Competing footer/caret relocation requests: inspected the installed Compose foundation 1.11.1 request queue; contained rectangles are queued together, so no conflicting non-overlap cancellation was established for the footer and its child field.
- Changes to confirmation, selections, typed-value binding, Retake, Close, or retained-photo layout: the diff preserves those callbacks/data paths and does not modify the independent Close overlay or photo renderer.

The adversarial skill's ten-issue quota was not converted into invented defects. Ten candidate failure paths were re-analyzed after the initial zero-finding pass; the two verified limitations above remain distinct from production findings.

## Evidence boundaries

Read-only source inspection, `graphify query`, and installed-library bytecode inspection were performed. No Gradle invocation, test execution, device/emulator interaction, staging, or production/test edit was performed. The root agent owns build and native verification. This report is the only file written by this reviewer.
