# Native verification continuation, 2026-10-14

Historical checkpoint: the subsequent gallery-photo correction has its own
complete October 15 physical run, passing 40/0/0/0 in all three flavors, and a
fresh inspected photo. See [the current verification report](gallery-photo-fit-verification-2026-10-15.md).
The older APKs, XML and attestations below must not be relabeled as evidence
for that correction.

Status: all three Android flavor suites now pass; final manual checks remain
in progress. No root quality or complete Apple build pass is claimed.

Worktree `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`, branch
`codex/figma-camera-flow`, HEAD `5ee5de077866feed70b418ee2760af24322b1d22`.
The intended changes remain local and uncommitted. Existing unrelated WIP is
preserved. The October 13 final device report remains the source for implementation,
review, JVM test counts, asset provenance and earlier red/green regressions.

## Device window and fresh observations

The user explicitly made the physical HONOR DNY-NX9 available for the current
verification window on October 14 and continues to prefer Wi-Fi. Exactly one
ADB endpoint was used:
`adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp`.

The initial USB-mode dialog obscured the app. A Back command did not dismiss it;
no transfer mode was selected. A later fresh hierarchy showed Home after the
dialog disappeared. Foreign-app/launcher focus changes stopped guarded taps.

Launching with `am start -W` and waiting for the actual app window avoids an
immediate launch/focus race. The directly captured and immediately inspected
`current-app.png` shows the complete corrected Home on the actual device.

Real import of the owned banana fixture succeeded again. The first hierarchy
was a normal transient `Identifying visible foods` state, not an established
recovery error. After waiting for actual review controls, `banana-review.png`
shows only banana, the requested instruction and centered plus. The Android
CLI screenshot operation had timed out and produced invalid image bytes in an
earlier attempt; that output is not evidence. Direct ADB screenshots work.

Native component tag:
`photo_analysis_component_l72_l65_l67_l69_l6f_l6e_x2d_d31_x2d_l62_l61_l6e_l61_l6e_l61`.
Native selection was verified after the complete suite: the banana checkbox
changed false → true, displayed its check badge and enabled Add to meal.
`banana-selected-native.png` was captured and immediately inspected; its
hierarchy is `banana-selected.xml`. Later guarded interactions again stopped
on foreign-app focus. Deselection/keyboard/Retake/Close manual checks remain
pending, and the user has been asked for a short uninterrupted app window.

System process-exit records show package replacement at 09:07 and force-stop
for starting/finishing instrumentation at 09:23. The latter ended the app
process during the manual window. Their origin is not established; they are
not reported as a current app crash. No concurrent active instrumentation or
Gradle daemon in this worktree's isolated Gradle home was present before the
new complete run. Do not use an inspector concurrently with the run.

Fresh images and hierarchies are in ignored
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-14/`.

## Owned complete Android run

Owned unified exec session: **3994**.
Log: `/tmp/supernutrition-connected-complete-oct14.log`.
This run finished with exit 1 after 26m 10s; no build from this session remains
active. Fresh XML counts are POC 40/0/0/0, production 40/1/0/0, and
uiAcceptance 40/0/0/0 (tests/failures/errors/skipped).

The full `:apps:androidApp:connectedAndroidTest` command uses strict dependency
verification, the sole device serial, `--continue`, no configuration cache,
`-Pandroid.experimental.testOptions.installApkTimeout=900`, and leaves the new
APKs installed for final manual review. It does not pass adb-client-only
`--no-streaming` to DDMLIB. The exact Android CLI inspector was stopped before
execution. The fresh reports show 40 executed tests per flavor. The command
failed solely on the production navigation case described below.

Original screen-off timeout: 600000. Temporary timeout for the long Wi-Fi
installation: 1800000. The owned shell has an EXIT cleanup that restores the
original value if it is still 1800000, preserving any independently changed
value. Its completion log confirms restoration to 600000. A later device
disconnect prevented a second readback; after the user re-enabled wireless
debugging the same sole endpoint was discovered again at 192.168.0.150:43037.
The readback then confirmed 600000.

## New finding recorded before correction

N-14: the full production run completed 40 tests with exactly one failure,
zero errors/skips. `centerCameraActionKeepsCaptureSurfaceAvailable` successfully
verified camera controls, then failed at Java line 138 after clicking
`capture_close`: `missing Compose semantic id: home_title`. POC completed
40/0/0/0. uiAcceptance subsequently completed 40/0/0/0. Acceptance: closing camera must
reliably return to the actual Home destination with its native semantics.
Investigate the actual destination, lifecycle and input delivery; do not
weaken the assertion or increase timeouts without cause. A complete relevant
rerun is required after a product correction. Device interruption remains an
alternative to investigate from evidence, not an assumed explanation.

The failing case's saved logcat now establishes an interrupted injected touch:
at 09:45:55.510 UiAutomation injected DOWN on Close; at .540 InputDispatcher
cancelled all ongoing pointer gestures and synthesized a cancellation for the
actual production app window; UP arrived at .544 and .547 reports asynchronous
input injection failure. The camera remained open. This does not establish a
broken product Close callback. No production or test change has been made in
response; an isolated replay and complete relevant rerun are still required.

The unchanged installed production test was replayed three times in isolation:
each finished `OK (1 test)` (6.875s, 5.396s, 5.746s test execution). Logs:
`/tmp/supernutrition-close-replay-{1,2,3}-oct14.log`. The persistent CLI inspector
was stopped before these runs. This supports input cancellation as the cause
of the earlier failed interaction; no assertion, timeout or product callback
was weakened. A complete production-flavor Gradle rerun was launched at
`/tmp/supernutrition-production-complete-replay-oct14.log`; the 40-test POC and
uiAcceptance XML passes above remain evidence for their unchanged inputs.

The complete production replay finished successfully in 8m 5s. Fresh XML is
40/0/0/0, including the unchanged Close case. POC and uiAcceptance reports each
remain 40/0/0/0 on the same unchanged APK/test inputs; these are three complete
flavor runs, rather than a green execution of the earlier root
command. The screen timeout was restored and read back as 600000.

Manual-harness finding before correction: its prefix-only food selector also
matched `photo_analysis_component_evidence`, the non-checkable container, and
incorrectly counted two food choices. The actual hierarchy contains exactly
one checkable banana component. Restricting the temporary helper to native
`checkable=true` nodes fixes the selector without changing product code.

The official physical-evidence emitter completed successfully with explicit
`SUPERNUTRITION_ALLOW_DIRTY_EVIDENCE=1`. All six files under ignored
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-14/native-evidence/`
were parsed with `tomllib`: each is `local-only`, `worktree_state=dirty`,
test_count=40, failures/errors/skipped=0. They do not replace the committed
clean activation bindings. The emitter independently binds uiAcceptance and
POC artifacts; the production XML pass is recorded separately above.

## Host limitations

`xcode-select -p` remains `/Library/Developer/CommandLineTools`; `xcrun --find
xcodebuild` fails. This does not complete native iOS bridge/host compilation.
Earlier explicit shared IosArm64 compilation and clean source lint remain
separate evidence. The expired committed native bindings and dirty worktree
still prevent a clean root quality/activation claim. No binding dates, TTLs,
coverage inventories or lifecycle policy are relaxed.
