package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class InferenceResultV3Test {
    private val textCandidates =
        "{\"contractVersion\":\"inference-evidence/v3\",\"modality\":\"text\",\"outcome\":{\"outcomeType\":" +
            "\"candidates\",\"itemGroups\":[{\"groupId\":\"item-1\",\"sourceSpan\":{\"start\":0,\"end\":15}," +
            "\"candidates\":[{\"candidateId\":\"food:egg-boiled\",\"label\":\"Egg, boiled\",\"rank\":1," +
            "\"confidence\":{\"score\":0.91,\"band\":\"high\"},\"unresolvedAssumptions\":[]," +
            "\"provenance\":{\"route\":\"local-text\",\"engineId\":\"fixture-text-engine\",\"modelVersion\":" +
            "\"fixture-text-model-1\",\"taxonomyVersion\":\"fixture-taxonomy-1\",\"calibrationVersion\":" +
            "\"fixture-text-calibration-1\",\"contractVersion\":\"inference-evidence/v3\"}}]}]}}"
    private val photoComponents =
        "{\"contractVersion\":\"inference-evidence/v3\",\"modality\":\"photo\",\"outcome\":{\"outcomeType\":" +
            "\"components\",\"components\":[{\"componentId\":\"component:salmon-1\",\"candidate\":{" +
            "\"candidateId\":\"ingredient:salmon\",\"label\":\"Salmon\",\"locale\":\"en\"},\"confidence\":{" +
            "\"score\":0.91,\"band\":\"high\"},\"visibility\":\"visible\",\"ambiguity\":\"none\"," +
            "\"region\":{\"left\":0.1,\"top\":0.2,\"width\":0.3,\"height\":0.4},\"mass\":{" +
            "\"massType\":\"estimated\",\"range\":{\"lowerGrams\":120.0,\"upperGrams\":155.0}," +
            "\"calibrationAssumptions\":[\"single-view-approximation\"]},\"provenance\":{" +
            "\"route\":\"local\",\"engineId\":\"fixture-photo-engine\",\"modelVersion\":\"fixture-photo-model-1\"," +
            "\"taxonomyVersion\":\"fixture-taxonomy-1\",\"calibrationVersion\":\"fixture-mass-calibration-1\"," +
            "\"contractVersion\":\"inference-evidence/v3\"}}]}}"

    @Test
    fun text_decodes_into_ranked_source_anchored_groups() {
        val result = InferenceContractV3Json.decode(textCandidates)

        val groups = assertIs<InferenceOutcomeV3.Candidates>(result.outcome).itemGroups
        assertEquals(InferenceModality.TEXT, result.modality)
        assertEquals(InferenceSourceSpan(0, 15), groups.single().sourceSpan)
        assertEquals(1, groups.single().candidates.single().rank)
        assertEquals(InferenceRouteV3.LOCAL_TEXT, groups.single().candidates.single().provenance.route)
    }

    @Test
    fun photo_decodes_into_component_mass_evidence() {
        val result = InferenceContractV3Json.decode(photoComponents)

        val component = assertIs<InferenceOutcomeV3.Components>(result.outcome).components.single()
        assertEquals(InferenceModality.PHOTO, result.modality)
        assertIs<InferenceV3MassEvidence.Estimated>(component.mass)
        assertEquals("ingredient:salmon", component.candidate.candidateId)
    }

    @Test
    fun both_canonical_modality_shapes_round_trip() {
        assertEquals(textCandidates, InferenceContractV3Json.encode(InferenceContractV3Json.decode(textCandidates)))
        assertEquals(photoComponents, InferenceContractV3Json.encode(InferenceContractV3Json.decode(photoComponents)))
    }

    @Test
    fun text_requires_every_group_to_carry_a_source_span() {
        val decoded = InferenceContractV3Json.decode(textCandidates)
        val group = assertIs<InferenceOutcomeV3.Candidates>(decoded.outcome).itemGroups.single()

        assertFailsWith<IllegalArgumentException> {
            InferenceResultV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = InferenceModality.TEXT,
                outcome = InferenceOutcomeV3.Candidates(listOf(group.copy(sourceSpan = null))),
            )
        }
    }

    @Test
    fun photo_cannot_use_the_text_candidate_shape() {
        val decoded = InferenceContractV3Json.decode(textCandidates)

        assertFailsWith<IllegalArgumentException> {
            InferenceResultV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = InferenceModality.PHOTO,
                outcome = decoded.outcome,
            )
        }
    }
}
