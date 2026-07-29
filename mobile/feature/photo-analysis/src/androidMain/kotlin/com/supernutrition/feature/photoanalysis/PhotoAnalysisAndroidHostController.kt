package com.supernutrition.feature.photoanalysis

import android.app.Activity
import android.content.Intent
import com.supernutrition.core.ui.CameraAvailability
import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge

/** Typed Android effect coordinator kept outside the Activity and platform bridge. */
class PhotoAnalysisAndroidHostController(
    private val feature: PhotoAnalysisFeature,
    private val photoBridge: AndroidPhotoBridge,
    private val normalizer: AndroidImageNormalizer,
    private val requestCameraPermission: () -> Unit,
    private val launchCamera: () -> Unit,
    private val launchImport: () -> Unit,
    private val launchSettings: () -> Unit,
) {
    private var captureSequence = 0

    fun initialize() {
        photoBridge.cleanupStaleCaptureFiles()
    }

    fun handleAction(action: PhotoAnalysisPresenterAction) {
        feature.route(action, ::handleNativeAction)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            openCamera()
        } else {
            feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        }
    }

    fun onActivityResult(resultCode: Int, data: Intent?, isCamera: Boolean) {
        if (resultCode != Activity.RESULT_OK) {
            feature.dispatch(
                PhotoAnalysisEvent.PreparationFailed(
                    if (isCamera && photoBridge.isCaptureFailure(data)) {
                        RecoveryReason.CAPTURE_FAILED
                    } else {
                        RecoveryReason.CANCELLED
                    },
                ),
            )
            return
        }

        val ref = feature.issuePhotoRef("android-capture-${++captureSequence}")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        val sourceBytes = photoBridge.photoBytesFromResult(data)
        if (sourceBytes == null) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
            return
        }

        try {
            feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalizer.normalize(ref, sourceBytes)))
        } catch (error: PhotoPreparationException) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
        }
    }

    private fun handleNativeAction(action: PhotoAnalysisNativeAction) {
        when (action) {
            PhotoAnalysisNativeAction.TakePhoto -> openCamera()
            PhotoAnalysisNativeAction.ImportPhoto -> launchImport()
            PhotoAnalysisNativeAction.OpenSettings -> launchSettings()
        }
    }

    private fun openCamera() {
        if (photoBridge.cameraPermissionState() != CameraPermissionState.GRANTED) {
            requestCameraPermission()
        } else if (photoBridge.cameraAvailability() == CameraAvailability.UNAVAILABLE) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))
        } else {
            launchCamera()
        }
    }
}
