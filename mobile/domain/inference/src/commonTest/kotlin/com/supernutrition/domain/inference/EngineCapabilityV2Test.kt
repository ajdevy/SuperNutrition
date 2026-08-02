package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The v2 capability envelope, against the contract's own canonical fixture.
 *
 * v2 shipped `capability-ready.json` and nothing could read it: `EngineCapability`
 * requires contract-v1 and has no modality field. `InferenceEngineV2` was
 * meanwhile less capable than the interface it replaces — `PhotoInferenceEngine`
 * reports capability and v2 did not — so a v2 engine had no way to say its model
 * was missing, and the E7.4 promise that the floor is reachable when the engine
 * is not ready had nothing to read "not ready" from.
 */
class EngineCapabilityV2Test {
    private val canonical =
        "{\"contractVersion\":\"inference-engine/v2\",\"modality\":\"text\",\"state\":\"ready\",\"reason\":\"" +
            "none\",\"userMessageKey\":\"inference.capability.ready\",\"engineId\":\"fixture-engine\",\"model" +
            "Version\":\"fixture-model-1\"}"

    @Test
    fun `the canonical fixture round-trips byte for byte`() {
        val capability = InferenceContractV2Json.decodeCapability(canonical)

        assertEquals(INFERENCE_CONTRACT_V2, capability.contractVersion)
        assertEquals(InferenceModality.TEXT, capability.modality)
        assertEquals(CapabilityState.READY, capability.state)
        assertEquals(CapabilityReason.NONE, capability.reason)
        assertEquals("fixture-model-1", capability.modelVersion)
        assertEquals(canonical, InferenceContractV2Json.encodeCapability(capability))
    }

    @Test
    fun `a v1 capability payload is rejected rather than silently accepted`() {
        // The reason this type exists rather than a widened v1 one.
        assertFailsWith<IllegalArgumentException> {
            InferenceContractV2Json.decodeCapability(
                canonical.replace(INFERENCE_CONTRACT_V2, "photo-inference-engine/v1"),
            )
        }
    }

    @Test
    fun `capability is per modality, so one engine can be ready for one and not the other`() {
        // The reason it takes a modality at all. An engine serving both can have
        // a working camera and a missing text model, and one answer for the pair
        // would have to be the pessimistic one.
        val photo = capability(InferenceModality.PHOTO, CapabilityState.READY, CapabilityReason.NONE, "model-1")
        val text = capability(
            InferenceModality.TEXT,
            CapabilityState.MODEL_REQUIRED,
            CapabilityReason.MODEL_MISSING,
            modelVersion = null,
        )

        assertEquals(CapabilityState.READY, photo.state)
        assertEquals(CapabilityState.MODEL_REQUIRED, text.state)
        assertNull(text.modelVersion)
    }

    @Test
    fun `a ready capability cannot also report why it is unavailable`() {
        // Self-contradicting reports are the ones a surface has to guess about.
        assertFailsWith<IllegalArgumentException> {
            capability(InferenceModality.TEXT, CapabilityState.READY, CapabilityReason.MODEL_MISSING, "model-1")
        }
    }

    @Test
    fun `a ready capability must name the model it is ready with`() {
        assertFailsWith<IllegalArgumentException> {
            capability(InferenceModality.TEXT, CapabilityState.READY, CapabilityReason.NONE, modelVersion = null)
        }
    }

    @Test
    fun `an unavailable capability must say why`() {
        assertFailsWith<IllegalArgumentException> {
            capability(InferenceModality.TEXT, CapabilityState.UNSUPPORTED, CapabilityReason.NONE, modelVersion = null)
        }
    }

    @Test
    fun `a capability must name the engine reporting it and a message to show`() {
        // Both are guards the type states and nothing exercised. A blank engine
        // id makes a capability unattributable in exactly the situation it
        // matters — several engines, one of them unavailable — and a blank
        // message key leaves a surface with an unavailable state and nothing to
        // tell the user about it.
        assertFailsWith<IllegalArgumentException> {
            EngineCapabilityV2(
                contractVersion = INFERENCE_CONTRACT_V2,
                modality = InferenceModality.TEXT,
                state = CapabilityState.READY,
                reason = CapabilityReason.NONE,
                userMessageKey = "  ",
                engineId = "fixture-engine",
                modelVersion = "model-1",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EngineCapabilityV2(
                contractVersion = INFERENCE_CONTRACT_V2,
                modality = InferenceModality.TEXT,
                state = CapabilityState.READY,
                reason = CapabilityReason.NONE,
                userMessageKey = "inference.capability.ready",
                engineId = "",
                modelVersion = "model-1",
            )
        }
    }

    @Test
    fun `a ready capability with a blank model version is as wrong as one with none`() {
        // The null case was covered and the blank one was not, which is the
        // difference between "no model version" and "a model version that says
        // nothing" — indistinguishable to a reader of the report.
        assertFailsWith<IllegalArgumentException> {
            capability(InferenceModality.TEXT, CapabilityState.READY, CapabilityReason.NONE, modelVersion = "   ")
        }
    }

    @Test
    fun `every unavailable state may report its own reason`() {
        // Each state is a different thing to tell a user, and the floor decides
        // what to offer from it. Constructing all five is what proves the
        // envelope does not quietly accept only the one the fixture happens to
        // carry.
        val unavailable = CapabilityState.entries.filterNot { it == CapabilityState.READY }
        val reported = unavailable.map { state ->
            capability(InferenceModality.TEXT, state, CapabilityReason.MODEL_MISSING, modelVersion = null).state
        }

        assertEquals(unavailable, reported)
    }

    @Test
    fun `every state and reason v1 publishes is still available in v2`() {
        // E7.1 requires capability states to be unchanged from v1. Asserted by
        // enumerating both, because "unchanged" is the kind of claim that stays
        // true right up until someone adds a value to one of them.
        assertEquals(6, CapabilityState.entries.size)
        assertEquals(6, CapabilityReason.entries.size)
    }

    private fun capability(
        modality: InferenceModality,
        state: CapabilityState,
        reason: CapabilityReason,
        modelVersion: String?,
    ) = EngineCapabilityV2(
        contractVersion = INFERENCE_CONTRACT_V2,
        modality = modality,
        state = state,
        reason = reason,
        userMessageKey = "inference.capability.ready",
        engineId = "fixture-engine",
        modelVersion = modelVersion,
    )
}
