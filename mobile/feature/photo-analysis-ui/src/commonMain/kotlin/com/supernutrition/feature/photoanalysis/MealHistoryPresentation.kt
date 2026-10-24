package com.supernutrition.feature.photoanalysis

data class MealHistoryPresentation(
    val referenceDay: Int? = null,
    val copy: MealHistoryCopy = MealHistoryCopy(),
    val detailsCopy: MealDetailsCopy = MealDetailsCopy(),
    val searchState: MealHistorySearchState? = null,
    val storageLoadFailed: Boolean = false,
)
