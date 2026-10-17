# N-29 Close-clearance edge review

Date: 2026-10-17. Independent Edge Case Hunter review using `.agents/skills/bmad-review-edge-case-hunter/SKILL.md`, including its deletion check. Static source review only; no builds, tests, device execution, source edits, staging, or commits performed by this reviewer.

Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`. Branch: `codex/figma-camera-flow`. HEAD: `5ee5de077866feed70b418ee2760af24322b1d22`. The checkout contains extensive unrelated tracked and untracked WIP.

Reviewed input: [review-close-clearance-scoped.diff](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/build/review-close-clearance-scoped.diff), against the preserved pre-N-29 source snapshot, rather than the checkout's unrelated full diff. The initial input SHA-256 was `7ede8c47915deec98664f30d43ca2f2491ec4e7decac533b3f181b8895df9c3f`. The dp boundary-correction checkpoint used SHA-256 `910268ede6d32b778c8bfb9c1314ab5ce2a11044419a866cdae4e8166bbaf3bf` and is superseded by the fractional-density finding below. The final independently reviewed pixel correction uses SHA-256 `bbf5fe3efd4b0d919581091ea70b8568b7151ee141c107cadfe84ad5158f13eb`. The architecture spine and [N-29 spec](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/_bmad-output/implementation-artifacts/spec-review-close-clearance.md) were read. Existing graph vocabulary was used for a read-only `graphify query` before following referenced source functions.

## Original finding — resolved in the final patch

The following finding applies to the initial input digest above and is retained as review history.

```json
[
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt:87-98",
    "trigger_condition": "Compact review height leaves less than a 48 dp viewport after Close clearance.",
    "guard_snippet": "val closeClearance = if (pinFooter) 0.dp else minOf(ReviewCloseTopPadding + CameraMinimumTouchSize, (maxHeight - CameraMinimumTouchSize).coerceAtLeast(0.dp))",
    "potential_consequence": "Actions lose their minimum visible target; at 54 dp every review control disappears."
  }
]
```

The introduced arithmetic reserves `minOf(maxHeight, 6.dp + 48.dp)` in the compact path and uses the remainder for both the scrolling viewport and focused-note relocation. At `maxHeight = 100.dp`, the viewport is `46.dp`; [ReviewActions](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisReviewActions.kt:38) requires at least `48.dp` per action, so no scroll position can expose even 48 dp of an action. For positive heights up to `54.dp`, the viewport becomes zero and the padding consumes all available height. The entire review form remains composed but its clipped scroll viewport cannot expose a choice, input, or action. [KeepFocusedReviewNoteVisible](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisReviewNoteFocus.kt:25) selects a requester; it cannot enlarge that viewport.

The [preserved source](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/build/reports/review-close-clearance/before/PhotoAnalysisMealReview.kt:86) gave the compact form the entire available height. These capacity losses were therefore introduced by the initial N-29 change. After this review, the implementing agent reproduced both conditions before correcting production code. The archived [boundary-red XML](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/build/reports/review-close-clearance/boundary-red/test-results/TEST-com.supernutrition.feature.photoanalysis.PhotoReviewCompactViewportTest.xml:1) was independently read: **8 tests, 2 failures, 0 errors, 0 skipped**, timestamp `2026-10-17T05:56:24.695Z`. The failures are the undisplayed input at 54 dp and Retake losing its 48 dp visible touch viewport at 100 dp. The archived run metadata records exit code 1. These executions were performed by the implementing agent, not this reviewer.

## Intermediate dp correction checkpoint — superseded

The dp revision capped Close clearance at the nonnegative height remaining after one 48 dp touch viewport. This static checkpoint traced the following ideal dp paths for compact height `H`:

| Available height | Close reservation | Scroll viewport |
| --- | --- | --- |
| `0 <= H <= 48 dp` | `0 dp` | `H` |
| `48 dp < H < 102 dp` | `H - 48 dp` | `48 dp` |
| `H >= 102 dp` | `54 dp` | `H - 54 dp` |

At density 1, the 54 dp case retained 48 dp with 6 dp reserved, and the 100 dp case retained 48 dp with 52 dp reserved. This review's earlier conclusion that the dp expression preserved the minimum at every density was incomplete: it missed independent rounding of the reservation and target to pixels. The later executable 215 dpi failure below supersedes that conclusion. A zero-height host was unchanged from the pre-N-29 source. The 206 dp case at density 1 retained a 54 dp reservation and 152 dp viewport; the pinned path retained zero reservation and its original full height.

The focused-note requester receives the same effective height used by the scroll container. Across the 48–102 dp capped range, that height stays constant while the outer padding changes; the note and its viewport move together, so a new relocation request is not required merely for their root offset. Transitions into or out of that range change the effect's viewport key when its available height changes. The existing focused field, footer instance, and independent Close callback remain intact.

That revision added the [54 dp reachability regression](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:62), strengthened the [100 dp oversized-footer check](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:203) to require 48 visible pixels at density 1, and retained the [206 dp complete-note separation check](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:92). The line links now point to their locations in the pixel revision. No tests were executed by this reviewer.

Findings returned at that static-only checkpoint, subsequently superseded:

```json
[]
```

## Fractional-density finding — resolved in the pixel revision

The independent Blind reviewer identified this second boundary; it was not discovered by this Edge reviewer at the previous checkpoint. The implementing agent reproduced it before changing the dp implementation. The archived [density-red XML](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/build/reports/review-close-clearance/density-red/test-results/TEST-com.supernutrition.feature.photoanalysis.PhotoReviewCompactViewportTest.xml:1) was independently read: **9 tests, 1 failure, 0 errors, 0 skipped**, timestamp `2026-10-17T06:02:39.924Z`. The failure is `fractional density preserves the minimum action viewport in pixels`, with Retake losing one pixel of its target. Archived run metadata records exit code 1.

At density `215 / 160 = 1.34375`, the 48 dp minimum rounds from 64.5 to 65 px. For a 134 px parent, the earlier dp reservation rounded independently to 70 px, leaving a 64 px physical viewport. Meanwhile its nominal 48 dp relocation height rounded to 65 px. The physical viewport and the requester comparison therefore disagreed by one pixel. This is an executable introduced defect in the intermediate dp revision, rather than a hypothetical density concern.

The historical finding is captured here with the Edge output fields; its source location applies to the superseded dp digest:

```json
[
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt:87-98",
    "trigger_condition": "Fractional density independently rounds the compact reservation and minimum touch target in opposite directions.",
    "guard_snippet": "val clearancePx = minOf(desiredPx, (constraints.maxHeight - minimumTargetPx).coerceAtLeast(0)); val viewportPx = constraints.maxHeight - clearancePx",
    "potential_consequence": "The scroll viewport becomes one pixel shorter than the minimum action target."
  }
]
```

## Final pixel patch re-review

The final [calculation](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt:87) uses integer `constraints.maxHeight`, the rounded minimum target, and the rounded desired clearance. Let `N` be parent height in pixels, `T = roundToPx(48.dp)`, and `D = roundToPx(54.dp)`. For the compact path:

| Parent height | Clearance pixels | Viewport pixels |
| --- | --- | --- |
| `0 <= N <= T` | `0` | `N` |
| `T < N < T + D` | `N - T` | `T` |
| `N >= T + D` | `D` | `N - D` |

The capped case is computed by integer subtraction before either layout dimension is converted to dp. At the reproduced 215 dpi boundary, `N = 134`, `T = 65`, and `D = 73`, so clearance is `min(73, 134 - 65) = 69 px` and viewport is `65 px`. The same integer viewport supplies both `.heightIn(min = viewportHeight)` and `KeepFocusedReviewNoteVisible`; it no longer compares a nominal 65 px requester height against a 64 px visible area. The [fractional-density regression](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt:32) checks both actions against 65 visible pixels at this exact density and parent height.

The final trace revisited saturation below one target, exact target and clearance boundaries, ordinary positive compact heights, pinned layout, conversion of final integer dimensions to modifier dp, the requester choice, font-scale-dependent footer size, compact/pinned transitions, and the independent Close callback. At density 1 the 54, 100, and 206 dp cases retain their intended dimensions. The focused field and action callbacks are unchanged. The deletion check found no separate lost behavior. No remaining introduced unhandled path was identified in the final scoped pixel diff.

Final Edge Case Hunter findings for SHA-256 `bbf5fe3efd4b0d919581091ea70b8568b7151ee141c107cadfe84ad5158f13eb`:

```json
[]
```

This final result is static review only. The implementing agent's full replay was still active; no final green build, native execution, or device result is asserted by this reviewer.

## Scope and limits

The path traces covered the compact and pinned branches, the exact pinning boundary, positive and saturated clearance ranges, the note-versus-footer requester decision, font-scale-dependent action height, keyboard shrink/grow transitions, `CANDIDATE_REVIEW` and retained-photo `READY`, and the independent Close callback. Both recorded introduced defects are resolved by the final integer arithmetic. The previous dp-only no-finding checkpoint is retained as history and is not final acceptance evidence. The replacement of the 48 dp and 6 dp literals preserves their existing values.

An arbitrarily tiny height cannot simultaneously fit a 48 dp Close target and the fixed 45.2 dp note vertically. Full separation in that impossible range is not a claimed requirement; preserving scroll reachability was the missing handling identified above and is now restored. Existing fixed note height at extreme font scales and pre-existing pinned-body overlap possibilities are outside this scoped change. Future larger Close artwork, unusual host constraints, and native inset differences were not asserted as defects without current evidence.
