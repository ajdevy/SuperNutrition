# androidApp

This is the thin Android host for the production Compose-owned
photo-analysis surface. `BootstrapApplication` is the sole Android Koin
bootstrap owner. The `production` flavor uses the canonical application ID
`com.supernutrition.android`; the `uiAcceptance` product flavor is a
non-distributable acceptance variant with the unique application ID
`com.supernutrition.android.uiAcceptance`. The Compose root exposes the stable
`bootstrap_shell_launch_di` substrate marker alongside the photo-analysis
semantics. Its device smoke is `uiAcceptanceDebugAndroidTest`.

No store/archive/publish path or product feature is defined here. The Android
instrumentation smoke must be run against a connected physical device for
device evidence; an emulator run is auxiliary only.
