package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P2 — a capture that fails before a photo exists left the user with nothing.
 *
 * reduceCapture answered PreparationFailed with `unchanged(Capture)`, so a
 * camera that could not open, or a preparation that failed before the photo was
 * registered, returned the user to the capture screen with no explanation and no
 * recovery offered. The reason was carried on the event and thrown away.
 *
 * PermissionDenied already produced a Recovery. Every other capture-time failure
 * did not, which made the one case anybody tested the only case that worked.
 */
class PhotoAnalysisCaptureFailureTest {

    private val attempt = AttemptId("capture-failure-attempt")

    private fun reducer() = PhotoAnalysisReducer(PhotoLeaseStore(), PhotoSessionStore(), attempt)

    @Test
    fun `a camera failure during capture offers recovery rather than silence`() {
        val reduction = reducer().reduce(
            PhotoAnalysisState.Capture,
            PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE),
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAMERA_UNAVAILABLE),
            reduction.state,
        )
    }

    @Test
    fun `the reason on the event is the reason the user is shown`() {
        // It was being discarded. A recovery screen that cannot say which thing
        // failed can only offer the same generic retry for every cause.
        for (reason in listOf(
            RecoveryReason.CAPTURE_FAILED,
            RecoveryReason.POOR_IMAGE,
            RecoveryReason.CAMERA_UNAVAILABLE,
        )) {
            val reduction = reducer().reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.PreparationFailed(reason),
            )
            assertEquals(PhotoAnalysisState.Recovery(reason), reduction.state, reason.name)
        }
    }

    @Test
    fun `a denied permission still reaches its own recovery`() {
        // The one case that already worked must keep working.
        val reduction = reducer().reduce(
            PhotoAnalysisState.Capture,
            PhotoAnalysisEvent.PermissionDenied,
        )

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED),
            reduction.state,
        )
    }

    @Test
    fun `an unrelated event still leaves capture alone`() {
        // The else branch must not become a recovery too: an event Capture does
        // not understand is not a failure to report to the user.
        val reduction = reducer().reduce(PhotoAnalysisState.Capture, PhotoAnalysisEvent.ScanAgain)

        assertEquals(PhotoAnalysisState.Capture, reduction.state)
    }
}
