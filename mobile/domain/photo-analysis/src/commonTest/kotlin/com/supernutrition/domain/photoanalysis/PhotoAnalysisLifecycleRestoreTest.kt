package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Background, restore and process-recreation behaviour, split for focus. */
class PhotoAnalysisLifecycleRestoreTest {
    private val attempt = AttemptId("photo-analysis-attempt")
    private val leases = PhotoLeaseStore()
    private val sessions = PhotoSessionStore()
    private val reducer = PhotoAnalysisReducer(leases, sessions, attempt)
    private val normalizer = PhotoInputNormalizer(digest = { "a".repeat(64) })

    private fun normalized(ref: PhotoRef) = normalizer.normalize(
        RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat.NORMALIZED,
        ),
    )

    @Test
    fun `background and restore keeps a prepared photo when the lease is alive`() {
        val ref = leases.issue(attempt, "photo-background-ready")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))

        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisState.Backgrounded(
                PhotoAnalysisBackgroundSnapshot.Ready(ref),
            ),
            reducer.state,
        )

        reducer.dispatch(PhotoAnalysisEvent.Restored)

        assertEquals(PhotoAnalysisState.Ready(input), reducer.state)
        assertTrue(leases.isActive(ref))
        assertEquals(input, sessions.read(ref))
    }

    @Test
    fun `restore from analyzing preserves the in progress request`() {
        val ref = leases.issue(attempt, "photo-background-analysis")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("request-background")))

        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(PhotoAnalysisState.Analyzing(ref, RequestId("request-background")), reducer.state)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("request-background")))

        assertEquals(PhotoAnalysisState.CandidateReview(ref, RequestId("request-background")), reducer.state)
    }

    @Test
    fun `background snapshot covers every live state and is idempotent`() {
        val captureSnapshot = PhotoAnalysisReducer(
            PhotoLeaseStore(),
            PhotoSessionStore(),
            AttemptId("snapshot-capture"),
        ).snapshotForPersistence()
        assertEquals(PhotoAnalysisBackgroundSnapshot.Capture, captureSnapshot)

        val preparingRef = leases.issue(attempt, "snapshot-preparing")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(preparingRef))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.Preparing(preparingRef),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(PhotoAnalysisState.Preparing(preparingRef), reducer.state)

        val readyRef = leases.issue(attempt, "snapshot-ready")
        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(readyRef))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(readyRef)))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.Ready(readyRef),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("snapshot-request")))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("snapshot-request")))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.CandidateReview(readyRef, RequestId("snapshot-request")),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(
            PhotoAnalysisState.CandidateReview(readyRef, RequestId("snapshot-request")),
            reducer.state,
        )

        reducer.dispatch(PhotoAnalysisEvent.Cancel)
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CANCELLED, null),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reducer.state)
    }

    @Test
    fun `late analysis callback after cancellation cannot reopen a result`() {
        val ref = leases.issue(attempt, "cancelled-photo")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("cancelled-request")))
        reducer.dispatch(PhotoAnalysisEvent.Cancel)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("cancelled-request")))

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reducer.state)
        assertFalse(leases.isActive(ref))
        assertNull(sessions.read(ref))
    }

    @Test
    fun `revoking one lease does not clear another session`() {
        val first = leases.issue(attempt, "photo-revoke-one")
        val second = leases.issue(attempt, "photo-revoke-two")
        sessions.put(normalized(first))
        sessions.put(normalized(second))

        val reduction = reducer.reduce(
            PhotoAnalysisState.Ready(normalized(first)),
            PhotoAnalysisEvent.Cancel,
        )
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reduction.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(first)), reduction.effects)
        reduction.effects.forEach { effect ->
            if (effect is PhotoAnalysisEffect.RevokePhoto) {
                leases.revoke(effect.ref)
                sessions.remove(effect.ref)
            }
        }

        assertNull(sessions.read(first))
        assertEquals(normalized(second), sessions.read(second))
    }

    @Test
    fun `process recreation with an expired lease cannot resurrect photo bytes`() {
        val ref = leases.issue(attempt, "photo-expired-background")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        val snapshot = assertIs<PhotoAnalysisState.Backgrounded>(reducer.state).snapshot

        leases.revoke(ref)
        sessions.clearAttempt(attempt)
        val recreated = PhotoAnalysisReducer(
            leases = leases,
            sessions = sessions,
            attemptId = attempt,
        )
        recreated.dispatch(PhotoAnalysisEvent.RestoreFromSnapshot(snapshot))

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, ref),
            recreated.state,
        )
        assertEquals(null, sessions.read(ref))
    }

    @Test
    fun `recovery and candidate review support replacement cancellation and empty scan`() {
        val ref = leases.issue(attempt, "review-photo")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(ref)))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("review-request")))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("review-request")))
        val reviewReplacement = leases.issue(attempt, "review-replacement")
        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(reviewReplacement))
        assertEquals(PhotoAnalysisState.Preparing(reviewReplacement), reducer.state)
        reducer.dispatch(PhotoAnalysisEvent.Cancel)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reducer.state)

        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)

        val recoveryRef = leases.issue(attempt, "recovery-photo")
        val recoveryReducer = PhotoAnalysisReducer(leases, sessions, attempt)
        recoveryReducer.dispatch(
            PhotoAnalysisEvent.RestoreFromSnapshot(
                PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CAPTURE_FAILED, recoveryRef),
            ),
        )
        val replacement = leases.issue(attempt, "recovery-replacement")
        recoveryReducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(replacement))
        assertEquals(PhotoAnalysisState.Preparing(replacement), recoveryReducer.state)
        recoveryReducer.dispatch(PhotoAnalysisEvent.Cancel)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), recoveryReducer.state)
    }
}
