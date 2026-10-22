package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Immutable

@Immutable
data class MealHistoryCopy(
    val title: String = "Meal history",
    val back: String = "Back to Profile",
    val search: String = "Search your meals",
    val clearSearch: String = "Clear",
    val emptyTitle: String = "No meals saved yet",
    val emptyDescription: String = "No confirmed meals are available in this session.",
    val noResultsTitle: String = "No meals found",
    val noResultsDescription: String = "Try another food or ingredient name.",
    val timeUnavailable: String = "Time unavailable",
    val photoUnavailable: String = "Photo unavailable",
    val confirmedFood: String = "Confirmed food",
    val confirmedFoods: String = "Confirmed foods",
    val today: String = "Today",
    val yesterday: String = "Yesterday",
    val dateUnavailable: String = "Date unavailable",
    val done: String = "Done",
)
