package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.GramRange
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.VisibleComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StagedComponentReviewTest {
    private val attempt = AttemptId("staged-component-attempt")
    private val leases = PhotoLeaseStore()
    private val sessions = PhotoSessionStore()
    private val reducer = PhotoAnalysisReducer(leases, sessions, attempt)

    @Test
    fun `matching analysis stages are factual and a stale stage is ignored`() {
        val setup = analyzing("stage-photo", "stage-request")

        reducer.dispatch(
            PhotoAnalysisEvent.LocalAnalysisStageChanged(
                requestId = setup.requestId,
                stage = LocalAnalysisStage.ESTIMATING_PORTIONS,
            ),
        )

        assertEquals(
            PhotoAnalysisState.Analyzing(
                ref = setup.ref,
                requestId = setup.requestId,
                stage = LocalAnalysisStage.ESTIMATING_PORTIONS,
            ),
            reducer.state,
        )

        reducer.dispatch(
            PhotoAnalysisEvent.LocalAnalysisStageChanged(
                requestId = RequestId("stale-stage-request"),
                stage = LocalAnalysisStage.CHECKING_RESULTS,
            ),
        )

        assertEquals(
            PhotoAnalysisState.Analyzing(
                ref = setup.ref,
                requestId = setup.requestId,
                stage = LocalAnalysisStage.ESTIMATING_PORTIONS,
            ),
            reducer.state,
        )

        leases.revoke(setup.ref)
        reducer.dispatch(
            PhotoAnalysisEvent.LocalAnalysisStageChanged(
                requestId = setup.requestId,
                stage = LocalAnalysisStage.CHECKING_RESULTS,
            ),
        )

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED), reducer.state)
        assertEquals(null, sessions.read(setup.ref))
    }

    @Test
    fun `matching typed evidence reaches review without turning dishes into ingredients`() {
        val setup = analyzing("review-photo", "review-request")
        val draft = CandidateReviewDraft(
            mainDishProposals = listOf(dish("dish-salmon", 1)),
            photoEvidence = listOf(component("component-rice", "ingredient:rice")),
        )

        reducer.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(setup.requestId, draft))

        val review = assertIs<PhotoAnalysisState.CandidateReview>(reducer.state)
        assertEquals(setup.ref, review.ref)
        assertEquals(setup.requestId, review.requestId)
        assertEquals(draft, review.draft)
        assertEquals("dish-salmon", review.draft.mainDishProposals.single().candidateId)
        assertEquals("component-rice", review.draft.photoEvidence.single().componentId)
    }

    @Test
    fun `stale evidence is ignored and expired evidence recovers without review`() {
        val setup = analyzing("stale-review-photo", "stale-review-request")
        val draft = CandidateReviewDraft(listOf(dish("dish-stale", 1)), emptyList())

        reducer.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(RequestId("late-request"), draft))
        assertIs<PhotoAnalysisState.Analyzing>(reducer.state)

        leases.revoke(setup.ref)
        reducer.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(setup.requestId, draft))

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            reducer.state,
        )
    }

    @Test
    fun `typed no-component outcome recovers without fabricating evidence`() {
        val setup = analyzing("empty-review-photo", "empty-review-request")

        reducer.dispatch(
            PhotoAnalysisEvent.ReviewEvidenceUnavailable(
                requestId = setup.requestId,
                reason = RecoveryReason.NO_FOOD,
            ),
        )

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.NO_FOOD, setup.ref), reducer.state)
        assertEquals(setup.ref, (reducer.state as PhotoAnalysisState.Recovery).ref)
        assertEquals(setup.ref, sessions.read(setup.ref)?.ref)
    }

    @Test
    fun `background restore keeps a live in-memory draft but opaque process restore fails closed`() {
        val setup = analyzing("background-review-photo", "background-review-request")
        val draft = CandidateReviewDraft(
            mainDishProposals = listOf(dish("dish-background", 1)),
            photoEvidence = listOf(component("component-background", "ingredient:greens")),
        )
        reducer.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(setup.requestId, draft))

        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        reducer.dispatch(PhotoAnalysisEvent.Restored)

        assertEquals(
            PhotoAnalysisState.CandidateReview(setup.ref, setup.requestId, draft),
            reducer.state,
        )

        val recreated = PhotoAnalysisReducer(leases, sessions, attempt)
        recreated.dispatch(
            PhotoAnalysisEvent.RestoreFromSnapshot(
                PhotoAnalysisBackgroundSnapshot.CandidateReview(setup.ref, setup.requestId),
            ),
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, setup.ref),
            recreated.state,
        )
    }

    private fun analyzing(photoId: String, request: String): Setup {
        val ref = leases.issue(attempt, photoId)
        val input = NormalizedPhotoInput(
            ref = ref,
            bytes = byteArrayOf(1),
            width = 1,
            height = 1,
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
            contentDigest = "a".repeat(64),
        )
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        val requestId = RequestId(request)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(requestId))
        return Setup(ref, requestId)
    }

    private fun dish(id: String, rank: Int): InferenceCandidate = InferenceCandidate(
        candidateId = id,
        label = id,
        rank = rank,
        confidence = Confidence(0.9, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.LOCAL,
            engineId = "fixture-engine",
            modelVersion = "fixture-model",
            calibrationVersion = "fixture-calibration",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    private fun component(id: String, candidateId: String): VisibleComponent = VisibleComponent(
        componentId = id,
        candidate = LocalizedCandidateIdentity(candidateId, candidateId, "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        mass = MassEvidence.Estimated(
            range = GramRange(100.0, 150.0),
            calibrationAssumptions = listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        provenance = InferenceV2Provenance(
            route = InferenceRoute.LOCAL,
            engineId = "fixture-engine",
            modelVersion = "fixture-model",
            taxonomyVersion = "fixture-taxonomy",
            calibrationVersion = "fixture-calibration",
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        ),
    )

    private data class Setup(val ref: PhotoRef, val requestId: RequestId)
}
