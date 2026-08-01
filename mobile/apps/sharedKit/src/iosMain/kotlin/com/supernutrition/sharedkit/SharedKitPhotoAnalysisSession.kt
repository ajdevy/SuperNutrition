package com.supernutrition.sharedkit

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterCopy
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterModel
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterState
import com.supernutrition.platform.photoios.IosImageNormalizer
import platform.Foundation.NSData

/** Shared Apple session facade; only the renderer differs between Apple targets. */
public class SharedKitPhotoAnalysisSession private constructor(
    attemptId: String,
    public val presenterCopy: PhotoAnalysisPresenterCopy = localizedPhotoAnalysisCopy(),
) {
    public constructor(attemptId: String) : this(attemptId, localizedPhotoAnalysisCopy())

    internal val feature = PhotoAnalysisFeature(
        attemptId = AttemptId(attemptId),
        leases = PhotoLeaseStore(),
        sessions = PhotoSessionStore(),
    )

    public fun presenterState(): PhotoAnalysisPresenterState = PhotoAnalysisPresenterModel.from(
        feature.state,
        presenterCopy,
    ).state

    public fun statusLabel(): String = PhotoAnalysisPresenterModel.from(feature.state, presenterCopy).statusLabel

    public fun shouldOfferSettings(): Boolean =
        (feature.state as? PhotoAnalysisState.Recovery)?.reason == RecoveryReason.PERMISSION_DENIED

    public fun permissionDenied() {
        feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
    }

    public fun cameraUnavailable() {
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))
    }

    public fun captureFailed() {
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
    }

    public fun captureCancelled() {
        feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CANCELLED))
    }

    public fun photoReturned(opaqueId: String, sourceData: NSData) {
        val ref = feature.issuePhotoRef(opaqueId)
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        try {
            val normalized = IosImageNormalizer().normalize(ref, sourceData)
            feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized))
        } catch (_: PhotoPreparationException) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
        } catch (_: Exception) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
        }
    }

    public fun scanAgain() {
        feature.dispatch(PhotoAnalysisEvent.ScanAgain)
    }

    public fun cancel() {
        feature.dispatch(PhotoAnalysisEvent.Cancel)
    }

    public fun backgrounded() {
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
    }

    public fun restored() {
        feature.dispatch(PhotoAnalysisEvent.Restored)
    }
}
