---
review: blind-hunter
skill: bmad-review-adversarial-general
date: 2026-10-17
status: complete-static-review
scope: N-28 ignored background invalidation
introduced_defects: 0
preexisting_validation_improvements: 1
executed_behavior_checks: 0
---

# N-28 Blind Hunter review

No introduced production or test defect was substantiated in the scoped diff. The change preserves existing accepted/ignored event semantics, route cancellation, typed live review, and the separate Capture-only process-restore guard. One pre-existing assertion gap is recorded below as a validation improvement; it is not an introduced defect or a merge blocker.

## Scope and environment

- Worktree: `REDACTED_WORKTREE/a704/SuperNutrition`.
- Branch: `codex/figma-camera-flow`; HEAD: `5ee5de077866feed70b418ee2760af24322b1d22`.
- Host architecture: `x86_64` (`uname -m`). The worktree was already dirty with extensive shared WIP. No unrelated files were changed, staged, or reverted.
- Review input: [review-ignored-invalidation-scoped.diff](REDACTED_WORKTREE/a704/SuperNutrition/mobile/build/review-ignored-invalidation-scoped.diff), 98 lines; SHA-256 `5faf5e422d50f036c3245ae2b7b16ba90ed441aab103bfc1b77e5bad2a65f9a8`.
- Contract: [spec-review-ignored-invalidation.md](REDACTED_WORKTREE/a704/SuperNutrition/_bmad-output/implementation-artifacts/spec-review-ignored-invalidation.md), plus the canonical `AGENTS.md` and architecture spine, particularly AD-5, AD-6, AD-8, AD-18, AD-31, AD-33 and immutable/session-scoped state conventions.

The reviewed diff contains only the common reducer maintenance change and extensions to `StagedComponentReviewTest` and `PhotoAnalysisMealReviewFeatureTest`. Necessary reference inspection was limited to reducer transitions/lifecycle/state/effect context, `PhotoAnalysisFeature`, `RouteAttemptCoordinator` in `LocalFirstRouting.kt`, and directly relevant existing domain/feature assertions. The archived `before/PhotoAnalysisReducer.kt` and `before/PhotoAnalysisMealReviewFeatureTest.kt` were checked to distinguish introduced behavior from pre-existing behavior. The rest of the dirty branch was not reviewed.

The installed adversarial skill was used for skeptical analysis. Its generic ten-issue quota was not used to manufacture findings; the explicit assignment requires substantiated defects and accurate classification.

## Findings

No introduced defect findings.

## Pre-existing validation improvement

- The extended retained-selection case restores a nonempty `"Current note"` and then dispatches accepted foreground `ScanAgain`, but its final assertion checks only the empty selection. Add an assertion that `manualIngredientStateFlow.value.query` is empty after that final Retake to directly cover the specification's requirement that accepted foreground Retake clears both selection and note. [Current test](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReviewFeatureTest.kt:180). This omission already exists in the [archived before test](REDACTED_WORKTREE/a704/SuperNutrition/mobile/domain/photo-analysis/build/reports/review-ignored-invalidation/before/PhotoAnalysisMealReviewFeatureTest.kt:165). The existing feature reset branch clears the entire manual-input state and selection together, so this is an assertion gap rather than evidence of incorrect production behavior.

## Static candidates examined and disposition

| Candidate | Source assessment |
| --- | --- |
| Ignored background replacement/Retake still destroys the typed review | Refuted. `Backgrounded` reduction returns the current public state for these events. `stateChanged` is false and the changed helper leaves the retained typed state intact. Snapshot restore while backgrounded is also ignored by the unchanged Capture-only predicate. |
| The new predicate accepts replacement or Retake while backgrounded | Refuted. No transition, event classification, or effect decision changes. The helper only maintains the private live cache after reduction. |
| Accepted invalidating events can leave stale cache because the new condition is false | Refuted for the reachable transitions. Accepted foreground replacement produces `Preparing` with a different photo; accepted Retake produces `Capture`; accepted Cancel produces cancellation recovery. These differ from their source states. Accepted `Restored` always leaves the `Backgrounded` wrapper. The cache is cleared in those normal-dispatch paths. |
| Route invalidation changed because state equality is now calculated earlier | Refuted. Between assigning `reduction.state` and evaluating the prior condition, the old helper modified only `inMemoryBackgroundState`. It never assigned public `state`. The new single `stateChanged` value therefore represents the same equality test for route cancellation. The background-work cancellation path is unchanged. |
| Explicit background cancellation is incorrectly treated as an ignored UI action | Refuted. `Cancel` remains an `updatesBackgroundWork()` event and takes the existing early path. It reduces against the retained live state, applies lease/session revocation, updates the opaque background snapshot and invalidates the route when that snapshot changes. It intentionally retains cancellation recovery for normal foreground restoration. |
| Preserving live cache enables typed review recreation from a process snapshot | Refuted. A fresh reducer has no retained cache. Candidate-review process snapshots still contain only `ref`/`requestId` and route to `REVIEW_RESTORE_UNAVAILABLE`. The separate process predicate and lifecycle fail-closed branch are unchanged. |
| Normal foreground restoration bypasses attempt, lease, or session validation | Refuted. `restoreInMemory` still uses `restoreInput` for typed review, checking current attempt, active lease and existing session before reconstructing the typed state. No validation code changes. |
| Feature note/selection is cleared by one of the ignored events | Refuted statically. With the cache preserved, `currentWorkState()` is equal before and after each ignored event. The existing feature reset condition therefore remains false. The extended tests compare the complete review after every ignored event, retain the nonempty query, check selected dish/components/pantry, and compare the complete explicit selection after foreground restore. |

Relevant source anchors: [dispatch/cache maintenance](REDACTED_WORKTREE/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt:27), [unchanged background transitions and accepted invalidation](REDACTED_WORKTREE/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducerTransitions.kt:6), [process/live restore distinction](REDACTED_WORKTREE/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducerLifecycle.kt:18), [feature reset condition](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisFeature.kt:59), and [route cancellation guard](REDACTED_WORKTREE/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/LocalFirstRouting.kt:302).

## Evidence and limits

This is a static source review, not executed behavioral proof. No Gradle build, unit test, coverage task, device/emulator action, or native compilation was started by this reviewer. The root agent owns the running full build. The red/green test counts reported in the specification were read as supplied context; their XML and freshness were not independently verified here. Static refutations above must not be described as executed tests.

Read-only checks performed: worktree/branch/status/HEAD inspection, `uname -m`, scoped diff SHA-256, referenced source/before-snapshot inspection, `graphify query "photo analysis reducer background state lifecycle snapshot route cancel review" --budget 1500`, and `git diff --check` (exit 0). The existing graph was used for navigation only; source was authoritative because the review includes uncommitted changes. Graph feedback/update writes were outside this assigned report-only scope and were not performed.

All eight listed static candidates were traced. None was promoted to an introduced defect. The single pre-existing assertion improvement was not executed or changed. There is no capped list of omitted introduced findings. This review does not certify the broader camera/UI WIP, native behavior, full task results, changed-branch coverage or the pending device matrix.

Only this report was written. No production/test edits, staging, commits, push or merge were performed.
