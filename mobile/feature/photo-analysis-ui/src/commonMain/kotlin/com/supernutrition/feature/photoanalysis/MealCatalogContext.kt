package com.supernutrition.feature.photoanalysis

/** The host owns source lifetime and retry; forms keep authoring state independently in memory. */
data class MealCatalogContext(
    val state: MealCatalogState = MealCatalogState.Unavailable,
    val onRetryRequested: () -> Unit = {},
    val onOpenLink: ((String) -> Unit)? = null,
    val copy: MealCatalogCopy = MealCatalogCopy(),
)
