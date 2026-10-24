package com.supernutrition.feature.photoanalysis

/** Initial content and correction context; the mutable draft stays inside the form. */
internal data class MealFormConfig(
    val title: String,
    val initialDraft: MealDraft = MealDraft(),
    val editing: Boolean = false,
    val existingMeal: SavedMeal? = null,
    val catalog: MealCatalogContext = MealCatalogContext(),
)
