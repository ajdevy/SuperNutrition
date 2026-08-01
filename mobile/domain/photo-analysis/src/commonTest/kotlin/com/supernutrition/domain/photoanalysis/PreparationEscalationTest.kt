package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals

class PreparationEscalationTest {
    @Test
    fun `an isolated failure is still about the photo`() {
        assertEquals(RecoveryReason.POOR_IMAGE, PreparationEscalation.reasonFor(RecoveryReason.POOR_IMAGE, 1))
        assertEquals(RecoveryReason.POOR_IMAGE, PreparationEscalation.reasonFor(RecoveryReason.POOR_IMAGE, 2))
    }

    @Test
    fun `a run of failures stops being about the photo`() {
        // Three bad images in a row is not three bad images. Repeating "poor
        // image" at that point blames the user for the pipeline's problem.
        assertEquals(
            RecoveryReason.CAPTURE_FAILED,
            PreparationEscalation.reasonFor(RecoveryReason.POOR_IMAGE, PreparationEscalation.ESCALATE_AFTER),
        )
        assertEquals(RecoveryReason.CAPTURE_FAILED, PreparationEscalation.reasonFor(RecoveryReason.POOR_IMAGE, 9))
    }

    @Test
    fun `a reason that already describes the pipeline is not relabelled`() {
        // Only POOR_IMAGE points at the photo, so only POOR_IMAGE escalates.
        // Rewriting the others would lose what the host actually observed.
        for (failures in 1..9) {
            assertEquals(
                RecoveryReason.CAPTURE_FAILED,
                PreparationEscalation.reasonFor(RecoveryReason.CAPTURE_FAILED, failures),
            )
            assertEquals(
                RecoveryReason.CAMERA_UNAVAILABLE,
                PreparationEscalation.reasonFor(RecoveryReason.CAMERA_UNAVAILABLE, failures),
            )
        }
    }

    @Test
    fun `every escalation still reaches a recoverable state`() {
        // The point of escalating is to change what the user is told, never to
        // end the journey. Both sides of the threshold must stay recoverable.
        val reducer = PhotoAnalysisReducer(PhotoLeaseStore(), PhotoSessionStore(), AttemptId("attempt-escalation"))
        for (failures in listOf(1, PreparationEscalation.ESCALATE_AFTER)) {
            val reason = PreparationEscalation.reasonFor(RecoveryReason.POOR_IMAGE, failures)
            reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(reason))
            reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        }
    }
}
