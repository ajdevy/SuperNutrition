package com.supernutrition.feature.photoanalysis

/** An explicit identity-only diary entry; no photo reference or nutrient claim is retained. */
data class SavedMeal(
    val id: String,
    /** Local calendar date expressed as days since 1970-01-01, supplied by the host clock. */
    val localDay: Int,
    val timeLabel: String,
    val name: String,
    val portionGrams: Double?,
    val ingredients: List<String>,
    val note: String = "",
    val source: SavedMealSource,
)

enum class SavedMealSource { PHOTO, TEXT }

/** Editable user input, never an inferred catalog identity or nutrient profile. */
data class MealDraft(
    val name: String = "",
    val portion: String = "",
    val ingredients: String = "",
    val note: String = "",
)

data class ValidatedMealDraft(
    val name: String,
    val portionGrams: Double?,
    val ingredients: List<String>,
    val note: String,
)

object MealDraftLimits {
    const val NAME_MAX_LENGTH = 160
    const val PORTION_MAX_LENGTH = 64
    const val INGREDIENTS_MAX_LENGTH = 6000
    const val INGREDIENTS_MAX_COUNT = 50
    const val INGREDIENT_MAX_LENGTH = 120
    const val NOTE_MAX_LENGTH = 2000
}

enum class MealDraftError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    PORTION_INVALID,
    INGREDIENTS_TOO_LONG,
    TOO_MANY_INGREDIENTS,
    INGREDIENT_TOO_LONG,
    NOTE_TOO_LONG,
    INGREDIENT_REQUIRED,
}

sealed interface MealDraftValidation {
    data class Valid(val value: ValidatedMealDraft) : MealDraftValidation
    data class Invalid(val errors: Set<MealDraftError>) : MealDraftValidation
}
