package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog

object MealDraftValidator {
    /** Normalizes explicit whitespace and decimal separators; invalid fields are never discarded. */
    fun validate(draft: MealDraft): MealDraftValidation = validate(draft, null, null)

    fun validate(draft: MealDraft, catalog: ApprovedNutritionCatalog?): MealDraftValidation =
        validate(draft, null, catalog)

    /** Untouched editor text retains structured labels, including commas that belong to one label. */
    fun validate(draft: MealDraft, existing: SavedMeal): MealDraftValidation = validate(draft, existing, null)

    fun validate(draft: MealDraft, existing: SavedMeal?, catalog: ApprovedNutritionCatalog?): MealDraftValidation {
        val normalized = draft.withInvalidatedHistoricalBinding(existing)
        val unchanged = existing?.takeIf { normalized.ingredients == it.toMealDraft().ingredients }
        val ingredients = if (unchanged != null) unchanged.ingredients else parseIngredients(normalized.ingredients)
        val length = if (unchanged != null) {
            structuredIngredientLength(ingredients)
        } else {
            normalized.ingredients.length.toLong()
        }
        return validateFields(normalized, ingredients, length).withCatalogValidation(existing, catalog)
    }

    /** Revalidates internal writes without parsing structured ingredient labels as editor text. */
    fun validate(
        draft: ValidatedMealDraft,
        existing: SavedMeal?,
        catalog: ApprovedNutritionCatalog?,
    ): MealDraftValidation {
        val normalized = MealDraft(
            name = draft.name,
            portion = if (draft.portionGrams == null) "" else draft.portionGrams.toString(),
            ingredients = draft.ingredients.joinToString(", "),
            note = draft.note,
            catalogBinding = draft.catalogBinding,
        ).withInvalidatedHistoricalBinding(existing)
        return validateFields(normalized, draft.ingredients, structuredIngredientLength(draft.ingredients))
            .withCatalogValidation(existing, catalog)
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
            MealDraftValidation.Valid(
                ValidatedMealDraft(name, grams, ingredients, draft.note.trim(), draft.catalogBinding),
            )
        } else {
            MealDraftValidation.Invalid(errors)
        }
    }

    private fun isValidPortion(raw: String, normalized: String, grams: Double?): Boolean =
        raw.length <= MealDraftLimits.PORTION_MAX_LENGTH &&
            (normalized.isEmpty() || (grams != null && grams.isFinite() && grams > 0.0))
}

private fun MealDraftValidation.withCatalogValidation(
    existing: SavedMeal?,
    catalog: ApprovedNutritionCatalog?,
): MealDraftValidation {
    if (this !is MealDraftValidation.Valid) return this
    val error = mealCatalogBindingError(value, existing, catalog)
    return if (error == null) {
        this
    } else {
        MealDraftValidation.Invalid(setOf(error))
    }
}
