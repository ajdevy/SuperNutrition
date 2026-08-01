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

/** Localizable copy is supplied by the product host; the presenter owns no locale lookup. */
data class PhotoAnalysisPresenterCopy(val capture: CaptureCopy, val status: StatusCopy, val recovery: RecoveryCopy) {
    init {
        (capture.values + status.values + recovery.values).forEach { require(it.isNotBlank()) }
    }
}

data class CaptureCopy(
    val title: String,
    val takeMealPhoto: String,
    val choosePhoto: String,
    val openSettings: String,
    val cancel: String,
) {
    val values: List<String>
        get() = listOf(title, takeMealPhoto, choosePhoto, openSettings, cancel)
}

data class StatusCopy(
    val readyToCapture: String,
    val preparingPhoto: String,
    val photoReady: String,
    val analyzingPhoto: String,
    val reviewCandidate: String,
    val restoringPhotoAnalysis: String,
) {
    val values: List<String>
        get() = listOf(
            readyToCapture,
            preparingPhoto,
            photoReady,
            analyzingPhoto,
            reviewCandidate,
            restoringPhotoAnalysis,
        )
}

data class RecoveryCopy(
    val scanAgain: String,
    val cameraPermissionRequired: String,
    val cameraUnavailable: String,
    val photoCaptureFailed: String,
    val photoQualityTooLow: String,
    val noFoodFound: String,
    val analysisOffline: String,
    val analysisCancelled: String,
) {
    val values: List<String>
        get() = listOf(
            scanAgain,
            cameraPermissionRequired,
            cameraUnavailable,
            photoCaptureFailed,
            photoQualityTooLow,
            noFoodFound,
            analysisOffline,
            analysisCancelled,
        )
}

data class PhotoAnalysisPresenterModel(
    val statusLabel: String,
    val state: PhotoAnalysisPresenterState,
    val actions: List<PhotoAnalysisPresenterAction>,
) {
    companion object {
        fun from(state: PhotoAnalysisState, copy: PhotoAnalysisPresenterCopy): PhotoAnalysisPresenterModel {
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
                statusLabel = statusLabel(state, copy),
                state = presenterState,
                actions = actions(state),
            )
        }

        private fun statusLabel(state: PhotoAnalysisState, copy: PhotoAnalysisPresenterCopy): String = when (state) {
            PhotoAnalysisState.Capture -> copy.status.readyToCapture
            is PhotoAnalysisState.Preparing -> copy.status.preparingPhoto
            is PhotoAnalysisState.Ready -> copy.status.photoReady
            is PhotoAnalysisState.Analyzing -> copy.status.analyzingPhoto
            is PhotoAnalysisState.CandidateReview -> copy.status.reviewCandidate
            is PhotoAnalysisState.Recovery -> recoveryLabel(state.reason, copy)
            is PhotoAnalysisState.Backgrounded -> copy.status.restoringPhotoAnalysis
        }

        private fun recoveryLabel(reason: RecoveryReason, copy: PhotoAnalysisPresenterCopy): String = when (reason) {
            RecoveryReason.PERMISSION_DENIED -> copy.recovery.cameraPermissionRequired
            RecoveryReason.CAMERA_UNAVAILABLE -> copy.recovery.cameraUnavailable
            RecoveryReason.CAPTURE_FAILED -> copy.recovery.photoCaptureFailed
            RecoveryReason.POOR_IMAGE -> copy.recovery.photoQualityTooLow
            RecoveryReason.NO_FOOD -> copy.recovery.noFoodFound
            RecoveryReason.OFFLINE -> copy.recovery.analysisOffline
            RecoveryReason.CANCELLED -> copy.recovery.analysisCancelled
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
