# Intel iOS simulator verification with Claude

**Currently blocked with Compose 1.11.1:** this pinned release has no
`iosX64` variants. The historical SwiftUI compatibility renderer still uses
`SharedKit`, which links the Compose feature modules. Its framework command
therefore fails during dependency resolution, so the Intel acceptance app
cannot currently build or run. Xcode installation does not resolve this
dependency limitation.

This runbook retains the older Intel commands and historical verification
record for reproducibility. Steps 3 and 4 require a genuinely successful
framework build; they are currently blocked. Do not use stale framework
output as evidence or change dependency versions, target policy, or source
filters to bypass the failure.

`iosSimulatorArm64` requires an Apple Silicon host under the current target
policy. An Intel Mac can still compile an `iosArm64` device framework and
package the arm64 host using the installed iPhone SDK. Compilation and
packaging do not establish simulator or physical-device execution.

## Copy this to Claude first

Paste this prompt into Claude Code from the repository root:

```text
You are working in the SuperNutrition repository. Read AGENTS.md and
docs/ios-simulator-claude-runbook.md before running commands.

My goal is to verify whether the iOS acceptance app can launch on an Intel
Mac simulator. The pinned Compose 1.11.1 has no iosX64 variants, including
for the older SwiftUI compatibility path because SharedKit still links
Compose. Preserve that limitation; do not change dependency versions,
source filters, or target policy. Do not run scripts/run-ui-journeys-ios.sh
on this Intel host or claim arm64 compilation as simulator execution.

First report the current git status, host architecture, Xcode version,
available iOS runtimes, and simulator UDID. Use process-local DEVELOPER_DIR
if selecting an installed Xcode; do not change global Xcode settings.
Execute the documented framework command only when a fresh reproduction
is needed. Stop at its first failure and report the command and relevant
output. Run simulator tests, install, and launch only after a fresh
framework build succeeds. Report each stage separately, marking unrun
stages as blocked and omitting a launch PID when no launch occurred.
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
uname -m
xcode-select -p
xcodebuild -version
xcrun simctl list runtimes
xcrun simctl list devices available
```

Select one available simulator and keep its complete UDID. Do not use the
physical iPhone UDID from the `Devices` section.

For the historical run recorded below, the simulator was:

```text
Name: SuperNutrition iPhone 17 Pro
UDID: REDACTED_SIMULATOR_UDID
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

The command below explicitly enables the Intel target;
`enableSharedKitFramework` exposes the framework consumed by the Xcode host.
This retained host-slice invocation first fails at missing `:domain:nutrition`
during configuration. A full-registry retry separately exposed missing strict
Intel dependency checksums; the recorded temporary checksum enrollment then
reached the unsupported Compose1.11.1 `iosX64` variant. Those enrollments were
reverted. Preserve the first failure and stop before step3.

```bash
./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosX64 \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64=true \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict \
  --no-configuration-cache
```

The historical successful output path was:

```text
mobile/apps/sharedKit/build/bin/iosX64/debugFramework/SharedKit.framework
```

The current Xcode production and acceptance phases run before Swift/linking
on every build, leaving transitive input tracking to Gradle. They both call
`mobile/apps/iosApp/build-sharedkit-framework.sh`. The helper validates
`CONFIGURATION=Debug|Release` and maps `KOTLIN_TARGET` as follows:

| Requested target (either initial case) | Task suffix | Intel target enabled |
| --- | --- | --- |
| `iosArm64` / `IosArm64` | `IosArm64` | `false` |
| `iosSimulatorArm64` / `IosSimulatorArm64` | `IosSimulatorArm64` | `false` |
| `iosX64` / `IosX64` | `IosX64` | `true` |

It retains the existing Apple flags, uses strict dependency verification and
`--no-configuration-cache`, inherits process-local `DEVELOPER_DIR`, and
propagates the Gradle exit status. It does not add targets unsupported by the
host policy. To compile a device framework from this Intel host instead:

```bash
PROJECT_DIR="$PWD/mobile/apps/iosApp" \
KOTLIN_TARGET=iosArm64 CONFIGURATION=Debug \
  /bin/sh mobile/apps/iosApp/build-sharedkit-framework.sh
```

That output is for an iPhone SDK build and cannot be installed in the Intel
simulator.

## 3. Run the iOS simulator acceptance tests

**Currently blocked:** run this retained command only after step 2 succeeds
with a fresh framework. The architecture flags must remain present on Intel
Macs.

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

**Currently blocked:** this retained step requires a successful fresh
framework build. It uses a temporary DerivedData directory so an old app
bundle is not mistaken for the current build.

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
3. the Gradle framework task, exit status, and dependency error if it failed;
4. the Xcode test command exit status, or why it did not run;
5. the installed bundle identifier and launch PID only if install/launch ran;
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
`scripts/run-ui-journeys-ios.sh`. That lane requires Apple Silicon under the
current target policy. The Intel commands in this runbook also remain blocked
by the current Compose dependency variants. Do not remove the arm64 target or
change the canonical script to hide the missing environment.

### No matching Compose variant for `iosX64`

This is the current Compose 1.11.1 limitation. The older SwiftUI renderer
still links Compose through `SharedKit`. Report the failed framework build
and unrun simulator stages; keep versions, source sets, and verification
metadata unchanged. For arm64 host compilation, use the helper with
`KOTLIN_TARGET=iosArm64` and keep execution results separate.

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

The older Intel command sequence was verified on 2026-07-28 at commit
`3586d445f128b0b7f18e81c515b0fdb7ac6cb41f` with:

- Intel macOS host;
- Xcode 26.0.1;
- iOS 26.0 simulator;
- `linkDebugFrameworkIosX64` successful;
- `xcodebuild clean test` successful with exit code `0`;
- manual `simctl install` successful;
- `simctl launch` successful for `com.supernutrition.ios.uiacceptance`.

That run predates the current dependency tuple and does not establish that
these commands work with Compose 1.11.1. It confirmed the temporary Intel
simulator path at that historical commit only, without claiming
`iosSimulatorArm64`, physical-device, camera, or release readiness.
