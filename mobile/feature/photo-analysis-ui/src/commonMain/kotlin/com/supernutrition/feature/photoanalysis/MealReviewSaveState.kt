package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Submission feedback is session-owned; a rejected save never changes review choices or text. */
@Stable
internal class MealReviewSaveState {
    var failed by mutableStateOf(false)
        private set

    fun submit(confirmation: PhotoAnalysisMealReviewConfirmation, surface: PhotoAnalysisCaptureSurface) {
        val save = surface.onMealReviewSaveRequested
        failed = if (save != null) {
            !save(confirmation)
        } else {
            surface.onMealReviewConfirmed(confirmation)
            false
        }
    }
}
