package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InferenceResultV3ValidationTest {
    private val provenance = InferenceProvenanceV3(
        route = InferenceRouteV3.LOCAL_TEXT,
        engineId = "engine-1",
        modelVersion = "model-1",
        taxonomyVersion = "taxonomy-1",
        calibrationVersion = "calibration-1",
        contractVersion = INFERENCE_CONTRACT_V3,
    )

    private fun candidate(id: String = "food:egg-boiled", rank: Int = 1) = InferenceCandidateV3(
        candidateId = id,
        label = "Egg, boiled",
        rank = rank,
        confidence = Confidence(0.91, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = provenance,
    )

    @Test
    fun candidate_ranks_must_be_unique_and_contiguous() {
        assertFailsWith<IllegalArgumentException> {
            InferenceItemGroup("item-1", null, listOf(candidate(), candidate("food:egg")))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceItemGroup("item-1", null, listOf(candidate("food:a", 1), candidate("food:b", 3)))
        }
        assertEquals(
            2,
            InferenceItemGroup("item-1", null, listOf(candidate("food:a"), candidate("food:b", 2))).candidates.size,
        )
    }

    @Test
    fun text_source_spans_and_candidate_identities_are_bounded() {
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(-1, 1) }
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(2, 2) }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidateV3(" ", "label", 1, Confidence(0.9, ConfidenceBand.HIGH), emptyList(), provenance)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceItemGroup(" ", null, listOf(candidate()))
        }
    }

    @Test
    fun photo_component_evidence_rejects_invalid_geometry_and_mass() {
        val candidateIdentity = InferenceV3LocalizedCandidate("ingredient:salmon", "Salmon", "en")
        assertFailsWith<IllegalArgumentException> {
            InferenceV3NormalizedRegion(0.8, 0.8, 0.4, 0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3GramRange(155.0, 120.0)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3VisibleComponent(
                componentId = "component:1",
                candidate = candidateIdentity,
                confidence = Confidence(0.91, ConfidenceBand.HIGH),
                visibility = InferenceV3ComponentVisibility.VISIBLE,
                ambiguity = InferenceV3ComponentAmbiguity.NONE,
                mass = InferenceV3MassEvidence.NotEvaluable(
                    InferenceV3MassNotEvaluableReason.MIXED_OR_OCCLUDED,
                    " ",
                ),
                provenance = InferenceProvenanceV3(
                    route = InferenceRouteV3.LOCAL,
                    engineId = "engine",
                    modelVersion = "model",
                    taxonomyVersion = "taxonomy",
                    calibrationVersion = "calibration",
                    contractVersion = INFERENCE_CONTRACT_V3,
                ),
            )
        }
    }

    @Test
    fun text_and_photo_outcomes_are_modality_specific() {
        val text = InferenceResultV3(
            contractVersion = INFERENCE_CONTRACT_V3,
            modality = InferenceModality.TEXT,
            outcome = InferenceOutcomeV3.Candidates(
                listOf(InferenceItemGroup("item-1", InferenceSourceSpan(0, 3), listOf(candidate()))),
            ),
        )
        assertEquals(InferenceModality.TEXT, text.modality)

        val photo = InferenceResultV3(
            contractVersion = INFERENCE_CONTRACT_V3,
            modality = InferenceModality.PHOTO,
            outcome = InferenceOutcomeV3.Components(
                listOf(
                    InferenceV3VisibleComponent(
                        componentId = "component:1",
                        candidate = InferenceV3LocalizedCandidate("ingredient:salmon", "Salmon", "en"),
                        confidence = Confidence(0.91, ConfidenceBand.HIGH),
                        visibility = InferenceV3ComponentVisibility.VISIBLE,
                        ambiguity = InferenceV3ComponentAmbiguity.NONE,
                        region = InferenceV3NormalizedRegion(0.1, 0.2, 0.3, 0.4),
                        mass = InferenceV3MassEvidence.NotEvaluable(
                            InferenceV3MassNotEvaluableReason.MIXED_OR_OCCLUDED,
                            "inference.mass.mixed_or_occluded",
                        ),
                        provenance = InferenceProvenanceV3(
                            route = InferenceRouteV3.LOCAL,
                            engineId = "engine",
                            modelVersion = "model",
                            taxonomyVersion = "taxonomy",
                            calibrationVersion = "calibration",
                            contractVersion = INFERENCE_CONTRACT_V3,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(InferenceModality.PHOTO, photo.modality)
    }
}
