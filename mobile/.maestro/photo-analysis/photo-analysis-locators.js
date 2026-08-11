// Locator dictionary only. Actions and assertions stay in the shared flow.
output.photo_analysis_title = "photo_analysis_title";
output.capture_shutter = "capture_shutter";
output.capture_import = "capture_import";
output.consent_surface = "consent_surface";
output.consent_market = "consent_market";
output.consent_processing_region = "consent_processing_region";
output.consent_retention = "consent_retention";
output.consent_copy_version = "consent_copy_version";
output.consent_locale = "consent_locale";
output.consent_grant = "consent_grant";
output.consent_decline = "consent_decline";
output.consent_unavailable = "consent_unavailable";
output.photo_analysis_main_dish_proposals = "photo_analysis_main_dish_proposals";

// P2 — the picker item is platform-specific, and this flow is shared.
//
// The flow hardcoded id: "icon_thumb", which is the Android system photo
// picker's thumbnail. The quality profile declares this flow covers iOS too,
// where that id does not exist — so the shared flow could only ever have run on
// Android, and the iOS coverage it claimed was never real.
//
// Kept as a locator rather than inlined so the platform-specific value has one
// home, and so the iOS value can be filled in by whoever runs the simulator lane
// rather than guessed at here.
output.system_picker_first_item = "icon_thumb";
