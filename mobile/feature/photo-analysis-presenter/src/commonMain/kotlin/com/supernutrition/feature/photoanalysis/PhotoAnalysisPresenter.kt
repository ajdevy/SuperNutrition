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
    val captureTitle: String,
    val takeMealPhoto: String,
    val choosePhoto: String,
    val openSettings: String,
    val scanAgain: String,
    val cancel: String,
    val readyToCapture: String,
    val preparingPhoto: String,
    val photoReady: String,
    val analyzingPhoto: String,
    val reviewCandidate: String,
    val restoringPhotoAnalysis: String,
    val cameraPermissionRequired: String,
    val cameraUnavailable: String,
    val photoCaptureFailed: String,
    val photoQualityTooLow: String,
    val noFoodFound: String,
    val analysisOffline: String,
    val analysisCancelled: String,
) {
    init {
        listOf(
            captureTitle,
            takeMealPhoto,
            choosePhoto,
            openSettings,
            scanAgain,
            cancel,
            readyToCapture,
            preparingPhoto,
            photoReady,
            analyzingPhoto,
            reviewCandidate,
            restoringPhotoAnalysis,
            cameraPermissionRequired,
            cameraUnavailable,
            photoCaptureFailed,
            photoQualityTooLow,
            noFoodFound,
            analysisOffline,
            analysisCancelled,
        ).forEach { require(it.isNotBlank()) }
    }

    companion object {
        fun english(): PhotoAnalysisPresenterCopy = PhotoAnalysisPresenterCopy(
            captureTitle = "Capture",
            takeMealPhoto = "Take meal photo",
            choosePhoto = "Choose photo",
            openSettings = "Open settings",
            scanAgain = "Scan again",
            cancel = "Cancel",
            readyToCapture = "Ready to capture",
            preparingPhoto = "Preparing photo",
            photoReady = "Photo ready",
            analyzingPhoto = "Analyzing photo",
            reviewCandidate = "Review candidate",
            restoringPhotoAnalysis = "Restoring photo analysis",
            cameraPermissionRequired = "Camera permission is required",
            cameraUnavailable = "Camera is unavailable",
            photoCaptureFailed = "Photo capture failed",
            photoQualityTooLow = "Photo quality is too low",
            noFoodFound = "No food was found in the photo",
            analysisOffline = "Analysis is offline",
            analysisCancelled = "Analysis cancelled",
        )
    }
}

data class PhotoAnalysisPresenterModel(
    val statusLabel: String,
    val state: PhotoAnalysisPresenterState,
    val actions: List<PhotoAnalysisPresenterAction>,
) {
    companion object {
        fun from(
            state: PhotoAnalysisState,
            copy: PhotoAnalysisPresenterCopy = PhotoAnalysisPresenterCopy.english(),
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
