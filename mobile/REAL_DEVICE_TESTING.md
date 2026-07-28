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

3. Build only the acceptance APKs:

   ```bash
   ./mobile/gradlew -p mobile \
     :apps:androidApp:assembleUiAcceptanceDebug \
     :apps:androidApp:assembleUiAcceptanceDebugAndroidTest \
   --no-daemon --no-configuration-cache
   ```

4. Install and run against the explicit physical serial (replace the value;
   never omit `-s`):

   ```bash
   adb -s <physical-serial> install -r mobile/apps/androidApp/build/outputs/apk/uiAcceptance/debug/androidApp-uiAcceptance-debug.apk
   adb -s <physical-serial> shell am start \
     -n com.supernutrition.android.uiAcceptance/com.supernutrition.feature.photoanalysis.PhotoAnalysisComposeActivity
   ./mobile/gradlew -p mobile \
   :apps:androidApp:connectedUiAcceptanceDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.deviceSerial=<physical-serial> \
    --dependency-verification=strict
   ```

To emit a reproducible native-evidence bundle after the connected run, use an
explicit physical serial:

```bash
ANDROID_SERIAL=<physical-serial> \
  scripts/emit-physical-android-evidence.sh "$RUNNER_TEMP/supernutrition-android-evidence"
```

The emitter binds the APK/test APK digest, fixture and quality-policy digests,
commit, device/API, Gradle version, UTC timestamp, and passing XML result. It
refuses a dirty worktree by default; `SUPERNUTRITION_ALLOW_DIRTY_EVIDENCE=1`
produces `status=local-only` diagnostics and can never satisfy activation.

If one handset appears twice in `adb devices -l` (for example, once as a
direct IP transport and once as an mDNS `_adb-tls-connect` alias), disconnect
the duplicate alias before the connected Gradle task. Two transports for one
package can produce `INSTALL_FAILED_DUPLICATE_PACKAGE` in the device's pending
install requests; this is a transport cleanup, not an application failure.

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
acceptance scheme is invoked with `-destination 'id=<UDID>'`. Signing/team
settings are local machine inputs and must not be committed. The checked-in
Intel fallback is an executable x64 simulator compatibility lane for shared
presenter/reducer and bridge acceptance; it is never substituted for iPhone
camera, lifecycle, or hardware evidence.

Run both the binary-bound Kotlin fixture and the physical `iosArm64` Xcode
acceptance suite before emitting the native-evidence bundle. The emitter
requires the fixture XML as well as the physical xcresult, so a UI-only run
cannot accidentally close the normalization gap:

```bash
./mobile/gradlew -p mobile :platform:photo-ios:iosArm64Test \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableIosDeviceTarget=true \
  --dependency-verification=strict --no-configuration-cache

xcodebuild test -quiet \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -scheme SuperNutritionUIAcceptance \
  -destination "id=<physical-iphone-udid>" \
  -derivedDataPath mobile/apps/iosApp/build/DerivedData \
  SHARED_KIT_FRAMEWORK_DIR="$PWD/mobile/apps/sharedKit/build/bin/iosArm64/debugFramework" \
  DEVELOPMENT_TEAM="<local-team-id>" \
  CODE_SIGN_STYLE=Automatic \
  CODE_SIGN_IDENTITY="Apple Development" \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration
```

Then emit the native-evidence bundle with the real iPhone UDID:

```bash
IOS_UDID=<physical-iphone-udid> \
  scripts/emit-physical-ios-evidence.sh "$RUNNER_TEMP/supernutrition-ios-evidence"
```

The emitter rejects simulator UDIDs, requires a passing physical xcresult,
passing `iosArm64Test` normalization XML, `Debug-iphoneos` app, and `iosArm64`
framework, and binds their paths and counts with the clean commit, device
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
