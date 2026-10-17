---
status: done
mode: one-shot
scope: N-29 keep the compact review note below the independent Close control
prior_source_snapshot: mobile/feature/photo-analysis-ui/build/reports/review-close-clearance/before/
review_loop_iteration: 0
---

# Keep the compact review field clear of Close

The final N-26/N-27/N-28 production APK passed all 120 Android tests. Actual small-emulator testing at 200% font then exposed an additional display defect: after Activity recreation retained the review and `apple`, focusing its note placed the entire footer within the short IME viewport. The note frame began around y=177, while Close occupied y=142–238. Its text remained readable, but Close visibly covered the pencil and frame. The observed screenshot is `small/final-ime-font200.png` and native bounds are `small/final-ime-font200.xml` under the matrix reports. This is an executed product finding, not a speculative review warning.

## Acceptance and code map

- Given the 320 dp review at 200% font is focused, when its viewport shrinks to 206 dp for the keyboard, then the whole note frame remains below the Close touch bounds with no overlap. Add a failing regression to `mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoReviewCompactViewportTest.kt` before changing production code.
- Given a compact review scrolls as one form, when it relocates the focused note, then its scroll viewport starts below Close and relocation uses the remaining height. Update `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt`. Keep its single parent/footer and current full-screen geometry.
- Given Close geometry changes, when the compact review reserves its touch area, then both use the same minimum touch size and review top offset. Share constants with `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraScreen.kt` instead of duplicating those dimensions.
- Given the complete footer fits in the remaining viewport, then input and both actions stay visible. When it does not fit, keep the entire note frame visible and actions reachable by scrolling. Existing normal-font, extremely short viewport and resize regressions must continue to pass.
- Given actual font-scale Activity recreation and keyboard hide/reopen, then current photo, explicit food choices and note remain unchanged. Retake and Close continue to clear/exit through existing actions; no capture, meal save or consent becomes implicit.

## Verification

Save the native red screenshot/XML and current source snapshot. Run the focused regression red before correction, then the full relevant UI module, scoped lint, explicit iOS Arm64 compilation, APK assembly and fresh canonical three-flavor Android suite. Preserve prior native reports by digest; no earlier native result qualifies as N-29 evidence. Independent Blind and Edge review follows the quick-dev workflow. Continue real small/large/tablet display checks and delete stopped temporary AVDs after saving their evidence. Preserve unrelated WIP; do not stage, commit, push or merge.

The new regression first failed for the recorded geometry: Close `Rect(13,3,61,51)` overlapped note frame `Rect(32,7,288,52)` (7/1/0/0). After correction the focused suite passes **7/0/0/0**; prior normal/short/oversized footer cases remain green. Archives: `review-close-clearance/red/` and `focused-green/`. Full module/native/profile evidence remains pending.

## Review patch — retain a usable viewport at tiny heights

Independent Edge review identified a concrete introduced boundary: reserving all 54 dp can leave a zero scroll viewport at a positive height of 54 dp or less, and only 46 dp at a 100 dp height. Classify as a medium patch finding because the original acceptance requires short-viewport reachability. Add a red tiny-height regression and strengthen the existing 100 dp action check before capping the reserve. Keep at least the 48 dp minimum touch viewport when the window itself permits it; otherwise preserve all available positive height. The verified 206 dp Close separation remains unchanged. Complete vertical separation cannot be promised where the entire window cannot fit both controls. The initial 110-test full build passed, but is an intermediate checkpoint superseded by this patch.

The boundary regressions then failed for both recorded conditions (**8/2/0/0**): input was not displayed at 54 dp, and Retake lost its 48 dp visible touch viewport at 100 dp. Both red XML failures are archived under `review-close-clearance/boundary-red/`. The compact reservation now uses the minimum of Close clearance and the nonnegative height remaining after one minimum touch target. The final full module/build replay and renewed independent patch review are running.

A second static review found density rounding can lose one pixel in the remaining touch viewport: at 215 dpi and a 134 px parent, independent dp rounding reserves 70 px and leaves 64 px while the 48 dp target is 65 px. Classify as a medium patch finding; reproduce with a non-unit-density regression before calculating both reservation and remaining height from integer constraints. The ongoing build already had a line-wrapping lint failure; only its exact client was interrupted and its owned idle daemon stopped. Exit 130/partial execution is archived as `lint-failed-incomplete`, not a successful full build or native result.

The fractional-density regression reproduced the lost pixel before correction (**9/1/0/0**, Retake target loses one pixel). Archive: `review-close-clearance/density-red/`. Reservation and remaining relocation viewport are now computed from the same integer parent constraints and individually rounded minimum target. Convert those final integer dimensions to dp only for the layout modifiers. No accepted lifecycle, routing, selection or note semantics changed.

## Final closure evidence

Current shared UI 114/0/0/0 and canonical Android 40/0/0/0 for each of POC, Production and UI Acceptance pass on production digest `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Current Large/tablet gallery and real-IME checks preserve banana/custom choice and `apple`, keep the full field clear of Close and expose both actions; compact N-29 actual-IME evidence concerns unchanged review sources. Rewards N-30 overlay and end clearance are verified at all four planned sizes and both font scales. Temporary guest cleanup and scoped limitations are in [the matrix report](emulator-screen-matrix-2026-10-17.md). Existing red/partial history is retained.
