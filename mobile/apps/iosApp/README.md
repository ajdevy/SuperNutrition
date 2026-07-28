# iosApp

This is the thin Xcode host for the photo-analysis acceptance profile. The
canonical Apple targets use the Compose-owned `PhotoAnalysisViewController`
from `SharedKit`; native camera, picker, and permission ownership lives in
`platform/photo-ios`, while Swift routes callbacks and opens settings. The
acceptance bundle identifier is
`com.supernutrition.ios.uiacceptance` and the launch marker is
`bootstrap_shell_launch_di`.

On the Intel compatibility lane, the same `SharedKitPhotoAnalysisSession`
drives a SwiftUI renderer because the pinned Compose release has no `iosX64`
publication. This is an executable local acceptance fallback, not canonical
Apple Compose or physical-device evidence.

On this Intel Mac, prepare the local x86 simulator framework with the
host-slice command:

```text
./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosX64 \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64=true \
  -Psupernutrition.enableSharedKitFramework=true

xcodebuild test -quiet \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -scheme SuperNutritionUIAcceptance \
  -destination 'platform=iOS Simulator,id=<simulator-udid>' \
  -derivedDataPath mobile/apps/iosApp/build/DerivedData \
  ARCHS=x86_64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO
```

That x64 output is the temporary executable simulator lane authorized by
`docs/decisions/2026-07-27-intel-ios-x64-acceptance-lane.md`. It does not prove
physical camera or hardware behavior. For a physical iPhone, build the
`iosArm64` framework with
`-Psupernutrition.enableIosDeviceTarget=true` and point Xcode's framework
search path at that device output. Device evidence must be collected on the
connected iPhone; no simulator result is accepted as device evidence.

## Physical iPhone acceptance run

Before launching a physical run, pair the iPhone in Xcode, enable Developer
Mode, keep it unlocked, and trust the developer certificate in
`Settings > General > VPN & Device Management` if iOS reports that the
developer app is untrusted. Confirm that the device is listed outside the
simulator section:

```text
xcrun xctrace list devices
```

The local signing team is intentionally not committed to this project. Set
`APPLE_TEAM_ID` to a team available in the local Xcode account, then run:

```text
export IOS_UDID="<physical-udid>"
export APPLE_TEAM_ID="<local-team-id>"

./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosArm64 \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict --no-configuration-cache

xcodebuild test -quiet \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
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

Kotlin 2.4.10 exposes the physical target as `iosArm64TestBinaries`, not as an
executable Gradle `iosArm64Test` task. The checked-in runner packages that
exact arm64 Kotlin/Native test binary into a temporary signed `.app`, installs
it with `devicectl`, runs it on the iPhone, and writes the JUnit XML consumed
by the evidence emitter. The runner executes the binary-bound iOS
normalization fixtures; the Xcode run covers the packaged host, native
camera/picker, lifecycle, and accessibility surface. After both complete
successfully, emit the
physical-device evidence record:

```text
IOS_UDID="<physical-udid>" \
IOS_DERIVED_DATA_ROOT="$PWD/mobile/apps/iosApp/build/DerivedData" \
  scripts/emit-physical-ios-evidence.sh "/tmp/supernutrition-ios-evidence"
```
