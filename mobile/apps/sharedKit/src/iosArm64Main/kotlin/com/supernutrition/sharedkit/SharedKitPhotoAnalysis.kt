package com.supernutrition.sharedkit

import com.supernutrition.core.config.ConfigurationSnapshot
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterModel
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterState
import com.supernutrition.feature.photoanalysis.PhotoAnalysisViewController
import com.supernutrition.platform.photoios.IosImageNormalizer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.UIKit.UIViewController

/** Canonical Apple production facade: shared presenter plus Compose surface. */
public class SharedKitPhotoAnalysisSession(
    attemptId: String,
) {
    internal val feature = PhotoAnalysisFeature(
        attemptId = AttemptId(attemptId),
        configurationSnapshot = configurationSnapshot(attemptId),
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

    private companion object {
        fun configurationSnapshot(attemptId: String): ConfigurationSnapshot = ConfigurationSnapshot(
            snapshotId = attemptId,
            revision = 1,
            defaultLocale = "en-US",
            measurementSystem = "metric",
            catalogCountry = "US",
            marketJurisdiction = "US",
            processingRegion = "us-east",
            policyVersion = "v1",
            digest = "a".repeat(64),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
public object SharedKitPhotoAnalysis {
    public fun viewController(
        session: SharedKitPhotoAnalysisSession,
        onNativeAction: (String) -> Unit,
    ): UIViewController = PhotoAnalysisViewController(session.feature, onNativeAction)
}
