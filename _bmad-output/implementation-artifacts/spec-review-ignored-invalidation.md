---
status: done
mode: one-shot
scope: N-28 preserve live review for ignored background invalidation events
prior_source_snapshot: mobile/domain/photo-analysis/build/reports/review-ignored-invalidation/before/
review_loop_iteration: 0
---

# Preserve the live review when background events are ignored

The user's broad interaction/display QA authorizes this follow-up. N-27 Blind Hunter identified an existing path: background `ReplacePhoto` and `ScanAgain` are ignored by reduction, but clear its live cache and later lose the typed review. This was recorded as a deferred N-27 finding before the follow-up; runtime reproduction is required before correction.

Preserve the accepted Capture-only process restore, opaque snapshot format, attempt/lease/session validation, explicit selections and note, route behavior, camera permission/capture safeguards and all completed UI/assets. Fix only the common reducer's maintenance of an ignored event; do not accept new event transitions.

## Acceptance and files

- Given a live review is backgrounded, when snapshot restore, duplicate replacement and Retake events are ignored, then its typed draft remains available until normal foreground restoration. Expand the existing case in `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/StagedComponentReviewTest.kt`; observe it fail before production changes. Keep fresh-process refusal intact.
- Given explicit selected foods and manual note, when the same sequence occurs, then the existing case in `mobile/feature/photo-analysis/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReviewFeatureTest.kt` retains the complete explicit selection and note, and accepted foreground Retake still clears them.
- Given a reduction changes the public state, when it handles an existing invalidating event, then `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt` clears its cache. Compute the actual state-change result once and share it with route invalidation; preserve the separate Capture-only process snapshot predicate.

## Verification

Focused red/green, full domain/feature/UI tasks, scoped lint, changed-line/branch coverage, explicit iOS compilation, six APK assemblies and a fresh canonical 120-test Android replay on the small guest. The final artifact also verifies N-26/N-27 and the remaining small/large/tablet/normal-font matrix. Preserve prior evidence separately. Independent review follows quick-dev step 4. One build and one emulator at a time; no staging, commits, push or merge.

Red replay reproduced lost state at the first ignored replacement: domain 115/1/0/0; feature focused class 9/1/0/0. Both messages compare the complete expected live review with the actual opaque Backgrounded snapshot. Archive: `mobile/domain/photo-analysis/build/reports/review-ignored-invalidation/red/`. The later ScanAgain iteration was not reached in this red attempt; its identical invalidation branch is source-derived until the complete green sequence runs.

Focused green passes domain 115/0/0/0 and feature class 9/0/0/0, executing snapshot, replacement and Retake iterations plus accepted foreground Retake. The complete selection object and note are asserted after foreground restoration. Full and native evidence remain pending.

## Final closure evidence

Current shared UI 114/0/0/0 and canonical Android 40/0/0/0 for each of POC, Production and UI Acceptance pass on production digest `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Current Large/tablet gallery and real-IME checks preserve banana/custom choice and `apple`, keep the full field clear of Close and expose both actions; compact N-29 actual-IME evidence concerns unchanged review sources. Rewards N-30 overlay and end clearance are verified at all four planned sizes and both font scales. Temporary guest cleanup and scoped limitations are in [the matrix report](emulator-screen-matrix-2026-10-17.md). Existing red/partial history is retained.
