package com.supernutrition.feature.photoanalysis

internal data class ProfileMealActions(
    val onEdited: (String, MealDraft) -> Boolean,
    val onDeleted: (String) -> Boolean,
)
