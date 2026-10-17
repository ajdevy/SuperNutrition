---
status: done
mode: one-shot
scope: N-26 focused review input after an IME-driven scroll-policy change
prior_source_snapshot: mobile/feature/photo-analysis-ui/build/reports/review-focused-resize/red/PhotoAnalysisMealReview.kt
review_loop_iteration: 0
---

# Keep the focused review input visible

The user authorized broad interaction/display verification and fixes on separate screen-size emulators. The 320×568 dp guest exposed a defect when the review switches from its pinned footer to scrolling the whole form: pressing the manual plus opens LatinIME, but the focused field remains below the visible viewport. Native screenshot/XML and WindowManager insets are recorded in `emulator-screen-matrix-2026-10-17.md`.

Preserve the existing gallery aspect-ratio fix, meaningful food suggestions/assets, stable single composition parent for the note, independent Close, selections, typed text, confirmation payload and normal-size geometry. Keep this fix inside common Compose UI; introduce no platform/domain/consent/model changes.

## Acceptance

- Given a focused note on a normal review, when the usable viewport shrinks enough to change scroll policy, then the field remains focused with the same text and becomes visible without a manual scroll.
- Given a viewport that can fit the footer, when that resize occurs, then the note and both footer actions are visible, preserving minimum action hit height.
- Given an unusually short viewport that cannot fit the whole footer, when the note is focused, then the note is visible and footer actions remain reachable through scrolling.
- Given the keyboard hides and reopens, when the layout expands/shrinks again, then the field/text are retained; Retake clears review data and Close remains independently available.

## Implementation and evidence

1. `PhotoReviewCompactViewportTest.kt`: add fitting-footer and oversized-footer resize regressions. Valid pre-fix replay is fresh 5 tests / 2 failures / 0 errors / 0 skipped; both failures state that `photo_analysis_manual_ingredient_input` is not displayed. The initial test setup used ScrollTo on an already pinned footer and is archived separately as an invalid setup attempt, not defect evidence.
2. `PhotoAnalysisReviewNoteFocus.kt`: retain focus/requesters as one UI state, wait for placement after a viewport change, request the whole footer when it fits and the field when it does not. Do not re-request because of user scrolling alone.
3. `PhotoAnalysisMealReview.kt`: retain one footer call under one parent; report field focus/footer size and place existing bottom padding with the footer so a relocation preserves it. Keep existing distances and confirmation behavior.
4. Run the focused suite, full UI module, six scoped lint tasks, explicit iOS Arm64 UI compile and six APK assemblies, then replay the canonical three-flavor Android suite and manual small/large/tablet checks against the new artifact digest. Earlier native evidence is retained as historical evidence.

The focused post-fix replay is 5/0/0/0. The first full replay compiled and executed the UI tests, but failed scoped lint on import ordering, helper declaration/file naming and a long test signature. These are patch findings; preserve the failed log before correcting them. Fresh current-source three-flavor emulator execution now passes 120/0/0/0; manual IME and remaining profile checks are pending. The unrelated dirty worktree content and main checkout must remain untouched.

## Review triage

Both independent reviews are saved in the adjacent `review-note-visibility-{blind,edge}-2026-10-17.md` files. The native IME timing limitation is addressed by the already-required emulator replay. The partial-visibility assertion gap is a medium patch finding: check full bounds. The oversized-footer child-vs-frame relocation finding is a medium patch candidate; add a normal-font 100 dp regression measuring the complete 45.2 dp note frame and observe its result before editing production. A visible note includes its surrounding input frame when that frame fits the viewport; actions remain scroll reachable. No published contract or owner-policy change is involved.

The frame regression reproduced the review finding: fresh focused replay is 6/1/0/0; the actual 45 px frame is clipped to 32 px (`Rect.fromLTRB(32.0, 68.0, 288.0, 100.0)`). Archive: `mobile/feature/photo-analysis-ui/build/reports/review-focused-resize/frame-red/`. Classify this as a medium patch and relocate the whole note Row. Strengthen the fitting-footer text assertion to reject partial visibility. Keep focus/text observation on the editable child and the footer request/padding unchanged.

## Current build checkpoint

The completed full replay verifies **109 tests / 0 failures / 0 errors / 0 skipped** across 19 current UI XML suites. All six scoped lint reports are clean; `commonTestSourceSet` is legitimately UP-TO-DATE with unchanged inputs, while the other five were regenerated. `compileKotlinIosArm64` executed explicitly on this Intel host. All six Android APKs were assembled and hashed; production SHA-256 is `972e11d2e616dfa095737252f7ada96ae48efd0ae1da5157e6ae88745b071bf9`. Exact tasks, timestamps, source digests and artifacts are in `mobile/apps/androidApp/build/reports/emulator-screen-matrix-2026-10-17/build-verification.json`. This does not claim full root quality, Swift-host execution or current native execution yet.

## Suggested Review Order

- Preserve one composition parent while choosing pinned or scrolling review.
  [PhotoAnalysisMealReview.kt:73](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt#L73)

- Request the fitting footer or complete note after layout placement.
  [PhotoAnalysisReviewNoteFocus.kt:25](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisReviewNoteFocus.kt#L25)

- Include footer padding in its measured relocation rectangle.
  [PhotoAnalysisMealReview.kt:185](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt#L185)

- Keep focus on the editable child and relocate its full frame.
  [PhotoAnalysisMealReview.kt:366](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt#L366)

- Reproduce resizing, reject clipping, preserve focus and scroll reachability.
  [PhotoReviewCompactViewportTest.kt:31](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt#L31)


## Current Android evidence

Canonical `:apps:androidApp:connectedAndroidTest` completed on the actual 320×568 dp API35 x86_64 guest with exit 0 in 4m15s. Fresh XML shows POC, Production and UI Acceptance each at **40/0/0/0**, nine class suites each. All six local APK digests still match the build manifest; the installed production APK also matches. All UI source digests remained unchanged through execution. Temporary native-run timeout was restored and verified. Exact command/logs/XML and cleanup: `mobile/apps/androidApp/build/reports/emulator-screen-matrix-2026-10-17/small/native/verified-current-counts.json`. Real IME replay remains separate from this native suite.

## Final closure evidence

Current shared UI 114/0/0/0 and canonical Android 40/0/0/0 for each of POC, Production and UI Acceptance pass on production digest `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Current Large/tablet gallery and real-IME checks preserve banana/custom choice and `apple`, keep the full field clear of Close and expose both actions; compact N-29 actual-IME evidence concerns unchanged review sources. Rewards N-30 overlay and end clearance are verified at all four planned sizes and both font scales. Temporary guest cleanup and scoped limitations are in [the matrix report](emulator-screen-matrix-2026-10-17.md). Existing red/partial history is retained.
