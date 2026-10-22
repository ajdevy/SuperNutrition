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
    val PHOTO_ANALYSIS_CUSTOM_MEAL = UiAutomationId("photo_analysis_custom_meal")
    val PHOTO_ANALYSIS_ADD_TO_MEAL = UiAutomationId("photo_analysis_add_to_meal")
    val CAPTURE_SHUTTER = UiAutomationId("capture_shutter")
    val CAPTURE_IMPORT = UiAutomationId("capture_import")
    val CAPTURE_SCAN_AGAIN = UiAutomationId("capture_scan_again")
    val CAPTURE_CANCEL = UiAutomationId("capture_cancel")
    val CAPTURE_CLOSE = UiAutomationId("capture_close")
    val CAPTURE_OPEN_SETTINGS = UiAutomationId("capture_open_settings")
    val CAPTURE_ALLOW_CAMERA = UiAutomationId("capture_allow_camera")
    val CAPTURE_NATIVE = UiAutomationId("capture_native")
    val MEAL_SEARCH_QUERY = UiAutomationId("meal_search_query")
    val MEAL_SEARCH_RESULTS = UiAutomationId("meal_search_results")
    val MEAL_SEARCH_NO_RESULTS = UiAutomationId("meal_search_no_results")
    val CAPTURE_TAB = UiAutomationId("capture_tab")

    /** Compatibility alias: the first tab is now the Home surface. */
    val HOME_TAB = CAPTURE_TAB
    val REWARDS_TAB = UiAutomationId("rewards_tab")
    val CAMERA_ACTION = UiAutomationId("camera_action")
    val BOTTOM_BAR_PILL = UiAutomationId("bottom_bar_pill")
    val HOME_TITLE = UiAutomationId("home_title")
    val HOME_HEADER = UiAutomationId("home_header")
    val HOME_CARE_PROMPT = UiAutomationId("home_care_prompt")
    val HOME_GREETING = UiAutomationId("home_greeting")
    val HOME_SUMMARY = UiAutomationId("home_summary")
    val HOME_NEXT_STEP = UiAutomationId("home_next_step")
    val HOME_METRIC_FIBER = UiAutomationId("home_metric_fiber")
    val HOME_METRIC_WATER = UiAutomationId("home_metric_water")
    val HOME_METRIC_PROTEIN = UiAutomationId("home_metric_protein")
    val HOME_METRIC_MOVEMENT = UiAutomationId("home_metric_movement")
    val HOME_EMPTY_TITLE = UiAutomationId("home_empty_title")
    val HOME_EMPTY_DESCRIPTION = UiAutomationId("home_empty_description")
    val HOME_ADD_FOOD = UiAutomationId("home_add_food")
    val HOME_SCREEN = UiAutomationId("home_screen")
    val HOME_DATE = UiAutomationId("home_date")
    val HOME_PROFILE = UiAutomationId("home_profile")
    val HOME_VIEW_GOALS = UiAutomationId("home_view_goals")
    val REWARDS_SCREEN = UiAutomationId("rewards_screen")
    val REWARDS_TITLE = UiAutomationId("rewards_title")
    val REWARDS_BACK = UiAutomationId("rewards_back")
    val REWARDS_LEVEL_HERO = UiAutomationId("rewards_level_hero")
    val REWARDS_EARNED_SECTION = UiAutomationId("rewards_earned_section")
    val REWARDS_EARNED_LINK = UiAutomationId("rewards_earned_link")
    val REWARDS_IN_PROGRESS_SECTION = UiAutomationId("rewards_in_progress_section")
    val REWARDS_LIST = UiAutomationId("rewards_list")
    val PROFILE_TITLE = UiAutomationId("profile_title")
    val PROFILE_ID = UiAutomationId("profile_id")
    val PROFILE_CLOSE = UiAutomationId("profile_close")
    val PROFILE_NAME = UiAutomationId("profile_name")
    val PROFILE_EDIT_NAME = UiAutomationId("profile_edit_name")
    val PROFILE_HISTORY = UiAutomationId("profile_history")
    val PROFILE_PRIVACY = UiAutomationId("profile_privacy")
    val PROFILE_TERMS = UiAutomationId("profile_terms")
    val PROFILE_CRASH_REPORTS = UiAutomationId("profile_crash_reports")
    val MEAL_HISTORY_SCREEN = UiAutomationId("meal_history_screen")
    val MEAL_HISTORY_TITLE = UiAutomationId("meal_history_title")
    val MEAL_HISTORY_CLOSE = UiAutomationId("meal_history_close")
    val MEAL_HISTORY_SEARCH = UiAutomationId("meal_history_search")
    val MEAL_HISTORY_LIST = UiAutomationId("meal_history_list")
    val MEAL_HISTORY_EMPTY = UiAutomationId("meal_history_empty")
    val MEAL_HISTORY_NO_RESULTS = UiAutomationId("meal_history_no_results")
    val MEAL_HISTORY_SUMMARY = UiAutomationId("meal_history_summary")

    fun mealHistoryRow(index: Int): UiAutomationId = UiAutomationId("meal_history_row_$index")

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

    fun photoAnalysisDishChoice(rank: Int): UiAutomationId = UiAutomationId("photo_analysis_dish_choice_$rank")

    fun photoAnalysisPantryChoice(id: String): UiAutomationId = UiAutomationId("photo_analysis_pantry_$id")

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
        PHOTO_ANALYSIS_CUSTOM_MEAL,
        PHOTO_ANALYSIS_ADD_TO_MEAL,
        CAPTURE_SHUTTER,
        CAPTURE_IMPORT,
        CAPTURE_SCAN_AGAIN,
        CAPTURE_CANCEL,
        CAPTURE_CLOSE,
        CAPTURE_OPEN_SETTINGS,
        CAPTURE_ALLOW_CAMERA,
        CAPTURE_NATIVE,
        MEAL_SEARCH_QUERY,
        MEAL_SEARCH_RESULTS,
        MEAL_SEARCH_NO_RESULTS,
        CAPTURE_TAB,
        REWARDS_TAB,
        CAMERA_ACTION,
        BOTTOM_BAR_PILL,
        HOME_TITLE,
        HOME_HEADER,
        HOME_CARE_PROMPT,
        HOME_GREETING,
        HOME_SUMMARY,
        HOME_NEXT_STEP,
        HOME_METRIC_FIBER,
        HOME_METRIC_WATER,
        HOME_METRIC_PROTEIN,
        HOME_METRIC_MOVEMENT,
        HOME_EMPTY_TITLE,
        HOME_EMPTY_DESCRIPTION,
        HOME_ADD_FOOD,
        HOME_SCREEN,
        HOME_DATE,
        HOME_PROFILE,
        HOME_VIEW_GOALS,
        REWARDS_SCREEN,
        REWARDS_TITLE,
        REWARDS_BACK,
        REWARDS_LEVEL_HERO,
        REWARDS_EARNED_SECTION,
        REWARDS_EARNED_LINK,
        REWARDS_IN_PROGRESS_SECTION,
        REWARDS_LIST,
        PROFILE_TITLE,
        PROFILE_ID,
        PROFILE_CLOSE,
        PROFILE_NAME,
        PROFILE_EDIT_NAME,
        PROFILE_HISTORY,
        PROFILE_PRIVACY,
        PROFILE_TERMS,
        PROFILE_CRASH_REPORTS,
        MEAL_HISTORY_SCREEN,
        MEAL_HISTORY_TITLE,
        MEAL_HISTORY_CLOSE,
        MEAL_HISTORY_SEARCH,
        MEAL_HISTORY_LIST,
        MEAL_HISTORY_EMPTY,
        MEAL_HISTORY_NO_RESULTS,
        MEAL_HISTORY_SUMMARY,
    )
}
