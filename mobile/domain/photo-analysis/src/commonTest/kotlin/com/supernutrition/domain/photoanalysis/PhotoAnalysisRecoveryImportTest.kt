package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * P1-6 — importing a photo from the Recovery screen.
 *
 * The Android host, on an import, dispatches PhotoCaptured and then
 * PreparationSucceeded. reduceRecovery accepted only ReplacePhoto and ScanAgain,
 * so both fell to `else -> unchanged`, and the feature sat in Recovery for ever:
 * the user picked a photo and the screen did not move. Nothing failed, which is
 * why it survived — a stuck state produces no error to notice.
 */
class PhotoAnalysisRecoveryImportTest {

    private val attempt = AttemptId("recovery-import-attempt")

    private class Fixture(attempt: AttemptId) {
        val leases = PhotoLeaseStore()
        private val sessions = PhotoSessionStore()
        val reducer = PhotoAnalysisReducer(leases, sessions, attempt)
    }

    private fun input(ref: PhotoRef) = NormalizedPhotoInput(
        ref = ref,
        bytes = byteArrayOf(1),
        width = 1,
        height = 1,
        format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
        contentDigest = "b".repeat(64),
    )

    @Test
    fun `importing from recovery moves to preparing`() {
        val fixture = Fixture(attempt)
        val recovery = PhotoAnalysisState.Recovery(RecoveryReason.POOR_IMAGE)
        val imported = fixture.leases.issue(attempt, "imported-photo")

        val reduction = fixture.reducer.reduce(recovery, PhotoAnalysisEvent.PhotoCaptured(imported))

        assertEquals(PhotoAnalysisState.Preparing(imported), reduction.state)
    }

    @Test
    fun `an import runs all the way through to ready`() {
        // The whole point: not just that Recovery accepts the event, but that the
        // flow completes. A transition that lands somewhere else that is also
        // stuck would be no better than the original.
        val fixture = Fixture(attempt)
        val imported = fixture.leases.issue(attempt, "imported-photo")

        // Reach Recovery the way the app does: a captured photo whose
        // preparation then fails. PreparationFailed straight from Capture stays
        // in Capture, so starting there would not have been the real state.
        val firstAttempt = fixture.leases.issue(attempt, "first-photo")
        fixture.reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(firstAttempt))
        fixture.reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
        assertIs<PhotoAnalysisState.Recovery>(fixture.reducer.state)

        fixture.reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(imported))
        fixture.reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input(imported)))

        val ready = assertIs<PhotoAnalysisState.Ready>(fixture.reducer.state)
        assertEquals(imported, ready.input.ref)
    }

    @Test
    fun `an import that fails preparation returns to recovery, not to a stuck state`() {
        val fixture = Fixture(attempt)
        val imported = fixture.leases.issue(attempt, "bad-import")

        val firstAttempt = fixture.leases.issue(attempt, "first-bad-photo")
        fixture.reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(firstAttempt))
        fixture.reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
        fixture.reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(imported))
        fixture.reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            fixture.reducer.state,
        )
    }

    @Test
    fun `a photo from another attempt is refused rather than accepted into recovery`() {
        // Recovery must not become a way in for a ref this attempt never issued.
        val fixture = Fixture(attempt)
        val foreignLeases = PhotoLeaseStore()
        val foreign = foreignLeases.issue(AttemptId("another-attempt"), "foreign-photo")
        val recovery = PhotoAnalysisState.Recovery(RecoveryReason.POOR_IMAGE)

        val reduction = fixture.reducer.reduce(recovery, PhotoAnalysisEvent.PhotoCaptured(foreign))

        assertEquals(recovery, reduction.state)
    }

    @Test
    fun `a revoked lease is refused rather than accepted into recovery`() {
        val fixture = Fixture(attempt)
        val revoked = fixture.leases.issue(attempt, "revoked-photo")
        fixture.leases.revoke(revoked)
        val recovery = PhotoAnalysisState.Recovery(RecoveryReason.POOR_IMAGE)

        val reduction = fixture.reducer.reduce(recovery, PhotoAnalysisEvent.PhotoCaptured(revoked))

        assertEquals(recovery, reduction.state)
    }
}
