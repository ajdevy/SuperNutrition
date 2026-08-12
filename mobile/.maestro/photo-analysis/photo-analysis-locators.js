// Locator dictionary only. Actions and assertions stay in the shared flow.
output.photo_analysis_title = "photo_analysis_title";
output.capture_shutter = "capture_shutter";
output.capture_import = "capture_import";
// P2-3 — the shared consent flow used the Android system picker's own
// `icon_thumb` id inline while the quality profile declares iOS coverage, so
// the "shared" flow could only ever pass on Android. Naming it here keeps the
// flow platform-neutral and puts the per-platform value in the dictionary that
// exists for exactly that.
output.system_picker_first_item = "icon_thumb";

