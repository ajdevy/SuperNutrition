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

fun photoAnalysisGridCellId(cellIndex: Int): UiAutomationId {
    require(cellIndex in 0..8) { "Grid cell index must be between 0 and 8" }
    return UiAutomationId("photo_analysis_grid_cell_${cellIndex + 1}")
}

object UiAutomationIds {
    val BOOTSTRAP_LAUNCH_DI = UiAutomationId("bootstrap_shell_launch_di")
    val PHOTO_ANALYSIS_TITLE = UiAutomationId("photo_analysis_title")
    val PHOTO_ANALYSIS_STATUS = UiAutomationId("photo_analysis_status")
    val PHOTO_ANALYSIS_STAGE = UiAutomationId("photo_analysis_stage")
    val PHOTO_ANALYSIS_RETAINED_PHOTO = UiAutomationId("photo_analysis_retained_photo")
    val PHOTO_ANALYSIS_GRID_OVERLAY = UiAutomationId("photo_analysis_grid_overlay")
    val PHOTO_ANALYSIS_LOADING = UiAutomationId("photo_analysis_loading")
    val PHOTO_ANALYSIS_MAIN_DISH_PROPOSALS = UiAutomationId("photo_analysis_main_dish_proposals")
    val PHOTO_ANALYSIS_COMPONENT_EVIDENCE = UiAutomationId("photo_analysis_component_evidence")
    val PHOTO_ANALYSIS_COMPOSITION_HINTS = UiAutomationId("photo_analysis_composition_hints")
    val PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT = UiAutomationId("photo_analysis_manual_ingredient_input")
    val PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD = UiAutomationId("photo_analysis_manual_ingredient_add")
    val CAPTURE_SHUTTER = UiAutomationId("capture_shutter")
    val CAPTURE_IMPORT = UiAutomationId("capture_import")
    val CAPTURE_SCAN_AGAIN = UiAutomationId("capture_scan_again")
    val CAPTURE_CANCEL = UiAutomationId("capture_cancel")
    val CAPTURE_CLOSE = UiAutomationId("capture_close")
    val CAPTURE_OPEN_SETTINGS = UiAutomationId("capture_open_settings")
    val CAPTURE_NATIVE = UiAutomationId("capture_native")
    val MEAL_SEARCH_QUERY = UiAutomationId("meal_search_query")
    val MEAL_SEARCH_RESULTS = UiAutomationId("meal_search_results")
    val MEAL_SEARCH_NO_RESULTS = UiAutomationId("meal_search_no_results")
    val CAPTURE_TAB = UiAutomationId("capture_tab")
    /** Compatibility alias: the first tab is now the Home surface. */
    val HOME_TAB = CAPTURE_TAB
    val REWARDS_TAB = UiAutomationId("rewards_tab")
    val CAMERA_ACTION = UiAutomationId("camera_action")
    val HOME_TITLE = UiAutomationId("home_title")
    val HOME_GREETING = UiAutomationId("home_greeting")
    val HOME_SUMMARY = UiAutomationId("home_summary")
    val HOME_EMPTY_TITLE = UiAutomationId("home_empty_title")
    val HOME_EMPTY_DESCRIPTION = UiAutomationId("home_empty_description")
    val HOME_ADD_FOOD = UiAutomationId("home_add_food")
    val REWARDS_TITLE = UiAutomationId("rewards_title")
    val REWARDS_LIST = UiAutomationId("rewards_list")
    val PROFILE_TITLE = UiAutomationId("profile_title")
    val PROFILE_ID = UiAutomationId("profile_id")
    val PROFILE_CLOSE = UiAutomationId("profile_close")

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

    fun rewardsRow(index: Int): UiAutomationId = UiAutomationId("rewards_row_$index")

    fun rewardsRowTitle(index: Int): UiAutomationId = UiAutomationId("rewards_row_${index}_title")

    fun rewardsRowProgress(index: Int): UiAutomationId = UiAutomationId("rewards_row_${index}_progress")

    fun rewardsRowState(index: Int): UiAutomationId = UiAutomationId("rewards_row_${index}_state")

    val all: List<UiAutomationId> = listOf(
        BOOTSTRAP_LAUNCH_DI,
        PHOTO_ANALYSIS_TITLE,
        PHOTO_ANALYSIS_STATUS,
        PHOTO_ANALYSIS_STAGE,
        PHOTO_ANALYSIS_RETAINED_PHOTO,
        PHOTO_ANALYSIS_GRID_OVERLAY,
        PHOTO_ANALYSIS_LOADING,
        PHOTO_ANALYSIS_MAIN_DISH_PROPOSALS,
        PHOTO_ANALYSIS_COMPONENT_EVIDENCE,
        PHOTO_ANALYSIS_COMPOSITION_HINTS,
        PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT,
        PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD,
        CAPTURE_SHUTTER,
        CAPTURE_IMPORT,
        CAPTURE_SCAN_AGAIN,
        CAPTURE_CANCEL,
        CAPTURE_CLOSE,
        CAPTURE_OPEN_SETTINGS,
        CAPTURE_NATIVE,
        MEAL_SEARCH_QUERY,
        MEAL_SEARCH_RESULTS,
        MEAL_SEARCH_NO_RESULTS,
        CAPTURE_TAB,
        REWARDS_TAB,
        CAMERA_ACTION,
        HOME_TITLE,
        HOME_GREETING,
        HOME_SUMMARY,
        HOME_EMPTY_TITLE,
        HOME_EMPTY_DESCRIPTION,
        HOME_ADD_FOOD,
        REWARDS_TITLE,
        REWARDS_LIST,
        PROFILE_TITLE,
        PROFILE_ID,
        PROFILE_CLOSE,
    )
}
