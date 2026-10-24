package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.CatalogMealNutritionUnknownReason

internal fun mealCatalogBindingError(
    draft: ValidatedMealDraft,
    existing: SavedMeal?,
    catalog: ApprovedNutritionCatalog?,
): MealDraftError? {
    val binding = draft.catalogBinding ?: return null
    if (!isBoundedCatalogBinding(binding) || draft.ingredients.isNotEmpty()) {
        return MealDraftError.CATALOG_SELECTION_INVALID
    }
    val grams = draft.portionGrams
    if (grams == null || !grams.isFinite() || grams <= 0.0) {
        return MealDraftError.PORTION_INVALID
    }
    val resolution = resolveCatalogMealIdentity(draft.name, draft.ingredients, binding, grams, catalog)
    val reason = when (resolution) {
        is CatalogMealNutritionResolution.Grounded -> return null
        is CatalogMealNutritionResolution.Unknown -> resolution.reason
    }
    if (reason == CatalogMealNutritionUnknownReason.INVALID_SERVING) {
        return MealDraftError.PORTION_INVALID
    }
    // An unchanged historical identity remains editable even when its original package cannot be resolved.
    if (binding == existing?.catalogBinding) return null
    return if (reason == CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE) {
        MealDraftError.CATALOG_UNAVAILABLE
    } else {
        MealDraftError.CATALOG_SELECTION_INVALID
    }
}

internal fun MealDraft.withInvalidatedHistoricalBinding(existing: SavedMeal?): MealDraft {
    if (existing?.catalogBinding == null || catalogBinding != existing.catalogBinding) return this
    return if (name != existing.name || ingredients != existing.toMealDraft().ingredients) {
        copy(catalogBinding = null)
    } else {
        this
    }
}
