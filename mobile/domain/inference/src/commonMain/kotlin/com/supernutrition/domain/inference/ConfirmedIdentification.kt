package com.supernutrition.domain.inference

/**
 * A candidate a user explicitly chose, carrying the provenance that produced it.
 *
 * Confirmation is where identification stops being a proposal, and it is also
 * where the route is easiest to lose. The surface showing a chosen food has no
 * use for "which engine answered", so a confirmation carrying only the candidate
 * id looks complete — and by the time a Grounded Nutrition Result has to say
 * where its identity came from, the engine that answered is gone. Keeping the
 * whole provenance rather than the id alone is what makes `local-text` survive
 * the boundary it would otherwise be flattened at.
 *
 * The correction UX over a proposed split — merge, remove, add — is E7.5. This
 * is only the carrier, and it deliberately cannot be produced without an
 * explicit call: no confidence level confirms an item as a side effect.
 */
data class ConfirmedIdentification(
    val candidateId: String,
    val label: String,
    /** The words this was chosen for. Null for a photo, which has no text to point at. */
    val sourceSpan: InferenceSourceSpan?,
    val provenance: InferenceProvenanceV2,
)

/**
 * Confirms one candidate of this group.
 *
 * Takes the group rather than a bare candidate id because an id cannot say which
 * words it was chosen for: a description naming the same food twice — "rye toast
 * and rye bread" — produces two groups whose candidate ids are identical, and
 * the span is what tells them apart. That is the same identity rule
 * `SegmentationReview` reached for its own edits.
 */
fun InferenceItemGroup.confirm(candidate: InferenceCandidateV2): ConfirmedIdentification {
    require(candidate in candidates) {
        "Cannot confirm a candidate that is not among group $groupId's proposals"
    }
    return ConfirmedIdentification(
        candidateId = candidate.candidateId,
        label = candidate.label,
        sourceSpan = sourceSpan,
        provenance = candidate.provenance,
    )
}
