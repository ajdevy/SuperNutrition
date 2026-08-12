package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The v2 result surface, asserted against the contract's own canonical
 * fixtures rather than against a shape read out of the schema by hand.
 *
 * The binding previously had no result type at all: `InferenceEngineV3.analyze`
 * returned v1's `PhotoInferenceResult`, so a text request was answerable only
 * in a photo-shaped result with nowhere to put the per-item spans a typed
 * description produces. The modality this contract exists to add was
 * representable on the way in and lost on the way out.
 */
class InferenceResultV3Test {
    private val textCandidates =
        "{\"contractVersion\":\"inference-engine/v3\",\"modality\":\"text\",\"outcome\":{\"outcomeType\":" +
            "\"candidates\",\"itemGroups\":[{\"groupId\":\"item-1\",\"sourceSpan\":{\"start\":0,\"end\":15},\"" +
            "candidates\":[{\"candidateId\":\"food:egg-boiled\",\"label\":\"Egg, boiled\",\"rank\":1,\"confid" +
            "ence\":{\"score\":0.91,\"band\":\"high\"},\"unresolvedAssumptions\":[],\"provenance\":{\"route\"" +
            ":\"local-text\",\"engineId\":\"fixture-engine\",\"modelVersion\":\"fixture-model-1\",\"calibrati" +
            "onVersion\":\"fixture-calibration-1\",\"contractVersion\":\"inference-engine/v3\"}}]},{\"groupId" +
            "\":\"item-2\",\"sourceSpan\":{\"start\":20,\"end\":40},\"candidates\":[{\"candidateId\":\"food:r" +
            "ye-bread\",\"label\":\"Bread, rye\",\"rank\":1,\"confidence\":{\"score\":0.87,\"band\":\"high\"}" +
            ",\"unresolvedAssumptions\":[{\"code\":\"portion-unknown\",\"userMessageKey\":\"inference.assumpt" +
            "ion.portion_unknown\"}],\"provenance\":{\"route\":\"local-text\",\"engineId\":\"fixture-engine\"" +
            ",\"modelVersion\":\"fixture-model-1\",\"calibrationVersion\":\"fixture-calibration-1\",\"contrac" +
            "tVersion\":\"inference-engine/v3\"}}]}]}}"
    private val photoCandidates =
        "{\"contractVersion\":\"inference-engine/v3\",\"modality\":\"photo\",\"outcome\":{\"outcomeType\"" +
            ":\"candidates\",\"itemGroups\":[{\"groupId\":\"item-1\",\"candidates\":[{\"candidateId\":\"food:" +
            "banana\",\"label\":\"Banana\",\"rank\":1,\"confidence\":{\"score\":0.93,\"band\":\"high\"},\"unr" +
            "esolvedAssumptions\":[{\"code\":\"portion-unknown\",\"userMessageKey\":\"inference.assumption.po" +
            "rtion_unknown\"}],\"provenance\":{\"route\":\"local\",\"engineId\":\"fixture-engine\",\"modelVer" +
            "sion\":\"fixture-model-1\",\"calibrationVersion\":\"fixture-calibration-1\",\"contractVersion\":" +
            "\"inference-engine/v3\"}}]}]}}"
    private val abstention =
        "{\"contractVersion\":\"inference-engine/v3\",\"modality\":\"text\",\"outcome\":{\"outcomeType\":" +
            "\"abstention\",\"reason\":\"description-unsegmentable\",\"userMessageKey\":\"inference.abstentio" +
            "n.description_unsegmentable\",\"provenance\":{\"route\":\"local-text\",\"engineId\":\"fixture-en" +
            "gine\",\"modelVersion\":\"fixture-model-1\",\"calibrationVersion\":\"fixture-calibration-1\",\"c" +
            "ontractVersion\":\"inference-engine/v3\"}}}"
    private val failure =
        "{\"contractVersion\":\"inference-engine/v3\",\"modality\":\"text\",\"outcome\":{\"outcomeType\":" +
            "\"failure\",\"code\":\"temporary-unavailable\",\"retryable\":true,\"userMessageKey\":\"inference" +
            ".failure.temporary_unavailable\",\"provenance\":{\"route\":\"local-text\",\"engineId\":\"fixture" +
            "-engine\",\"modelVersion\":\"fixture-model-1\",\"calibrationVersion\":\"fixture-calibration-1\"," +
            "\"contractVersion\":\"inference-engine/v3\"}}}"

    @Test
    fun `a text result decodes with one group per item, each carrying its span`() {
        val result = InferenceContractV3Json.decode(textCandidates)

        assertEquals(INFERENCE_CONTRACT_V3, result.contractVersion)
        assertEquals(InferenceModality.TEXT, result.modality)
        val groups = assertIs<InferenceOutcomeV3.Candidates>(result.outcome).itemGroups
        assertEquals(listOf("item-1", "item-2"), groups.map { it.groupId })
        assertEquals(InferenceSourceSpan(0, 15), groups[0].sourceSpan)
        assertEquals(InferenceSourceSpan(20, 40), groups[1].sourceSpan)
        assertEquals(InferenceRouteV3.LOCAL_TEXT, groups[0].candidates.single().provenance.route)
    }

    @Test
    fun `a photo result decodes with groups and no spans`() {
        val result = InferenceContractV3Json.decode(photoCandidates)

        assertEquals(InferenceModality.PHOTO, result.modality)
        val groups = assertIs<InferenceOutcomeV3.Candidates>(result.outcome).itemGroups
        assertNull(groups.single().sourceSpan, "a photo has no character offsets to point at")
        assertEquals(InferenceRouteV3.LOCAL, groups.single().candidates.single().provenance.route)
    }

    @Test
    fun `abstention and failure decode with their v2 reasons`() {
        val abstained = assertIs<InferenceOutcomeV3.Abstention>(
            InferenceContractV3Json.decode(abstention).outcome,
        )
        assertEquals(AbstentionReasonV3.DESCRIPTION_UNSEGMENTABLE, abstained.reason)

        val failed = assertIs<InferenceOutcomeV3.Failure>(InferenceContractV3Json.decode(failure).outcome)
        assertEquals(InferenceFailureCode.TEMPORARY_UNAVAILABLE, failed.code)
        assertEquals(true, failed.retryable)
    }

    @Test
    fun `every canonical fixture round-trips byte-identically`() {
        for (fixture in listOf(textCandidates, photoCandidates, abstention, failure)) {
            assertEquals(fixture, InferenceContractV3Json.encode(InferenceContractV3Json.decode(fixture)))
        }
    }

    @Test
    fun `a partly anchored text result is rejected rather than surfaced`() {
        // partial_segmentation = "reject". Some items traceable to the user's
        // words and some not is the FM-6 failure wearing provenance.
        val anchored = InferenceContractV3Json.decode(textCandidates)
        val groups = assertIs<InferenceOutcomeV3.Candidates>(anchored.outcome).itemGroups

        assertFailsWith<IllegalArgumentException> {
            InferenceResultV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = InferenceModality.TEXT,
                outcome = InferenceOutcomeV3.Candidates(
                    listOf(groups[0], groups[1].copy(sourceSpan = null)),
                ),
            )
        }
    }

    @Test
    fun `a photo result carrying a text span is rejected`() {
        val photo = InferenceContractV3Json.decode(photoCandidates)
        val group = assertIs<InferenceOutcomeV3.Candidates>(photo.outcome).itemGroups.single()

        assertFailsWith<IllegalArgumentException> {
            InferenceResultV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = InferenceModality.PHOTO,
                outcome = InferenceOutcomeV3.Candidates(listOf(group.copy(sourceSpan = InferenceSourceSpan(0, 5)))),
            )
        }
    }

    @Test
    fun `a v1 contract version is refused everywhere v2 is required`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceContractV3Json.decode(textCandidates.replace(INFERENCE_CONTRACT_V3, PHOTO_INFERENCE_CONTRACT_V1))
        }
    }
}
