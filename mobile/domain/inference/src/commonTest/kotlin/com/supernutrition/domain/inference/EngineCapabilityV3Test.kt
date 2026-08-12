@file:Suppress("EqualsNullCall", "MaxLineLength", "MaximumLineLength")

package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class EngineCapabilityV3Test {
    private val canonical =
        "{\"contractVersion\":\"inference-evidence/v3\",\"modality\":\"text\",\"state\":\"ready\",\"reason\":\"none\"," +
            "\"userMessageKey\":\"inference.capability.ready\",\"engineId\":\"fixture-text-engine\",\"modelVersion\":" +
            "\"fixture-text-model-1\",\"taxonomyVersion\":\"fixture-taxonomy-1\",\"calibrationVersion\":\"fixture-text-calibration-1\"}"

    @Test
    fun canonical_capability_round_trips() {
        val capability = InferenceContractV3Json.decodeCapability(canonical)

        assertEquals(InferenceModality.TEXT, capability.modality)
        assertEquals(CapabilityState.READY, capability.state)
        assertEquals("fixture-taxonomy-1", capability.taxonomyVersion)
        assertEquals(canonical, InferenceContractV3Json.encodeCapability(capability))
    }

    @Test
    fun capability_is_per_modality() {
        val photo = capability(InferenceModality.PHOTO, CapabilityState.READY, CapabilityReason.NONE)
        val text = capability(InferenceModality.TEXT, CapabilityState.MODEL_REQUIRED, CapabilityReason.MODEL_MISSING)

        assertEquals(CapabilityState.READY, photo.state)
        assertEquals(CapabilityState.MODEL_REQUIRED, text.state)
        assertEquals(photo, photo.copy())
        assertFalse(photo == text)
        assertFalse(photo.equals(null))
        assertFalse(photo.equals("not-a-capability"))
        assertFalse(photo == photo.copy(modality = InferenceModality.TEXT))
        assertFalse(
            photo == photo.copy(state = CapabilityState.MODEL_REQUIRED, reason = CapabilityReason.MODEL_MISSING),
        )
        assertFalse(photo == photo.copy(userMessageKey = "other"))
        assertFalse(photo == photo.copy(engineId = "other-engine"))
        assertFalse(photo == photo.copy(modelVersion = "other-model"))
        assertFalse(photo == photo.copy(taxonomyVersion = "other-taxonomy"))
        assertFalse(photo == photo.copy(calibrationVersion = "other-calibration"))
    }

    @Test
    fun state_and_reason_must_agree() {
        assertFailsWith<IllegalArgumentException> {
            capability(InferenceModality.TEXT, CapabilityState.READY, CapabilityReason.MODEL_MISSING)
        }
        assertFailsWith<IllegalArgumentException> {
            capability(InferenceModality.TEXT, CapabilityState.UNSUPPORTED, CapabilityReason.NONE)
        }
    }

    @Test
    fun all_capability_metadata_is_required() {
        fun invalid(
            userMessageKey: String = "inference.capability.ready",
            engineId: String = "engine",
            modelVersion: String = "model",
            taxonomyVersion: String = "taxonomy",
            calibrationVersion: String = "calibration",
        ) = EngineCapabilityV3(
            contractVersion = INFERENCE_CONTRACT_V3,
            modality = InferenceModality.PHOTO,
            state = CapabilityState.READY,
            reason = CapabilityReason.NONE,
            userMessageKey = userMessageKey,
            engineId = engineId,
            modelVersion = modelVersion,
            taxonomyVersion = taxonomyVersion,
            calibrationVersion = calibrationVersion,
        )
        listOf<() -> EngineCapabilityV3>(
            { invalid(userMessageKey = " ") },
            { invalid(engineId = " ") },
            { invalid(modelVersion = " ") },
            { invalid(taxonomyVersion = " ") },
            { invalid(calibrationVersion = " ") },
        ).forEach { invalidCapability ->
            assertFailsWith<IllegalArgumentException> { invalidCapability() }
        }
    }

    @Test
    fun unavailable_capability_round_trips_with_its_modality_and_reason() {
        val capability = capability(
            InferenceModality.PHOTO,
            CapabilityState.MODEL_REQUIRED,
            CapabilityReason.MODEL_MISSING,
        )

        assertEquals(
            capability,
            InferenceContractV3Json.decodeCapability(InferenceContractV3Json.encodeCapability(capability)),
        )
    }

    @Test
    fun previous_versions_are_rejected() {
        assertFailsWith<IllegalArgumentException> {
            InferenceContractV3Json.decodeCapability(
                canonical.replace(INFERENCE_CONTRACT_V3, PHOTO_INFERENCE_CONTRACT_V1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceContractV3Json.decodeCapability(
                canonical.replace(INFERENCE_CONTRACT_V3, INFERENCE_EVIDENCE_CONTRACT_V2),
            )
        }
    }

    private fun capability(modality: InferenceModality, state: CapabilityState, reason: CapabilityReason) =
        EngineCapabilityV3(
            contractVersion = INFERENCE_CONTRACT_V3,
            modality = modality,
            state = state,
            reason = reason,
            userMessageKey = "inference.capability.ready",
            engineId = "fixture-engine",
            modelVersion = "fixture-model-1",
            taxonomyVersion = "fixture-taxonomy-1",
            calibrationVersion = "fixture-calibration-1",
        )
}
