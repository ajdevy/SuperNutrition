# androidApp

This is the thin Android host for the Compose-owned, local-model-backed
photo-analysis surface. `BootstrapApplication` is the sole Android Koin and
photo-analysis runtime bootstrap owner. All Android variants use the same
local model runtime and assets that powered the original PoC screen. The
canonical application ID is `com.supernutrition.android`; the
`uiAcceptance` product flavor is a non-distributable acceptance variant with
the unique application ID `com.supernutrition.android.uiAcceptance`. The
Compose root exposes the stable `bootstrap_shell_launch_di` substrate marker
alongside the photo-analysis semantics. Its device smoke is
`uiAcceptanceDebugAndroidTest`.

No store/archive/publish path or product feature is defined here. The Android
instrumentation smoke must be run against a connected physical device for
device evidence; an emulator run is auxiliary only.
