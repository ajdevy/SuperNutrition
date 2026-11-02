# Physical-device evidence

This repository does not count an Android emulator or an iOS simulator as
camera, lifecycle, accessibility, performance, or energy evidence.

## Gradle execution defaults

`mobile/gradle.properties` enables the project defaults: a 256 MB initial / 4 GB
maximum Gradle heap, 1.5 GB metaspace, a 256 MB initial / 2 GB maximum Kotlin
daemon with 1 GB metaspace, four workers, build/configuration caches, parallel
execution, VFS watching, and a 30-minute idle timeout for both daemons. The
Kotlin daemon keeps its own 2 GB maximum heap and cannot silently fall back to
in-process compilation (which would duplicate memory). Normal local runs should omit
`--no-daemon --no-configuration-cache` so those settings can take effect. Use
those flags only when deliberately reproducing a clean, uncached evidence run.

The included `mobile/build-logic` build uses a separate 128 MB / 1 GB Gradle
profile, a 128 MB / 768 MB Kotlin daemon, and two workers. Its Kotlin daemon
also expires after 30 minutes of inactivity. `verifyMobilePolicy`
checks both bounded profiles, so an oversized heap or missing cache/fail-closed
setting cannot silently return to the scaffold.

## Android

1. Enable Developer options and USB debugging on the physical phone.
2. Connect it over USB, accept the RSA prompt, and confirm one non-emulator
   serial appears:

   ```bash
   adb devices -l
   ```

3. Build and run every instrumented app variant on the explicit physical serial:

   ```bash
   ./mobile/gradlew -p mobile \
     :apps:androidApp:connectedAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.deviceSerial=<physical-serial> \
     --no-configuration-cache --console=plain
   ```

   This runs the `pocDebug`, `productionDebug`, and `uiAcceptanceDebug` lanes.
   Keep their XML reports: the evidence emitter requires both the UI-acceptance
   and PoC result, so a successful UI-only run cannot close the PoC inference
   coverage gap.

4. For a manual acceptance launch, install and run against the explicit
   physical serial (replace the value; never omit `-s`):

   ```bash
   adb -s <physical-serial> install -r mobile/apps/androidApp/build/outputs/apk/uiAcceptance/debug/androidApp-uiAcceptance-debug.apk
   adb -s <physical-serial> shell am start \
     -n app.supernutrition.uiAcceptance/com.supernutrition.feature.photoanalysis.PhotoAnalysisComposeActivity
   ```

To emit a reproducible native-evidence bundle after the connected run, use an
explicit physical serial:

```bash
ANDROID_SERIAL=<physical-serial> \
  scripts/emit-physical-android-evidence.sh "$RUNNER_TEMP/supernutrition-android-evidence"
```

The emitter binds independent UI-acceptance and PoC APK/test-APK digests,
fixture and quality-policy digests, commit, device/API, Gradle version, UTC
timestamp, and their passing XML results. It refuses a dirty worktree by
default; `SUPERNUTRITION_ALLOW_DIRTY_EVIDENCE=1` produces `status=local-only`
diagnostics and can never satisfy activation.

If one handset appears twice in `adb devices -l` (for example, once as a
direct IP transport and once as an mDNS `_adb-tls-connect` alias), disconnect
the duplicate alias before the connected Gradle task. Two transports for one
package can produce `INSTALL_FAILED_DUPLICATE_PACKAGE` in the device's pending
install requests; this is a transport cleanup, not an application failure.
Some devices reconnect their wireless alias immediately. If that happens,
restart only the local ADB server with mDNS auto-connect disabled, confirm the
single USB serial again, and inherit the setting in the Gradle invocation:

```bash
ADB_MDNS_AUTO_CONNECT=0 adb kill-server
ADB_MDNS_AUTO_CONNECT=0 adb start-server
ADB_MDNS_AUTO_CONNECT=0 adb devices -l
ADB_MDNS_AUTO_CONNECT=0 ./mobile/gradlew -p mobile \
  :apps:androidApp:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial=<physical-serial> \
  --no-configuration-cache --console=plain
```

The Android acceptance host now launches the production Compose-owned
photo-analysis surface. The activity remains thin: it owns lifecycle, Android
permission, camera, picker, and settings intents while the presenter and UI
stay in shared KMP modules. The physical Honor lane covers camera/picker,
permission recovery, snapshot restoration, and binary fixture instrumentation.

Maestro 2.7.0 is the CI/emulator UI-flow owner. On the available Honor
wireless-debugging device, the OEM power manager freezes the temporary Maestro
driver process while it is backgrounded; do not turn that into a passing
Maestro claim. Physical instrumentation remains the authoritative device lane.

## iPhone

An attached iPhone must appear in:

```bash
xcrun xctrace list devices
```

The iOS device framework is built with the `iosArm64` target, then the Xcode
acceptance scheme is invoked with `-destination 'id=<UDID>'`. Configure a local
Apple account with access to the development team referenced by the Xcode
project. Signing certificates, private keys, and generated provisioning
profiles remain local machine inputs and must not be committed. The checked-in
Intel fallback is an executable x64 simulator compatibility lane for shared
presenter/reducer and bridge acceptance; it is never substituted for iPhone
camera, lifecycle, or hardware evidence.

### Current host limitation (2026-08-11)

The configured physical iPhone runs iOS `26.5.2`. Apple's
[Xcode system requirements](https://developer.apple.com/xcode/system-requirements/)
require macOS Tahoe `26.2` or later for Xcode `26.5`/`26.6`, the releases that
support iOS `26.5`; Xcode `26.3` can run on Sequoia but supports only through
iOS `26.2`. This evidence host is an Intel MacBookPro15,1 on macOS `15.7.7`,
which Apple does not list as compatible with Tahoe
[in its Tahoe compatibility list](https://support.apple.com/en-us/122867).
There is therefore no Apple-supported Xcode download that can execute the
physical iPhone lane on this machine.

Run the supported on-host iOS compile lane and record it separately from
physical evidence:

```bash
./mobile/gradlew -p mobile :apps:sharedKit:compileKotlinIosArm64 \
  -Psupernutrition.enableIosDeviceTarget=true \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

This compiles the `iosArm64` KMP graph only; it does not provide camera,
lifecycle, XCTest, performance, or G-3 benchmark evidence.

Run `pod install` in `mobile/apps/iosApp` before using the workspace. Its
post-install settings keep static CocoaPods libraries unsigned; the application
and test runner are signed by Xcode. A physical-device launch also requires the
iPhone to be paired, in Developer Mode, and unlocked.

Run both the binary-bound Kotlin fixture and the physical `iosArm64` Xcode
acceptance suite before emitting the native-evidence bundle. The emitter
requires the fixture XML as well as the physical xcresult, so a UI-only run
cannot accidentally close the normalization gap:

```bash
export APPLE_TEAM_ID="${APPLE_TEAM_ID:-REDACTED_TEAM_ID}"
export IOS_CODE_SIGN_IDENTITY="Apple Development: <name> (<certificate-id>)"
export IOS_UDID="<physical-iphone-udid>"
xcodebuild test -quiet \
  -workspace mobile/apps/iosApp/SuperNutritionUIAcceptance.xcworkspace \
  -scheme SuperNutritionUIAcceptance \
  -destination "id=$IOS_UDID" \
  -derivedDataPath mobile/apps/iosApp/build/DerivedData \
  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosArm64/debugFramework" \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  CODE_SIGN_STYLE=Automatic \
  CODE_SIGN_IDENTITY="Apple Development" \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration

scripts/run-physical-ios-fixtures.sh
```

With a wildcard profile, the runner uses its checked-in fixture bundle ID
`com.supernutrition.ios.arm64fixture.runner`. Set `IOS_FIXTURE_BUNDLE_ID` only
when the profile allows a different matching bundle ID.

Then emit the native-evidence bundle with the real iPhone UDID:

```bash
IOS_UDID=<physical-iphone-udid> \
  scripts/emit-physical-ios-evidence.sh "$RUNNER_TEMP/supernutrition-ios-evidence"
```

The fixture runner reuses the valid development profile embedded by the
physical Xcode acceptance build. If the profile is stored elsewhere, pass it
explicitly with `IOS_PROVISIONING_PROFILE="<path-to-mobileprovision>"`.

The runner builds the `iosArm64TestBinaries` output, executes its exact
Kotlin/Native binary inside a temporary signed app on the iPhone, and converts
the TeamCity stream into the `iosArm64Test` normalization XML expected by the
evidence schema. The emitter rejects simulator UDIDs, requires a passing
physical xcresult, passing binary-bound normalization XML, `Debug-iphoneos`
app, and `iosArm64` framework, and binds their paths and counts with the clean commit, device
line, Xcode/Gradle versions, fixture/policy digests, and UTC execution time.
Dirty output is explicitly `local-only` and cannot satisfy activation.

On this Intel host, select the matching architecture explicitly for that
compatibility lane:

```bash
./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosX64 \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64=true \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict --no-configuration-cache
xcodebuild clean test \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -scheme SuperNutritionUIAcceptance \
  -destination 'platform=iOS Simulator,id=<simulator-udid>' \
  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosX64/debugFramework" \
  CODE_SIGNING_ALLOWED=NO ARCHS=x86_64 ONLY_ACTIVE_ARCH=YES
```
