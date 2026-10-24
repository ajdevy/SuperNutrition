package com.supernutrition.feature.photoanalysis

/** The stored list's content budget excludes spacing inserted only for display. */
internal fun structuredIngredientLength(ingredients: List<String>): Long =
    ingredients.sumOf { it.length.toLong() } + (ingredients.size - 1).coerceAtLeast(0)

internal fun mealIngredientErrors(ingredients: List<String>, textLength: Long): Set<MealDraftError> {
    val errors = linkedSetOf<MealDraftError>()
    if (textLength > MealDraftLimits.INGREDIENTS_MAX_LENGTH) errors += MealDraftError.INGREDIENTS_TOO_LONG
    if (ingredients.size > MealDraftLimits.INGREDIENTS_MAX_COUNT) errors += MealDraftError.TOO_MANY_INGREDIENTS
    if (ingredients.any(String::isBlank)) errors += MealDraftError.INGREDIENT_REQUIRED
    if (ingredients.any { it.length > MealDraftLimits.INGREDIENT_MAX_LENGTH }) {
        errors += MealDraftError.INGREDIENT_TOO_LONG
    }
    return errors
}
