# sharedKit

`sharedKit` owns the single public iOS composition/bootstrap API. The thin
Swift host calls `SharedKitBootstrap.start()` once; repeated calls are safe and
do not create a second Koin application.

On Intel, Apple targets are enabled with
`-Psupernutrition.enableTestingAppleTargets=true` together with
`-Psupernutrition.enableIntelIosX64=true` for the temporary Intel lane. Each
enabled Apple target exports
a static `SharedKit` framework when the additional Gradle property
`-Psupernutrition.enableSharedKitFramework=true` is supplied. On Intel,
physical-device `iosArm64` is enabled with
`-Psupernutrition.enableIosDeviceTarget=true`; it is automatic on ARM64. The
`iosX64` compatibility target also requires its explicit flag. Canonical Apple
evidence remains `iosSimulatorArm64` plus physical `iosArm64` device evidence.

Because the current Intel AGP/Kotlin Native toolchain cannot share its build
service classloaders, the local framework handoff uses the explicit owner slice
`-Psupernutrition.hostSlice=sharedKit`. This is not aggregate quality evidence;
CI/ARM64 runs the full graph.
