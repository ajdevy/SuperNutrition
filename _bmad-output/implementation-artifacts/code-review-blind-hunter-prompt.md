# BMAD Blind Hunter prompt

Run the `bmad-review-adversarial-general` skill in a separate session.

Review only the unified diff below. You have no project context. Be cynical and find at least ten actionable issues or improvements. Return a Markdown list of findings only. Do not modify files.

## Diff

```diff
diff --git a/docs/ios-simulator-claude-runbook.md b/docs/ios-simulator-claude-runbook.md
new file mode 100644
index 0000000..8abd472
--- /dev/null
+++ b/docs/ios-simulator-claude-runbook.md
@@ -0,0 +1,262 @@
+# Launch SuperNutrition on the iOS Simulator with Claude
+
+This runbook is for a developer using Claude Code on an Intel Mac. It builds
+the project's temporary `iosX64` simulator compatibility lane, runs the
+acceptance tests, installs the app, and launches it with `simctl`.
+
+It does not use `iosSimulatorArm64`. That is the canonical Apple target, but it
+requires an Apple Silicon host. The `iosX64` result is local simulator
+acceptance only; it is not physical-device, camera, hardware, release, or
+canonical Apple evidence.
+
+## Copy this to Claude first
+
+Paste this prompt into Claude Code from the repository root:
+
+```text
+You are working in the SuperNutrition repository. Read AGENTS.md and
+docs/ios-simulator-claude-runbook.md before running commands.
+
+My goal is to launch the iOS acceptance app on an Intel Mac simulator.
+Use the documented iosX64 compatibility lane only. Do not use
+iosSimulatorArm64, do not run scripts/run-ui-journeys-ios.sh on this Intel
+host, and do not edit production source files.
+
+First report the current git status, Xcode version, available iOS runtimes,
+and simulator UDID. Then execute the documented framework build, simulator
+test, app install, and simctl launch commands. Use the exact simulator UDID,
+not a guessed device name. Stop and report the first error with the command
+and relevant output. At the end report the build result, test result, bundle
+identifier, launch PID, and any files changed.
+```
+
+## Preconditions
+
+The developer needs:
+
+- macOS with Xcode and its command-line tools selected;
+- a simulator runtime installed in Xcode;
+- Java available for the Gradle wrapper;
+- a clean or intentionally understood checkout of this repository.
+
+No Apple signing team or physical iPhone is required for this simulator lane.
+
+Run all commands from the repository root, the directory containing
+`mobile/`, `scripts/`, and `AGENTS.md`.
+
+## 1. Check the host and choose a simulator
+
+```bash
+git status --short --branch
+xcode-select -p
+xcodebuild -version
+xcrun simctl list runtimes
+xcrun simctl list devices available
+```
+
+Select one available simulator and keep its complete UDID. Do not use the
+physical iPhone UDID from the `Devices` section.
+
+For the verified run on this checkout, the simulator was:
+
+```text
+Name: SuperNutrition iPhone 17 Pro
+UDID: REDACTED_SIMULATOR_UDID
+Runtime: iOS 26.0 (Xcode 26.0.1)
+```
+
+Set the chosen UDID in the shell:
+
+```bash
+export SIMULATOR_UDID="<available-simulator-udid>"
+```
+
+Boot it and wait until it is ready:
+
+```bash
+xcrun simctl boot "$SIMULATOR_UDID" 2>/dev/null || true
+xcrun simctl bootstatus "$SIMULATOR_UDID" -b
+```
+
+## 2. Build the Intel simulator framework
+
+The flags below are required. In particular, `enableIntelIosX64` prevents an
+implicit arm64 host build, and `enableSharedKitFramework` exposes the
+framework path consumed by the Xcode host.
+
+```bash
+./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosX64 \
+  -Psupernutrition.hostSlice=sharedKit \
+  -Psupernutrition.enableTestingAppleTargets=true \
+  -Psupernutrition.enableIntelIosX64=true \
+  -Psupernutrition.enableSharedKitFramework=true \
+  --dependency-verification=strict \
+  --no-configuration-cache
+```
+
+Expected result:
+
+```text
+BUILD SUCCESSFUL
+```
+
+The framework must exist at:
+
+```text
+mobile/apps/sharedKit/build/bin/iosX64/debugFramework/SharedKit.framework
+```
+
+## 3. Run the iOS simulator acceptance tests
+
+Run the Xcode scheme against the explicit simulator. The architecture flags
+must remain present on Intel Macs.
+
+```bash
+xcodebuild clean test -quiet \
+  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
+  -scheme SuperNutritionUIAcceptance \
+  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
+  -derivedDataPath mobile/apps/iosApp/build/DerivedData \
+  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosX64/debugFramework" \
+  CODE_SIGNING_ALLOWED=NO \
+  ARCHS=x86_64 \
+  ONLY_ACTIVE_ARCH=YES
+```
+
+Expected result:
+
+```text
+Testing started
+```
+
+The command must exit with status `0`. Xcode may print warnings about not
+stripping signed XCTest binaries; those warnings are expected when the command
+still exits successfully.
+
+## 4. Build, install, and launch the app manually
+
+This step proves that the app itself can be opened, independently of the test
+runner. It uses a temporary DerivedData directory so an old app bundle is not
+mistaken for the current build.
+
+```bash
+set -euo pipefail
+
+DERIVED_DATA_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ios-simulator.XXXXXX")"
+
+xcodebuild build -quiet \
+  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
+  -scheme SuperNutritionUIAcceptance \
+  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
+  -derivedDataPath "$DERIVED_DATA_ROOT" \
+  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosX64/debugFramework" \
+  CODE_SIGNING_ALLOWED=NO \
+  ARCHS=x86_64 \
+  ONLY_ACTIVE_ARCH=YES
+
+APP_PATH="$(find "$DERIVED_DATA_ROOT/Build/Products" \
+  -type d -path '*/Debug-iphonesimulator/SuperNutrition.app' \
+  -print -quit)"
+test -n "$APP_PATH"
+
+xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
+xcrun simctl launch "$SIMULATOR_UDID" com.supernutrition.ios.uiacceptance
+```
+
+Expected result is a launch line containing the bundle identifier and a PID:
+
+```text
+com.supernutrition.ios.uiacceptance: <pid>
+```
+
+The app's acceptance launch marker is
+`bootstrap_shell_launch_di`. The acceptance bundle identifier is
+`com.supernutrition.ios.uiacceptance`.
+
+Optional verification:
+
+```bash
+xcrun simctl listapps "$SIMULATOR_UDID" | grep -A8 -B2 \
+  'com.supernutrition.ios.uiacceptance'
+```
+
+## What Claude should report
+
+The final report should include:
+
+1. the simulator name, runtime, and exact UDID;
+2. the Xcode version;
+3. the Gradle framework task and whether it ended with `BUILD SUCCESSFUL`;
+4. the Xcode test command exit status;
+5. the installed bundle identifier and launch PID;
+6. `git status --short` output and any files changed.
+
+A successful simulator launch does not close the physical iPhone evidence
+requirements. Keep those results separate.
+
+## Troubleshooting
+
+### `Unable to find a destination`
+
+The UDID is stale or the simulator is unavailable. Re-run:
+
+```bash
+xcrun simctl list devices available
+```
+
+Choose an available simulator UDID and export it again. Do not substitute the
+iPhone UDID shown by `xcrun xctrace list devices`.
+
+### `iosSimulatorArm64` or Xcode 26.4.1 is required
+
+That message comes from the canonical active Apple lane, usually through
+`scripts/run-ui-journeys-ios.sh`. On an Intel Mac, use this runbook's explicit
+`iosX64` commands instead. Do not remove the arm64 target or change the
+canonical script to hide the missing Apple Silicon environment.
+
+### `SharedKit.framework` cannot be found
+
+Re-run the Gradle framework command from the repository root and verify:
+
+```bash
+test -d mobile/apps/sharedKit/build/bin/iosX64/debugFramework/SharedKit.framework
+```
+
+Then pass the absolute `SHARED_KIT_FRAMEWORK_DIR` shown in this runbook.
+
+### Code signing errors on the simulator
+
+Confirm that both Xcode commands include:
+
+```text
+CODE_SIGNING_ALLOWED=NO
+```
+
+Do not add a development team or store signing credentials for this simulator
+lane.
+
+### The old app is still visible
+
+Uninstall only this acceptance bundle and reinstall the newly built app:
+
+```bash
+xcrun simctl uninstall "$SIMULATOR_UDID" com.supernutrition.ios.uiacceptance || true
+xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
+xcrun simctl launch "$SIMULATOR_UDID" com.supernutrition.ios.uiacceptance
+```
+
+## Verification record
+
+This runbook was verified on 2026-07-28 at commit
+`3586d445f128b0b7f18e81c515b0fdb7ac6cb41f` with:
+
+- Intel macOS host;
+- Xcode 26.0.1;
+- iOS 26.0 simulator;
+- `linkDebugFrameworkIosX64` successful;
+- `xcodebuild clean test` successful with exit code `0`;
+- manual `simctl install` successful;
+- `simctl launch` successful for `com.supernutrition.ios.uiacceptance`.
+
+The run confirmed the temporary Intel simulator path only. It did not claim
+`iosSimulatorArm64`, physical-device, camera, or release readiness.
diff --git a/mobile/REAL_DEVICE_TESTING.md b/mobile/REAL_DEVICE_TESTING.md
index 9f85e71..b873d77 100644
--- a/mobile/REAL_DEVICE_TESTING.md
+++ b/mobile/REAL_DEVICE_TESTING.md
@@ -106,8 +106,6 @@ cannot accidentally close the normalization gap:
 APPLE_TEAM_ID="<local-team-id>" \
 IOS_CODE_SIGN_IDENTITY="Apple Development: <name> (<certificate-id>)" \
 IOS_UDID="<physical-iphone-udid>" \
-  scripts/run-physical-ios-fixtures.sh
-
 xcodebuild test -quiet \
   -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
   -scheme SuperNutritionUIAcceptance \
@@ -118,6 +116,11 @@ xcodebuild test -quiet \
   CODE_SIGN_STYLE=Automatic \
   CODE_SIGN_IDENTITY="Apple Development" \
   -allowProvisioningUpdates -allowProvisioningDeviceRegistration
+
+APPLE_TEAM_ID="<local-team-id>" \
+IOS_CODE_SIGN_IDENTITY="Apple Development: <name> (<certificate-id>)" \
+IOS_UDID="<physical-iphone-udid>" \
+  scripts/run-physical-ios-fixtures.sh
 ```
 
 Then emit the native-evidence bundle with the real iPhone UDID:
@@ -127,6 +130,10 @@ IOS_UDID=<physical-iphone-udid> \
   scripts/emit-physical-ios-evidence.sh "$RUNNER_TEMP/supernutrition-ios-evidence"
 ```
 
+The fixture runner reuses the valid development profile embedded by the
+physical Xcode acceptance build. If the profile is stored elsewhere, pass it
+explicitly with `IOS_PROVISIONING_PROFILE="<path-to-mobileprovision>"`.
+
 The runner builds the `iosArm64TestBinaries` output, executes its exact
 Kotlin/Native binary inside a temporary signed app on the iPhone, and converts
 the TeamCity stream into the `iosArm64Test` normalization XML expected by the
diff --git a/mobile/apps/iosApp/README.md b/mobile/apps/iosApp/README.md
index 8e97de3..302b086 100644
--- a/mobile/apps/iosApp/README.md
+++ b/mobile/apps/iosApp/README.md
@@ -92,3 +92,9 @@ IOS_UDID="<physical-udid>" \
 IOS_DERIVED_DATA_ROOT="$PWD/mobile/apps/iosApp/build/DerivedData" \
   scripts/emit-physical-ios-evidence.sh "/tmp/supernutrition-ios-evidence"
 ```
+
+The fixture runner must use a valid development provisioning profile for the
+same acceptance bundle ID. After the physical Xcode test, it discovers the
+profile from `IOS_APP_PATH/embedded.mobileprovision`; otherwise pass an
+explicit `IOS_PROVISIONING_PROFILE` path. A signing certificate and matching
+entitlements alone are not installable on a real iPhone.
diff --git a/scripts/run-physical-ios-fixtures.sh b/scripts/run-physical-ios-fixtures.sh
index a5ef29f..0674e1d 100755
--- a/scripts/run-physical-ios-fixtures.sh
+++ b/scripts/run-physical-ios-fixtures.sh
@@ -6,11 +6,12 @@ repo_root="$(cd "$script_dir/.." && pwd)"
 ios_udid="${IOS_UDID:-}"
 team_id="${IOS_DEVELOPMENT_TEAM:-${APPLE_TEAM_ID:-}}"
 code_sign_identity="${IOS_CODE_SIGN_IDENTITY:-Apple Development}"
-bundle_id="${IOS_FIXTURE_BUNDLE_ID:-com.supernutrition.ios.arm64fixture.runner}"
+bundle_id="${IOS_FIXTURE_BUNDLE_ID:-}"
+provisioning_profile="${IOS_PROVISIONING_PROFILE:-}"
 results_root="${IOS_ARM64_TEST_RESULTS_ROOT:-$repo_root/mobile/platform/photo-ios/build/test-results/iosArm64Test}"
 fixture_binary="$repo_root/mobile/platform/photo-ios/build/bin/iosArm64/debugTest/test.kexe"
 
-for command_name in codesign plutil python3 xcrun; do
+for command_name in codesign plutil python3 security xcrun; do
   command -v "$command_name" >/dev/null 2>&1 || {
     printf 'missing required command: %s\n' "$command_name" >&2
     exit 1
@@ -55,9 +56,61 @@ rm -f "$results_root"/*IosImageNormalizerFixtureTest.xml \
 runner_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ios-fixture.XXXXXX")"
 trap 'rm -rf "$runner_root"' EXIT
 app_path="$runner_root/iosArm64FixtureRunner.app"
+
+# The Kotlin/Native test binary is wrapped in a temporary app, so it needs the
+# same valid development profile as a real iPhone host. A certificate and a
+# hand-written entitlements file are not sufficient for CoreDevice install.
+# Prefer an explicitly supplied profile, then reuse the profile embedded in a
+# locally built Debug-iphoneos acceptance app.
+if test -z "$provisioning_profile" && test -n "${IOS_APP_PATH:-}" && \
+  test -f "$IOS_APP_PATH/embedded.mobileprovision"; then
+  provisioning_profile="$IOS_APP_PATH/embedded.mobileprovision"
+fi
+if test -z "$provisioning_profile"; then
+  provisioning_profile="$(find "$derived_data_root" -type f \
+    -path '*/Debug-iphoneos/*.app/embedded.mobileprovision' -print -quit)"
+fi
+test -f "$provisioning_profile" || {
+  printf 'a valid iOS development provisioning profile is required; build the physical acceptance app first or set IOS_PROVISIONING_PROFILE\n' >&2
+  exit 1
+}
+
+profile_plist="$runner_root/profile.plist"
+security cms -D -i "$provisioning_profile" -o "$profile_plist" 2>/dev/null || {
+  printf 'unable to decode iOS provisioning profile: %s\n' "$provisioning_profile" >&2
+  exit 1
+}
+profile_app_id="$(plutil -extract Entitlements.application-identifier raw -o - "$profile_plist" 2>/dev/null || true)"
+profile_team_id="$(plutil -extract TeamIdentifier.0 raw -o - "$profile_plist" 2>/dev/null || true)"
+test -n "$profile_app_id" && test -n "$profile_team_id" || {
+  printf 'provisioning profile lacks application identifier/team entitlements: %s\n' "$provisioning_profile" >&2
+  exit 1
+}
+profile_bundle_pattern="${profile_app_id#*.}"
+if test -z "$bundle_id"; then
+  if test "$profile_bundle_pattern" = "*" || [[ "$profile_bundle_pattern" == *\** ]]; then
+    printf 'IOS_FIXTURE_BUNDLE_ID is required for a wildcard provisioning profile\n' >&2
+    exit 1
+  fi
+  bundle_id="$profile_bundle_pattern"
+fi
+if test "$profile_bundle_pattern" != "*" && [[ "$profile_bundle_pattern" != *\** ]] && \
+  test "$bundle_id" != "$profile_bundle_pattern"; then
+  printf 'fixture bundle id does not match provisioning profile: %s != %s\n' \
+    "$bundle_id" "$profile_bundle_pattern" >&2
+  exit 1
+fi
+if test -n "$team_id" && test "$team_id" != "$profile_team_id"; then
+  printf 'requested development team does not match provisioning profile: %s != %s\n' \
+    "$team_id" "$profile_team_id" >&2
+  exit 1
+fi
+team_id="$profile_team_id"
+
 mkdir -p "$app_path"
 cp "$fixture_binary" "$app_path/SuperNutritionIosArm64FixtureRunner"
 cp "$script_dir/ios-fixture-runner/Info.plist" "$app_path/Info.plist"
+cp "$provisioning_profile" "$app_path/embedded.mobileprovision"
 cp "$script_dir/ios-fixture-runner/Entitlements.plist" "$runner_root/Entitlements.plist"
 plutil -replace CFBundleIdentifier -string "$bundle_id" "$app_path/Info.plist"
 /usr/libexec/PlistBuddy -c "Set :application-identifier $team_id.$bundle_id" \

```

