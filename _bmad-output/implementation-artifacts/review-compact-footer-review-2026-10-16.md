# Compact review footer review — 2026-10-16

Read-only adversarial review of N-25 using the project `bmad-review-adversarial-general` skill. Compared `mobile/feature/photo-analysis-ui/build/reports/review-compact-footer/PhotoAnalysisMealReview.before.kt` with the current source and reviewed `PhotoReviewCompactViewportTest.kt`. No production/test edits, Gradle runs, or device interaction were performed.

Worktree `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`; branch `codex/figma-camera-flow`; HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. Unrelated changes are excluded.

## Findings

- Existing tests still scroll the newly pinned footer. At `PhotoAnalysisMealReview.kt:81–82`, only the header/food region has the scroll ancestor at line 97; the manual field, Retake, and Add to meal now sit outside it. Footer `performScrollTo()` calls remain in `AdditionalInteractionLayoutsTest.kt:184,228,229,231,233` and `PhotoAnalysisCameraScreenTest.kt:81,107,109,112,115,152,178`. These calls require a scrollable ancestor and need to become direct visibility/input/action assertions. Preserve `performScrollTo()` for food/dish/custom controls inside the scroll region. This is an introduced test migration issue, not a defect in pinning the footer.
- The header/food region can become completely unreachable at very short usable heights. `PhotoAnalysisMealReview.kt:77–82` gives the footer unweighted priority while retaining 62.04dp of fixed top/bottom padding. Its normal minimum footprint is already 4 + 45.2 + 18.6 + 48 = 115.8dp (`:163–165`, `:340`, `PhotoAnalysisReviewActions.kt:34,58–63`). At approximately 178dp or less of usable height, the weighted region has no height; taller wrapped action labels can reach this boundary earlier. The old whole-content scroll retained access to dish/food choices in this condition. Add an adaptive compact-height policy or reachable overflow fallback, and cover a viewport below that boundary. This is a conditional production regression established from the constraints; it was not reproduced on a device by this reviewer.

## Optional test improvement

- `PhotoReviewCompactViewportTest.kt:38–42` checks clipped `boundsInRoot`, whose containment alone does not establish that an input is fully visible. The current exclusion of the intrinsic-height text field from the 48dp button assertion is appropriate. Comparing unclipped field bounds with the visible bounds would strengthen the full-visibility claim; native IME geometry remains necessary because this JVM case substitutes a shorter viewport for a real keyboard.

## Preserved behavior and evidence

No food/header/evidence content, selection callback, confirmation payload, automation ID, or focus requester was removed. The independent foreground Close remains in `PhotoAnalysisCameraScreen.kt:95–101`; retained-photo fitting is unchanged. At ordinary heights where the content fits, positions are preserved: if inner height is H, food/evidence group height is S, and footer height is F, both layouts place the food group at H−S−F and the footer at H−F. The new 360×414dp test covers font scales 1 and 2 and scrolls the custom dish into view.

The archived `red` and `red-focused` compact-test XMLs each record **1 test, 1 failure, 0 errors, 0 skipped**, at `2026-10-16T08:28:22.093Z` and `2026-10-16T08:30:52.171Z`. They were read, not run by this reviewer. Graph navigation used vocabulary-confirmed `meal review footer input actions`; the baseline and current source supplied the review evidence. No fresh green test, native IME, or device result is claimed here. Both concrete findings were sent to the implementing agent before this report was saved.

## Final disposition of the original findings

- Footer test migration is closed. The original Add/Retake/manual-field `performScrollTo()` calls were removed from `AdditionalInteractionLayoutsTest.kt` and `PhotoAnalysisCameraScreenTest.kt`. Remaining scroll actions target food/dish/custom controls; the new very-short fallback test intentionally scrolls the footer because that mode gives it a scroll ancestor.
- Zero-height food/header reachability is closed. `PhotoAnalysisMealReview.kt:59–64,80–93` shares the note height, gaps and outer padding with the rendered footer, calls the same `reviewActionsHeight` used by the buttons (`PhotoAnalysisReviewActions.kt:48,58–78`), and requires at least 64dp of scrollable body before pinning. Smaller heights use the same `MealReviewScrollableContent` with a footer lambda, preserving the content and avoiding a duplicated renderer. The new 170dp/100% and 200dp/200% cases exercise this fallback.
- The optional clipping check is addressed: the 414dp test compares `getUnclippedBoundsInRoot()` height with visible bounds, while preserving the 48dp requirement for buttons rather than the intrinsic text field.

Read the UI XML replay: **105 tests, 0 failures, 0 errors, 0 skipped**, across 19 report files, written at `2026-10-16T09:45:26Z`; the compact class contributes two passing methods, including both fallback configurations. The three scoped detekt XMLs contained zero findings and were newer than their source inputs at that replay's cutpoint. These are saved implementing-agent results, not executions by this reviewer. The transition test described below was subsequently added at `2026-10-16T10:14:54Z`, so the 105-test replay and preceding JVM-test lint do not cover that addition.

## Remaining finding from final verification

- Crossing the pinned/fallback threshold while the note is focused replaces the focused field. `PhotoAnalysisMealReview.kt:85–93` composes `MealReviewFooter` under different parents in the two branches. `ReviewNoteInput` at `:360–364` only attaches the remembered `FocusRequester`; it does not preserve the field subtree or restore focus after replacement. An IME opening/resizing or window-height change that crosses this boundary therefore drops the active field's focus. The direct-start 170/200/414dp tests do not cover this transition. Preserve the focused footer across layout modes or explicitly restore its active focus, and verify a focus/typed-text transition through 780→414→170→414→780dp. The implementing agent was informed and has added `resizing a focused review preserves the manual field and text` (`PhotoReviewCompactViewportTest.kt:31–58`); this reviewer has not executed that case or read a result for it. This finding remains open at this disposition.

## Closure of the third finding

The focus finding is now **closed**, superseding its open disposition above. Current `PhotoAnalysisMealReview.kt:83–96` keeps one unconditional outer `Column` and one unconditional `MealReviewFooter` call. The pinned/overflow policy changes only the outer and body modifiers (`:84–87,94,111–114`), preserving the field's composition parent and identity while retaining both scroll behaviors. No focus-restoration workaround or duplicate footer is required.

Read the saved `focus-red` XML: **1 test, 1 failure, 0 errors, 0 skipped**, timestamp `2026-10-16T10:16:23.651Z`; the resize assertion records `Focused = false` while both editable/input text remain `apple`. The final `verified-stable-green/xml` replay has **106 tests, 0 failures, 0 errors, 0 skipped** across 19 UI report files. Its compact class has all **3 tests passing**, timestamp `2026-10-16T10:35:50.501Z`, including the focus/text transition through 780→414→170→414→780dp. The final production source mtime is `2026-10-16T10:21:58.255409Z`, before that replay. All six archived scoped check XMLs contain zero findings.

All three concrete N-25 findings are closed; no remaining concrete finding was identified in this verification scope. These results were read from the implementing agent's saved reports. This reviewer performed no builds, device actions, or production/test edits and makes no independent native-IME execution claim.
