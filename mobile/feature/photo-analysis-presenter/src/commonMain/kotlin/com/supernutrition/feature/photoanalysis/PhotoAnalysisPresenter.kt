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
data class PhotoAnalysisPresenterCopy(
    val capture: CaptureCopy,
    val status: StatusCopy,
    val recovery: RecoveryCopy,
) {
    val captureTitle: String get() = capture.title
    val takeMealPhoto: String get() = capture.takeMealPhoto
    val choosePhoto: String get() = capture.choosePhoto
    val openSettings: String get() = capture.openSettings
    val scanAgain: String get() = recovery.scanAgain
    val cancel: String get() = capture.cancel
    val readyToCapture: String get() = status.readyToCapture
    val preparingPhoto: String get() = status.preparingPhoto
    val photoReady: String get() = status.photoReady
    val analyzingPhoto: String get() = status.analyzingPhoto
    val reviewCandidate: String get() = status.reviewCandidate
    val restoringPhotoAnalysis: String get() = status.restoringPhotoAnalysis
    val cameraPermissionRequired: String get() = recovery.cameraPermissionRequired
    val cameraUnavailable: String get() = recovery.cameraUnavailable
    val photoCaptureFailed: String get() = recovery.photoCaptureFailed
    val photoQualityTooLow: String get() = recovery.photoQualityTooLow
    val noFoodFound: String get() = recovery.noFoodFound
    val analysisOffline: String get() = recovery.analysisOffline
    val analysisCancelled: String get() = recovery.analysisCancelled

    init {
        listOf(capture.title, capture.takeMealPhoto, capture.choosePhoto, capture.openSettings, capture.cancel)
            .forEach { require(it.isNotBlank()) }
        listOf(
            status.readyToCapture,
            status.preparingPhoto,
            status.photoReady,
            status.analyzingPhoto,
            status.reviewCandidate,
            status.restoringPhotoAnalysis,
        ).forEach { require(it.isNotBlank()) }
        listOf(
            recovery.scanAgain,
            recovery.cameraPermissionRequired,
            recovery.cameraUnavailable,
            recovery.photoCaptureFailed,
            recovery.photoQualityTooLow,
            recovery.noFoodFound,
            recovery.analysisOffline,
            recovery.analysisCancelled,
        ).forEach { require(it.isNotBlank()) }
    }
}

data class CaptureCopy(
    val title: String,
    val takeMealPhoto: String,
    val choosePhoto: String,
    val openSettings: String,
    val cancel: String,
)

data class StatusCopy(
    val readyToCapture: String,
    val preparingPhoto: String,
    val photoReady: String,
    val analyzingPhoto: String,
    val reviewCandidate: String,
    val restoringPhotoAnalysis: String,
)

data class RecoveryCopy(
    val scanAgain: String,
    val cameraPermissionRequired: String,
    val cameraUnavailable: String,
    val photoCaptureFailed: String,
    val photoQualityTooLow: String,
    val noFoodFound: String,
    val analysisOffline: String,
    val analysisCancelled: String,
)

data class PhotoAnalysisPresenterModel(
    val statusLabel: String,
    val state: PhotoAnalysisPresenterState,
    val actions: List<PhotoAnalysisPresenterAction>,
) {
    companion object {
        fun from(
            state: PhotoAnalysisState,
            copy: PhotoAnalysisPresenterCopy,
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
                statusLabel = statusLabel(state, copy),
                state = presenterState,
                actions = actions(state),
            )
        }

        private fun statusLabel(state: PhotoAnalysisState, copy: PhotoAnalysisPresenterCopy): String = when (state) {
            PhotoAnalysisState.Capture -> copy.readyToCapture
            is PhotoAnalysisState.Preparing -> copy.preparingPhoto
            is PhotoAnalysisState.Ready -> copy.photoReady
            is PhotoAnalysisState.Analyzing -> copy.analyzingPhoto
            is PhotoAnalysisState.CandidateReview -> copy.reviewCandidate
            is PhotoAnalysisState.Recovery -> recoveryLabel(state.reason, copy)
            is PhotoAnalysisState.Backgrounded -> copy.restoringPhotoAnalysis
        }

        private fun recoveryLabel(reason: RecoveryReason, copy: PhotoAnalysisPresenterCopy): String = when (reason) {
            RecoveryReason.PERMISSION_DENIED -> copy.cameraPermissionRequired
            RecoveryReason.CAMERA_UNAVAILABLE -> copy.cameraUnavailable
            RecoveryReason.CAPTURE_FAILED -> copy.photoCaptureFailed
            RecoveryReason.POOR_IMAGE -> copy.photoQualityTooLow
            RecoveryReason.NO_FOOD -> copy.noFoodFound
            RecoveryReason.OFFLINE -> copy.analysisOffline
            RecoveryReason.CANCELLED -> copy.analysisCancelled
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
