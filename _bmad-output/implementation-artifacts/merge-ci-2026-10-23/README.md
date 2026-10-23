# Merge and CI verification — 2026-10-23

PR55 merged into main as `663b42129c0305b613f9025cec7aa31c3e66d034`, integrating: Home→Profile, Profile→Meal history, existing-screen Figma alignment and grounded Home capybara. Website legal documents are already integrated through the separate website repository. Historical closed/superseded branches and unrelated dirty worktrees remain preserved.

## Confirmed CI repairs

The previous main [iOS run39863651736](https://github.com/flexsent-labs/SuperNutrition/actions/runs/39863651736) ran7 XCTest cases:1 passed,6 failed. Exported stderr proves a missing Compose `capture_close.png`; see [summary](ios-before-summary.json) and [crash excerpt](ios-before-crash.txt).

SharedKit now aggregates transitive resources through the pinned Compose plugin. Both native hosts call one helper that links the selected framework and synchronizes resources into the Xcode bundle. Supported arm64 Xcode inputs are required; standalone framework linking is retained. The Compose compiler's three existing JVM/metadata lock configurations were refreshed after reproducing the strict-lock failure.

The JVM workflow uses `--continue`, retains strict policy failure, has30minutes for independent work, runs report checks after non-cancelled failures, and uploads test results plus Gradle problem diagnostics. The outdated README snapshot was regenerated without changing coverage policy.

## Local verification

- Intel x86_64 macOS15.8, Android Studio JDK, process-local Xcode26.6. Global Xcode selection unchanged.
- New resource/helper regression tests failed before the fix (7 cases,13 failing subtest observations reported by implementation agent). After the startup repair, all51 script tests pass, including12 boot-health cases.
- Actual SharedKit iosArm64 link and resource synchronization passed with strict dependency verification in5m39s. All261 original Compose assets, including Fraunces/Nunito, are packaged byte-for-byte: [inventory](ios-resource-inventory.json).
- Actual unsigned production Xcode host build passed in66.76s. Its bundle contains270files including all261 Compose resources: [command/result](ios-production-host-build.json). This proves compilation and packaging; local simulator/device execution is unavailable on Intel.
- Contract/schema/market/root/release checks passed. CI policy and owner-decision strict validation passed. `git diff --check` passed.
- Full local `qualityCheckJvmAndroid mobileUnitCoverage mobileCoverageReports` plus all six APK builds completed with strict verification, `--continue` and one worker in28m34s while the emulator was stopped. All918 tests in18modules pass with0failures/0errors/0skips and freshly regenerated XML. The sole failed task is `verifyMobilePolicy` for stale evidence. [Exact command/result](local-quality-result.json), [per-module counts](local-quality-tests.json). No mobile source changed during the run.
- Detekt tasks reported type-analysis accuracy warnings (18 compiler-resolution diagnostics in domain inference,3 in UI Android,3 in UI JVM) without an additional failed task. These warnings limit type-resolved analysis; this is not a full green quality gate.
- Final local cold boot of task-owned API35 AVD remained ADB-offline for300seconds despite73%system memory free. The Android CLI returned0 while printing the prerequisite failure; actual ADB state is authoritative. No native test ran on this attempt. The exact owned emulator process was stopped after checking its AVD identity. [Result and limitation](local-cold-boot-limit.json). Latest canonical all-variant native execution therefore remains unavailable locally; hosted API36 must provide the real result.

## Preserved prerequisite

All six required successful Android evidence records expired on2026-10-12T08:04:52Z. Five explicitly require a physical Android device. The user has only an emulator; that cannot renew physical evidence. [Typed manifest snapshot](policy-blockers.json). Full policy remains red until genuine fresh physical execution is bound; no dates, requirements, skips, floors or exclusions were changed.

See [findings](findings.md) and [independent review triage](review-triage.md). PR55 and the final handoff carry the actual merge identity; the results below identify their exact tested source commit.

## Follow-up candidate

Hosted Android run39986624811 completed50uiAcceptance cases with1failure/0errors/0skips. Logcat proves an asynchronous test precondition: Back was injected14ms after requesting IME hide, before its animation ended. The test now waits actual visible/hidden insets plus native animation completion, handles API30 pre-start cancellation, and restores the hardware-keyboard setting. Explicit Core1.16.0 test compile dependency uses the existing runtime version. Three variant javac builds passed. Two local focused attempts were blocked at initial Home lookup by stock Quickstep/SystemUI ANR windows, before the IME/Back assertions; neither is a passing regression result. [Original XML counts](android-before-tests.json).

Hosted iOS run39988860538 confirms the resource crash is gone:8cases executed,7assertion failures/1pass/0skips, all at the obsolete camera status selector. [Diagnosis](ios-candidate-diagnosis.json). XCTest now verifies the current canonical stage/value and capture controls with product-owned localization in its own test bundle. All progress/recovery/navigation assertions remain. The subsequent hosted rerun passes, as recorded below.

SharedKit JVM compilation initially exposed missing lock associations and missing direct Compose Runtime. Both are corrected; portable common metadata locks were regenerated following independent review. The complete local run now compiles and executes SharedKit's3 JVM tests. Source review and successful compilation are not a full policy pass.

## Hosted candidate79535a2d

[JVM run39994937686](https://github.com/flexsent-labs/SuperNutrition/actions/runs/39994937686) completed in8m12s overall. Its Gradle invocation completed in7m25s with exactly one failure: `verifyMobilePolicy` rejects stale `bootstrap-android-host` evidence. Compilation and test execution have no reported failures. All892 tests pass in12modules, with0failures/0errors/0skips; [per-module XML counts](hosted-jvm-79535-tests.json) avoid mixing stale local reports with this clean hosted run.

All nine declared numerical JVM denominators meet their floors; nutrition has99.991679%line/100%branch coverage. The two branchless adapters have nonempty line counters and absent branch denominators. [Exact counters](hosted-jvm-79535-coverage.json). These numerical results do not certify the expired native-equivalent evidence.

The static coverage dashboard, README snapshot check and artifact upload all succeeded after the failed policy task, confirming the diagnostic workflow correction.

[iOS run39994989354](https://github.com/flexsent-labs/SuperNutrition/actions/runs/39994989354) passed:8/8 XCTest and1/1 Maestro,0failures/0skips, on the arm64 iPhone17Pro simulator with iOS26.4.1/Xcode26.4.1. Real XCTest execution took209.889seconds. This verifies the packaged resources, updated camera readiness, native picker/recovery, lifecycle, navigation and Profile entry in the actual hosted app. [Exact device and test counts](hosted-ios-79535-tests.json). This is simulator evidence, not physical iPhone evidence.

Android run39994995975 completed50cases with12failures/0errors/0skips. Its final hierarchy is a SystemUI ANR dialog raised before instrumentation; all UI failures are blocked at initial app-surface lookup. Native boot broadcasts were still completing after the script accepted Binder service availability and forcibly stopped launcher/GMS/IME. No app FATAL/ANR was found in the examined log. [Diagnosis](hosted-android-79535-diagnosis.json), [bounded recommendation and primary-source anchors](hosted-android-79535-bootstrap-recommendation.json).

## Android startup repair

The runner now retains startup logcat from the first connection, removes the premature launcher/GMS/IME force-stops, and waits for unlocked native boot, stopped boot animation, native broadcast idle, a single real HOME launch, current launcher/window hierarchy, and native IMMS readiness. The helper rejects ANR/crash events, stale/error UIAutomator output and partial or timed-out service dumps. Its own monotonic deadline starts after a serial connects; it does not claim a deadline over the entire retained ADB discovery loop. Private-runner CPU capacity remains respected and connected Gradle uses one worker. API36/GPU/heap and all application assertions remain unchanged.

Twelve focused tests cover actual command failure contracts. Meaningful red→green regressions reproduced early ActivityManager absence, stale successful-then-failed hierarchy dumps, false/missing IMMS readiness and valid readiness followed by native timeout/error footers. Both independent reviewers confirm closure of the recorded source defects. All51 script tests, shell syntax, CI policy and diff checks pass. This source/fake validation requires a new real API36 run; it is not a passing native result.

## Main663b4212 and Android rendering follow-up

PR55 is merged normally; its complete source tree matches main663b4212. There are no remaining open mobile or website PRs at this checkpoint. Unrelated primary-worktree changes remain untouched.

[Main iOS40000963075](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40000963075) passes8/8 XCTest and1/1 Maestro, zero failures/skips, on arm64 iPhone17Pro/iOS26.4.1: [fresh exact counts](main-ios-tests.json). [Main JVM40000963058](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40000963058) passes892tests across12modules with zero failures/errors/skips; its sole failed task remains stale native evidence: [fresh XML counts](main-jvm-tests.json).

[Main Android40000963150](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40000963150) correctly stops before instrumentation on a genuine Phone/SafetySourceReceiver ANR, followed by SystemUI/KeyguardService ANR. No app test ran: [native diagnosis](main-android-diagnosis.json). The pre-boot display now uses720×1600 at280dpi, exactly preserving the prior1080×2400/420dpi logical viewport while reducing framebuffer pixels55.56%. Named skin overrides are removed; an explicit numeric skin and authoritative AVD density are used. API36,2CPU,2560MiB/576MiB, SwiftShader, all app assertions and fatal-event rejection remain.

Empty boot-animation service state is accepted only with actual positive signed32 `debug.sf.nobootanimation` proof. Zero/negative/absent/malformed/overflow flags and running animation remain rejected. A local task-owned API36 emulator independently returned boot_completed1, disabled flag1 and empty bootanim, confirming that observed native mode; no application test is claimed from that probe. The owned emulator was stopped by its verified PID.

The new disabled-animation regression failed before the fix (two missing-success cases); all16 focused boot/display tests and all55 script tests now pass. Shell syntax, CI policy, strict owner-decision validation and diff whitespace checks pass. The rendering remediation still requires actual hosted boot/instrumentation/Maestro execution.
