package com.supernutrition.feature.photoanalysis

object MealDraftValidator {
    /** Normalizes explicit whitespace and decimal separators; invalid fields are never discarded. */
    fun validate(draft: MealDraft): MealDraftValidation = validateFields(
        draft = draft,
        ingredients = parseIngredients(draft.ingredients),
        ingredientLength = draft.ingredients.length.toLong(),
    )

    /** Untouched editor text retains structured labels, including commas that belong to one label. */
    fun validate(draft: MealDraft, existing: SavedMeal): MealDraftValidation =
        if (draft.ingredients == existing.toMealDraft().ingredients) {
            validateFields(
                draft = draft,
                ingredients = existing.ingredients,
                ingredientLength = structuredIngredientLength(existing.ingredients),
            )
        } else {
            validate(draft)
        }

    private fun parseIngredients(value: String): List<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty)

    private fun validateFields(
        draft: MealDraft,
        ingredients: List<String>,
        ingredientLength: Long,
    ): MealDraftValidation {
        val errors = linkedSetOf<MealDraftError>()
        val name = draft.name.trim()
        val portion = draft.portion.trim().replace(',', '.')
        val grams = portion.toDoubleOrNull()
        if (name.isEmpty()) errors += MealDraftError.NAME_REQUIRED
        if (draft.name.length > MealDraftLimits.NAME_MAX_LENGTH) errors += MealDraftError.NAME_TOO_LONG
        if (!isValidPortion(draft.portion, portion, grams)) errors += MealDraftError.PORTION_INVALID
        errors += mealIngredientErrors(ingredients, ingredientLength)
        if (draft.note.length > MealDraftLimits.NOTE_MAX_LENGTH) errors += MealDraftError.NOTE_TOO_LONG
        return if (errors.isEmpty()) {
            MealDraftValidation.Valid(ValidatedMealDraft(name, grams, ingredients, draft.note.trim()))
        } else {
            MealDraftValidation.Invalid(errors)
        }
    }

    private fun isValidPortion(raw: String, normalized: String, grams: Double?): Boolean =
        raw.length <= MealDraftLimits.PORTION_MAX_LENGTH &&
            (normalized.isEmpty() || (grams != null && grams.isFinite() && grams > 0.0))
}
