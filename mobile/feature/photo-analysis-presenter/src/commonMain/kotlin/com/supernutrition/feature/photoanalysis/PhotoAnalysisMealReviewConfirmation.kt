package com.supernutrition.feature.photoanalysis

/** Explicit choices only, with opaque photo-evidence IDs kept apart from manually added foods. */
data class PhotoAnalysisMealReviewConfirmation(
    val dishCandidateId: String?,
    val dishLabel: String,
    val photoEvidenceComponentIds: List<String>,
    val addedIngredientLabels: List<String>,
    val note: String,
)
