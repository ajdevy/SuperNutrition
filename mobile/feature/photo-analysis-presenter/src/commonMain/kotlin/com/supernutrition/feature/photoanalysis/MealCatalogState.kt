package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog

/** Session-owned availability contains no authoring draft and never selects a source implicitly. */
sealed interface MealCatalogState {
    data object Loading : MealCatalogState

    data class Ready(val catalog: ApprovedNutritionCatalog) : MealCatalogState

    data object Unavailable : MealCatalogState
}

val MealCatalogState.catalogOrNull: ApprovedNutritionCatalog?
    get() = (this as? MealCatalogState.Ready)?.catalog
