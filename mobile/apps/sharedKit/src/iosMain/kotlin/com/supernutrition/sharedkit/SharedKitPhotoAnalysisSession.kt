package com.supernutrition.sharedkit

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.feature.photoanalysis.IosRetainedPhotoPreview
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAnalysisLauncher
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterCopy
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterModel
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterState
import com.supernutrition.platform.photoios.IosImageNormalizer
import platform.Foundation.NSData

/** Shared Apple session facade; only the renderer differs between Apple targets. */
@Suppress("TooManyFunctions")
public class SharedKitPhotoAnalysisSession private constructor(
    attemptId: String,
    public val presenterCopy: PhotoAnalysisPresenterCopy = localizedPhotoAnalysisCopy(),
) {
    public constructor(attemptId: String) : this(attemptId, localizedPhotoAnalysisCopy())

    private val leases = PhotoLeaseStore()
    private val sessions = PhotoSessionStore()
    internal val feature = PhotoAnalysisFeature(
        attemptId = AttemptId(attemptId),
        leases = leases,
        sessions = sessions,
    )
    private val analysisLauncher: PhotoAnalysisAnalysisLauncher = PhotoAnalysisAnalysisLauncher.None

    /** Native display copy retained only while the current photo flow needs it. */
    public var retainedPhoto: IosRetainedPhotoPreview? = null
        private set

    public fun presenterState(): PhotoAnalysisPresenterState = PhotoAnalysisPresenterModel.from(
        feature.state,
        presenterCopy,
    ).state

    public fun statusLabel(): String = PhotoAnalysisPresenterModel.from(
        feature.state,
        presenterCopy,
    ).statusLabel

    public fun shouldOfferSettings(): Boolean =
        (feature.state as? PhotoAnalysisState.Recovery)?.reason == RecoveryReason.PERMISSION_DENIED

    public fun permissionDenied() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
    }

    public fun cameraUnavailable() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))
    }

    public fun captureFailed() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
    }

    public fun captureCancelled() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CANCELLED))
    }

    public fun photoReturned(opaqueId: String, sourceData: NSData) {
        clearRetainedPhoto()
        val ref = feature.issuePhotoRef(opaqueId)
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        try {
            val normalized = IosImageNormalizer().normalizeWithPreview(ref, sourceData)
            retainedPhoto = IosRetainedPhotoPreview(ref = ref, image = normalized.preview)
            feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized.input))
            analysisLauncher.start()
        } catch (_: PhotoPreparationException) {
            analysisLauncher.cancel()
            clearRetainedPhoto()
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
        } catch (_: Exception) {
            analysisLauncher.cancel()
            clearRetainedPhoto()
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
        }
    }

    public fun scanAgain() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.ScanAgain)
    }

    public fun cancel() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.Cancel)
    }

    public fun backgrounded() {
        analysisLauncher.cancel()
        clearRetainedPhoto()
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
    }

    public fun restored() {
        feature.dispatch(PhotoAnalysisEvent.Restored)
    }

    private fun clearRetainedPhoto() {
        retainedPhoto = null
    }
}
