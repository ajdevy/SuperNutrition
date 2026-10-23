# Existing-screen Figma refresh verification

Worktree: `/Users/REDACTED/.codex/worktrees/f2ff/SuperNutrition`
Branch: `codex/home-profile`
Baseline: `7a141facbd0ec0b4fc86efce4948fdfd3b2ce660`
Status: implementation, shared UI and fresh final-source Android native verification passed. Required physical policy evidence remains unavailable. iOS runtime is unavailable on this Intel host.

[Current-frame audit and retained constraints](../existing-screens-figma-audit-2026-10-23.md) · [Specification and review trail](../spec-existing-screens-figma-refresh.md) · [Machine-readable counts](results.json)

## Scope and result

Existing Home, Rewards, shared navigation, capture/review and inline Profile name editing now use the current Figma artwork, Fraunces/Nunito, spacing and controls. Home → Profile → Meal history remains available. All seven real rewards retain their actual earned state, counts and progress; unavailable Colors/Fats stay unavailable. Session-only name editing and meal confirmation remain honest about their lifetime. No new meal storage, provider routing, privacy contract, Add-by-text, reward details, onboarding or barcode implementation is introduced.

The camera circle is minimally darker than Figma for white-icon contrast; Review retains accessible leaf text buttons, stronger photo scrim and its functional Close action. At200% text, naturally sized content remains scrollable rather than clipped. Home fits the inspected normal360×780dp native viewport without scrolling; no no-scroll claim is made at200%.

## Fresh final-source Android follow-up

A new isolated AVD `SN_Screenshots_20261023` (API35 x86_64, host OpenGL, 360×780dp) completed the unchanged full `connectedAndroidTest` command with strict dependency verification in8m3s. Fresh XML reports show50/50 each for Production, POC and UI Acceptance:150 total,0 failures/errors/skipped. This run includes the final signature/SAM cleanup on source commit `3d54a716`. The installed APK SHA-256 matches the current511675995-byte production build. Home → Profile → Meal history, native IME, draft cancellation, Rewards, camera permission/capture and native picker → Review were exercised. Home was inspected at normal and200% text; at200% the lower cards are reachable by scrolling, and font scale was restored to1.0. See [current runtime screenshots and exact evidence](../emulator-verification-2026-10-23/README.md).

The older failed attempts below remain diagnostic history; they no longer describe the current Android verification result. No physical-device proof or full policy-gate success is claimed. The iOS Intel framework attempt reached an upstream Compose1.11.1 `ios_x64` variant failure; the user accepted that runtime limitation.

## Completed verification

| Check | Tests | Failures / errors / skipped | Evidence |
|---|---:|---|---|
| Final shared UI JVM, after signature/SAM cleanup |230|0 /0 /0|46 fresh suite XMLs in `jvm-ui/`; `ui-jvm.log`|
| Final Core UI JVM |4|0 /0 /0|`jvm-core-ui/`|
| Final Presenter JVM |19|0 /0 /0|`jvm-presenter/`|
| Final Feature JVM |25|0 /0 /0|`jvm-feature/`|
| Android Production, before signature/SAM cleanup |50|0 /0 /0|`android-production.xml`|
| Android POC, before signature/SAM cleanup |50|0 /0 /0|`android-poc.xml`|
| Android UI Acceptance, before signature/SAM cleanup |50|0 /0 /0|`android-uiAcceptance.xml`|

The150-test full native run includes the nonblocking CameraX readiness boundary, native Profile Back cancellation, resource-ID name controls, IME, navigation selection and system-bar appearance. It passed **before** the final shape-only Profile parameter grouping/SaverScope lambda correction. That correction changes no layout, state or IME logic; it restores the original public five-argument ProfileScreen API. The final230-test UI run and explicit iOS compile include that correction. Earlier native XML is not presented as a fresh post-cleanup run.

A follow-up on final source commit `3d54a716` started the unchanged full instrumentation command. It was interrupted after36 completed POC runner records:29 passed and7 failed with Activity launch timeouts. Production and UI Acceptance were not reached; these are partial status records, not final suite XML. The saved logs also show system renderer stalls, and an independent system screenshot timed out. A bounded headless SwiftShader restart then displayed a Quickstep ANR on Launcher before the app was launched, so no second instrumentation run began. A production-code root cause has not been established. See [follow-up diagnostics](resume-native-diagnostics.md), `native-final-attempt1-partial.json` and `native-final-attempt1-interrupted.log`.

Explicit `:feature:photo-analysis:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true` passed on Intel with the full Xcode developer directory, including the changed shared UI. `apple-compile.log` is compilation evidence; no Apple device/simulator execution is claimed. Existing iOS production reachability remains camera/review plus the standalone Rewards factory; the acceptance Home placeholder is not production Home evidence.

`qualityFormat --continue` completed with0 fresh UI findings. The final `qualityCheckJvmAndroid --no-configuration-cache --continue` has exactly one failed task: `verifyMobilePolicy`, because required physical `bootstrap-android-host` evidence has expired. All fresh code-analysis XMLs have0 findings. Typed Detekt additionally reports3 existing frontend compiler errors affecting analysis accuracy; zero findings do not establish complete typed analysis. No suppression, baseline, denominator exclusion or evidence expiry was changed. Aggregate coverage is not claimed green. See `format.log` and `quality-gate.log`.

## Commands and host

Host: macOS Intel x86_64; Android Studio JBR. Android evidence used only `emulator-5554`, AVD `SN_Final_Verification_35_20261018`, API35 x86_64,720×1560 at320dpi (360×780dp). Only one ADB endpoint was connected during the successful full run. No physical Android proof is claimed; the user explicitly accepted emulator-only verification.

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --console=plain
./mobile/gradlew -p mobile :core:ui:jvmTest :feature:photo-analysis-presenter:jvmTest :feature:photo-analysis:jvmTest
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest --no-configuration-cache --continue --console=plain --dependency-verification=strict -Pandroid.testInstrumentationRunnerArguments.deviceSerial=emulator-5554
./mobile/gradlew -p mobile :feature:photo-analysis:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --no-configuration-cache --console=plain
./mobile/gradlew -p mobile qualityCheckJvmAndroid --no-configuration-cache --continue --console=plain
```

JDK: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`. Apple compile used `DEVELOPER_DIR=/Applications/Xcode-26.6.0.app/Contents/Developer`, without an SDKROOT override. One Gradle invocation per worktree was active.

## Regression and review evidence

New regressions were observed failing before their fixes: original UI2/2 failures, Review/Profile2/2, pending-provider native4 tests/1 intended failure, independent acceptance9 tests/8 intended failures, standalone localized-title clearance1/1, rendered camera contrast1/1. The final native Back red run had5 tests/1 intended failure while all4 camera-provider cases passed. Their saved `red-*.xml` reports describe deliberate pre-fix execution.

Two fresh-context source reviews are retained in [Blind Hunter](review-blind.md) and [Edge Case Hunter](review-edge.md), with [triage](review-triage.md). Source review is separate from runtime verification. The final UI regressions cover RTL at normal/200%, actual reward counts, standalone Back/title clearance, complete localized summary templates, native IDs and visible session-name guidance.

## Images and pending device verification

`fixture-home-375x812.png`, `fixture-rewards-375x830.png` and `fixture-review.png` are fresh **JVM** renders with explicit Figma fixture data/photo. Their nutrition and earned examples never enter the production host.

`native-initial-*.png` are the earlier actual emulator visual pass **before independent review corrections**; they document normal/200% geometry, inline keyboard and Profile/history routing. They are not final acceptance screenshots. `red-camera-anr.png` documents the original app camera deadlock, whose blocked future read was subsequently fixed and whose readiness tests passed. `environment-quickstep-anr.png`/`environment-system-ui.json` describe later Android system-component failures during cold-start setup, separately from the app defect.

A post-cleanup native rerun was guarded on `sys.boot_completed`; the guard exited before starting Gradle. It is a missing execution prerequisite, not failing/passing product evidence. After ADB transport failure, repeated cold starts stalled before full Android readiness and showed Launcher/System UI ANRs; host load average reached390.56. The task emulator was stopped; no user applications were terminated. The final post-cleanup production assembly and48 affected-module unit tests passed (`production-assembly-and-unit.log`). The APK was rebuilt from the final sources; its SHA-256 is recorded in results.json, without an installed-APK comparison. At that point, final native screenshots, installed-APK hash comparison and camera smoke were pending; the clean-AVD follow-up above has now completed all three. Font scale was restored to1.0 during the earlier completed visual pass.

The later final-source attempt described above reached Android boot completion but did not yield a completed passing native run. `environment-resume-system-ui-anr.png` and `environment-headless-quickstep-anr.png` are system-failure diagnostics, not app acceptance images. The task emulator and its isolated Gradle invocation were stopped; no verification jobs were left running. Required physical-device evidence remains unavailable by the user's accepted emulator-only constraint.

## Final APK

Current production-debug APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`
SHA-256: `7a7984fa1ef5eed14919e861210d4f232db2da7d16b3d80868970993b81a2a33`
Size: 511675995 bytes. The final assembly passed in7m59s. The clean-AVD follow-up confirmed the same SHA-256 on the installed APK; see the current runtime evidence linked above.

Stored XML copies use LF line endings; their parsed records were compared before and after normalization and are unchanged. Stored console-log copies trim trailing whitespace. Original build reports and raw logs remain untouched.
