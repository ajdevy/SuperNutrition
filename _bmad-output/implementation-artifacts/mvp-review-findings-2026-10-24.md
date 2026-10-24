# MVP independent review triage — 2026-10-24

Reviewed diff:bcb2b090bfd455a4911a8282737a27b97a83b73b84c4251d452dcb0fcb4348b6,
baseline0f1fbb4e599e0a71accaef5c074466436b9e8e0b. Two fresh no-history
reviewers invoked the adversarial-general and edge-case skills independently.
Both inspected sources read-only; neither ran builds or devices. Findings below
are recorded before any review fixes. Root assigns severity by user impact.

| ID | Severity / route | Finding and acceptance condition |
| --- | --- | --- |
| R1 | high / patch | Camera text form is conditionally composed and loses its draft on backgrounding or analysis completion (`PhotoAnalysisCameraScreen.kt:91`). Keep its state and open surface across those state transitions, in memory only; cancel writes nothing. |
| R2 | medium / patch | History query uses remember and resets when details replace the screen (`MealHistoryScreen.kt:58`). Search, open a row, return:the query and filtered list remain. Keep health text in session memory rather than a Bundle. |
| R3 | medium / patch | Existing photo note is copied into a draft but no note editor is rendered (`MealForm.kt:43`). Edit and clear a saved note, save, reopen:the correction persists; cancel preserves it. |
| R4 | high / patch | Both native photo-confirm callbacks discard false from saveReviewedMeal (`PhotoAnalysisComposeActivity.kt:810`, iOS counterpart). A2001-character note or write failure must show a readable failure and preserve the review/draft. Keep the existing Unit callback compatible if adding a result-aware seam. |
| R5 | medium / patch | Rhythm details deny diary contribution and receive only grounded foods (`RewardDetailCopy.kt:118`, `RewardsDetailsContext.kt:6`). Pass explicit saved recording evidence, explain it correctly and list credited dates without manufacturing nutrient facts. |
| R6 | medium / patch | Rhythm details use a rolling seven-day window while the evaluator uses a consecutive suffix with yesterday grace (`RewardDetailsEvidence.kt:17`). For days3..9 at reference10 list all seven; exclude disconnected dates. Share the evaluator's canonical streak implementation. |
| R7 | high / patch | New Swift Main/Profile factories are arm64-only but referenced unconditionally (`SuperNutritionApp.swift:112,128`). Guard the unavailable lane so Intel code can compile as compatibility-only; show the accepted unsupported-renderer limitation, never fake Compose execution. |
| R8 | medium / patch | Supported merged-ID overlap can reveal an old grounded record after deleting its authored replacement, and old facts continue rewarding after correction (`ProfileSelectedMeal.kt:39`, `RewardsFeature.kt:36`). Apply a session-scoped supersession/deletion projection consistently to visible confirmed foods and reward evaluation. Preserve immutable grounded records; no source activation or durable retention policy is introduced. Test deletion success/failure and unrelated records. |
| R9 | medium / defer | Grounded-only ledger entries are read-only. This was already true before this story; no current production G-2 package produces them. Their full correction/grounding flow requires a focused follow-up with approved G-2 contracts and honest unknown capture time. The current accepted correction AC concerns SavedMeal records. |

R5 combines the duplicate rule-copy and missing-record evidence findings from
both reviewers. R8 combines the conditional overlap deletion and stale-reward
findings; although current UUID capture does not generate overlaps, the merge
adapter explicitly supports them, so handle the integration boundary consistently.
No intent-gap or bad-spec loopback is needed:the frozen identity-only/session-only
constraints and explicit recording-habit change log already determine the fixes.

Initial status:regression tests before source fixes pending;no finding called fixed at that point.

First regression run (`review-red.log`,3m4s,exit1) before source fixes:
- UI:8 tests executed,8 failures,0 errors/skips. Assertions confirm the missing
  sheet after state transitions, empty returned query, missing Note editor,
  omitted grace day3, included disconnected record and wrong rhythm rule copy.
- Feature:RewardsFeatureTest6 tests,3 failures (all3 new supersession cases),
  0 errors/skips; the3 prior tests passed.
- Domain:new8-case helper suite could not compile because the canonical
  recordingStreakDays API does not exist yet. This is structural RED, not8
  executed test failures.

R7 source fix:production Swift gates the arm64-only Main/Profile references behind
`#if arch(x86_64)` and shows a truthful unsupported-renderer notice on Intel.
`swiftc -frontend -parse` passes after the guard. Full arm64 native bundle builds
and the acceptance test target build remain required after integration; no Intel
Compose runtime result is inferred from parsing.

R6 domain GREEN (`domain-review-green.log`,2m10s,exit0):280 tests across35
suites,0 failures/errors/skips. Kover XML:478/478 branch outcomes,12043/12044
lines (the unchanged CatalogServingDefaultSource artifact is the one missed
line). New RecordingStreakDays.kt:7/7 lines,12/12 branch outcomes.

Second regression run (`review-red2.log`,3m5s,exit1):4 UI tests,3 failures.
The saved-only rhythm case fails for missing Diary day3; its nutrient-negative
control passes. Both photo-save tests initially failed earlier in their harness:
performScrollTo was applied to the fixed Add footer, which has no scroll parent.
These two failures are NOT product RED evidence. Corrected only that test
interaction to the existing camera test's visible-footer click convention;
R4 behavior remains unchanged until the feedback assertion is witnessed failing.

Combined regression run (`review-green-red3.log`,4m55s,exit1):feature66
across10 suites all pass. UI266 across60 suites has3 failures,0 errors/skips.
Both R4 cases now fail exactly for missing meal_review_save_error, so the
result-aware behavior fix is released. The additional saved-only rhythm failure
is a viewport assertion:Jan4 exists but is not displayed after scrolling its
preceding title to the bottom. Scroll the date itself before checking visibility;
do not remove its date or seven-day assertions. All initial8 regressions pass.

Final-gate lint findings before cleanup (`full-gates-v3.log`,run still active):
feature commonTest has argument wrapping in four RewardsFeatureTest fixture calls
and function-signature layout in its helper and MealDiaryHistoryActionsTest.
Root iOS saved-record context wiring increases AppleMainContent to62 lines against
60. Share one RewardsDetailsContext between both branches to remove duplication;
format only the reported test declarations/calls. Do not edit sources during the
active build; collect remaining findings and rerun complete relevant gates.

V3 completed in19m50s:596tasks (114executed,5cache,477up-to-date),exit1
with4 failed tasks:policy and feature commonTest/typed-test lint plus iOSMain
LongMethod. No functional test, coverage, target compile or framework link failed.
All lint findings are now corrected without changing behavior or policy:
fixture calls/signatures formatted; one local RewardsDetailsContext is shared by
the two iOS branches. Both complete gates and native compilation are rerunning in
full-gates-v4.log; no result from V3 is called a completed root quality gate.

Full Android instrumentation RED (connected-final.log,still running):the PoC
variant reaches34/51 cases with15 failures,all in camera-permission fixtures at
PhotoAnalysisCameraPermissionInstrumentationTest.java:318. The Java helper selects
Kotlin's synthetic default constructor by hardcoded arity6;the new optional
MealDiary parameter increases it to7. This helper is construction glue,not an ABI
assertion. Find the synthetic constructor via DefaultConstructorMarker and supply
default-mask arguments for the trailing optional dependencies,without depending on
an exact arity or adding test-only production constructors. Keep every permission
assertion intact and rerun the entire connectedAndroidTest across all variants.

The first PoC variant completed51 cases with15 failures(all the fixture constructor
issue). Production started10/51 with0 failures;the run was interrupted deliberately
(exit130) after the known RED to avoid repeating the same fixture failures.
Its daemon/UTP cancellation completed before another build. This is partial
execution of the full entrypoint,not a completed-suite result. Preserved raw PoC
RED XML in the ignored verification directory. Helper now identifies the synthetic
marker and applies default bits only after its four explicit dependencies;no
production constructor or permission assertion changed. Full rerun follows.

Second native run:constructor/permission failures are gone. Two PoC brand checks
failed (wrong foreground package and launch-idle timeout). The saved brand log
confirms competing TestRunners:PID6512 starts the PoC brand test at04:59:58.979;
PID6304 finishes the prior production centerCameraAction test at05:00:02.503,
inside the same window. Interrupting the host Gradle/UTP did not immediately stop
its device-side instrumentation. This is an execution-preparation error,not grounds
to weaken foreground ownership or screenshot assertions. Let this run finish;
verify no active instrumentation,force-stop only the six task-owned app/test
packages,then rerun the complete entrypoint. No additional product/test assertion
change is authorized by this finding. Preserve all intermediate failures honestly.

Second run completed12m15s:PoC51/2 failures,production51/1,uiAcceptance51/1,
all0 errors/skips. The two PoC failures match the confirmed orphan-runner overlap.
Production/UI acceptance each miss meal_details_delete_confirm in the new journey.
This separate failure is not dismissed as device noise. Raw variant XML preserved.
The driver currently accepts any partially visible node and clicks immediately
after fast30-step swipes; log shows delete click at(360,1366) after scrolling.
That suggests an unsettled/occluded target,but is not yet a proven cause. Add the
existing read-only semantic diagnostic on missing controls,then reproduce in an
isolated production runner before changing interactions or product code.

Isolated diagnostic RED:1 test/1 failure in38.972s,after installing both target
and test APKs (the first standalone attempt had no target because AGP uninstalls
apps after its run;that was setup failure,not a test result). Read-only dump shows
foreground app.supernutrition,reportViewIds/retrieveWindows enabled,only the main
app window,and fully visible Delete bounds(32,1316)-(688,1416). No dialog exists.
Manual inspection after instrumentation ended found Launcher because the target
process exits;no action was injected into another app. Next bounded diagnostic:
after preserving the original failure/dump,probe one stationary Delete click and
still fail the test regardless of its outcome. Remove that probe after diagnosis.

Stationary probe confirms the interaction cause:the unchanged first click still
fails;after preserving that RED,a single click on the same now-stationary Delete
opens the confirmation (opened=true). Test deliberately remained failed1/1 in
40.716s. No product or assertion was relaxed. Remove the temporary probe;slow
physical swipes to the existing brand test's90-step convention and require a
fully visible target with stable bounds before exactly one click. Keep the
original confirmation assertion and failure diagnostics. Focused green then full
all-variant verification required. This is a driver settle fix,not a dialog-ID fix.

Focused native GREEN after removing the probe and requiring stable visible targets:
journey-settled-green.log,OK1 test in36.531s. One physical click opens confirmation
and explicit confirmation empties history. Diagnostic remains read-only on failure;
no click retry or relaxed assertion. Clean full rerun starts only after verifying
no active SuperNutrition instrumentation and force-stopping the six owned packages.


## Final disposition

R1–R8 are fixed and verified within the documented platform scope;R9 remains the
explicit pre-existing G-2 follow-up. Final clean native suites each execute51 tests
with0 failures/errors/skips across PoC,production and UI acceptance. Existing
camera-permission assertions and the new diary confirmation/deletion assertions
are unchanged. No temporary probe,click retry,skip or suppression remains. Root
quality gates are still blocked only by expired physical evidence;their coverage
verifier does not run. Arm64 Kotlin/framework/Swift/production/acceptance/XCTest
builds pass;Intel primary-renderer and physical execution are not claimed.
