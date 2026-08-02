package com.supernutrition.core.ui

internal val UI_AUTOMATION_ID_PATTERN = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

/**
 * Stable, locale-neutral automation identifiers shared by Compose and native
 * platform hosts. Labels remain localized; these values are an API contract.
 */
data class UiAutomationId(val value: String) {
    init {
        require(value.matches(UI_AUTOMATION_ID_PATTERN)) {
            "Automation IDs must use lowercase snake_case: $value"
        }
    }
}

/**
 * Stable locale-neutral identifier for one v2 photo-evidence component.
 *
 * Component IDs are opaque contract values and may contain punctuation or
 * uppercase characters, so encode every code point with a type prefix instead
 * of exposing the raw ID or relying on lossy sanitization.
 */
fun photoAnalysisVisibleComponentId(componentId: String): UiAutomationId {
    require(componentId.isNotBlank()) { "Visible component ID must not be blank" }
    val encoded = componentId.map { character ->
        val prefix = when {
            character in 'a'..'z' -> "l"
            character in '0'..'9' -> "d"
            character in 'A'..'Z' -> "u"
            else -> "x"
        }
        "$prefix${character.code.toString(16)}"
    }.joinToString("_")
    return UiAutomationId("photo_analysis_component_$encoded")
}

object UiAutomationIds {
    val BOOTSTRAP_LAUNCH_DI = UiAutomationId("bootstrap_shell_launch_di")
    val PHOTO_ANALYSIS_TITLE = UiAutomationId("photo_analysis_title")
    val PHOTO_ANALYSIS_STATUS = UiAutomationId("photo_analysis_status")
    val PHOTO_ANALYSIS_STAGE = UiAutomationId("photo_analysis_stage")
    val PHOTO_ANALYSIS_RETAINED_PHOTO = UiAutomationId("photo_analysis_retained_photo")
    val PHOTO_ANALYSIS_MAIN_DISH_PROPOSALS = UiAutomationId("photo_analysis_main_dish_proposals")
    val PHOTO_ANALYSIS_COMPONENT_EVIDENCE = UiAutomationId("photo_analysis_component_evidence")
    val CAPTURE_SHUTTER = UiAutomationId("capture_shutter")
    val CAPTURE_IMPORT = UiAutomationId("capture_import")
    val CAPTURE_SCAN_AGAIN = UiAutomationId("capture_scan_again")
    val CAPTURE_CANCEL = UiAutomationId("capture_cancel")
    val CAPTURE_OPEN_SETTINGS = UiAutomationId("capture_open_settings")
    val CAPTURE_NATIVE = UiAutomationId("capture_native")
    val MEAL_SEARCH_QUERY = UiAutomationId("meal_search_query")
    val MEAL_SEARCH_RESULTS = UiAutomationId("meal_search_results")
    val MEAL_SEARCH_NO_RESULTS = UiAutomationId("meal_search_no_results")

    /**
     * Per-row identifiers, by position rather than by canonical record id.
     *
     * A record id like `food:egg-boiled` cannot be an automation id — the colon
     * and hyphen fail [UI_AUTOMATION_ID_PATTERN] — and a description naming the
     * same food twice would produce two rows sharing one id anyway. Position is
     * what a test and a screen reader both actually address.
     *
     * These are generated rather than listed in [all], which is a registry of
     * fixed identifiers. They are still validated: a negative index produces a
     * value the pattern rejects, so it fails where it is built.
     */
    fun mealSearchRow(index: Int): UiAutomationId = UiAutomationId("meal_search_row_$index")

    fun mealSearchRowTitle(index: Int): UiAutomationId = UiAutomationId("meal_search_row_${index}_title")

    fun mealSearchRowDetail(index: Int): UiAutomationId = UiAutomationId("meal_search_row_${index}_detail")

    val all: List<UiAutomationId> = listOf(
        BOOTSTRAP_LAUNCH_DI,
        PHOTO_ANALYSIS_TITLE,
        PHOTO_ANALYSIS_STATUS,
        PHOTO_ANALYSIS_STAGE,
        PHOTO_ANALYSIS_RETAINED_PHOTO,
        PHOTO_ANALYSIS_MAIN_DISH_PROPOSALS,
        PHOTO_ANALYSIS_COMPONENT_EVIDENCE,
        CAPTURE_SHUTTER,
        CAPTURE_IMPORT,
        CAPTURE_SCAN_AGAIN,
        CAPTURE_CANCEL,
        CAPTURE_OPEN_SETTINGS,
        CAPTURE_NATIVE,
        MEAL_SEARCH_QUERY,
        MEAL_SEARCH_RESULTS,
        MEAL_SEARCH_NO_RESULTS,
    )
}
