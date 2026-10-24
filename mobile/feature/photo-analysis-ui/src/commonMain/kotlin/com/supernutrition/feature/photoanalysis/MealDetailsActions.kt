package com.supernutrition.feature.photoanalysis

data class MealDetailsActions(
    val onBackRequested: () -> Unit,
    val onEditRequested: (() -> Unit)? = null,
    val onDeleteRequested: (() -> Boolean)? = null,
)
