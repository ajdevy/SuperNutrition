package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.GramRange
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.inference.V2AbstentionReason
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.LocalAnalysisStage
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.RequestId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PhotoAnalysisAnalysisCoordinatorTest {
    @Test
    fun `coordinator sequences local stages and keeps v1 dishes separate from v2 evidence`() = runBlocking {
        val feature = preparedFeature("coordinator-success")
        val stages = mutableListOf<LocalAnalysisStage>()
        val coordinator = coordinator(
            feature,
            v2 = v2Engine(components = listOf(component())),
            stages = stages,
        )

        val result = coordinator.run(
            RequestId("request-success"),
            attemptContext("coordinator-success", "request-success"),
        )

        val review = assertIs<PhotoAnalysisAnalysisResult.Review>(result)
        assertEquals(
            PhotoAnalysisState.CandidateReview(
                ref = (feature.state as PhotoAnalysisState.CandidateReview).ref,
                requestId = RequestId("request-success"),
                draft = review.draft,
            ),
            feature.state,
        )
        assertEquals(
            listOf(
                LocalAnalysisStage.FINDING_VISIBLE_FOODS,
                LocalAnalysisStage.IDENTIFYING_DISH,
                LocalAnalysisStage.CHECKING_RESULTS,
            ),
            stages,
        )
        assertEquals(listOf("dish-1"), result.draft.mainDishProposals.map(InferenceCandidate::candidateId))
        assertEquals(listOf("component-1"), result.draft.photoEvidence.map(VisibleComponent::componentId))
        assertEquals("ingredient:cucumber", result.draft.compositionHints.first().ingredientId)
    }

    @Test
    fun `engine boundary rejects a result whose modality differs from the request`() = runBlocking {
        val feature = preparedFeature("coordinator-modality")
        val result = coordinator(
            feature,
            v2 = v2Engine(
                inputModality = InferenceInputModality.TEXT,
                outcome = InferenceV2Outcome.Abstention(
                    reason = V2AbstentionReason.IMAGE_QUALITY,
                    userMessageKey = "inference.abstention.image_quality",
                    provenance = v2Provenance(),
                ),
            ),
        ).run(
            RequestId("request-modality"),
            attemptContext("coordinator-modality", "request-modality"),
        )

        assertEquals(PhotoAnalysisAnalysisResult.Recovery(RecoveryReason.CAPTURE_FAILED), result)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED), feature.state)
    }

    @Test
    fun `v2 abstention and failure become typed recovery without fabricating review evidence`() = runBlocking {
        val abstained = preparedFeature("coordinator-abstention")
        val abstentionResult = coordinator(
            abstained,
            v2 = v2Engine(
                outcome = InferenceV2Outcome.Abstention(
                    reason = V2AbstentionReason.NO_FOOD_DETECTED,
                    userMessageKey = "inference.abstention.no_food_detected",
                    provenance = v2Provenance(),
                ),
            ),
        ).run(
            RequestId("request-abstention"),
            attemptContext("coordinator-abstention", "request-abstention"),
        )
        assertEquals(PhotoAnalysisAnalysisResult.Recovery(RecoveryReason.NO_FOOD), abstentionResult)
        val noFoodRecovery = assertIs<PhotoAnalysisState.Recovery>(abstained.state)
        assertEquals(RecoveryReason.NO_FOOD, noFoodRecovery.reason)
        assertNotNull(noFoodRecovery.ref)

        val failed = preparedFeature("coordinator-failure")
        val failureResult = coordinator(
            failed,
            v2 = v2Engine(
                outcome = InferenceV2Outcome.Failure(
                    code = V2InferenceFailureCode.MODEL_UNAVAILABLE,
                    retryable = true,
                    userMessageKey = "inference.failure.model_unavailable",
                    provenance = v2Provenance(),
                ),
            ),
        ).run(
            RequestId("request-failure"),
            attemptContext("coordinator-failure", "request-failure"),
        )
        assertEquals(PhotoAnalysisAnalysisResult.Recovery(RecoveryReason.ANALYSIS_FAILED), failureResult)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.ANALYSIS_FAILED), failed.state)
    }

    @Test
    fun `stale request and cancellation cannot reopen review`() = runBlocking {
        val staleFeature = preparedFeature("coordinator-stale")
        val staleCoordinator = coordinator(staleFeature, v2 = v2Engine(components = listOf(component())))
        staleFeature.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("other-request")))
        val staleResult = staleCoordinator.run(
            RequestId("request-stale"),
            attemptContext("coordinator-stale", "request-stale"),
        )
        assertEquals(PhotoAnalysisAnalysisResult.Ignored, staleResult)
        assertIs<PhotoAnalysisState.Analyzing>(staleFeature.state)

        val cancelledFeature = preparedFeature("coordinator-cancelled")
        val cancellingV2 = v2Engine(components = listOf(component()), beforeReturn = {
            cancelledFeature.dispatch(PhotoAnalysisEvent.Cancel)
        })
        val cancelledResult = coordinator(cancelledFeature, v2 = cancellingV2)
            .run(
                RequestId("request-cancelled"),
                attemptContext("coordinator-cancelled", "request-cancelled"),
            )
        assertEquals(PhotoAnalysisAnalysisResult.Ignored, cancelledResult)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), cancelledFeature.state)
    }

    @Test
    fun `backgrounded analysis retains its completed review until the host restores`() = runBlocking {
        val feature = preparedFeature("coordinator-background")
        val result = coordinator(
            feature,
            v2 = v2Engine(
                components = listOf(component()),
                beforeReturn = { feature.dispatch(PhotoAnalysisEvent.Backgrounded) },
            ),
        ).run(
            RequestId("request-background"),
            attemptContext("coordinator-background", "request-background"),
        )

        val review = assertIs<PhotoAnalysisAnalysisResult.Review>(result)
        assertIs<PhotoAnalysisState.Backgrounded>(feature.state)
        val retained = assertIs<PhotoAnalysisState.CandidateReview>(feature.currentWorkState())
        assertEquals(RequestId("request-background"), retained.requestId)
        assertEquals(review.draft, retained.draft)

        feature.dispatch(PhotoAnalysisEvent.Restored)

        assertEquals(feature.currentWorkState(), feature.state)
        assertEquals(review.draft, assertIs<PhotoAnalysisState.CandidateReview>(feature.state).draft)
    }

    private fun coordinator(
        feature: PhotoAnalysisFeature,
        v2: InferenceV2Engine,
        stages: MutableList<LocalAnalysisStage> = mutableListOf(),
    ) = PhotoAnalysisAnalysisCoordinator(feature, DishEngine(), v2, stages::add)

    private fun preparedFeature(attempt: String): PhotoAnalysisFeature {
        val leases = PhotoLeaseStore()
        val feature = PhotoAnalysisFeature(AttemptId(attempt), leases, PhotoSessionStore())
        val ref = feature.issuePhotoRef("photo-$attempt")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        feature.dispatch(
            PhotoAnalysisEvent.PreparationSucceeded(
                NormalizedPhotoInput(
                    ref = ref,
                    bytes = byteArrayOf(1),
                    width = 1,
                    height = 1,
                    orientation = ImageOrientation.UP,
                    colorSpace = "sRGB",
                    metadataKeys = emptySet(),
                    contentDigest = "a".repeat(64),
                ),
            ),
        )
        return feature
    }

    private fun attemptContext(attempt: String, request: String) = InferenceAttemptContext(
        attemptId = AttemptId(attempt),
        requestId = request,
        configurationVersion = "config-$attempt",
        configurationDigest = "b".repeat(64),
    )

    private inner class DishEngine : PhotoInferenceEngine {
        override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1
        override suspend fun capability() = error("capability is not used by the coordinator")
        override suspend fun analyze(request: PhotoInferenceRequest) = PhotoInferenceResult(
            PHOTO_INFERENCE_CONTRACT_V1,
            InferenceOutcome.Candidates(listOf(dish())),
        )
    }

    private fun v2Engine(
        outcome: InferenceV2Outcome = InferenceV2Outcome.Components(listOf(component())),
        components: List<VisibleComponent> = emptyList(),
        inputModality: InferenceInputModality = InferenceInputModality.PHOTO,
        beforeReturn: (() -> Unit)? = null,
    ) = object : InferenceV2Engine() {
        override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2
        override suspend fun capability() = error("capability is not used by the coordinator")
        override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result {
            beforeReturn?.invoke()
            return InferenceV2Result(
                INFERENCE_EVIDENCE_CONTRACT_V2,
                inputModality,
                if (components.isNotEmpty()) InferenceV2Outcome.Components(components) else outcome,
            )
        }
    }

    private fun component() = VisibleComponent(
        componentId = "component-1",
        candidate = LocalizedCandidateIdentity("ingredient-1", "Rice", "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        region = NormalizedVisibleRegion(0.1, 0.1, 0.3, 0.3),
        mass = MassEvidence.Estimated(
            GramRange(100.0, 150.0),
            listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        provenance = v2Provenance(),
    )

    private fun v2Provenance() = InferenceV2Provenance(
        route = InferenceRoute.LOCAL,
        engineId = "component-engine",
        modelVersion = "component-model",
        taxonomyVersion = "component-taxonomy",
        calibrationVersion = "component-calibration",
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
    )

    private fun dish() = InferenceCandidate(
        candidateId = "dish-1",
        label = "Greek salad",
        rank = 1,
        confidence = Confidence(0.9, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.LOCAL,
            engineId = "dish-engine",
            modelVersion = "dish-model",
            calibrationVersion = "dish-calibration",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )
}
