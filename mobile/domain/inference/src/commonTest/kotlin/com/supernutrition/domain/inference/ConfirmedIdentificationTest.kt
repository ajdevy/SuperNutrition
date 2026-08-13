package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The other half of "the route is preserved": telemetry records it, and
 * confirmation carries it forward to whatever calculates nutrition from it.
 */
class ConfirmedIdentificationTest {
    private fun candidate(id: String, route: InferenceRouteV3) = InferenceCandidateV3(
        candidateId = id,
        label = "Egg, boiled",
        rank = 1,
        confidence = Confidence(score = 0.91, band = ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenanceV3(
            route = route,
            engineId = "fixture-engine",
            modelVersion = "fixture-model-1",
            taxonomyVersion = "fixture-taxonomy-1",
            calibrationVersion = "fixture-calibration-1",
            contractVersion = INFERENCE_CONTRACT_V3,
        ),
    )

    @Test
    fun `confirming a text candidate keeps local-text and the words it came from`() {
        val group = InferenceItemGroup(
            groupId = "item-1",
            sourceSpan = InferenceSourceSpan(0, 15),
            candidates = listOf(candidate("food:egg-boiled", InferenceRouteV3.LOCAL_TEXT)),
        )

        val confirmed = group.confirm(group.candidates.single())

        assertEquals(InferenceRouteV3.LOCAL_TEXT, confirmed.provenance.route)
        assertEquals(InferenceSourceSpan(0, 15), confirmed.sourceSpan)
        assertEquals("food:egg-boiled", confirmed.candidateId)
    }

    @Test
    fun `confirming a photo candidate keeps local and carries no span`() {
        val group = InferenceItemGroup(
            groupId = "item-1",
            candidates = listOf(candidate("food:banana", InferenceRouteV3.LOCAL)),
        )

        val confirmed = group.confirm(group.candidates.single())

        assertEquals(InferenceRouteV3.LOCAL, confirmed.provenance.route)
        assertNull(confirmed.sourceSpan)
    }

    @Test
    fun `a candidate from another group cannot be confirmed into this one`() {
        // The two groups of "rye toast and rye bread" carry the same candidate
        // id, so an id-keyed confirmation would accept either for either. The
        // span is the identity, which means the group has to be the one asked.
        val first = InferenceItemGroup(
            groupId = "item-1",
            sourceSpan = InferenceSourceSpan(0, 9),
            candidates = listOf(candidate("food:bread-rye", InferenceRouteV3.LOCAL_TEXT)),
        )
        val second = InferenceItemGroup(
            groupId = "item-2",
            sourceSpan = InferenceSourceSpan(14, 23),
            candidates = listOf(candidate("food:bread-rye", InferenceRouteV3.REMOTE)),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            first.confirm(second.candidates.single())
        }

        assertEquals(true, failure.message.orEmpty().contains("item-1"))
    }
}
