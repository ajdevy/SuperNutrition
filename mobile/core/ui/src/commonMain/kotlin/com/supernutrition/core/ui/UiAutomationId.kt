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
    val HOME_METRIC_COLORS = UiAutomationId("home_metric_colors")
    val HOME_METRIC_FATS = UiAutomationId("home_metric_fats")
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
    val PROFILE_NAME_INPUT = UiAutomationId("profile_name_input")
    val PROFILE_NAME_CLEAR = UiAutomationId("profile_name_clear")
    val PROFILE_NAME_SAVE = UiAutomationId("profile_name_save")
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
    val CAPTURE_TEXT = UiAutomationId("capture_text")
    val CAPTURE_TEXT_HINT = UiAutomationId("capture_text_hint")
    val MEAL_FORM_TITLE = UiAutomationId("meal_form_title")
    val MEAL_FORM_NAME = UiAutomationId("meal_form_name")
    val MEAL_FORM_PORTION = UiAutomationId("meal_form_portion")
    val MEAL_FORM_INGREDIENTS = UiAutomationId("meal_form_ingredients")
    val MEAL_FORM_NOTE = UiAutomationId("meal_form_note")
    val MEAL_REVIEW_SAVE_ERROR = UiAutomationId("meal_review_save_error")
    val MEAL_FORM_SAVE = UiAutomationId("meal_form_save")
    val MEAL_FORM_CANCEL = UiAutomationId("meal_form_cancel")
    val MEAL_FORM_ERROR = UiAutomationId("meal_form_error")
    val MEAL_DETAILS_SCREEN = UiAutomationId("meal_details_screen")
    val MEAL_DETAILS_TITLE = UiAutomationId("meal_details_title")
    val MEAL_DETAILS_BACK = UiAutomationId("meal_details_back")
    val MEAL_DETAILS_EDIT = UiAutomationId("meal_details_edit")
    val MEAL_DETAILS_PORTION = UiAutomationId("meal_details_portion")
    val MEAL_DETAILS_PER_100 = UiAutomationId("meal_details_per_100")
    val MEAL_DETAILS_INGREDIENTS = UiAutomationId("meal_details_ingredients")
    val MEAL_DETAILS_CARBS = UiAutomationId("meal_details_carbs")
    val MEAL_DETAILS_FATS = UiAutomationId("meal_details_fats")
    val MEAL_DETAILS_VITAMINS = UiAutomationId("meal_details_vitamins")
    val MEAL_DETAILS_MINERALS = UiAutomationId("meal_details_minerals")
    val MEAL_DETAILS_MORE = UiAutomationId("meal_details_more")
    val MEAL_DETAILS_NUTRITION_UNKNOWN = UiAutomationId("meal_details_nutrition_unknown")
    val MEAL_DETAILS_DELETE = UiAutomationId("meal_details_delete")
    val MEAL_DETAILS_DELETE_CONFIRM = UiAutomationId("meal_details_delete_confirm")
    val REWARD_DETAILS_TITLE = UiAutomationId("reward_details_title")
    val REWARD_DETAILS_BACK = UiAutomationId("reward_details_back")
    val REWARD_DETAILS_PROGRESS = UiAutomationId("reward_details_progress")
    val REWARD_DETAILS_HOW = UiAutomationId("reward_details_how")
    val REWARD_DETAILS_FOODS = UiAutomationId("reward_details_foods")

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
        HOME_METRIC_COLORS,
        HOME_METRIC_FATS,
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
        PROFILE_NAME_INPUT,
        PROFILE_NAME_CLEAR,
        PROFILE_NAME_SAVE,
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
        CAPTURE_TEXT,
        CAPTURE_TEXT_HINT,
        MEAL_FORM_TITLE,
        MEAL_FORM_NAME,
        MEAL_FORM_PORTION,
        MEAL_FORM_INGREDIENTS,
        MEAL_FORM_NOTE,
        MEAL_REVIEW_SAVE_ERROR,
        MEAL_FORM_SAVE,
        MEAL_FORM_CANCEL,
        MEAL_FORM_ERROR,
        MEAL_DETAILS_SCREEN,
        MEAL_DETAILS_TITLE,
        MEAL_DETAILS_BACK,
        MEAL_DETAILS_EDIT,
        MEAL_DETAILS_PORTION,
        MEAL_DETAILS_PER_100,
        MEAL_DETAILS_INGREDIENTS,
        MEAL_DETAILS_CARBS,
        MEAL_DETAILS_FATS,
        MEAL_DETAILS_VITAMINS,
        MEAL_DETAILS_MINERALS,
        MEAL_DETAILS_MORE,
        MEAL_DETAILS_NUTRITION_UNKNOWN,
        MEAL_DETAILS_DELETE,
        MEAL_DETAILS_DELETE_CONFIRM,
        REWARD_DETAILS_TITLE,
        REWARD_DETAILS_BACK,
        REWARD_DETAILS_PROGRESS,
        REWARD_DETAILS_HOW,
        REWARD_DETAILS_FOODS,
    )
}
