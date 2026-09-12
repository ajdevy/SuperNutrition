# androidApp

This is the thin Android host for the Compose-owned, local-model-backed
photo-analysis surface. `BootstrapApplication` is the sole Android Koin and
photo-analysis runtime bootstrap owner. All Android variants use the same
local model runtime and assets that powered the original PoC screen. The
canonical application ID is `app.supernutrition`; the
`uiAcceptance` product flavor is a non-distributable acceptance variant with
the unique application ID `app.supernutrition.uiAcceptance`. The
Compose root exposes the stable `bootstrap_shell_launch_di` substrate marker
alongside the photo-analysis semantics. Its device smoke is
`uiAcceptanceDebugAndroidTest`.

The Android host accepts profile App Links in the form
`https://supernutrition.app/profile/{profileId}`. Profile IDs currently use
letters, numbers, `_`, and `-` (up to 128 characters). The app shows a simple
profile destination while the final profile design and data contract are being
developed. For Android App Links verification, the website must publish
`https://supernutrition.app/.well-known/assetlinks.json` with the production
package `app.supernutrition` and the SHA-256 fingerprint of the release
signing certificate. The app can still receive an explicit profile intent
before domain verification is configured.

No store/archive/publish path or product feature is defined here. The Android
instrumentation smoke must be run against a connected physical device for
device evidence; an emulator run is auxiliary only.
