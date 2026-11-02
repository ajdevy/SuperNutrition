# iosApp

This is the thin Xcode host for the photo-analysis acceptance profile. The
canonical Apple targets use the Compose-owned `PhotoAnalysisViewController`
from `SharedKit`; native camera, picker, and permission ownership lives in
`platform/photo-ios`, while Swift routes callbacks and opens settings. The
acceptance bundle identifier is
`com.supernutrition.ios.uiacceptance` and the launch marker is
`bootstrap_shell_launch_di`.

## Framework target selection

Both the production and acceptance Xcode framework phases run before Swift
compilation/linking and call `build-sharedkit-framework.sh` on every build.
Gradle tracks transitive Kotlin/build inputs; Xcode does not skip the phase
based solely on the helper timestamp. It accepts `Debug` or `Release`, normalizes the
target to its registered Gradle task suffix, and selects Intel support only
for an explicit `iosX64` request:

| `KOTLIN_TARGET` | Gradle task suffix | `enableIntelIosX64` |
| --- | --- | --- |
| `iosArm64` / `IosArm64` | `IosArm64` | `false` |
| `iosSimulatorArm64` / `IosSimulatorArm64` | `IosSimulatorArm64` | `false` |
| `iosX64` / `IosX64` | `IosX64` | `true` |

Missing or unknown targets/configurations fail before Gradle runs. The helper
preserves the existing Apple/framework flags, uses strict dependency
verification with the configuration cache disabled, and propagates the
Gradle exit status. It inherits `DEVELOPER_DIR` from the calling process; it
does not change the global Xcode selection or the existing host target policy.
In particular, an Intel host cannot build `iosSimulatorArm64` through this
helper.

For a device framework from the repository root, use:

```bash
PROJECT_DIR="$PWD/mobile/apps/iosApp" \
KOTLIN_TARGET=iosArm64 CONFIGURATION=Debug \
  /bin/sh mobile/apps/iosApp/build-sharedkit-framework.sh
```

The existing framework output is
`mobile/apps/sharedKit/build/bin/iosArm64/debugFramework/SharedKit.framework`.
Framework compilation, Xcode host packaging, and device execution are separate
verification steps.

## Compose resources in Xcode bundles

Both host phases pass `--xcode-resources` to the same build helper. SharedKit
applies the pinned Compose plugin so `syncComposeResourcesForIos` aggregates
resources from its transitive dependencies and installs them under the app's
`compose-resources` directory. The static framework alone does not contain
those host resources.

This mode requires Xcode's `BUILT_PRODUCTS_DIR`,
`UNLOCALIZED_RESOURCES_FOLDER_PATH`, `PLATFORM_NAME` and `ARCHS`; missing values
fail before Gradle starts. It supports arm64 devices and arm64 simulators.
The pinned plugin cannot synchronize iosX64 resources and reports that
limitation explicitly. Calling the helper without this option retains the
standalone framework-only command above.

## Intel simulator limitation

The pinned Compose **1.11.1 has no `iosX64` variants**. The older compatibility
code has a SwiftUI renderer driven by `SharedKitPhotoAnalysisSession`, but
`SharedKit` still links the Compose feature modules. Consequently that
compatibility command currently fails during dependency resolution and cannot
build or run an Intel simulator app. Installing Xcode or selecting the SwiftUI
renderer does not remove this dependency.

The historical host-slice command below first fails during configuration
because the slice omits `:domain:nutrition`. A full-registry retry separately
exposed missing strict-verification metadata; only the recorded temporary
checksum enrollment reached the unsupported Compose variant. Those enrollments
were reverted. Stop at the first failure; the subsequent Xcode command requires
a successfully built framework and is currently blocked:

```text
./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosX64 \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64=true \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict --no-configuration-cache

xcodebuild test -quiet \
  -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -scheme SuperNutritionUIAcceptance \
  -destination 'platform=iOS Simulator,id=<simulator-udid>' \
  -derivedDataPath mobile/apps/iosApp/build/DerivedData \
  ARCHS=x86_64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO
```

The temporary Intel lane was authorized by
`docs/decisions/2026-07-27-intel-ios-x64-acceptance-lane.md`; that historical
decision does not establish compatibility with the current dependency tuple.
See `docs/ios-simulator-claude-runbook.md` for the retained commands and their
current limitation. For a physical iPhone, build `iosArm64` and point Xcode's
framework search path at that device output. Collect device evidence on the
connected iPhone.

## Physical iPhone acceptance run

Before launching a physical run, pair the iPhone in Xcode, enable Developer
Mode, keep it unlocked, and trust the developer certificate in
`Settings > General > VPN & Device Management` if iOS reports that the
developer app is untrusted. Confirm that the device is listed outside the
simulator section:

```text
xcrun xctrace list devices
```

### Apple account and provisioning

The checked-in Xcode targets reference a development team. Configure a local
Apple account with access to that team before signing:

```text
DEVELOPMENT_TEAM: configured in Xcode project build settings
```

`DEVELOPMENT_TEAM` is set for the production, acceptance-host, and acceptance-
test configurations in `SuperNutritionUIAcceptance.xcodeproj`. The signing
certificate, private key, and generated provisioning profiles remain local
machine inputs and must never be committed. Run CocoaPods from the iOS host
directory before opening the workspace so its static-library targets remain
unsigned and only the application is signed:

```bash
cd mobile/apps/iosApp
pod install
cd ../../..
```

Use `APPLE_TEAM_ID` only when intentionally testing with a different local
team; set the team ID to one you own before signing.

Run the physical acceptance lane with the paired device unlocked:

```text
export IOS_UDID="<physical-udid>"
export APPLE_TEAM_ID="${APPLE_TEAM_ID:?Set APPLE_TEAM_ID to your development team}"
export IOS_CODE_SIGN_IDENTITY="Apple Development: <name> (<certificate-id>)"

./mobile/gradlew -p mobile :apps:sharedKit:linkDebugFrameworkIosArm64 \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict --no-configuration-cache

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

The fixture runner must use a valid development provisioning profile for the
same acceptance bundle ID. After the physical Xcode test, it discovers the
profile from `IOS_APP_PATH/embedded.mobileprovision`; otherwise pass an
explicit `IOS_PROVISIONING_PROFILE` path. A signing certificate and matching
entitlements alone are not installable on a real iPhone.
