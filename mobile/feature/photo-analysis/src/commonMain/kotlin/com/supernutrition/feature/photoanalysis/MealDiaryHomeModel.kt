package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

/** Both hosts project the same merged authority once for the count and daily nutrient totals. */
internal fun MealDiary.homeScreenModel(
    dateLabel: String,
    localDay: Int,
    confirmedFoods: List<ConfirmedConsumedFood>,
    savedMeals: List<SavedMeal>,
    catalog: MealCatalogState,
): HomeScreenModel {
    val history = mergedMealHistory(
        confirmedFoods.filter { it.localDay == localDay },
        savedMeals.filter { it.localDay == localDay },
        catalog.catalogOrNull,
    )
    return liveHomeScreenModel(
        dateLabel,
        history.size,
        storageLoadFailed = loadFailed,
        todayNutrients = todayMealNutrients(history, localDay),
    )
}
