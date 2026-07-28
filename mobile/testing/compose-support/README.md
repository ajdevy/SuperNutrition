# Compose acceptance support

This module owns only the shared v2 acceptance substrate: typed step and
semantic IDs, bounded polling, redacted failure bundles, and screen-scope
interfaces. Concrete product robots remain colocated with their feature.

Bootstrap and photo-analysis acceptance executables are separate,
non-distributable consumers. This module never supplies product UI, fake
engines, provider fixtures, credentials, or release artifacts.

The production capture surface owns the Compose UI Test v2 contract in
`feature/photo-analysis-ui/src/commonTest`. Its JVM host test uses the
platform-selected, test-only Skiko runtime so the same semantic IDs can be
rendered on the local Intel macOS host without adding Skiko to production
artifacts. The physical Android semantic smoke is owned by
`apps/androidApp/src/androidTest`; canonical Apple execution remains owned by
the ARM64 simulator/device lanes.

On Intel macOS, Apple targets for this test-support owner are enabled with
`-Psupernutrition.enableTestingAppleTargets=true`. `iosX64` additionally requires
`-Psupernutrition.enableIntelIosX64=true`. The installed Xcode 26.0.1 rejects
`iosX64Test` simulator execution, so it is compile-only and never lifecycle
evidence. The canonical Apple lane remains `iosSimulatorArm64Test`, with
physical-device evidence on `iosArm64`.
