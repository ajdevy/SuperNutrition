package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoAnalysisAnalysisTransitionsBranchTest {
    @Test
    fun `unrelated event is unchanged while analysis is active`() {
        val fixture = Fixture()
        val current = fixture.analyzing("unrelated-photo", "unrelated-request")
        val unrelated = PhotoAnalysisEvent.PhotoCaptured(
            fixture.leases.issue(fixture.attempt, "unrelated-event-photo"),
        )

        val reduction = fixture.reducer.reduce(current, unrelated)

        assertEquals(current, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `unavailable evidence from another request is ignored`() {
        val fixture = Fixture()
        val current = fixture.analyzing("stale-unavailable-photo", "active-request")

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReviewEvidenceUnavailable(
                RequestId("stale-request"),
                RecoveryReason.NO_FOOD,
            ),
        )

        assertEquals(current, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `no-food recovery retains the photo until scan again is requested`() {
        val fixture = Fixture()
        val current = fixture.analyzing("no-food-photo", "no-food-request")

        val recovery = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReviewEvidenceUnavailable(
                current.requestId,
                RecoveryReason.NO_FOOD,
            ),
        )

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.NO_FOOD, current.ref), recovery.state)
        assertTrue(recovery.effects.isEmpty())

        val scanAgain = fixture.reducer.reduce(recovery.state, PhotoAnalysisEvent.ScanAgain)

        assertEquals(PhotoAnalysisState.Capture, scanAgain.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), scanAgain.effects)
    }

    @Test
    fun `empty completed review is retained no-food recovery instead of a dead end`() {
        val fixture = Fixture()
        val current = fixture.analyzing("empty-completed-review", "empty-completed-request")

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReviewEvidenceAvailable(
                current.requestId,
                CandidateReviewDraft(emptyList(), emptyList()),
            ),
        )

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.NO_FOOD, current.ref), reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `unavailable evidence fails closed when the photo lease expired`() {
        val fixture = Fixture()
        val current = fixture.analyzing("expired-unavailable-photo", "expired-request")
        fixture.leases.revoke(current.ref)

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReviewEvidenceUnavailable(
                current.requestId,
                RecoveryReason.OFFLINE,
            ),
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            reduction.state,
        )
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), reduction.effects)
    }

    @Test
    fun `legacy completion from another request cannot enter review`() {
        val fixture = Fixture()
        val current = fixture.analyzing("stale-completion-photo", "active-request")

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.AnalysisSucceeded(RequestId("stale-request")),
        )

        assertEquals(current, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `legacy completion fails closed when the photo lease expired`() {
        val fixture = Fixture()
        val current = fixture.analyzing("expired-completion-photo", "expired-request")
        fixture.leases.revoke(current.ref)

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.AnalysisSucceeded(current.requestId),
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            reduction.state,
        )
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), reduction.effects)
    }

    @Test
    fun `live replacement moves back to preparation and revokes the old photo`() {
        val fixture = Fixture()
        val current = fixture.analyzing("replace-old-photo", "replace-request")
        val replacement = fixture.leases.issue(fixture.attempt, "replace-new-photo")

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReplacePhoto(replacement),
        )

        assertEquals(PhotoAnalysisState.Preparing(replacement), reduction.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), reduction.effects)
    }

    @Test
    fun `foreign inactive and same-photo replacements are ignored`() {
        val fixture = Fixture()
        val current = fixture.analyzing("replace-current-photo", "replace-request")
        val inactive = fixture.leases.issue(fixture.attempt, "replace-inactive-photo")
        fixture.leases.revoke(inactive)
        val foreign = PhotoRef.issue(AttemptId("foreign-attempt"), "foreign-photo")

        assertUnchanged(fixture, current, PhotoAnalysisEvent.ReplacePhoto(foreign))
        assertUnchanged(fixture, current, PhotoAnalysisEvent.ReplacePhoto(inactive))
        assertUnchanged(fixture, current, PhotoAnalysisEvent.ReplacePhoto(current.ref))
    }

    @Test
    fun `cancellation recovers and revokes the active photo`() {
        val fixture = Fixture()
        val current = fixture.analyzing("cancel-photo", "cancel-request")

        val reduction = fixture.reducer.reduce(current, PhotoAnalysisEvent.Cancel)

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED),
            reduction.state,
        )
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), reduction.effects)
    }

    @Test
    fun `scan again returns active analysis to capture and revokes the photo`() {
        val fixture = Fixture()
        val current = fixture.analyzing("scan-again-photo", "scan-again-request")

        val reduction = fixture.reducer.reduce(current, PhotoAnalysisEvent.ScanAgain)

        assertEquals(PhotoAnalysisState.Capture, reduction.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), reduction.effects)
    }

    @Test
    fun `grid prompt returns a live analysis to ready without revoking the photo`() {
        val fixture = Fixture()
        val current = fixture.analyzing("grid-photo", "grid-request")

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.GridCellSelected(7),
        )

        assertEquals(PhotoAnalysisState.Ready(fixture.inputFor(current.ref)), reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `grid prompt can restart a completed review`() {
        val fixture = Fixture()
        val current = fixture.analyzing("grid-review-photo", "grid-review-request")
        val review = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReviewEvidenceAvailable(
                current.requestId,
                fixture.reviewDraft(),
            ),
        ).state

        val reduction = fixture.reducer.reduce(
            review,
            PhotoAnalysisEvent.GridCellSelected(0),
        )

        assertEquals(PhotoAnalysisState.Ready(fixture.inputFor(current.ref)), reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `grid prompt fails closed when the retained photo is no longer live`() {
        val fixture = Fixture()
        val current = fixture.analyzing("grid-expired-photo", "grid-expired-request")
        fixture.leases.revoke(current.ref)

        val reduction = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.GridCellSelected(8),
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, current.ref),
            reduction.state,
        )
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), reduction.effects)
    }

    @Test
    fun `grid prompt is a no-op before a ready request is started`() {
        val fixture = Fixture()
        val current = fixture.analyzing("grid-ready-photo", "grid-ready-request")
        val ready = PhotoAnalysisState.Ready(fixture.inputFor(current.ref))

        val reduction = fixture.reducer.reduce(ready, PhotoAnalysisEvent.GridCellSelected(1))

        assertEquals(ready, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `candidate review replace scan and cancel each preserve the correct cleanup decision`() {
        val fixture = Fixture()
        val review = fixture.candidateReview("candidate-controls-photo", "candidate-controls-request")
        val replacement = fixture.leases.issue(fixture.attempt, "candidate-controls-replacement")

        val replace = fixture.reducer.reduce(review, PhotoAnalysisEvent.ReplacePhoto(replacement))
        val scanAgain = fixture.reducer.reduce(review, PhotoAnalysisEvent.ScanAgain)
        val cancel = fixture.reducer.reduce(review, PhotoAnalysisEvent.Cancel)

        assertEquals(PhotoAnalysisState.Preparing(replacement), replace.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(review.ref)), replace.effects)
        assertEquals(PhotoAnalysisState.Capture, scanAgain.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(review.ref)), scanAgain.effects)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), cancel.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(review.ref)), cancel.effects)
    }

    @Test
    fun `candidate grid selection fails closed when no normalized input was retained`() {
        val fixture = Fixture()
        val review = fixture.candidateReviewWithoutInput("candidate-missing-input", "candidate-missing-request")

        val reduction = fixture.reducer.reduce(review, PhotoAnalysisEvent.GridCellSelected(0))

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, review.ref),
            reduction.state,
        )
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(review.ref)), reduction.effects)
    }

    @Test
    fun `ready replace and cancel use their own cleanup transitions`() {
        val fixture = Fixture()
        val ready = fixture.ready("ready-controls-photo")
        val replacement = fixture.leases.issue(fixture.attempt, "ready-controls-replacement")

        val replace = fixture.reducer.reduce(ready, PhotoAnalysisEvent.ReplacePhoto(replacement))
        val cancel = fixture.reducer.reduce(ready, PhotoAnalysisEvent.Cancel)

        assertEquals(PhotoAnalysisState.Preparing(replacement), replace.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(ready.input.ref)), replace.effects)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), cancel.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(ready.input.ref)), cancel.effects)
    }

    @Test
    fun `analysis failure releases a live photo while no photo recovery stays explicit`() {
        val fixture = Fixture()
        val current = fixture.analyzing("failed-analysis-photo", "failed-analysis-request")

        val failed = fixture.reducer.reduce(
            current,
            PhotoAnalysisEvent.ReviewEvidenceUnavailable(
                current.requestId,
                RecoveryReason.ANALYSIS_FAILED,
            ),
        )
        val emptyRecovery = fixture.reducer.reduce(
            PhotoAnalysisState.Recovery(RecoveryReason.CAMERA_UNAVAILABLE),
            PhotoAnalysisEvent.ScanAgain,
        )

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.ANALYSIS_FAILED), failed.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(current.ref)), failed.effects)
        assertEquals(PhotoAnalysisState.Capture, emptyRecovery.state)
        assertTrue(emptyRecovery.effects.isEmpty())
    }

    @Test
    fun `process restore keeps no-photo recovery and rejects a ready snapshot without input`() {
        val fixture = Fixture()
        val missingInputRef = fixture.leases.issue(fixture.attempt, "restore-missing-input")

        val noPhotoRecovery = fixture.reducer.reduce(
            PhotoAnalysisState.Capture,
            PhotoAnalysisEvent.RestoreFromSnapshot(
                PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CAMERA_UNAVAILABLE, null),
            ),
        )
        val missingInput = fixture.reducer.reduce(
            PhotoAnalysisState.Capture,
            PhotoAnalysisEvent.RestoreFromSnapshot(PhotoAnalysisBackgroundSnapshot.Ready(missingInputRef)),
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAMERA_UNAVAILABLE),
            noPhotoRecovery.state,
        )
        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, missingInputRef),
            missingInput.state,
        )
    }

    private fun assertUnchanged(fixture: Fixture, current: PhotoAnalysisState.Analyzing, event: PhotoAnalysisEvent) {
        val reduction = fixture.reducer.reduce(current, event)
        assertEquals(current, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    private class Fixture {
        val attempt = AttemptId("transition-attempt")
        val leases = PhotoLeaseStore()
        private val sessions = PhotoSessionStore()
        val reducer = PhotoAnalysisReducer(leases, sessions, attempt)

        fun inputFor(ref: PhotoRef): NormalizedPhotoInput = sessions.read(ref)!!

        fun ready(photoId: String): PhotoAnalysisState.Ready {
            val ref = leases.issue(attempt, photoId)
            val input = input(ref)
            reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
            reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
            return PhotoAnalysisState.Ready(input)
        }

        fun analyzing(photoId: String, request: String): PhotoAnalysisState.Analyzing {
            val ref = leases.issue(attempt, photoId)
            val input = input(ref)
            reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
            reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
            val requestId = RequestId(request)
            reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(requestId))
            return PhotoAnalysisState.Analyzing(ref, requestId)
        }

        fun candidateReview(photoId: String, request: String): PhotoAnalysisState.CandidateReview {
            val current = analyzing(photoId, request)
            return PhotoAnalysisState.CandidateReview(
                current.ref,
                current.requestId,
                CandidateReviewDraft(emptyList(), emptyList()),
            )
        }

        fun candidateReviewWithoutInput(photoId: String, request: String): PhotoAnalysisState.CandidateReview {
            val ref = leases.issue(attempt, photoId)
            return PhotoAnalysisState.CandidateReview(
                ref,
                RequestId(request),
                CandidateReviewDraft(emptyList(), emptyList()),
            )
        }

        fun reviewDraft(): CandidateReviewDraft = CandidateReviewDraft(
            mainDishProposals = listOf(
                InferenceCandidate(
                    candidateId = "dish-review",
                    label = "Review dish",
                    rank = 1,
                    confidence = Confidence(0.9, ConfidenceBand.HIGH),
                    unresolvedAssumptions = emptyList(),
                    provenance = InferenceProvenance(
                        route = InferenceRoute.LOCAL,
                        engineId = "fixture-engine",
                        modelVersion = "fixture-model",
                        calibrationVersion = "fixture-calibration",
                        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                    ),
                ),
            ),
            photoEvidence = emptyList(),
        )

        private fun input(ref: PhotoRef): NormalizedPhotoInput = NormalizedPhotoInput(
            ref = ref,
            bytes = byteArrayOf(1),
            width = 1,
            height = 1,
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
            contentDigest = "b".repeat(64),
        )
    }
}
