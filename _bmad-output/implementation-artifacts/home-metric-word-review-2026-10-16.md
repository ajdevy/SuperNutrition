# Home metric word review — 2026-10-16

Read-only review using the project `bmad-review-adversarial-general` skill. Compared the explicit `HomeRhythmCard.before.kt` snapshot with the current `HomeRhythmCard.kt`, and reviewed the new `large Home metrics keep their standard titles whole` case in `AdditionalInteractionLayoutsTest.kt:62–87`. Related existing layout assertions were inspected only to identify regressions caused by this change. No source/test edits, Gradle execution, or device interaction were performed.

Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`; branch `codex/figma-camera-flow`; HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. Unrelated dirty work was excluded.

## Findings

- The new single-column layout invalidates an existing relevant visual test. `PhotoAnalysisLayoutVisualTest.kt:253–272` runs `metric rows retain equal card heights when values wrap` at 320dp and font scale 1.5, then requires Fiber/Water and Protein/Movement to share top/bottom bounds. `HomeRhythmCard.kt:68` now selects one column at that font scale, and line 70 places every metric in its own row. Their top coordinates must differ, so the old assertions cannot pass. Update this existing test to verify ordered, full-width single-column cards at large text, and keep a separate ordinary-font case that verifies paired rows. This is a verification regression, not evidence that the intended single-column UI is wrong; do not treat a passing new focused test as a passing affected module until the existing assertion is corrected.

No introduced user-visible defect was substantiated in the production change or the new focused test.

## Optional improvement

- The new test checks standard English label line integrity and text overflow at 360dp/200% and 280dp/100%. It does not itself assert ordinary-font paired geometry, value overflow, or final Home action clearance at 200%. Those are useful acceptance checks to preserve in the broader layout/device verification. Their absence from this one focused test does not demonstrate a current defect.

## Review reasoning

The relevant shared-presentation/accessibility architecture rules and N-24 acceptance in `additional-interaction-review-2026-10-16.md:109–115` were considered. Queried the existing graph with vocabulary-confirmed tokens `home rhythm grid metric card`, then verified the concrete code directly. Graph mutation was excluded from this read-only review.

Ten concrete risk avenues were checked; unsupported issues were not invented to satisfy the skill's numeric target:

- Item order remains Fiber, Water, Protein, Movement in both column modes.
- Each metric retains its original model field, icon, colors, and automation ID.
- Progress numerators remain 61, 77, 102, and 91 over the unchanged 132.75 design denominator; the shared fractional-fill rendering is unchanged.
- The two-column branch remains active when available grid width is at least 248dp and font scale is at most 1.2; ordinary 375dp phone layout retains it.
- The width decision uses space available inside the rhythm card, rather than the entire device width.
- Large-font and narrow layouts share the same item/card implementation, avoiding divergent copy, semantics, or progress code.
- `fillMaxWidth()` applies to the label's weighted text cell and leaves the icon's width and separation intact.
- Card minimum heights and intrinsic row height remain content-driven; the new `BoxWithConstraints` is outside the rows that query card intrinsic height.
- `HOME_SUMMARY`, the summary content description, and each metric root ID remain attached to their existing semantic surfaces.
- Home's existing vertical scrolling and navigation height reservation are unchanged, so the taller grid participates in normal scroll content rather than being assigned a fixed clipping height.

## Evidence and limits

The archived `mobile/feature/photo-analysis-ui/build/reports/home-metric-word/red-movement/TEST-com.supernutrition.feature.photoanalysis.AdditionalInteractionLayoutsTest.xml` records timestamp `2026-10-16T07:33:20.550Z`, **1 test, 1 failure, 0 errors, 0 skipped**, with `Movement -> [Movemen, t]`. This is the actual prior word-split failure detected by the new assertion. The implementation note also records a matching native screenshot; this reviewer did not operate that device or independently claim that native run.

The failing archived XML was read, not executed by this reviewer. The existing paired-row test regression above is established directly from the branch condition, one-item chunking, and contradictory bounds assertions; it has not been executed by this reviewer. Fresh full-module, compile, native, and device evidence belongs to the implementing agent. No green execution result is claimed here.
