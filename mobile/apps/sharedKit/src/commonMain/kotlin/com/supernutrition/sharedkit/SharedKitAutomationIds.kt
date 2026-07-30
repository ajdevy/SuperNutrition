package com.supernutrition.sharedkit

import com.supernutrition.core.ui.UiAutomationIds

/** Cross-language projection of the protected core/ui automation registry. */
public object SharedKitAutomationIds {
    public val bootstrapLaunchMarker: String
        get() = UiAutomationIds.BOOTSTRAP_LAUNCH_DI.value
    public val photoAnalysisTitle: String
        get() = UiAutomationIds.PHOTO_ANALYSIS_TITLE.value
    public val photoAnalysisStatus: String
        get() = UiAutomationIds.PHOTO_ANALYSIS_STATUS.value
    public val captureShutter: String
        get() = UiAutomationIds.CAPTURE_SHUTTER.value
    public val captureImport: String
        get() = UiAutomationIds.CAPTURE_IMPORT.value
    public val captureOpenSettings: String
        get() = UiAutomationIds.CAPTURE_OPEN_SETTINGS.value
    public val captureNative: String
        get() = UiAutomationIds.CAPTURE_NATIVE.value
}
