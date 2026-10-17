# N-27 retained-state Blind Hunter review

Reviewed on 2026-10-17 with the installed `bmad-review-adversarial-general` skill. No introduced production defect was confirmed in the supplied scoped diff after a second pass through the cache, restoration, invalidation, and feature-state paths. This is a static scoped review; it does not establish that the whole dirty branch or the Android recreation matrix passes.

## Scope and identity

- Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`.
- Branch: `codex/figma-camera-flow`.
- HEAD: `5ee5de077866feed70b418ee2760af24322b1d22`.
- Starting checkout: dirty tracked and untracked WIP, confirmed with `pwd -P`, `git status --short --branch`, and `git worktree list --porcelain`; no checkout changes were made.
- Review artifact: `mobile/build/review-state-recreation-scoped.diff`, SHA-256 `9e8ebdd6d6407e50871798fa4f2b5dd43587322417dc509c4c6d6b32247b7343`.
- Read `AGENTS.md`, the architecture spine, and `spec-review-state-recreation.md`. The reviewed patch changes one reducer and extends two tests. Other dirty changes were not reviewed as part of this result.
- Existing graph navigation: `graphify query "photo analysis reducer lifecycle restored snapshot backgrounded review lease" --budget 1300`. Tokens came from the existing graph vocabulary. The graph result was truncated by the requested budget; current source was then read for the necessary lifecycle, state, transition, feature, test, and Android Activity references. No graph output was written.

## Introduced defects

None confirmed. Findings were not manufactured to satisfy the skill's nominal issue quota.

## Evidence supporting that result

- The accepted process-restoration states are unchanged. The old condition was `current == Capture && event is RestoreFromSnapshot`; the new shared predicate returns exactly `this == Capture`. Moving the event check first does not widen restoration. Both pure reduction and cache maintenance now use that same predicate. See [PhotoAnalysisReducer.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt:73).
- In the failing retained-review sequence, `Backgrounded` stores the typed previous state, and `RestoreFromSnapshot` remains ignored by the state transitions. Previously it nevertheless erased that typed cache. The patch preserves it, so `Restored` reaches the existing `restoreInMemory` path and then clears the cache after restoration. The new domain assertion compares the complete `CandidateReview`, including its request and typed draft, both before and after foreground restoration. See [StagedComponentReviewTest.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/StagedComponentReviewTest.kt:145).
- Keeping the cache does not bypass live-photo validation. The retained CandidateReview restoration calls `restoreInput`, which checks the owning attempt, active lease, and session input. The patch changes none of those checks. It also leaves the fresh-process CandidateReview snapshot path returning `REVIEW_RESTORE_UNAVAILABLE`, with the existing assertion retained in the extended test. See [PhotoAnalysisReducerLifecycle.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducerLifecycle.kt:27).
- The snapshot shape is unchanged: review snapshots carry only the opaque photo reference and request ID. No typed evidence, note, native handle, consent, or image bytes are added to persistence. See [PhotoAnalysisState.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisState.kt:21).
- The feature retains manual input and selection separately. Neither snapshot restoration nor foreground restoration is a `resetsMealReview` event; preserving `currentWorkState()` also keeps selection edits available while backgrounded. The extended feature test checks the retained review, dish/component/pantry choices, a successful selection edit while backgrounded, the note after `Restored`, and reset after `ScanAgain`. See [PhotoAnalysisMealReviewFeatureTest.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReviewFeatureTest.kt:141).
- The modeled event order is supported by the current Android host: the Activity obtains the feature from a retained ViewModel, calls snapshot restoration in `onCreate`, and dispatches `Restored` in `onStart` when the retained background flag is set. `onStop` backgrounds the feature. These host paths are unchanged by this scoped patch. See [PhotoAnalysisComposeActivity.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt:210).

## Existing behavior and test gaps, not introduced regressions

- An ignored `ReplacePhoto` or `ScanAgain` while already `Backgrounded` can still discard the cache: the Backgrounded transition returns its current state, while the unchanged invalidation list clears the live cache. A subsequent `Restored` would use the opaque snapshot instead. This is a static trace of existing behavior, not a new defect in N-27; both invalidations are unchanged on the before side, and the spec explicitly preserves other cache-invalidating transitions. It was not reproduced by execution during this review. See [PhotoAnalysisReducer.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt:67) and [PhotoAnalysisReducerTransitions.kt](/Users/anastasia/.codex/worktrees/a704/SuperNutrition/mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducerTransitions.kt:13).
- The added tests demonstrate one valid retained-review sequence and retain the fresh-process refusal. They do not add the same sequence with lease revocation, a missing session, repeated ignored snapshots, or asynchronous evidence arriving between snapshot and foreground restoration. The existing restoration validation remains intact by inspection; this review does not claim newly executed boundary-case coverage.
- The feature test checks pantry IDs before `Restored` and component IDs plus the note afterward. It does not assert the entire selection object afterward. The domain test does assert complete restored state. This is an assertion-strength limit, not evidence that the patch loses a selection.

## Verification limits and handoff

- Static review host: Darwin x86_64, macOS 15.7.3. No device or emulator was used.
- No Gradle/build commands, test execution, device actions, source/test edits, staging, commits, pushes, or merges were performed. The root agent owns the running build and the required native replay.
- Independently executed tests in this review: zero. The spec's recorded red/green counts were read as reported history; their XML, timestamps, and artifact/source digests were not independently verified here.
- `git diff --check` completed with exit 0. The untracked report also produced no whitespace diagnostics under `git diff --no-index --check /dev/null <report>`; that command returned the expected difference status 1. The scoped diff digest was unchanged at handoff.
- Runtime font-scale recreation, the real IME at 200% text, prevention of implicit capture/inference/consent, all-size geometry, six APK assemblies, full module results, lint, iOS compilation, and the canonical Android suite remain outside this review's execution evidence. Completion of those acceptance checks must come from the root agent's fresh runs.
- Only this report was written. No confirmed introduced finding remains to fix from this Blind Hunter pass; the execution limits above remain open evidence requirements.
