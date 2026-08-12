package com.supernutrition.domain.photoanalysis

/**
 * When a run of preparation failures stops being about the photo.
 *
 * A single failure is usually the image: bad light, a thumb over the lens, a
 * frame the decoder could not read. Telling the user to try again is the right
 * answer and costs them one tap. The same failure three times running is not
 * the image, and repeating "poor image" at that point blames the user for
 * something the pipeline is doing.
 *
 * So nothing here ends the journey — every reason it returns still reaches
 * Recovery, which offers Scan Again and Import. What changes is what the user
 * is told, and only after the evidence supports saying it.
 *
 * This lives in the domain rather than in the Android host because it is a
 * product rule about when to stop blaming the photo, not a detail of how one
 * platform catches exceptions. Both hosts should reach the same conclusion from
 * the same streak.
 */
object PreparationEscalation {
    /**
     * Failures in a row before the reason escalates. Three rather than two: a
     * second failure is still plausibly the same bad lighting the first was.
     */
    const val ESCALATE_AFTER: Int = 3

    /**
     * The reason to report, given how many preparation attempts have now failed
     * in a row. [consecutiveFailures] counts this failure, so the first failure
     * arrives as 1.
     *
     * Only [RecoveryReason.POOR_IMAGE] escalates, because it is the only reason
     * here that points at the photo. A reason that already describes the
     * pipeline is left alone rather than relabelled.
     */
    fun reasonFor(reason: RecoveryReason, consecutiveFailures: Int): RecoveryReason =
        if (reason == RecoveryReason.POOR_IMAGE && consecutiveFailures >= ESCALATE_AFTER) {
            RecoveryReason.CAPTURE_FAILED
        } else {
            reason
        }
}
