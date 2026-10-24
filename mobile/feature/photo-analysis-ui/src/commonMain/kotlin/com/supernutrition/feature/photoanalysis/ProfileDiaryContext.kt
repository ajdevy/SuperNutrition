package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

/** The owning session supplies diary evidence and explicit correction operations together. */
data class ProfileDiaryContext(
    val confirmedFoods: List<ConfirmedConsumedFood> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val onMealEdited: (String, MealDraft) -> Boolean = { _, _ -> false },
    val onMealDeleted: (String) -> Boolean = { false },
    val copy: MealDiaryCopy = MealDiaryCopy(),
)
