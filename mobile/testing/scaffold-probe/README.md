# Scaffold probe

This is a non-production, test-only KMP probe for the bootstrap profile. It
exercises the common compilation boundary for the pinned Koin, Decompose,
Ktor/Ktorfit, SQLDelight, serialization, and coroutine substrate. It does not
own production APIs, camera behavior, inference, nutrition, remote transfer,
credentials, or product UI.

On Intel macOS, Apple targets for this test-support owner are enabled with
`-Psupernutrition.enableTestingAppleTargets=true`; `iosX64` additionally requires
`-Psupernutrition.enableIntelIosX64=true`. The installed Xcode 26.0.1 rejects
`iosX64Test` simulator execution, so this is compile-only on the current host,
not canonical evidence and not a replacement for `iosSimulatorArm64Test` or a
physical `iosArm64` device.
