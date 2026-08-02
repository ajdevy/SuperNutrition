package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The v2 telemetry surface, asserted against the contract's own canonical
 * fixture.
 *
 * The binding had no v2 telemetry type at all, so a `local-text` result could
 * only be recorded through v1's event — whose route enum has no `local-text` and
 * whose initializer rejects a v2 contract version. The route the contract added
 * was representable in the result and lost at the telemetry boundary.
 */
class InferenceTelemetryV2Test {
    private val canonical =
        "{\"contractVersion\":\"inference-engine/v2\",\"modality\":\"text\",\"eventName\":\"inference.attem" +
            "pt.completed\",\"route\":\"local-text\",\"capabilityTier\":\"ready\",\"durationBucket\":\"under" +
            "-2s\",\"resourceBucket\":\"low\",\"terminalOutcome\":\"candidates\",\"itemGroupCountBucket\":\"" +
            "2-3\",\"engineId\":\"fixture-engine\",\"modelVersion\":\"fixture-model-1\"}"

    private fun provenance(route: InferenceRouteV2) = InferenceProvenanceV2(
        route = route,
        engineId = "fixture-engine",
        modelVersion = "fixture-model-1",
        calibrationVersion = "fixture-calibration-1",
        contractVersion = INFERENCE_CONTRACT_V2,
    )

    private fun group(id: String, span: InferenceSourceSpan?, route: InferenceRouteV2) = InferenceItemGroup(
        groupId = id,
        sourceSpan = span,
        candidates = listOf(
            InferenceCandidateV2(
                candidateId = "food:egg-boiled",
                label = "Egg, boiled",
                rank = 1,
                confidence = Confidence(score = 0.91, band = ConfidenceBand.HIGH),
                unresolvedAssumptions = emptyList(),
                provenance = provenance(route),
            ),
        ),
    )

    private fun textResult(vararg groups: InferenceItemGroup) = InferenceResultV2(
        contractVersion = INFERENCE_CONTRACT_V2,
        modality = InferenceModality.TEXT,
        outcome = InferenceOutcomeV2.Candidates(groups.toList()),
    )

    @Test
    fun `the canonical fixture round-trips byte for byte`() {
        val event = InferenceContractV2Json.decodeTelemetry(canonical)

        assertEquals(InferenceRouteV2.LOCAL_TEXT, event.route)
        assertEquals(InferenceModality.TEXT, event.modality)
        assertEquals(TelemetryEventNameV2.ATTEMPT_COMPLETED, event.eventName)
        assertEquals(TelemetryItemGroupCountBucket.TWO_TO_THREE, event.itemGroupCountBucket)
        assertEquals(canonical, InferenceContractV2Json.encodeTelemetry(event))
    }

    @Test
    fun `a text result reports local-text, distinct from both local and remote`() {
        val event = InferenceTelemetryEventV2.forAttempt(
            result = textResult(
                group("item-1", InferenceSourceSpan(0, 15), InferenceRouteV2.LOCAL_TEXT),
                group("item-2", InferenceSourceSpan(20, 40), InferenceRouteV2.LOCAL_TEXT),
            ),
            capabilityTier = CapabilityState.READY,
            durationBucket = TelemetryDurationBucketV2.UNDER_2S,
            resourceBucket = TelemetryResourceBucketV2.LOW,
        )

        assertEquals(InferenceRouteV2.LOCAL_TEXT, event.route)
        assertTrue(event.route != InferenceRouteV2.LOCAL && event.route != InferenceRouteV2.REMOTE)
        assertEquals(canonical, InferenceContractV2Json.encodeTelemetry(event))
    }

    @Test
    fun `the encoded event carries neither the description nor the spans it derived from`() {
        // The property the contract states as `loggable = false` and
        // `telemetry_eligible = false`. Asserted on the encoded bytes rather
        // than on the type, because what reaches a log is the bytes: a field
        // added later would pass a type-level check and fail this one.
        val spans = listOf(InferenceSourceSpan(0, 15), InferenceSourceSpan(20, 40))
        val event = InferenceTelemetryEventV2.forAttempt(
            result = textResult(
                group("item-1", spans[0], InferenceRouteV2.LOCAL_TEXT),
                group("item-2", spans[1], InferenceRouteV2.LOCAL_TEXT),
            ),
            capabilityTier = CapabilityState.READY,
            durationBucket = TelemetryDurationBucketV2.UNDER_2S,
            resourceBucket = TelemetryResourceBucketV2.LOW,
        )

        val encoded = InferenceContractV2Json.encodeTelemetry(event)
        assertTrue("sourceSpan" !in encoded, "spans index the user's own words and must not be logged")
        assertTrue("description" !in encoded, "the description is not telemetry eligible")
        assertTrue("food:egg-boiled" !in encoded, "what a person ate is not an operational measurement")
        assertTrue("Egg, boiled" !in encoded, "nor is the label shown for it")
    }

    @Test
    fun `an abstention reports the reason bucket without the description that caused it`() {
        val result = InferenceResultV2(
            contractVersion = INFERENCE_CONTRACT_V2,
            modality = InferenceModality.TEXT,
            outcome = InferenceOutcomeV2.Abstention(
                reason = AbstentionReasonV2.DESCRIPTION_UNSEGMENTABLE,
                userMessageKey = "inference.abstention.description_unsegmentable",
                provenance = provenance(InferenceRouteV2.LOCAL_TEXT),
            ),
        )

        val event = InferenceTelemetryEventV2.forAttempt(
            result = result,
            capabilityTier = CapabilityState.READY,
            durationBucket = TelemetryDurationBucketV2.UNDER_2S,
            resourceBucket = TelemetryResourceBucketV2.LOW,
        )

        assertEquals(InferenceTelemetryOutcome.ABSTENTION, event.terminalOutcome)
        assertEquals(TelemetryItemGroupCountBucket.NONE, event.itemGroupCountBucket)
        assertEquals(InferenceRouteV2.LOCAL_TEXT, event.route)
    }

    @Test
    fun `a failure reports the route of the engine that failed`() {
        // A failure is where the route matters most operationally: "the text
        // engine is failing" and "the photo engine is failing" are different
        // problems, and a flattened route makes them one line in a dashboard.
        val result = InferenceResultV2(
            contractVersion = INFERENCE_CONTRACT_V2,
            modality = InferenceModality.TEXT,
            outcome = InferenceOutcomeV2.Failure(
                code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                retryable = true,
                userMessageKey = "inference.failure.temporary_unavailable",
                provenance = provenance(InferenceRouteV2.LOCAL_TEXT),
            ),
        )

        val event = InferenceTelemetryEventV2.forAttempt(
            result = result,
            capabilityTier = CapabilityState.TEMPORARILY_UNAVAILABLE,
            durationBucket = TelemetryDurationBucketV2.OVER_5S,
            resourceBucket = TelemetryResourceBucketV2.HIGH,
        )

        assertEquals(InferenceTelemetryOutcome.FAILURE, event.terminalOutcome)
        assertEquals(TelemetryItemGroupCountBucket.NONE, event.itemGroupCountBucket)
        assertEquals(InferenceRouteV2.LOCAL_TEXT, event.route)
        assertEquals(CapabilityState.TEMPORARILY_UNAVAILABLE, event.capabilityTier)
    }

    @Test
    fun `a text attempt cannot be recorded as local`() {
        // The flattening this whole route exists to prevent: reported as
        // `local`, an on-device text attempt is indistinguishable in a log from
        // an on-device photo attempt.
        val failure = assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV2(
                contractVersion = INFERENCE_CONTRACT_V2,
                modality = InferenceModality.TEXT,
                eventName = TelemetryEventNameV2.ATTEMPT_COMPLETED,
                route = InferenceRouteV2.LOCAL,
                capabilityTier = CapabilityState.READY,
                durationBucket = TelemetryDurationBucketV2.UNDER_2S,
                resourceBucket = TelemetryResourceBucketV2.LOW,
                terminalOutcome = InferenceTelemetryOutcome.CANDIDATES,
                itemGroupCountBucket = TelemetryItemGroupCountBucket.ONE,
                engineId = "fixture-engine",
                modelVersion = "fixture-model-1",
            )
        }

        assertTrue("flattening" in failure.message.orEmpty())
    }

    @Test
    fun `a photo attempt cannot be recorded as local-text`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV2(
                contractVersion = INFERENCE_CONTRACT_V2,
                modality = InferenceModality.PHOTO,
                eventName = TelemetryEventNameV2.ATTEMPT_COMPLETED,
                route = InferenceRouteV2.LOCAL_TEXT,
                capabilityTier = CapabilityState.READY,
                durationBucket = TelemetryDurationBucketV2.UNDER_2S,
                resourceBucket = TelemetryResourceBucketV2.LOW,
                terminalOutcome = InferenceTelemetryOutcome.CANDIDATES,
                itemGroupCountBucket = TelemetryItemGroupCountBucket.ONE,
                engineId = "fixture-engine",
                modelVersion = "fixture-model-1",
            )
        }
    }

    @Test
    fun `a v1 contract version is rejected rather than silently accepted`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceContractV2Json.decodeTelemetry(
                canonical.replace(INFERENCE_CONTRACT_V2, "photo-inference-engine/v1"),
            )
        }
    }

    @Test
    fun `a result assembled from two engines needs one event per route`() {
        // Reporting the first candidate's route would attribute every candidate
        // to whichever engine happened to rank first, which is a plausible and
        // wrong operational answer rather than a missing one.
        val failure = assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV2.forAttempt(
                result = textResult(
                    group("item-1", InferenceSourceSpan(0, 15), InferenceRouteV2.LOCAL_TEXT),
                    group("item-2", InferenceSourceSpan(20, 40), InferenceRouteV2.REMOTE),
                ),
                capabilityTier = CapabilityState.READY,
                durationBucket = TelemetryDurationBucketV2.UNDER_2S,
                resourceBucket = TelemetryResourceBucketV2.LOW,
            )
        }

        assertTrue("one event per route" in failure.message.orEmpty())
    }

    @Test
    fun `the item group bucket reports shape rather than an exact count`() {
        assertEquals(TelemetryItemGroupCountBucket.NONE, TelemetryItemGroupCountBucket.of(0))
        assertEquals(TelemetryItemGroupCountBucket.ONE, TelemetryItemGroupCountBucket.of(1))
        assertEquals(TelemetryItemGroupCountBucket.TWO_TO_THREE, TelemetryItemGroupCountBucket.of(3))
        assertEquals(TelemetryItemGroupCountBucket.FOUR_PLUS, TelemetryItemGroupCountBucket.of(7))
    }
}
