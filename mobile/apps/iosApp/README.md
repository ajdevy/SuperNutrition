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
