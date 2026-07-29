package com.supernutrition.sharedkit

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature
import com.supernutrition.feature.photoanalysis.PhotoAnalysisNativeAction
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterModel
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterState
import com.supernutrition.platform.photoios.IosImageNormalizer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.UIKit.UIViewController

/**
 * Intel compatibility facade. It shares the real feature reducer/presenter;
 * only the Compose renderer is unavailable on this host because the pinned
 * Compose release has no iosX64 publication.
 */
public class SharedKitPhotoAnalysisSession(
    attemptId: String,
) {
    private val feature = PhotoAnalysisFeature(
        attemptId = AttemptId(attemptId),
        leases = PhotoLeaseStore(),
        sessions = PhotoSessionStore(),
    )

    public fun presenterState(): PhotoAnalysisPresenterState = PhotoAnalysisPresenterModel.from(feature.state).state

    public fun statusLabel(): String = PhotoAnalysisPresenterModel.from(feature.state).statusLabel

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
        runCatching {
            IosImageNormalizer().normalize(ref, sourceData)
        }.onSuccess { normalized ->
            feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized))
        }.onFailure {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
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

/**
 * Keeps the Swift API shape identical while the Intel renderer remains a
 * compatibility-only SwiftUI implementation. This function is never called
 * from the x86_64 branch of the host.
 */
@OptIn(ExperimentalForeignApi::class)
public object SharedKitPhotoAnalysis {
    public fun viewController(
        session: SharedKitPhotoAnalysisSession,
        onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
    ): UIViewController = error("Compose iOS UI is unavailable on the iosX64 compatibility lane")
}
