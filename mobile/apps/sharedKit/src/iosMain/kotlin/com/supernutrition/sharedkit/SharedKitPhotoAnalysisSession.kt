package com.supernutrition.sharedkit

import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.BoundedDishIngredientFusion
import com.supernutrition.domain.photoanalysis.LocalAnalysisStage
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.RequestId
import com.supernutrition.feature.photoanalysis.IosRetainedPhotoPreview
import com.supernutrition.feature.photoanalysis.LocalUserData
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAnalysisLauncher
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterCopy
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterModel
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterState
import com.supernutrition.platform.photoios.IosImageNormalizer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import platform.Foundation.NSData
import platform.Foundation.NSThread
import platform.Foundation.create

/** Shared Apple session facade; only the renderer differs between Apple targets. */
@Suppress("TooManyFunctions")
public class SharedKitPhotoAnalysisSession internal constructor(
    attemptId: String,
    public val presenterCopy: PhotoAnalysisPresenterCopy = localizedPhotoAnalysisCopy(),
    internal val localData: LocalUserData,
) {
    public constructor(attemptId: String) : this(attemptId, localizedPhotoAnalysisCopy(), LocalUserData())

    private val leases = PhotoLeaseStore()
    private val sessions = PhotoSessionStore()
    internal val feature = PhotoAnalysisFeature(
        attemptId = AttemptId(attemptId),
        leases = leases,
        sessions = sessions,
        mealDiary = localData.mealDiary,
    )
    private val analysisLauncher: PhotoAnalysisAnalysisLauncher = PhotoAnalysisAnalysisLauncher.None
    private val catalogScope = MainScope()

    /** Call on Main after the root host has rendered; repeated starts retain the same source owner. */
    public fun startCatalog() {
        requireCatalogMainThread()
        localData.catalogSession.start(catalogScope, Dispatchers.Default)
    }

    /** Root-owner disposal only. Closing a capture modal or cancelling a photo flow keeps the catalog alive. */
    public fun closeCatalog() {
        requireCatalogMainThread()
        localData.catalogSession.close()
        catalogScope.cancel()
    }

    /** Native display copy retained only while the current photo flow needs it. */
    public var retainedPhoto: IosRetainedPhotoPreview? = null
        private set
    private var normalizedInferenceData: NSData? = null
    private var normalizedInferenceOpaqueId: String? = null

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

    @OptIn(ExperimentalForeignApi::class)
    public fun photoReturned(opaqueId: String, sourceData: NSData) {
        clearRetainedPhoto()
        val ref = feature.issuePhotoRef(opaqueId)
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        try {
            val normalized = IosImageNormalizer().normalizeWithPreview(ref, sourceData)
            retainedPhoto = IosRetainedPhotoPreview(ref = ref, image = normalized.preview)
            normalizedInferenceData = normalized.input.bytes.toNSData()
            normalizedInferenceOpaqueId = opaqueId
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

    /** Returns the exact bounded bytes used by the shared photo contract. */
    @OptIn(ExperimentalForeignApi::class)
    public fun normalizedPhotoData(opaqueId: String): NSData? =
        normalizedInferenceData?.takeIf { normalizedInferenceOpaqueId == opaqueId }

    /**
     * Completes the shared review flow with Food-101 dish candidates from the
     * native iOS PoC runner. The photo identity is checked before any event is
     * accepted so a late native result cannot replace a newer photo.
     */
    public fun applyNativePocAnalysis(
        opaqueId: String,
        requestId: String,
        dishLabels: List<String>,
        dishConfidences: List<Double>,
    ) {
        val ready = feature.currentWorkState() as? PhotoAnalysisState.Ready ?: return
        if (ready.input.ref.opaqueId != opaqueId) return
        require(dishLabels.size == dishConfidences.size) { "Dish labels and confidences must have equal sizes" }

        val request = RequestId(requestId)
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        if ((feature.currentWorkState() as? PhotoAnalysisState.Analyzing)?.requestId != request) return

        feature.dispatch(PhotoAnalysisEvent.LocalAnalysisStageChanged(request, LocalAnalysisStage.IDENTIFYING_DISH))
        val dishes = nativePocDishes(dishLabels, dishConfidences)
        if (dishes.isEmpty()) {
            feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceUnavailable(request, RecoveryReason.NO_FOOD))
            return
        }
        feature.dispatch(PhotoAnalysisEvent.LocalAnalysisStageChanged(request, LocalAnalysisStage.CHECKING_RESULTS))
        feature.dispatch(
            PhotoAnalysisEvent.ReviewEvidenceAvailable(
                request,
                BoundedDishIngredientFusion.merge(dishes, emptyList()),
            ),
        )
    }

    private fun nativePocDishes(dishLabels: List<String>, dishConfidences: List<Double>): List<InferenceCandidate> {
        val dishProvenance = InferenceProvenance(
            route = InferenceRoute.LOCAL,
            engineId = IOS_POC_DISH_ENGINE_ID,
            modelVersion = IOS_POC_DISH_MODEL_VERSION,
            calibrationVersion = IOS_POC_DISH_CALIBRATION_VERSION,
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        )
        return dishLabels.take(MAX_DISH_PROPOSALS).mapIndexed { index, label ->
            val score = dishConfidences[index]
            require(score.isFinite() && score in 0.0..1.0) { "Dish confidence must be finite and bounded" }
            InferenceCandidate(
                candidateId = "food101:$label",
                label = label,
                rank = index + 1,
                confidence = Confidence(score, ConfidenceBand.fromScore(score)),
                unresolvedAssumptions = listOf(
                    InferenceAssumption(AssumptionCode.PORTION_UNKNOWN, "inference.assumption.portion_unknown"),
                    InferenceAssumption(
                        AssumptionCode.HIDDEN_INGREDIENTS_UNKNOWN,
                        "inference.assumption.hidden_ingredients_unknown",
                    ),
                ),
                provenance = dishProvenance,
            )
        }
    }

    public fun nativePocFailed(opaqueId: String, requestId: String) {
        val ready = feature.currentWorkState() as? PhotoAnalysisState.Ready ?: return
        if (ready.input.ref.opaqueId != opaqueId) return
        val request = RequestId(requestId)
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        if ((feature.currentWorkState() as? PhotoAnalysisState.Analyzing)?.requestId == request) {
            feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceUnavailable(request, RecoveryReason.ANALYSIS_FAILED))
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
        // Backgrounding is lifecycle persistence, not user cancellation. Keep
        // the photo lease and in-flight work so the review can be restored.
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
    }

    public fun restored() {
        feature.dispatch(PhotoAnalysisEvent.Restored)
    }

    private fun clearRetainedPhoto() {
        retainedPhoto = null
        normalizedInferenceData = null
        normalizedInferenceOpaqueId = null
    }

    private companion object {
        const val MAX_DISH_PROPOSALS = 3
        const val IOS_POC_DISH_ENGINE_ID = "local-ios-poc-dish"
        const val IOS_POC_DISH_MODEL_VERSION = "poc-food101-swin-int8-v1"
        const val IOS_POC_DISH_CALIBRATION_VERSION = "softmax-proxy-v1"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun requireCatalogMainThread() {
    check(NSThread.isMainThread) { "Catalog lifetime operations must run on Main" }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
