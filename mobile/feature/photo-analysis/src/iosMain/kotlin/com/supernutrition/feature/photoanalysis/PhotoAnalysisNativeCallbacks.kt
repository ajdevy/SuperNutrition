package com.supernutrition.feature.photoanalysis

/** Successful review completion never runs the host's cancellation cleanup. */
data class PhotoAnalysisNativeCallbacks(
    val onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
    val onCloseRequested: () -> Unit,
    val onMealReviewCompleted: () -> Unit,
)
