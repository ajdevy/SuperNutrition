package com.supernutrition.feature.photoanalysis

import android.app.Activity
import android.content.Intent
import com.supernutrition.core.ui.CameraAvailability
import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.platform.photoandroid.AndroidPhotoBridgePort
import com.supernutrition.platform.photoandroid.AndroidPhotoCaptureFiles
import com.supernutrition.platform.photoandroid.AndroidPhotoNormalizerPort
import java.util.UUID

private val ACTIONS_RESETTING_ANALYSIS = setOf(
    PhotoAnalysisPresenterAction.Cancel,
    PhotoAnalysisPresenterAction.ScanAgain,
    PhotoAnalysisPresenterAction.TakePhoto,
    PhotoAnalysisPresenterAction.ImportPhoto,
)

/** Typed Android effect coordinator kept outside the Activity and platform bridge. */
@Suppress("LongParameterList", "TooManyFunctions")
class PhotoAnalysisAndroidHostController(
    private val feature: PhotoAnalysisFeature,
    private val photoBridge: AndroidPhotoBridgePort,
    private val normalizer: AndroidPhotoNormalizerPort,
    private val requestCameraPermission: () -> Unit,
    private val showCameraPermissionRationale: (onContinue: () -> Unit) -> Unit,
    private val capturePhoto: (String) -> Unit,
    private val launchImport: () -> Unit,
    private val launchSettings: () -> Unit,
    private val captureOperation: CaptureOperationState,
    private val onRetainedPhotoReady: (NormalizedPhotoInput) -> Unit,
    private val onRetainedPhotoInvalidated: () -> Unit,
    private var analysisLauncher: PhotoAnalysisAnalysisLauncher? = null,
) {
    private var pendingAnalysisStart = false
    private var pendingGridCellIndex: Int? = null

    fun initialize() {
        photoBridge.cleanupStaleCaptureFiles()
    }

    fun setAnalysisLauncher(launcher: PhotoAnalysisAnalysisLauncher) {
        analysisLauncher = launcher
        if (pendingAnalysisStart) {
            pendingAnalysisStart = false
            val gridCellIndex = pendingGridCellIndex
            pendingGridCellIndex = null
            launcher.start(gridCellIndex)
        }
    }

    fun handleAction(action: PhotoAnalysisPresenterAction) {
        feature.route(action, ::handleNativeAction)
        if (action in ACTIONS_RESETTING_ANALYSIS) {
            pendingAnalysisStart = false
            pendingGridCellIndex = null
            analysisLauncher?.cancel()
            onRetainedPhotoInvalidated()
        }
    }

    fun handleGridCellSelected(index: Int) {
        val action = PhotoAnalysisPresenterAction.SelectGridCell(index)
        feature.route(action, ::handleNativeAction)
        val launcher = analysisLauncher
        if (launcher == null) {
            pendingAnalysisStart = true
            pendingGridCellIndex = index
        } else {
            launcher.start(index)
        }
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

    fun onInlinePhotoResult(operationId: String, result: Result<ByteArray>) {
        val expectedOperation = captureOperation.pendingOperationId
        captureOperation.pendingOperationId = null
        val sourceBytes = result.getOrNull()
        if (expectedOperation != operationId) {
            sourceBytes?.fill(0)
            failPreparation(RecoveryReason.CAPTURE_FAILED)
            return
        }
        if (sourceBytes == null) {
            failPreparation(RecoveryReason.CAPTURE_FAILED)
        } else {
            processPhotoBytes(sourceBytes)
        }
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun onActivityResult(resultCode: Int, data: Intent?, isCamera: Boolean) {
        if (isCamera) {
            val expectedOperation = captureOperation.pendingOperationId
            if (expectedOperation == null) {
                failPreparation(
                    if (resultCode == Activity.RESULT_OK) RecoveryReason.CAPTURE_FAILED else RecoveryReason.CANCELLED,
                )
                return
            }
            val returnedOperation = data?.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_OPERATION_ID)
            captureOperation.pendingOperationId = null
            if (returnedOperation != expectedOperation) {
                failPreparation(
                    if (resultCode == Activity.RESULT_OK) RecoveryReason.CAPTURE_FAILED else RecoveryReason.CANCELLED,
                )
                return
            }
        }
        if (resultCode != Activity.RESULT_OK) {
            val reason = if (isCamera && photoBridge.isCaptureFailure(data)) {
                RecoveryReason.CAPTURE_FAILED
            } else {
                RecoveryReason.CANCELLED
            }
            failPreparation(reason)
            return
        }

        val sourceBytes = photoBridge.photoBytesFromResult(data)
        if (sourceBytes == null) {
            failPreparation(RecoveryReason.CAPTURE_FAILED)
            return
        }

        processPhotoBytes(sourceBytes)
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun processPhotoBytes(sourceBytes: ByteArray) {
        analysisLauncher?.cancel()
        onRetainedPhotoInvalidated()
        val ref = feature.issuePhotoRef(UUID.randomUUID().toString())
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        try {
            val input = normalizer.normalize(ref, sourceBytes)
            feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
            onRetainedPhotoReady(input)
            val launcher = analysisLauncher
            if (launcher == null) {
                pendingAnalysisStart = true
                pendingGridCellIndex = null
            } else {
                launcher.start()
            }
        } catch (_: PhotoPreparationException) {
            failPreparation(RecoveryReason.POOR_IMAGE)
        } catch (_: RuntimeException) {
            failPreparation(RecoveryReason.CAPTURE_FAILED)
        } finally {
            sourceBytes.fill(0)
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
                        capturePhoto(operationId)
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

    private fun failPreparation(reason: RecoveryReason) {
        analysisLauncher?.cancel()
        onRetainedPhotoInvalidated()
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(reason))
    }
}

class CaptureOperationState {
    var pendingOperationId: String? = null
    var permissionRequestInFlight: Boolean = false
}
