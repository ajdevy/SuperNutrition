# Final 152-food review triage

Baseline: `5ee5de077866feed70b418ee2760af24322b1d22`, branch `codex/figma-camera-flow`.
Two fresh cold reviewers completed actual source reviews. Their reports are
`final-152-blind-review-2026-10-13.md` and `final-152-edge-review-2026-10-13.md`.
Neither reviewer executed native tests or Gradle. The working tree remains dirty.

## Accepted findings and regression conditions, before production fixes

| Finding | Acceptance condition | Verification |
| --- | --- | --- |
| B-01 | Review instructions, food labels and progress remain readable over a white photo/preview, with ordinary-text contrast at least 4.5:1 | Render production composables at density 3 and measure text-area foreground/background luminance |
| B-02 | The empty manual-input placeholder and entered text remain readable over bright photos | Same rendered contrast check, plus existing input interaction tests |
| B-03 | Small Rewards progress and earned text have adequate contrast on white cards | Render the actual progress label; retain the decorative artwork palette |
| B-06 | Close, gallery and shutter do not overlap in a 320x240 dp viewport with 24 dp vertical safe areas | Actual Compose semantic bounds, all controls displayed |
| Edge finding | Standalone review reanalysis clears obsolete photo component/dish IDs through READY or ANALYZING, preserving manual choices | Select an old component, reanalyse, present a different ID, require Add to stay disabled until a current choice is made |

The new regression tests were added before these production fixes. Exact red/green
results will be recorded after execution, not inferred from the static reports.

2026-10-13 red execution: `/tmp/supernutrition-final-review-red.log`, 18 tests,
7 failures, 0 errors/skips. All seven new regression cases reproduced their
stated defect. Pixel contrast was 2.26 (review instruction), 3.19 (progress),
4.19 (Rewards), 2.64 (Home next-step heading), and 3.10 (Home metric suffix).
Close/gallery bounds overlapped by 23 dp. Reanalysis left Add enabled from
the obsolete selection. The first attempt failed at test compilation due to
missing fixture arguments; no tests ran in that attempt.

B-04/B-05 are now accepted and under correction, preserving decorative colors
while using darker text roles. B-07 now has three new native regressions for
queued interruption, interruption before a CameraX callback, and idle cleanup;
its production fix will follow actual failing physical-device execution.

The final controlled native fixture keeps a callback outstanding through the
injected native capture port; it does not photograph anything or claim physical
CameraX sensor evidence. On the HONOR API 36 device, the queued and started
interruption cases both failed with `expected 1 completion, actual 0` before
the lifecycle fix; the idle-cleanup case passed. An earlier unbound-CameraX
fixture did not hold a callback outstanding and was corrected rather than
counted as proof of the started-operation defect.

Further source tracing found that `restoreActivityState` resurrected the old
shutter token from a Bundle even after the retained host released it. Added a
fourth regression using an actual Activity and saved stale token before editing
that restoration code. Inline requests have an Activity-owned controller and
cannot be resumed from an orphaned token after configuration/process recreation.

The final four-case native red run completed on the HONOR API 36 phone with
4 tests / 3 failures / no ignored tests, at
`/tmp/supernutrition-inline-lifecycle-native-red.log`. The Bundle case also
reproduced its expected failure (`orphaned-photo` instead of null). This focused
`am instrument` result is separate from the complete Gradle XML evidence.

Corrections: cancellation now releases queued or started operations exactly
once, controller callbacks ignore an obsolete operation, and Bundle restoration
does not resurrect a shutter request without its owning controller. CameraX
remains the default native effect behind the narrow capture port.

Green focused native execution: `/tmp/supernutrition-inline-lifecycle-native-green.log`,
4 tests, all passing, on the same physical HONOR API 36 phone after installing
the corrected production APK. This includes legacy Bundle restoration and
queued/started cancellation with a controlled late callback. It is not a
complete instrumentation-suite or physical camera-sensor result.

Full relevant JVM module results: core UI 4, presenter 19, shared UI 83, feature
25; zero failures/errors/skips. The UI total includes the seven reproduced
readability, overlap and standalone-reanalysis regressions.

## Remaining review triage

- B-04 and B-05 were reproduced by the rendered Home contrast checks and corrected with darker text roles; the full shared UI run passes those regressions.
- B-07 was reproduced by three failing native interruption/restoration regressions and corrected; all four focused lifecycle cases now pass. Complete native execution remains separate and pending below.
- B-08 is a conditional design question. Current minimal consumer review follows the user's Figma flow and simple selection instruction; this review did not demonstrate wrong photo identity or selection.
- B-09 is an unverified native screen-reader transition question. Absence of a custom announcement is not proof of failed framework behavior. Native direct-button semantics are separately being verified.
- B-10 is a future inventory-maintenance suggestion, not a current defect: reviewers independently confirmed 152 identities, 574 unique aliases, and exact current JSON/runtime parity.

The 152 illustrations are a presentation library. They do not broaden the shipped
five-label local detector, and the palette only renders actual current photo evidence.
