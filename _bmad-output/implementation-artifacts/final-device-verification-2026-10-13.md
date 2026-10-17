# Capture, Home, Rewards and food artwork verification

Status: physical acceptance still in progress. This document does not claim the
complete Android suite or the root quality gate is green.

October 14 update: the latest complete XML reports now show 40/0/0/0 for
production, POC and uiAcceptance. This supersedes the native-run/pending
statuses below; the original full command had one cancelled-touch failure and
the complete production replay passed unchanged. See
[the current report](native-device-verification-2026-10-14.md) for commands,
selection evidence and remaining manual/host limitations. No root quality
pass is implied.

## Identity and implementation

- Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`.
- Branch: `codex/figma-camera-flow`.
- HEAD and refreshed `origin/main`: `5ee5de077866feed70b418ee2760af24322b1d22`.
- Intended changes remain local and uncommitted; unrelated WIP is preserved.
- Camera/review follows the referenced Figma flow. Custom entry is a centered
  plus. Instruction: "Pick the foods in your photo. Uncheck the rest."
- Home has the substantial foreground mascot/paws, centered care actions,
  complete next-step card and bottom navigation. Rewards uses the recovered
  original prototype artwork with current domain progress, equal columns and
  aligned progress/footer text. Its source was a local unreachable prototype,
  not a rich Kotlin screen found on current main.
- Permissions are requested on foreground Capture entry. Denial leaves an
  access action; permanent refusal opens app Settings. Grants and returning
  from Settings refresh preview without pressing the shutter. Interrupted
  captures release their operation exactly once and obsolete callbacks/tokens
  cannot revive a shutter request.
- The illustration inventory is 152 identities, 156 runtime PNGs and 574
  unique exact English/Russian aliases. All 105 additional generated assets
  passed hash, 1254x1254 RGBA and alpha-range validation. The actual shipped
  detector still has five labels (apple, banana, broccoli, carrot, orange).
  Illustration coverage does not broaden recognition; unknown labels remain
  readable text and only current photo evidence creates default choices.

## JVM, source quality and Apple

Verified XML counts, tests/failures/errors/skipped:

| Module | Counts | Report directory |
| --- | --- | --- |
| core UI | 4 / 0 / 0 / 0 | `mobile/core/ui/build/test-results/jvmTest/` |
| presenter | 19 / 0 / 0 / 0 | `mobile/feature/photo-analysis-presenter/build/test-results/jvmTest/` |
| shared UI | 83 / 0 / 0 / 0 | `mobile/feature/photo-analysis-ui/build/test-results/jvmTest/` |
| feature | 25 / 0 / 0 / 0 | `mobile/feature/photo-analysis/build/test-results/jvmTest/` |

Core UI is genuinely unchanged since its passing report: its production
automation-ID source predates that report, and the latest complete commands
report its test task UP-TO-DATE. The other three reports were refreshed Oct 13.
Seven newly added readability, short-window overlap and stale-selection
regressions first failed as stated, then passed after the corrections.

Final source checks report no applicable lint findings. The root
`qualityCheckJvmAndroid --continue` attempt still fails at `verifyMobilePolicy`:
the committed `bootstrap-android-host` native evidence is older than its 30-day
limit. No TTL, binding dates, exclusions or lifecycle policy were changed.
Coverage-task completion must not be inferred from clean lint or module tests.

On this Intel (`x86_64`) host, explicit IosArm64 compilation succeeded for
core UI, presenter, shared UI and the feature, using the installed Kotlin/Native
2.4.10 distribution and `enableIosDeviceTarget=true`. The changed sharedKit
host cannot complete compilation because four photo-ios cinterops require
full Xcode and report `MissingXcodeException`. Current changed Apple source
lint is clean; no Swift build or Apple device execution is claimed.

Logs: `/tmp/supernutrition-final-device-ready-build.log`,
`/tmp/supernutrition-final-quality-connected-oct13.log`,
`/tmp/supernutrition-final-source-quality-apple-attempt.log`.

## Physical Android evidence

Physical HONOR DNY-NX9, Android 16/API 36, arm64, 1264x2736 at 560 dpi.
Exactly one Wi-Fi ADB endpoint:
`adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp`.

Four focused native lifecycle regressions reproduced three failures before
the fix, then all four passed after the corrected production APK was installed.
The fixture deliberately controls an outstanding CameraX callback; it is not
physical sensor-photo or whole-suite evidence. Logs:
`/tmp/supernutrition-inline-lifecycle-native-red.log` and
`/tmp/supernutrition-inline-lifecycle-native-green.log`.

Fresh screenshots were visually inspected immediately on capture:

- `home.png`: foreground paws, centered care actions, intact next-step card,
  anchored navigation on the actual phone.
- `rewards-final.png` and `rewards-scrolled-final.png`: equal card columns and
  progress alignment, readable status bar, scrollable collection and fixed menu.
- `camera-current.png`: live physical camera preview, reachable close/gallery/
  shutter. An unrelated call overlay is not part of app-layout evidence.

These files are under ignored output
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-13/`.
Other-app/locked-device/interrupted screenshots are not app acceptance evidence
and must not be published. Manual OS permission recovery, selection,
plus/keyboard, Retake and Close still require final verification.

The owned banana QA fixture is unchanged Evan-Amos `Banana-Single.jpg`,
CC BY-SA 3.0, downloaded from its verified Wikimedia source and pushed to
`/sdcard/Pictures/SuperNutritionQA/banana_single_2026_10_13.jpg`. The original,
hash/license provenance and use restriction (QA only, not shipped) are under
`mobile/build/device-fixtures/`. Real import through the normal DocumentsUI
picker succeeded. The immediately inspected `banana-review.png` shows only
the banana illustration, its actual banana photo backdrop, the requested simple
instruction and centered plus. No unrelated foods appear.

The actual component ID is `region-1-banana`; its native tag is
`photo_analysis_component_l72_l65_l67_l69_l6f_l6e_x2d_d31_x2d_l62_l61_l6e_l61_l6e_l61`.
The later manual toggle commands accidentally copied an incomplete tag ending
in `bana`. Those commands safely made no tap, and are not a product selection
failure or completed toggle evidence. Use the full fresh hierarchy and actual
resource ID when resuming. The local action helper now traverses that full tree
and limits inspector calls to 25 seconds.

## Complete native execution attempts

The old POC APK was backed up to
`/private/tmp/supernutrition-old-poc-2026-10-13.apk`. The user explicitly approved
removal of that old differently signed POC; a corrected POC APK is now installed.
No production app data was erased.

An initial retry used local `installation.installOptions` with `--no-streaming`.
DDMLIB passed it to Android package-manager `install-create`, where that adb
client-only option is invalid. All three variants stopped at installation;
zero tests executed. That local option was removed before the next run.

The complete retry command used the actual supported timeout property,
with no unsupported installation options:

```sh
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
  --init-script "$PWD/mobile/build/agent-ui-runtime.gradle" \
  -Pandroid.experimental.testOptions.installApkTimeout=900 \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial=adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp \
  -Pkotlin.compiler.execution.strategy=in-process \
  --dependency-verification strict --no-watch-fs --continue \
  --no-configuration-cache --console=plain
```

Start: 2026-10-13T10:51:13Z. Owned exec session 73092 was stopped with exit 130
after POC reached 33/40 with six inspector-connection failures. The other two
variants did not execute in that attempt. Log:
`/tmp/supernutrition-final-connected-wifi-retry.log`.
No Gradle build is currently running. Expect 40 tests per flavor on the next
complete run; fresh full XML counts remain pending. Do not reuse the previous
zero-test/partial reports as completed evidence.

Harness finding recorded before recovery: the successful POC installation
reached 33/40 tests, with six UI semantics failures. The first failure is
`UiAutomationService ... already registered`, followed by disconnected
UiAutomation errors. A device process check confirms the earlier manual
Android CLI inspector `com.android.cli.interact.instrumentation` is still
running and owns the automation connection. Stop that exact inspector before
native UI execution; do not call its connection failures passing app tests or
modify product assertions. A complete relevant rerun is required after recovery.

After stopping that exact inspector and the interrupted POC test process, a
fresh **complete direct POC** invocation ran all 40 cases in 23.807 seconds,
with `OK (40 tests)` and no failed/ignored cases. Log:
`/tmp/supernutrition-final-poc-ui-channel-recovery.log`. This includes the six
native navigation/action semantics, fifteen permission coordinator cases and
four interrupted-capture cases. It verifies inspector recovery but does not
replace the required three-flavor Gradle XML run. Do not use Android CLI
layout/capture concurrently with instrumentation; stop its inspector first.

Gradle/Android/Skiko writable caches are under ignored mobile build directories;
the existing global Gradle dependency cache is used read-only. The root product
and evidence policy have not been changed for this run.

## Completion prerequisites

- Inspect complete fresh production, POC and uiAcceptance XML counts and fix
  any actual product failure; separate installation prerequisites from tests.
- Finish manual real-photo and permission recovery checks.
- Emit real native evidence using `SUPERNUTRITION_ALLOW_DIRTY_EVIDENCE=1` only
  into ignored local output; it must remain `local-only`, with `worktree_state`
  `dirty`. It cannot replace a clean committed activation attestation.
- The original screen-off timeout **600000** was restored and read back on
  2026-10-14; the ignored backup now has `restore_required=false`.
- `git diff --check` has passed; graphify was updated after final source changes
  (20,480 nodes / 27,329 edges / 1,953 communities). SQL parser warnings and
  empty ASTs for non-code files are recorded, not hidden.
- Hourly continuation was requested but is not active. Config is now
  `on-request`; this already-running session remains approval policy `never`,
  and heartbeat creation is rejected until Codex restarts with the new policy.
  A renewed explicit request on 2026-10-14 produced the same actual tool
  rejection. No automation directory/saved schedule exists. Current phone
  foreground is Chrome; no interactive app testing was resumed from yesterday's
  availability window.
