package com.supernutrition.core.ui

/**
 * Stable, locale-neutral automation identifiers shared by Compose and native
 * platform hosts. Labels remain localized; these values are an API contract.
 */
data class UiAutomationId(val value: String) {
    init {
        require(value.matches(IDENTIFIER_PATTERN)) {
            "Automation IDs must use lowercase snake_case: $value"
        }
    }

    private companion object {
        val IDENTIFIER_PATTERN = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
    }
}

object UiAutomationIds {
    val BOOTSTRAP_LAUNCH_DI = UiAutomationId("bootstrap_shell_launch_di")
    val PHOTO_ANALYSIS_TITLE = UiAutomationId("photo_analysis_title")
    val PHOTO_ANALYSIS_STATUS = UiAutomationId("photo_analysis_status")
    val CAPTURE_SHUTTER = UiAutomationId("capture_shutter")
    val CAPTURE_IMPORT = UiAutomationId("capture_import")
    val CAPTURE_SCAN_AGAIN = UiAutomationId("capture_scan_again")
    val CAPTURE_CANCEL = UiAutomationId("capture_cancel")
    val CAPTURE_OPEN_SETTINGS = UiAutomationId("capture_open_settings")
    val CAPTURE_PERMISSION_RATIONALE = UiAutomationId("capture_permission_rationale")

    val all: List<UiAutomationId> = listOf(
        BOOTSTRAP_LAUNCH_DI,
        PHOTO_ANALYSIS_TITLE,
        PHOTO_ANALYSIS_STATUS,
        CAPTURE_SHUTTER,
        CAPTURE_IMPORT,
        CAPTURE_SCAN_AGAIN,
        CAPTURE_CANCEL,
        CAPTURE_OPEN_SETTINGS,
        CAPTURE_PERMISSION_RATIONALE,
    )
}
