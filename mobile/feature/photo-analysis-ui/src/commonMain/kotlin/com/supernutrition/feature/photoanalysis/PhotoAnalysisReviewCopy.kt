package com.supernutrition.feature.photoanalysis

internal fun MealReviewCopy.pantryLabel(sticker: FoodSticker): String =
    pantryIngredientLabels[sticker.id] ?: sticker.label

internal fun PhotoAnalysisVisibleComponentModel.reviewDescription(): String = listOfNotNull(
    label,
    sourceLabel,
    confidenceLabel,
    ambiguityLabel,
    visibilityLabel,
    regionLabel,
    mass?.label,
    provenanceLabel,
).joinToString()
