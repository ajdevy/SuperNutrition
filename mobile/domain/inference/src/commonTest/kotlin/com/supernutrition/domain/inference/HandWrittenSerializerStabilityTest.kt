package com.supernutrition.domain.inference

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The hand-written serializers, held to the shape the generated ones produced.
 *
 * Replacing a generated serializer is only safe if the bytes are unchanged, so
 * each type is pinned to a literal encoding rather than to a round-trip alone: a
 * round-trip passes even when both directions drift together. The literals below
 * were captured from the generated serializers before they were replaced.
 *
 * Both Json configurations are exercised because the two differ in exactly the
 * behaviour a hand-written serializer is most likely to get wrong — whether an
 * absent optional is written as null or omitted.
 */
class HandWrittenSerializerStabilityTest {

    private val explicitNulls = Json { encodeDefaults = true }
    private val omittedNulls = INFERENCE_JSON

    // --- ComponentModelManifest --------------------------------------------

    private fun manifest() = ComponentModelManifest(
        manifestVersion = COMPONENT_MODEL_MANIFEST_V1,
        modelVersion = "component-model-1",
        artifactDigest = "c".repeat(64),
        taxonomyVersion = "ingredient-taxonomy-1",
        taxonomyDigest = "d".repeat(64),
        preprocessingVersion = "preprocess-1",
        postprocessingVersion = "postprocess-1",
        maskContractVersion = "mask-region-v1",
        calibrationVersion = "mass-calibration-1",
        runtime = "onnx-runtime",
        executionProvider = "cpu",
        compatibilityTier = "android-test",
        ownership = ComponentModelOwnership.APP_MANAGED,
        activation = ComponentModelActivation.RESEARCH_ONLY,
        rollbackTargetVersion = "component-model-0",
        productApprovalRef = null,
        engineeringApprovalRef = null,
        legalApprovalRef = null,
        corpusRef = null,
        benchmarkEvidenceRef = null,
        rollbackEvidenceRef = null,
    )

    @Test
    fun theManifestEncodingIsUnchangedWhenNullsAreWritten() {
        val expected = "{\"manifestVersion\":\"component-model/v1\",\"modelVersion\":\"component-model-1\"," +
            "\"artifactDigest\":\"" + "c".repeat(64) + "\",\"taxonomyVersion\":\"ingredient-taxonomy-1\"," +
            "\"taxonomyDigest\":\"" + "d".repeat(64) + "\",\"preprocessingVersion\":\"preprocess-1\"," +
            "\"postprocessingVersion\":\"postprocess-1\",\"maskContractVersion\":\"mask-region-v1\"," +
            "\"calibrationVersion\":\"mass-calibration-1\",\"runtime\":\"onnx-runtime\"," +
            "\"executionProvider\":\"cpu\",\"compatibilityTier\":\"android-test\"," +
            "\"ownership\":\"app-managed\",\"activation\":\"research-only\"," +
            "\"rollbackTargetVersion\":\"component-model-0\",\"productApprovalRef\":null," +
            "\"engineeringApprovalRef\":null,\"legalApprovalRef\":null,\"corpusRef\":null," +
            "\"benchmarkEvidenceRef\":null,\"rollbackEvidenceRef\":null}"

        assertEquals(expected, explicitNulls.encodeToString(ComponentModelManifest.serializer(), manifest()))
    }

    @Test
    fun theManifestEncodingIsUnchangedWhenNullsAreOmitted() {
        val encoded = omittedNulls.encodeToString(ComponentModelManifest.serializer(), manifest())

        assertTrue("null" !in encoded, encoded)
        assertTrue("\"rollbackTargetVersion\":\"component-model-0\"" in encoded, encoded)
        assertTrue("productApprovalRef" !in encoded, encoded)
    }

    @Test
    fun theManifestSurvivesEncodeDecodeEncode() {
        listOf(explicitNulls, omittedNulls).forEach { json ->
            val once = json.encodeToString(ComponentModelManifest.serializer(), manifest())
            val decoded = json.decodeFromString(ComponentModelManifest.serializer(), once)
            val twice = json.encodeToString(ComponentModelManifest.serializer(), decoded)

            assertEquals(manifest(), decoded)
            assertEquals(once, twice)
        }
    }

    @Test
    fun aManifestMissingARequiredFieldIsRejectedRatherThanDefaulted() {
        val without = explicitNulls
            .encodeToString(ComponentModelManifest.serializer(), manifest())
            .replace("\"modelVersion\":\"component-model-1\",", "")

        assertFailsWith<IllegalArgumentException> {
            explicitNulls.decodeFromString(ComponentModelManifest.serializer(), without)
        }
    }

    @Test
    fun aManifestDecodedFromJsonStillEnforcesItsRules() {
        // The rules must survive the codec swap: a manifest that arrives over the
        // wire is validated exactly as one built in code.
        val platformManaged = explicitNulls
            .encodeToString(ComponentModelManifest.serializer(), manifest())
            .replace("\"ownership\":\"app-managed\"", "\"ownership\":\"platform-managed\"")

        assertFailsWith<IllegalArgumentException> {
            explicitNulls.decodeFromString(ComponentModelManifest.serializer(), platformManaged)
        }
    }

    // --- EngineCapabilityV3 -------------------------------------------------

    private fun capability(modelVersion: String? = "fixture-model-1") = EngineCapabilityV3(
        contractVersion = INFERENCE_CONTRACT_V3,
        modality = InferenceModality.PHOTO,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = "fixture-engine",
        modelVersion = modelVersion,
    )

    @Test
    fun theCapabilityEncodingIsUnchanged() {
        val expected = "{\"contractVersion\":\"inference-engine/v3\",\"modality\":\"photo\"," +
            "\"state\":\"ready\",\"reason\":\"none\",\"userMessageKey\":\"inference.capability.ready\"," +
            "\"engineId\":\"fixture-engine\",\"modelVersion\":\"fixture-model-1\"}"

        assertEquals(expected, omittedNulls.encodeToString(EngineCapabilityV3.serializer(), capability()))
    }

    @Test
    fun theCapabilitySurvivesEncodeDecodeEncode() {
        listOf(explicitNulls, omittedNulls).forEach { json ->
            val once = json.encodeToString(EngineCapabilityV3.serializer(), capability())
            val decoded = json.decodeFromString(EngineCapabilityV3.serializer(), once)

            assertEquals(capability(), decoded)
            assertEquals(once, json.encodeToString(EngineCapabilityV3.serializer(), decoded))
        }
    }

    @Test
    fun aCapabilityDecodedFromJsonStillEnforcesItsRules() {
        val readyWithoutModel = omittedNulls
            .encodeToString(EngineCapabilityV3.serializer(), capability())
            .replace(",\"modelVersion\":\"fixture-model-1\"", "")

        assertFailsWith<IllegalArgumentException> {
            omittedNulls.decodeFromString(EngineCapabilityV3.serializer(), readyWithoutModel)
        }
    }

    @Test
    fun aCapabilityMissingARequiredFieldIsRejected() {
        val without = omittedNulls
            .encodeToString(EngineCapabilityV3.serializer(), capability())
            .replace("\"engineId\":\"fixture-engine\",", "")

        assertFailsWith<IllegalArgumentException> {
            omittedNulls.decodeFromString(EngineCapabilityV3.serializer(), without)
        }
    }

    // --- InferenceItemGroup -------------------------------------------------

    private fun candidate(rank: Int = 1, id: String = "food:egg-boiled") = InferenceCandidateV3(
        candidateId = id,
        label = "Egg, boiled",
        rank = rank,
        confidence = Confidence(score = 0.91, band = ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenanceV3(
            route = InferenceRouteV3.REMOTE,
            engineId = "fixture-engine",
            modelVersion = "fixture-model-1",
            calibrationVersion = "fixture-calibration-1",
            contractVersion = INFERENCE_CONTRACT_V3,
        ),
    )

    private fun group(sourceSpan: InferenceSourceSpan? = null) = InferenceItemGroup(
        groupId = "group-1",
        sourceSpan = sourceSpan,
        candidates = listOf(candidate()),
    )

    @Test
    fun theItemGroupOmitsAnAbsentSpanAndKeepsAPresentOne() {
        val without = omittedNulls.encodeToString(InferenceItemGroup.serializer(), group())
        val with = omittedNulls.encodeToString(
            InferenceItemGroup.serializer(),
            group(InferenceSourceSpan(0, 3)),
        )

        assertTrue("sourceSpan" !in without, without)
        assertTrue("\"sourceSpan\":{\"start\":0,\"end\":3}" in with, with)
        assertTrue(with.startsWith("{\"groupId\":\"group-1\","), with)
    }

    @Test
    fun theItemGroupSurvivesEncodeDecodeEncode() {
        listOf(group(), group(InferenceSourceSpan(0, 3))).forEach { original ->
            listOf(explicitNulls, omittedNulls).forEach { json ->
                val once = json.encodeToString(InferenceItemGroup.serializer(), original)
                val decoded = json.decodeFromString(InferenceItemGroup.serializer(), once)

                assertEquals(original, decoded)
                assertEquals(once, json.encodeToString(InferenceItemGroup.serializer(), decoded))
            }
        }
    }

    @Test
    fun anItemGroupDecodedFromJsonStillEnforcesItsRules() {
        // Duplicate ranks are rejected whether the group is built in code...
        assertFailsWith<IllegalArgumentException> {
            InferenceItemGroup(
                groupId = "group-1",
                sourceSpan = null,
                candidates = listOf(candidate(), candidate(id = "food:other")),
            )
        }

        // ...or arrives over the wire.
        val blankGroupId = omittedNulls
            .encodeToString(InferenceItemGroup.serializer(), group())
            .replace("\"groupId\":\"group-1\"", "\"groupId\":\"\"")

        assertFailsWith<IllegalArgumentException> {
            omittedNulls.decodeFromString(InferenceItemGroup.serializer(), blankGroupId)
        }
    }

    @Test
    fun anItemGroupMissingItsCandidatesIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            omittedNulls.decodeFromString(
                InferenceItemGroup.serializer(),
                """{"groupId":"group-1"}""",
            )
        }
    }
}
