# Runtime verification — 2026-10-23

Android verification is complete on final UI source `3d54a716730858a313ac54ba943675251d10852a`. iOS simulator execution is unavailable on this Intel host; the user explicitly accepted that limitation. The full iPhone arm64 SharedKit framework build passed with strict dependency verification; The unsigned Xcode production target now also compiles and links; package inspection found a separate, pre-existing omission of Compose resources.

Worktree: `/Users/REDACTED/.codex/worktrees/f2ff/SuperNutrition`
Branch: `codex/home-profile`; starting HEAD `779fc327405b19f6301874edc79d5f5c6536b15e`.
Host: Intel x86_64, macOS15.8; JDK from Android Studio. Xcode16.4 (16F6) and26.6 (17F113) are installed. Global `xcode-select` remains unchanged.

## Android result

A new task-owned AVD `SN_Screenshots_20261023`, API35 x86_64,720×1560 at320dpi (360×780dp), host OpenGL, was used. Existing AVD data was preserved. Only `emulator-5554` was connected.

| Variant | Tests | Failures | Errors | Skipped | Fresh XML |
|---|---:|---:|---:|---:|---|
| Production |50|0|0|0|[Report](android-production.xml)|
| POC |50|0|0|0|[Report](android-poc.xml)|
| UI Acceptance |50|0|0|0|[Report](android-uiAcceptance.xml)|

The unchanged full suite completed in8m3s with exit0. XML timestamps were checked against this run and their nested testcase counts were independently counted. See [machine-readable results](android-native-results.json) and [full log](android-native.log).

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
  --no-configuration-cache --continue --console=plain \
  --dependency-verification=strict \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial=emulator-5554 \
  --no-daemon --max-workers=2
```

The current production-debug APK and installed package both hash to `7a7984fa1ef5eed14919e861210d4f232db2da7d16b3d80868970993b81a2a33` (511675995bytes). The production APK was reinstalled after the suite teardown. [Exact comparison](android-installed-apk.json).

Manual checks covered Home → Profile → Meal history and Back; native name-edit IME and draft cancellation; Rewards; camera foreground permission and emulated capture; native picker import → on-device analysis → Review. The empty history is real: no fixture candidates were confirmed. At200% font scale the Home cards are readable through scrolling; font scale was restored to1.0. [Manual results](android-manual-smoke.json).

## Actual Android screenshots

| Screen | Screenshot |
|---|---|
| Home |[PNG](android-home.png)|
| Profile |[PNG](android-profile.png)|
| Meal history, empty session |[PNG](android-meal-history.png)|
| Inline name editing and native keyboard |[PNG](android-profile-edit-keyboard.png)|
| Rewards |[PNG](android-rewards.png)|
| Emulated camera preview |[PNG](android-camera.png)|
| Review after importing the checked-in food photo |[PNG](android-review-test-photo.png)|
| Home at200%, initial viewport |[PNG](android-home-200.png)|
| Home at200%, scrolled |[PNG](android-home-200-scrolled.png)|

These are device captures, not fixture renders. Review shows actual unconfirmed model candidates; it is not an accuracy pass. See [findings](findings.md).

## iOS result and accepted limitation

Both Xcode installations initially reported no simulator runtimes. The documented SharedKit slice first failed because it omits `domain:nutrition`. Using the full registry, strict verification first exposed missing Intel dependency checksums.47 exact temporary enrollments were checked against Maven Central / Google publications and their verified module file digests; the final attempt then reached the actual architecture failure: Compose1.11.1 runtime, foundation, UI and resources publish no `ios_x64` variant. [Final build log](ios-x64-no-variant.log), [checksum audit](ios-dependency-checksums.json).

The checked-in SwiftUI compatibility host still links that shared UI dependency graph; the old runbook therefore does not provide a working current-source simulator build. No iOS test, app launch or screenshot is claimed. The user replied “Сейчас только Intel — зафиксируй ограничение”. The task-owned runtime download was interrupted with exit130; temporary checksum enrollments were reverted exactly. No dependency version, verification mode, source filter, lifecycle policy or global Xcode selection was changed.

## iPhone arm64 compilation

`:apps:sharedKit:linkDebugFrameworkIosArm64` passed in9m21s with strict dependency verification, Xcode26.6 and the original verification metadata. `lipo -archs` confirms arm64; the produced framework is239MiB. This is compilation/linking evidence only. [Result and digest](ios-arm64-framework.json), [full log](ios-arm64-framework.log).

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR=/Applications/Xcode-26.6.0.app/Contents/Developer \
./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosArm64 \
  -Psupernutrition.enableSharedKitFramework=true \
  -Psupernutrition.enableIosDeviceTarget=true \
  --dependency-verification=strict --continue --no-configuration-cache \
  --no-daemon --max-workers=2 --console=plain
```

## Xcode production host build

The initial scheme build had no eligible generic iOS destination and exited70 before compilation. A direct production-target SDK build then exposed a real project defect: both framework phases forced the unsupported Intel target even for an arm64 iPhone build. The pre-fix build failed exit65 in its late framework phase, after Swift/link commands had already run; [red command](ios-production-target-red.json) and [red output](ios-production-target-red.log) are preserved.

Both Xcode phases now run first and unconditionally through one validated helper, selecting Intel dependencies only for an explicit iosX64 target. Gradle owns incremental input tracking. A timestamp-only regression probe initially exposed a dependency cycle from the inherited late phase order; the corrected rebuild runs Gradle before Swift/link, and a second unchanged incremental build also invokes Gradle and passes. [Regression red](ios-incremental-red.json), [unchanged-build green](ios-incremental-green.json). All13 isolated helper boundary checks passed, including three target mappings and aliases, Release, missing/invalid input, quoted paths and Gradle failure propagation. [Boundary results](ios-helper-boundaries.json).

With Xcode26.6, the first corrected direct production-target SDK build passed in45s. After review, the final timestamp-regression build passed with exit0 in17.5s: the framework phase used strict verification, Swift compiled, and the app executable linked as arm64. The fresh bundle identifier is `com.supernutrition.ios` and installed display name is `Super Nutrition`. Signing was disabled for this local compile. [Exact command/result](ios-production-target-build.json), [log](ios-production-target-build.log), [package inventory](ios-app-package.json). No simulator, device, installation, launch or scheme-build success is implied.

Package inspection found another existing defect: the nine-file bundle contains no shared Compose resources, including the fonts and artwork required by Home/Profile/Rewards. This is recorded as IOS-PACKAGE-1 in [findings](findings.md) and deferred for focused iOS packaging work. Consequently the compilation pass does **not** establish a complete, runnable iOS UI package. The accepted Intel execution limitation remains in force.

After reverting the temporary iOS checksum enrollments, production APK and all three instrumentation APK variants assembled again with strict verification in49s. The production APK SHA-256 is unchanged. [Post-revert assembly log](android-original-trust-assembly.log).

## Remaining boundaries

No physical Android or Apple evidence was collected. The required physical `bootstrap-android-host` record remains expired; this emulator run does not make the full policy gate green. Apple runtime on a supported Mac or iPhone remains unverified. The existing-source JVM and explicit Apple module compilation results are retained in the [earlier report](../existing-screens-verification-2026-10-23/README.md).

## Review and handoff

Two independent skill-directed reviewers inspected the complete diff. Five deduplicated actionable findings were resolved: Xcode dependency-analysis skipping, late framework phase order, command failure-stage documentation, original red-build wording, and manual-record timestamp scope. [Triage](review-triage.md). No capped or incomplete independent review is represented as a pass.

The existing Compose resource packaging defect remains open. No physical Android/iOS evidence was collected, and no iOS UI was launched. The task emulator and runtime download were stopped; the font scale was restored.

`sh -n`, PBX `plutil -lint`, and `git diff --check` pass. `graphify update . --no-cluster` refreshed the local code graph to21660 nodes/31260 edges; its diagnostics report488 zero-node inputs and unavailable optional SQL parser for two SQL files. No semantic document/image refresh is claimed. [Graph update log](graphify-update.log).

Stored logs normalize trailing whitespace and final blank lines only; changed log digests are recorded in [normalization metadata](log-normalization.json).
