---
status: done
mode: one-shot
scope: N-27 preserve the live review across Activity configuration recreation
prior_source_snapshot: mobile/domain/photo-analysis/build/reports/review-state-recreation/before/
review_loop_iteration: 0
---

# Preserve the retained review during configuration recreation

The small emulator retained the banana photo but lost its review after changing font scale. Evidence is recorded before correction in `emulator-screen-matrix-2026-10-17.md`, N-27. Android retains its ViewModel and typed feature across configuration recreation. Its new Activity dispatches `RestoreFromSnapshot` before `Restored`. The reducer ignores the first event outside Capture, but unconditionally clears its in-memory background state. The later foreground restore therefore falls back to the intentionally opaque process snapshot and loses the live draft.

Preserve the current note-visibility correction, selected food IDs, manual query, explicit choices, normal geometry and completed assets. Keep the single common reducer as the lifecycle authority. Do not serialize photos, typed evidence, provider consent or native handles into process snapshots. The existing fresh-process fail-closed review behavior and lease/session validation must remain intact. No new published contract or privacy policy is introduced.

## Acceptance and code map

- Given a valid live review is backgrounded, when an ignored process-snapshot event precedes foreground restoration, then the typed draft remains available and restores unchanged. Expand `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/StagedComponentReviewTest.kt`; observe failure before production changes. Its existing fresh-process assertion must continue to reject opaque review restoration.
- Given explicit selected component IDs and a manual note, when that same recreation sequence occurs, then `mobile/feature/photo-analysis/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReviewFeatureTest.kt` preserves both along with the live review. Observe its failure before correction.
- Given `RestoreFromSnapshot` is ignored outside Capture, when the reducer maintains its lifecycle cache, then `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt` preserves that cache. Clear it only for accepted Capture restoration; retain all other cache-invalidating transitions.
- Given actual Android font-scale recreation, when the fixed app restarts its Activity, then photo, result, selections and note survive; no implicit photo capture or inference/provider consent occurs. Replay this on the owned small guest, verify real IME at 200% text and continue the remaining size matrix.

## Verification

Run one Gradle build at a time. Focused red/green classes, full domain/feature/UI JVM modules, relevant scoped lint, explicit iOS Arm64 compilation and six Android APK assemblies precede the fresh canonical three-flavor 120-test native replay. Archive prior N-26 results separately; validate report freshness and source/artifact digests. Preserve dirty unrelated WIP and the main checkout. Independent review follows the project quick-dev workflow. Do not stage, commit, push or merge the preserved mixed slice.

The pre-fix replay reproduced both assertions: domain 115/1/0/0 (the full domain task ran), feature's focused class 9/1/0/0. Each expected the retained `CandidateReview`, but `currentWorkState()` returned `Backgrounded` after the ignored snapshot event cleared its live cache. Archive: `mobile/domain/photo-analysis/build/reports/review-state-recreation/red/`. Use one shared Capture-only predicate for pure reduction and cache maintenance so their acceptance policy cannot drift.

Focused post-fix replay passes domain 115/0/0/0 and feature class 9/0/0/0. Full evidence and independent review remain pending.

## Final closure evidence

Current shared UI 114/0/0/0 and canonical Android 40/0/0/0 for each of POC, Production and UI Acceptance pass on production digest `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Current Large/tablet gallery and real-IME checks preserve banana/custom choice and `apple`, keep the full field clear of Close and expose both actions; compact N-29 actual-IME evidence concerns unchanged review sources. Rewards N-30 overlay and end clearance are verified at all four planned sizes and both font scales. Temporary guest cleanup and scoped limitations are in [the matrix report](emulator-screen-matrix-2026-10-17.md). Existing red/partial history is retained.
