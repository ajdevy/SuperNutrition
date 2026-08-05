package com.supernutrition.feature.photoanalysis

import android.app.Activity
import android.content.Intent
import com.supernutrition.core.ui.CameraAvailability
import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.PreparationEscalation
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.platform.photoandroid.AndroidPhotoBridgePort
import com.supernutrition.platform.photoandroid.AndroidPhotoCaptureFiles
import com.supernutrition.platform.photoandroid.AndroidPhotoNormalizerPort
import java.util.UUID

/**
 * What the host can do on the controller's behalf.
 *
 * These five are all one thing — how the Activity surfaces something to the
 * user — and they are what made the controller's constructor a list rather than
 * a signature. Grouping them separates them from the collaborators the
 * controller reasons with.
 */
class PhotoAnalysisHostCallbacks(
    val requestCameraPermission: () -> Unit,
    val showCameraPermissionRationale: (onContinue: () -> Unit) -> Unit,
    val launchCamera: (String) -> Unit,
    val launchImport: () -> Unit,
    val launchSettings: () -> Unit,
)

/** Typed Android effect coordinator kept outside the Activity and platform bridge. */
@Suppress("LongParameterList")
class PhotoAnalysisAndroidHostController(
    private val feature: PhotoAnalysisFeature,
    private val photoBridge: AndroidPhotoBridgePort,
    private val normalizer: AndroidPhotoNormalizerPort,
    private val callbacks: PhotoAnalysisHostCallbacks,
    private val captureOperation: CaptureOperationState,
    private val onRetainedPhotoReady: (NormalizedPhotoInput) -> Unit,
    private val onRetainedPhotoInvalidated: () -> Unit,
    private val analysisLauncher: PhotoAnalysisAnalysisLauncher = PhotoAnalysisAnalysisLauncher.None,
) {
    fun initialize() {
        photoBridge.cleanupStaleCaptureFiles()
    }

    fun handleAction(action: PhotoAnalysisPresenterAction) {
        feature.route(action, ::handleNativeAction)
        if (action == PhotoAnalysisPresenterAction.Cancel || action == PhotoAnalysisPresenterAction.ScanAgain) {
            analysisLauncher.cancel()
            onRetainedPhotoInvalidated()
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

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun onActivityResult(resultCode: Int, data: Intent?, isCamera: Boolean) {
        if (isCamera && !isOurCapture(resultCode, data)) return
        if (resultCode != Activity.RESULT_OK) {
            val reason = if (isCamera && photoBridge.isCaptureFailure(data)) {
                RecoveryReason.CAPTURE_FAILED
            } else {
                RecoveryReason.CANCELLED
            }
            reportFailure(reason)
            return
        }

        analysisLauncher.cancel()
        onRetainedPhotoInvalidated()
        val ref = feature.issuePhotoRef(UUID.randomUUID().toString())
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        val sourceBytes = photoBridge.photoBytesFromResult(data)
        if (sourceBytes == null) {
            reportFailure(RecoveryReason.CAPTURE_FAILED)
            return
        }
        prepare(ref, sourceBytes)
    }

    /**
     * Whether this result belongs to the capture we launched. A missing pending
     * operation, or one that does not match what came back, dispatches its own
     * failure — the caller only has to stop.
     */
    private fun isOurCapture(resultCode: Int, data: Intent?): Boolean {
        val expectedOperation = captureOperation.pendingOperationId
        if (expectedOperation == null) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(failedUnlessCancelled(resultCode)))
            return false
        }
        val returnedOperation = data?.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_OPERATION_ID)
        captureOperation.pendingOperationId = null
        if (returnedOperation != expectedOperation) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(failedUnlessCancelled(resultCode)))
            return false
        }
        return true
    }

    /**
     * A result that arrived OK but does not belong to us is a failure; anything
     * else at this point is the user backing out.
     */
    private fun failedUnlessCancelled(resultCode: Int): RecoveryReason =
        if (resultCode == Activity.RESULT_OK) RecoveryReason.CAPTURE_FAILED else RecoveryReason.CANCELLED

    /**
     * Preparation never ends the journey.
     *
     * This is a camera boundary: whatever the decoder does, the user must land
     * somewhere they can act. An earlier version caught only the four
     * exceptions normalization was known to raise, which let anything else
     * propagate and take the capture flow down — trading a recoverable failure
     * for a crash to satisfy a lint rule. The catch is deliberately broad and
     * the rule is suppressed here rather than obeyed.
     *
     * Recoverable does not mean silent. Every failure is reported with its
     * cause, and a run of them escalates through [PreparationEscalation] so a
     * persistent pipeline problem stops being reported as a bad photo.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun prepare(ref: PhotoRef, sourceBytes: ByteArray) {
        try {
            val input = normalizer.normalize(ref, sourceBytes)
            feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
            captureOperation.consecutivePreparationFailures = 0
            onRetainedPhotoReady(input)
            // Preparation no longer starts the analysis. A prepared photo stops
            // at Ready so the consent surface can be shown and answered; the
            // granted decision is what starts the launcher. Auto-starting here
            // would transfer before the user was asked, which is the whole thing
            // the consent surface exists to prevent (AD-40).
        } catch (error: PhotoPreparationException) {
            reportFailure(RecoveryReason.POOR_IMAGE, error.message)
        } catch (error: Throwable) {
            // Throwable rather than Exception: an OutOfMemoryError decoding a
            // large capture is exactly the case where dropping the user out of
            // the flow helps least, and it is an Error rather than an Exception.
            reportFailure(RecoveryReason.CAPTURE_FAILED, error.message ?: error::class.simpleName)
        } finally {
            // The source buffer is scrubbed on every path: a failed preparation
            // must not leave photo bytes reachable either (AD-8).
            sourceBytes.fill(0)
        }
    }

    /**
     * The single preparation-failure path: escalate by consecutive-failure count,
     * record the diagnostic, and release everything the attempt was holding.
     * Two separate helpers here meant a failure could escalate without releasing,
     * or release without escalating, depending on which one the caller reached.
     */
    private fun reportFailure(reason: RecoveryReason, diagnostic: String? = null) {
        analysisLauncher.cancel()
        onRetainedPhotoInvalidated()
        val failures = captureOperation.consecutivePreparationFailures + 1
        captureOperation.consecutivePreparationFailures = failures
        feature.dispatch(
            PhotoAnalysisEvent.PreparationFailed(
                PreparationEscalation.reasonFor(reason, failures),
                diagnostic?.let { "$it (attempt $failures)" },
            ),
        )
    }

    private fun handleNativeAction(action: PhotoAnalysisNativeAction) {
        when (action) {
            PhotoAnalysisNativeAction.TakePhoto -> openCamera()
            PhotoAnalysisNativeAction.ImportPhoto -> callbacks.launchImport()
            PhotoAnalysisNativeAction.OpenSettings -> callbacks.launchSettings()
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
                        callbacks.launchCamera(operationId)
                    }
                }
            }

            CameraPermissionState.NOT_DETERMINED,
            CameraPermissionState.DENIED,
            -> requestPermission()

            CameraPermissionState.RATIONALE -> callbacks.showCameraPermissionRationale(::requestPermission)

            CameraPermissionState.PERMANENTLY_DENIED,
            CameraPermissionState.RESTRICTED,
            -> feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        }
    }

    private fun requestPermission() {
        if (!captureOperation.permissionRequestInFlight) {
            captureOperation.permissionRequestInFlight = true
            photoBridge.markCameraPermissionRequestStarted()
            callbacks.requestCameraPermission()
        }
    }
}

class CaptureOperationState {
    var pendingOperationId: String? = null
    var permissionRequestInFlight: Boolean = false

    /**
     * Reset by a successful preparation, so the streak means "in a row" rather
     * than "ever". Retained with the rest of the host state, because a
     * configuration change mid-capture is not a fresh start for the user.
     */
    var consecutivePreparationFailures: Int = 0
}
