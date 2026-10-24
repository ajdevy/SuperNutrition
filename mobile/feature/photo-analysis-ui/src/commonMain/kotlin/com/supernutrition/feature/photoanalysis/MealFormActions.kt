package com.supernutrition.feature.photoanalysis

internal data class MealFormActions(val onSave: (MealDraft) -> Boolean, val onCancel: () -> Unit)
