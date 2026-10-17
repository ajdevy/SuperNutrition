package com.supernutrition.sharedkit

import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.MassNotEvaluableReason
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.VisibleComponent
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
import platform.Foundation.NSData
import platform.Foundation.create

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
     * Completes the shared review flow with results produced by the native iOS
     * PoC runner. The opaque photo identity is checked before any event is
     * accepted so a late native result cannot replace a newer photo.
     */
    public fun applyNativePocAnalysis(
        opaqueId: String,
        requestId: String,
        dishLabels: List<String>,
        dishConfidences: List<Double>,
        regionLabels: List<String>,
        regionConfidences: List<Double>,
        regionLefts: List<Double>,
        regionTops: List<Double>,
        regionRights: List<Double>,
        regionBottoms: List<Double>,
    ) {
        val ready = feature.currentWorkState() as? PhotoAnalysisState.Ready ?: return
        if (ready.input.ref.opaqueId != opaqueId) return
        require(dishLabels.size == dishConfidences.size) { "Dish labels and confidences must have equal sizes" }
        require(
            regionLabels.size == regionConfidences.size &&
                regionLabels.size == regionLefts.size &&
                regionLabels.size == regionTops.size &&
                regionLabels.size == regionRights.size &&
                regionLabels.size == regionBottoms.size,
        ) { "Region result arrays must have equal sizes" }

        val request = RequestId(requestId)
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        if ((feature.currentWorkState() as? PhotoAnalysisState.Analyzing)?.requestId != request) return

        feature.dispatch(
            PhotoAnalysisEvent.LocalAnalysisStageChanged(request, LocalAnalysisStage.FINDING_VISIBLE_FOODS),
        )
        if (regionLabels.isEmpty()) {
            feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceUnavailable(request, RecoveryReason.NO_FOOD))
            return
        }

        feature.dispatch(PhotoAnalysisEvent.LocalAnalysisStageChanged(request, LocalAnalysisStage.IDENTIFYING_DISH))
        val dishes = nativePocDishes(dishLabels, dishConfidences)
        val components = nativePocComponents(
            regionLabels,
            regionConfidences,
            regionLefts,
            regionTops,
            regionRights,
            regionBottoms,
        )
        feature.dispatch(PhotoAnalysisEvent.LocalAnalysisStageChanged(request, LocalAnalysisStage.CHECKING_RESULTS))
        feature.dispatch(
            PhotoAnalysisEvent.ReviewEvidenceAvailable(
                request,
                BoundedDishIngredientFusion.merge(dishes, components),
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

    private fun nativePocComponents(
        regionLabels: List<String>,
        regionConfidences: List<Double>,
        regionLefts: List<Double>,
        regionTops: List<Double>,
        regionRights: List<Double>,
        regionBottoms: List<Double>,
    ): List<VisibleComponent> {
        val componentProvenance = InferenceV2Provenance(
            route = InferenceRoute.LOCAL,
            engineId = IOS_POC_COMPONENT_ENGINE_ID,
            modelVersion = IOS_POC_COMPONENT_MODEL_VERSION,
            taxonomyVersion = IOS_POC_COMPONENT_TAXONOMY_VERSION,
            calibrationVersion = IOS_POC_COMPONENT_CALIBRATION_VERSION,
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        )
        return regionLabels.mapIndexed { index, label ->
            val score = regionConfidences[index]
            val left = regionLefts[index]
            val top = regionTops[index]
            val right = regionRights[index]
            val bottom = regionBottoms[index]
            require(listOf(score, left, top, right, bottom).all { it.isFinite() }) {
                "Region output must be finite"
            }
            require(
                score in 0.0..1.0 && left in 0.0..1.0 && top in 0.0..1.0 &&
                    right in left..1.0 && bottom in top..1.0 && right > left && bottom > top,
            ) {
                "Region output must be bounded and non-degenerate"
            }
            VisibleComponent(
                componentId = "region-${index + 1}-$label",
                candidate = LocalizedCandidateIdentity("coco:$label", label, "en"),
                confidence = CalibratedConfidence(score, ConfidenceBand.fromScore(score)),
                visibility = ComponentVisibility.VISIBLE,
                ambiguity = ComponentAmbiguity.NONE,
                region = NormalizedVisibleRegion(left, top, right - left, bottom - top),
                mass = MassEvidence.NotEvaluable(
                    MassNotEvaluableReason.MISSING_CALIBRATION,
                    "inference.mass.not-evaluable",
                ),
                provenance = componentProvenance,
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
        const val IOS_POC_COMPONENT_ENGINE_ID = "local-ios-poc-component"
        const val IOS_POC_DISH_MODEL_VERSION = "poc-food101-swin-int8-v1"
        const val IOS_POC_COMPONENT_MODEL_VERSION = "poc-yolo-food-components-v1"
        const val IOS_POC_COMPONENT_TAXONOMY_VERSION = "supernutrition-visible-food-v1"
        const val IOS_POC_DISH_CALIBRATION_VERSION = "softmax-proxy-v1"
        const val IOS_POC_COMPONENT_CALIBRATION_VERSION = "uncalibrated-proxy-v1"
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
