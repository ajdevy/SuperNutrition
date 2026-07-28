# Launch SuperNutrition on the iOS Simulator with Claude

This runbook is for a developer using Claude Code on an Intel Mac. It builds
the project's temporary `iosX64` simulator compatibility lane, runs the
acceptance tests, installs the app, and launches it with `simctl`.

It does not use `iosSimulatorArm64`. That is the canonical Apple target, but it
requires an Apple Silicon host. The `iosX64` result is local simulator
acceptance only; it is not physical-device, camera, hardware, release, or
canonical Apple evidence.

## Copy this to Claude first

Paste this prompt into Claude Code from the repository root:

```text
You are working in the SuperNutrition repository. Read AGENTS.md and
docs/ios-simulator-claude-runbook.md before running commands.

My goal is to launch the iOS acceptance app on an Intel Mac simulator.
Use the documented iosX64 compatibility lane only. Do not use
iosSimulatorArm64, do not run scripts/run-ui-journeys-ios.sh on this Intel
host, and do not edit production source files.

First report the current git status, Xcode version, available iOS runtimes,
and simulator UDID. Then execute the documented framework build, simulator
test, app install, and simctl launch commands. Use the exact simulator UDID,
not a guessed device name. Stop and report the first error with the command
and relevant output. At the end report the build result, test result, bundle
identifier, launch PID, and any files changed.
```

## Preconditions

The developer needs:

- macOS with Xcode and its command-line tools selected;
- a simulator runtime installed in Xcode;
- Java available for the Gradle wrapper;
- a clean or intentionally understood checkout of this repository.

No Apple signing team or physical iPhone is required for this simulator lane.

Run all commands from the repository root, the directory containing
`mobile/`, `scripts/`, and `AGENTS.md`.

## 1. Check the host and choose a simulator

```bash
git status --short --branch
xcode-select -p
xcodebuild -version
xcrun simctl list runtimes
xcrun simctl list devices available
```

Select one available simulator and keep its complete UDID. Do not use the
physical iPhone UDID from the `Devices` section.

For the verified run on this checkout, the simulator was:

```text
Name: SuperNutrition iPhone 17 Pro
UDID: 5C25FE73-EFFC-47A7-820F-3B12FE1F027F
Runtime: iOS 26.0 (Xcode 26.0.1)
```

Set the chosen UDID in the shell:

```bash
export SIMULATOR_UDID="<available-simulator-udid>"
```

Boot it and wait until it is ready:

```bash
xcrun simctl boot "$SIMULATOR_UDID" 2>/dev/null || true
xcrun simctl bootstatus "$SIMULATOR_UDID" -b
```

## 2. Build the Intel simulator framework

The flags below are required. In particular, `enableIntelIosX64` prevents an
implicit arm64 host build, and `enableSharedKitFramework` exposes the
framework path consumed by the Xcode host.

```bash
./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosX64 \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64=true \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict \
  --no-configuration-cache
```

Expected result:

```text
BUILD SUCCESSFUL
```

The framework must exist at:

```text
mobile/apps/sharedKit/build/bin/iosX64/debugFramework/SharedKit.framework
```

## 3. Run the iOS simulator acceptance tests

Run the Xcode scheme against the explicit simulator. The architecture flags
must remain present on Intel Macs.

```bash
xcodebuild clean test -quiet \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -scheme SuperNutritionUIAcceptance \
  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
  -derivedDataPath mobile/apps/iosApp/build/DerivedData \
  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosX64/debugFramework" \
  CODE_SIGNING_ALLOWED=NO \
  ARCHS=x86_64 \
  ONLY_ACTIVE_ARCH=YES
```

Expected result:

```text
Testing started
```

The command must exit with status `0`. Xcode may print warnings about not
stripping signed XCTest binaries; those warnings are expected when the command
still exits successfully.

## 4. Build, install, and launch the app manually

This step proves that the app itself can be opened, independently of the test
runner. It uses a temporary DerivedData directory so an old app bundle is not
mistaken for the current build.

```bash
set -euo pipefail

DERIVED_DATA_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ios-simulator.XXXXXX")"

xcodebuild build -quiet \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -scheme SuperNutritionUIAcceptance \
  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
  -derivedDataPath "$DERIVED_DATA_ROOT" \
  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosX64/debugFramework" \
  CODE_SIGNING_ALLOWED=NO \
  ARCHS=x86_64 \
  ONLY_ACTIVE_ARCH=YES

APP_PATH="$(find "$DERIVED_DATA_ROOT/Build/Products" \
  -type d -path '*/Debug-iphonesimulator/SuperNutrition.app' \
  -print -quit)"
test -n "$APP_PATH"

xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
xcrun simctl launch "$SIMULATOR_UDID" com.supernutrition.ios.uiacceptance
```

Expected result is a launch line containing the bundle identifier and a PID:

```text
com.supernutrition.ios.uiacceptance: <pid>
```

The app's acceptance launch marker is
`bootstrap_shell_launch_di`. The acceptance bundle identifier is
`com.supernutrition.ios.uiacceptance`.

Optional verification:

```bash
xcrun simctl listapps "$SIMULATOR_UDID" | grep -A8 -B2 \
  'com.supernutrition.ios.uiacceptance'
```

## What Claude should report

The final report should include:

1. the simulator name, runtime, and exact UDID;
2. the Xcode version;
3. the Gradle framework task and whether it ended with `BUILD SUCCESSFUL`;
4. the Xcode test command exit status;
5. the installed bundle identifier and launch PID;
6. `git status --short` output and any files changed.

A successful simulator launch does not close the physical iPhone evidence
requirements. Keep those results separate.

## Troubleshooting

### `Unable to find a destination`

The UDID is stale or the simulator is unavailable. Re-run:

```bash
xcrun simctl list devices available
```

Choose an available simulator UDID and export it again. Do not substitute the
iPhone UDID shown by `xcrun xctrace list devices`.

### `iosSimulatorArm64` or Xcode 26.4.1 is required

That message comes from the canonical active Apple lane, usually through
`scripts/run-ui-journeys-ios.sh`. On an Intel Mac, use this runbook's explicit
`iosX64` commands instead. Do not remove the arm64 target or change the
canonical script to hide the missing Apple Silicon environment.

### `SharedKit.framework` cannot be found

Re-run the Gradle framework command from the repository root and verify:

```bash
test -d mobile/apps/sharedKit/build/bin/iosX64/debugFramework/SharedKit.framework
```

Then pass the absolute `SHARED_KIT_FRAMEWORK_DIR` shown in this runbook.

### Code signing errors on the simulator

Confirm that both Xcode commands include:

```text
CODE_SIGNING_ALLOWED=NO
```

Do not add a development team or store signing credentials for this simulator
lane.

### The old app is still visible

Uninstall only this acceptance bundle and reinstall the newly built app:

```bash
xcrun simctl uninstall "$SIMULATOR_UDID" com.supernutrition.ios.uiacceptance || true
xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
xcrun simctl launch "$SIMULATOR_UDID" com.supernutrition.ios.uiacceptance
```

## Verification record

This runbook was verified on 2026-07-28 at commit
`3586d445f128b0b7f18e81c515b0fdb7ac6cb41f` with:

- Intel macOS host;
- Xcode 26.0.1;
- iOS 26.0 simulator;
- `linkDebugFrameworkIosX64` successful;
- `xcodebuild clean test` successful with exit code `0`;
- manual `simctl install` successful;
- `simctl launch` successful for `com.supernutrition.ios.uiacceptance`.

The run confirmed the temporary Intel simulator path only. It did not claim
`iosSimulatorArm64`, physical-device, camera, or release readiness.
