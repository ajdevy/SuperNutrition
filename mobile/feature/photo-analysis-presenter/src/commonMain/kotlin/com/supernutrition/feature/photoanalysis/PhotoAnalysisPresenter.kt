package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RecoveryReason

/** Stable state vocabulary exposed to platform presenters. */
enum class PhotoAnalysisPresenterState {
    CAPTURE,
    PREPARING,
    READY,
    ANALYZING,
    CANDIDATE_REVIEW,
    RECOVERY,
    BACKGROUNDED,
}

/** UI intents stay explicit; the presenter never invents remote consent. */
enum class PhotoAnalysisPresenterAction {
    TakePhoto,
    ImportPhoto,
    OpenSettings,
    Cancel,
    ScanAgain,
}

data class PhotoAnalysisPresenterModel(
    val title: String,
    val statusLabel: String,
    val shutterLabel: String,
    val importLabel: String,
    val shutterAutomationId: String,
    val importAutomationId: String,
    val state: PhotoAnalysisPresenterState,
    val actions: List<PhotoAnalysisPresenterAction>,
) {
    companion object {
        fun from(
            state: PhotoAnalysisState,
            surface: CaptureSurfaceUi = CaptureSurfaceUi(),
        ): PhotoAnalysisPresenterModel {
            val presenterState = when (state) {
                PhotoAnalysisState.Capture -> PhotoAnalysisPresenterState.CAPTURE
                is PhotoAnalysisState.Preparing -> PhotoAnalysisPresenterState.PREPARING
                is PhotoAnalysisState.Ready -> PhotoAnalysisPresenterState.READY
                is PhotoAnalysisState.Analyzing -> PhotoAnalysisPresenterState.ANALYZING
                is PhotoAnalysisState.CandidateReview -> PhotoAnalysisPresenterState.CANDIDATE_REVIEW
                is PhotoAnalysisState.Recovery -> PhotoAnalysisPresenterState.RECOVERY
                is PhotoAnalysisState.Backgrounded -> PhotoAnalysisPresenterState.BACKGROUNDED
            }
            return PhotoAnalysisPresenterModel(
                title = surface.title,
                statusLabel = statusLabel(state),
                shutterLabel = surface.shutterLabel,
                importLabel = surface.importLabel,
                shutterAutomationId = surface.shutterAutomationId,
                importAutomationId = surface.importAutomationId,
                state = presenterState,
                actions = actions(state),
            )
        }

        private fun statusLabel(state: PhotoAnalysisState): String = when (state) {
            PhotoAnalysisState.Capture -> "Ready to capture"
            is PhotoAnalysisState.Preparing -> "Preparing photo"
            is PhotoAnalysisState.Ready -> "Photo ready"
            is PhotoAnalysisState.Analyzing -> "Analyzing photo"
            is PhotoAnalysisState.CandidateReview -> "Review candidate"
            is PhotoAnalysisState.Recovery -> recoveryLabel(state.reason)
            is PhotoAnalysisState.Backgrounded -> "Restoring photo analysis"
        }

        private fun recoveryLabel(reason: RecoveryReason): String = when (reason) {
            RecoveryReason.PERMISSION_DENIED -> "Camera permission is required"
            RecoveryReason.CAMERA_UNAVAILABLE -> "Camera is unavailable"
            RecoveryReason.CAPTURE_FAILED -> "Photo capture failed"
            RecoveryReason.POOR_IMAGE -> "Photo quality is too low"
            RecoveryReason.NO_FOOD -> "No food was found in the photo"
            RecoveryReason.OFFLINE -> "Analysis is offline"
            RecoveryReason.CANCELLED -> "Analysis cancelled"
        }

        private fun actions(state: PhotoAnalysisState): List<PhotoAnalysisPresenterAction> = when (state) {
            PhotoAnalysisState.Capture -> listOf(
                PhotoAnalysisPresenterAction.TakePhoto,
                PhotoAnalysisPresenterAction.ImportPhoto,
            )

            is PhotoAnalysisState.Recovery -> when (state.reason) {
                RecoveryReason.PERMISSION_DENIED -> listOf(
                    PhotoAnalysisPresenterAction.OpenSettings,
                    PhotoAnalysisPresenterAction.ImportPhoto,
                )

                else -> listOf(PhotoAnalysisPresenterAction.ScanAgain, PhotoAnalysisPresenterAction.ImportPhoto)
            }

            is PhotoAnalysisState.Backgrounded -> emptyList()

            is PhotoAnalysisState.Preparing,
            is PhotoAnalysisState.Ready,
            is PhotoAnalysisState.Analyzing,
            is PhotoAnalysisState.CandidateReview,
            -> listOf(PhotoAnalysisPresenterAction.Cancel)
        }
    }
}
