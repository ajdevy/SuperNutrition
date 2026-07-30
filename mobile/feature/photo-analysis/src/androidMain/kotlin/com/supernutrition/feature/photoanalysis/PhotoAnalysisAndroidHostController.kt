package com.supernutrition.feature.photoanalysis

import android.app.Activity
import android.content.Intent
import com.supernutrition.core.ui.CameraAvailability
import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.platform.photoandroid.AndroidPhotoBridgePort
import com.supernutrition.platform.photoandroid.AndroidPhotoNormalizerPort
import com.supernutrition.platform.photoandroid.AndroidPhotoCaptureFiles
import java.util.UUID

/** Typed Android effect coordinator kept outside the Activity and platform bridge. */
class PhotoAnalysisAndroidHostController(
    private val feature: PhotoAnalysisFeature,
    private val photoBridge: AndroidPhotoBridgePort,
    private val normalizer: AndroidPhotoNormalizerPort,
    private val requestCameraPermission: () -> Unit,
    private val showCameraPermissionRationale: (onContinue: () -> Unit) -> Unit,
    private val launchCamera: (String) -> Unit,
    private val launchImport: () -> Unit,
    private val launchSettings: () -> Unit,
    private val captureOperation: CaptureOperationState,
) {
    fun initialize() {
        photoBridge.cleanupStaleCaptureFiles()
    }

    fun handleAction(action: PhotoAnalysisPresenterAction) {
        feature.route(action, ::handleNativeAction)
    }

    fun onPermissionResult(granted: Boolean) {
        photoBridge.markCameraPermissionRequestCompleted()
        captureOperation.permissionRequestInFlight = false
        if (granted) {
            openCamera()
        } else {
            feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        }
    }

    fun onActivityResult(resultCode: Int, data: Intent?, isCamera: Boolean) {
        if (isCamera) {
            val expectedOperation = captureOperation.pendingOperationId
            if (expectedOperation == null) {
                feature.dispatch(
                    PhotoAnalysisEvent.PreparationFailed(
                        if (resultCode == Activity.RESULT_OK) {
                            RecoveryReason.CAPTURE_FAILED
                        } else {
                            RecoveryReason.CANCELLED
                        },
                    ),
                )
                return
            }
            val returnedOperation = data?.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_OPERATION_ID)
            captureOperation.pendingOperationId = null
            if (returnedOperation != expectedOperation) {
                feature.dispatch(
                    PhotoAnalysisEvent.PreparationFailed(
                        if (resultCode == Activity.RESULT_OK) RecoveryReason.CAPTURE_FAILED else RecoveryReason.CANCELLED,
                    ),
                )
                return
            }
        }
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

        val ref = feature.issuePhotoRef(UUID.randomUUID().toString())
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
        } catch (error: Exception) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
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
        when (photoBridge.cameraPermissionState()) {
            CameraPermissionState.GRANTED -> if (photoBridge.cameraAvailability() == CameraAvailability.UNAVAILABLE) {
                feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))
            } else {
                if (captureOperation.pendingOperationId == null) {
                    UUID.randomUUID().toString().also { operationId ->
                        captureOperation.pendingOperationId = operationId
                        launchCamera(operationId)
                    }
                }
            }

            CameraPermissionState.NOT_DETERMINED,
            CameraPermissionState.DENIED,
            -> requestPermission()

            CameraPermissionState.RATIONALE -> showCameraPermissionRationale(::requestPermission)
            CameraPermissionState.PERMANENTLY_DENIED,
            CameraPermissionState.RESTRICTED,
            -> feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        }
    }

    private fun requestPermission() {
        if (!captureOperation.permissionRequestInFlight) {
            captureOperation.permissionRequestInFlight = true
                photoBridge.markCameraPermissionRequestStarted()
                requestCameraPermission()
        }
    }
}

class CaptureOperationState {
    var pendingOperationId: String? = null
    var permissionRequestInFlight: Boolean = false
}
