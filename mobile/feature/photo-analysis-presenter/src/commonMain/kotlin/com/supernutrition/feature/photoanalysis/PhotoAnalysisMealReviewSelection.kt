package com.supernutrition.feature.photoanalysis

/** Session-scoped UI choices; evidence IDs stay separate from manually chosen palette foods. */
data class PhotoAnalysisMealReviewSelection(
    val dishId: String? = null,
    val customMeal: Boolean = false,
    val componentIds: Set<String> = emptySet(),
    val pantryIds: Set<String> = emptySet(),
) {
    /** Reanalysis invalidates only photo evidence; explicit manual choices remain session-scoped. */
    fun withoutPhotoEvidence() = copy(dishId = null, componentIds = emptySet())
}
