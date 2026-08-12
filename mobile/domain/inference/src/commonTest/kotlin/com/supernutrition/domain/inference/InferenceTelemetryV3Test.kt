@file:Suppress("MaxLineLength", "MaximumLineLength")

package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InferenceTelemetryV3Test {
    private val canonical =
        "{\"contractVersion\":\"inference-evidence/v3\",\"modality\":\"text\",\"eventName\":\"inference.attempt.completed\"," +
            "\"route\":\"local-text\",\"capabilityTier\":\"ready\",\"durationBucket\":\"under-2s\"," +
            "\"resourceBucket\":\"low\",\"terminalOutcome\":\"candidates\",\"itemGroupCountBucket\":\"2-3\"," +
            "\"componentCountBucket\":\"zero\",\"engineId\":\"fixture-text-engine\",\"modelVersion\":" +
            "\"fixture-text-model-1\",\"taxonomyVersion\":\"fixture-taxonomy-1\",\"calibrationVersion\":" +
            "\"fixture-text-calibration-1\"}"

    private fun provenance(route: InferenceRouteV3) = InferenceProvenanceV3(
        route = route,
        engineId = "fixture-engine",
        modelVersion = "fixture-model-1",
        taxonomyVersion = "fixture-taxonomy-1",
        calibrationVersion = "fixture-calibration-1",
        contractVersion = INFERENCE_CONTRACT_V3,
    )

    private fun textResult() = InferenceResultV3(
        contractVersion = INFERENCE_CONTRACT_V3,
        modality = InferenceModality.TEXT,
        outcome = InferenceOutcomeV3.Candidates(
            listOf(
                InferenceItemGroup(
                    groupId = "item-1",
                    sourceSpan = InferenceSourceSpan(0, 15),
                    candidates = listOf(
                        InferenceCandidateV3(
                            candidateId = "food:egg-boiled",
                            label = "Egg, boiled",
                            rank = 1,
                            confidence = Confidence(0.91, ConfidenceBand.HIGH),
                            unresolvedAssumptions = emptyList(),
                            provenance = provenance(InferenceRouteV3.LOCAL_TEXT),
                        ),
                    ),
                ),
                InferenceItemGroup(
                    groupId = "item-2",
                    sourceSpan = InferenceSourceSpan(20, 40),
                    candidates = listOf(
                        InferenceCandidateV3(
                            candidateId = "food:rye-bread",
                            label = "Bread, rye",
                            rank = 1,
                            confidence = Confidence(0.87, ConfidenceBand.HIGH),
                            unresolvedAssumptions = emptyList(),
                            provenance = provenance(InferenceRouteV3.LOCAL_TEXT),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun telemetry_round_trips_and_keeps_text_route_distinct() {
        val event = InferenceContractV3Json.decodeTelemetry(canonical)

        assertEquals(InferenceRouteV3.LOCAL_TEXT, event.route)
        assertEquals(InferenceV3TelemetryOutcome.CANDIDATES, event.terminalOutcome)
        assertEquals(canonical, InferenceContractV3Json.encodeTelemetry(event))
    }

    @Test
    fun text_telemetry_contains_no_description_labels_or_spans() {
        val event = InferenceTelemetryEventV3.forAttempt(
            result = textResult(),
            capabilityTier = CapabilityState.READY,
            durationBucket = TelemetryDurationBucketV3.UNDER_2S,
            resourceBucket = TelemetryResourceBucketV3.LOW,
        )
        val encoded = InferenceContractV3Json.encodeTelemetry(event)

        assertTrue("description" !in encoded)
        assertTrue("sourceSpan" !in encoded)
        assertTrue("Egg, boiled" !in encoded)
    }

    @Test
    fun photo_routes_cannot_be_local_text_and_text_routes_cannot_be_local() {
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = InferenceModality.PHOTO,
                eventName = TelemetryEventNameV3.ATTEMPT_COMPLETED,
                route = InferenceRouteV3.LOCAL_TEXT,
                capabilityTier = CapabilityState.READY,
                durationBucket = TelemetryDurationBucketV3.UNDER_2S,
                resourceBucket = TelemetryResourceBucketV3.LOW,
                terminalOutcome = InferenceV3TelemetryOutcome.COMPONENTS,
                itemGroupCountBucket = TelemetryItemGroupCountBucket.NONE,
                componentCountBucket = TelemetryComponentCountBucket.ONE,
                engineId = "engine",
                modelVersion = "model",
                taxonomyVersion = "taxonomy",
                calibrationVersion = "calibration",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = InferenceModality.TEXT,
                eventName = TelemetryEventNameV3.ATTEMPT_COMPLETED,
                route = InferenceRouteV3.LOCAL,
                capabilityTier = CapabilityState.READY,
                durationBucket = TelemetryDurationBucketV3.UNDER_2S,
                resourceBucket = TelemetryResourceBucketV3.LOW,
                terminalOutcome = InferenceV3TelemetryOutcome.CANDIDATES,
                itemGroupCountBucket = TelemetryItemGroupCountBucket.ONE,
                componentCountBucket = TelemetryComponentCountBucket.ZERO,
                engineId = "engine",
                modelVersion = "model",
                taxonomyVersion = "taxonomy",
                calibrationVersion = "calibration",
            )
        }
    }

    @Test
    fun mixed_provenance_cannot_be_reduced_to_one_telemetry_event() {
        val base = textResult()
        val groups = (base.outcome as InferenceOutcomeV3.Candidates).itemGroups
        val mixed = base.copy(
            outcome = InferenceOutcomeV3.Candidates(
                groups.mapIndexed { index, group ->
                    group.copy(
                        candidates = group.candidates.map { candidate ->
                            candidate.copy(
                                provenance = candidate.provenance.copy(
                                    route = if (index == 0) InferenceRouteV3.LOCAL_TEXT else InferenceRouteV3.REMOTE,
                                ),
                            )
                        },
                    )
                },
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3.forAttempt(
                mixed,
                CapabilityState.READY,
                TelemetryDurationBucketV3.UNDER_2S,
                TelemetryResourceBucketV3.LOW,
            )
        }
    }
}
