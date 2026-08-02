package com.supernutrition.domain.photoanalysis

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

        fun analyzing(photoId: String, request: String): PhotoAnalysisState.Analyzing {
            val ref = leases.issue(attempt, photoId)
            val input = NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 1,
                height = 1,
                orientation = ImageOrientation.UP,
                colorSpace = "sRGB",
                metadataKeys = emptySet(),
                contentDigest = "b".repeat(64),
            )
            reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
            reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
            val requestId = RequestId(request)
            reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(requestId))
            return PhotoAnalysisState.Analyzing(ref, requestId)
        }
    }
}
