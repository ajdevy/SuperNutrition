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
    public val captureClose: String
        get() = UiAutomationIds.CAPTURE_CLOSE.value
    public val captureOpenSettings: String
        get() = UiAutomationIds.CAPTURE_OPEN_SETTINGS.value
    public val captureScanAgain: String
        get() = UiAutomationIds.CAPTURE_SCAN_AGAIN.value
    public val captureNative: String
        get() = UiAutomationIds.CAPTURE_NATIVE.value
    public val captureTab: String
        get() = UiAutomationIds.CAPTURE_TAB.value
    public val homeTab: String
        get() = UiAutomationIds.HOME_TAB.value
    public val rewardsTab: String
        get() = UiAutomationIds.REWARDS_TAB.value
    public val cameraAction: String
        get() = UiAutomationIds.CAMERA_ACTION.value
    public val homeTitle: String
        get() = UiAutomationIds.HOME_TITLE.value
    public val homeGreeting: String
        get() = UiAutomationIds.HOME_GREETING.value
    public val homeSummary: String
        get() = UiAutomationIds.HOME_SUMMARY.value
    public val homeEmptyTitle: String
        get() = UiAutomationIds.HOME_EMPTY_TITLE.value
    public val homeEmptyDescription: String
        get() = UiAutomationIds.HOME_EMPTY_DESCRIPTION.value
    public val homeAddFood: String
        get() = UiAutomationIds.HOME_ADD_FOOD.value
    public val rewardsTitle: String
        get() = UiAutomationIds.REWARDS_TITLE.value
    public val rewardsList: String
        get() = UiAutomationIds.REWARDS_LIST.value
    public val homeProfile: String
        get() = UiAutomationIds.HOME_PROFILE.value
    public val profileTitle: String
        get() = UiAutomationIds.PROFILE_TITLE.value
    public val profileClose: String
        get() = UiAutomationIds.PROFILE_CLOSE.value
}
