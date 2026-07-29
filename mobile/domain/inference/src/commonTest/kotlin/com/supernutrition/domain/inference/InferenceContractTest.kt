package com.supernutrition.domain.inference

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InferenceContractTest {
    @Test
    fun `engine request binds the opaque photo to its immutable attempt context`() {
        val attempt = AttemptId("attempt-contract-1")
        val request = PhotoInferenceRequest(
            photoRef = PhotoRef.issue(attempt, "leased-photo-1"),
            attemptContext = InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-contract-1",
                configurationVersion = "config-1",
                configurationDigest = "a".repeat(64),
            ),
        )

        assertEquals(attempt, request.photoRef.attemptId)
        assertEquals("PhotoRef(redacted)", request.photoRef.toString())
        assertFailsWith<IllegalArgumentException> {
            PhotoInferenceRequest(
                photoRef = request.photoRef,
                attemptContext = request.attemptContext.copy(attemptId = AttemptId("other-attempt")),
            )
        }
    }

    @Test
    fun `attempt and photo identity boundaries reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { AttemptId(" ") }

        val attempt = AttemptId("attempt-contract-invalid")
        assertFailsWith<IllegalArgumentException> { PhotoRef.issue(attempt, " ") }
        assertFailsWith<IllegalArgumentException> {
            InferenceAttemptContext(
                attemptId = attempt,
                requestId = "",
                configurationVersion = "config-1",
                configurationDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-contract-invalid",
                configurationVersion = "",
                configurationDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-contract-invalid",
                configurationVersion = "config-1",
                configurationDigest = "A".repeat(64),
            )
        }
    }

    @Test
    fun `canonical candidate fixture has stable contract json`() {
        val expectedJson = listOf(
            "{\"contractVersion\":\"photo-inference-engine/v1\",\"outcome\":{\"outcomeType\":\"candidates\",",
            "\"candidates\":[",
            "{\"candidateId\":\"food:banana\",\"label\":\"Banana\",\"rank\":1,\"confidence\":",
            "{\"score\":0.93,\"band\":\"high\"},\"unresolvedAssumptions\":[",
            "{\"code\":\"portion-unknown\",\"userMessageKey\":\"inference.assumption.portion_unknown\"}],",
            "\"provenance\":{\"route\":\"local\",\"engineId\":\"fixture-engine\",\"modelVersion\":\"fixture-model-1\",",
            "\"calibrationVersion\":\"fixture-calibration-1\",\"contractVersion\":\"photo-inference-engine/v1\"}}]}}",
        ).joinToString("")

        assertEquals(
            expectedJson,
            InferenceContractJson.encode(InferenceContractFixtures.candidateResult),
        )
    }

    @Test
    fun `unknown fields are ignored at every evidence boundary`() {
        val payload = InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
            .replaceFirst(
                "{\"contractVersion\"",
                "{\"futureEnvelopeField\":true,\"contractVersion\"",
            )
            .replaceFirst(
                "{\"candidateId\"",
                "{\"futureCandidateField\":\"ignored\",\"candidateId\"",
            )

        assertEquals(
            InferenceContractFixtures.candidateResult,
            InferenceContractJson.decode(payload),
        )
    }

    @Test
    fun `missing required fields and incompatible versions are rejected`() {
        val encoded = InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
        val missingOutcome = encoded.replace(",\"outcome\":", ",\"removedOutcome\":")
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(missingOutcome)
        }

        val incompatible = encoded.replace(
            "contractVersion\":\"$PHOTO_INFERENCE_CONTRACT_V1\"",
            "contractVersion\":\"photo-inference-engine/v2\"",
        )
        assertFailsWith<IllegalArgumentException> {
            InferenceContractJson.decode(incompatible)
        }
    }

    @Test
    fun `canonical outcomes contain identification evidence but no nutrition`() {
        val results = listOf(
            InferenceContractFixtures.candidateResult,
            InferenceContractFixtures.abstentionResult,
            InferenceContractFixtures.failureResult,
        )

        results.forEach { result ->
            val json = InferenceContractJson.encode(result).lowercase()
            assertFalse(json.contains("calorie"))
            assertFalse(json.contains("macro"))
            assertFalse(json.contains("micronutrient"))
            assertFalse(json.contains("nutrition"))
        }
        assertTrue(InferenceContractFixtures.candidateResult.outcome is InferenceOutcome.Candidates)
        assertTrue(InferenceContractFixtures.abstentionResult.outcome is InferenceOutcome.Abstention)
        assertTrue(InferenceContractFixtures.failureResult.outcome is InferenceOutcome.Failure)
    }

    @Test
    fun `canonical result and telemetry fixtures round trip`() {
        listOf(
            InferenceContractFixtures.candidateResult,
            InferenceContractFixtures.abstentionResult,
            InferenceContractFixtures.failureResult,
        ).forEach { result ->
            assertEquals(result, InferenceContractJson.decode(InferenceContractJson.encode(result)))
        }
        assertEquals(
            InferenceContractFixtures.telemetryEvent,
            InferenceContractJson.decodeTelemetry(
                InferenceContractJson.encodeTelemetry(InferenceContractFixtures.telemetryEvent),
            ),
        )
    }

    @Test
    fun `candidate and capability invariants reject ambiguous or incomplete evidence`() {
        val candidate = InferenceContractFixtures.candidateResult.outcome
            .let { it as InferenceOutcome.Candidates }
            .candidates
            .single()

        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(candidate, candidate.copy(rank = 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceAssumption(AssumptionCode.MIXED_DISH, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            Confidence(Double.NaN, ConfidenceBand.LOW)
        }
        assertFailsWith<IllegalArgumentException> {
            EngineCapability(
                contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                state = CapabilityState.READY,
                reason = CapabilityReason.MODEL_MISSING,
                userMessageKey = "inference.capability.ready",
                engineId = "fixture-engine",
            )
        }
    }

    @Test
    fun `candidate, outcome, and provenance invariants reject malformed evidence`() {
        val candidate = InferenceContractFixtures.candidateResult.outcome
            .let { it as InferenceOutcome.Candidates }
            .candidates
            .single()
        val provenance = candidate.provenance

        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate("", candidate.label, candidate.rank, candidate.confidence, emptyList(), provenance)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(candidate.candidateId, "", candidate.rank, candidate.confidence, emptyList(), provenance)
        }
        assertFailsWith<IllegalArgumentException> { candidate.copy(rank = 0) }
        assertFailsWith<IllegalArgumentException> { InferenceOutcome.Candidates(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(candidate, candidate.copy(candidateId = "food:apple", rank = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(candidate, candidate.copy(candidateId = "food:apple", rank = 3)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(candidate, candidate.copy(rank = 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Abstention(
                reason = AbstentionReason.IMAGE_QUALITY,
                userMessageKey = " ",
                provenance = provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Failure(
                code = InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                userMessageKey = " ",
                provenance = provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> { Confidence(-0.01, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { provenance.copy(engineId = "") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(modelVersion = "") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(calibrationVersion = "") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(contractVersion = "photo-inference-engine/v2") }
    }

    @Test
    fun `capability invariants cover ready and unavailable states`() {
        val validUnavailable = EngineCapability(
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.model_required",
            engineId = "fixture-engine",
        )
        assertEquals(CapabilityState.MODEL_REQUIRED, validUnavailable.state)

        assertFailsWith<IllegalArgumentException> {
            validUnavailable.copy(contractVersion = "photo-inference-engine/v2")
        }
        assertFailsWith<IllegalArgumentException> { validUnavailable.copy(userMessageKey = " ") }
        assertFailsWith<IllegalArgumentException> { validUnavailable.copy(engineId = " ") }
        assertFailsWith<IllegalArgumentException> {
            InferenceContractFixtures.readyCapability.copy(modelVersion = null)
        }
    }

    @Test
    fun `canonical capability json is versioned and ignores unknown fields`() {
        val expectedJson = listOf(
            "{\"contractVersion\":\"photo-inference-engine/v1\",\"state\":\"ready\",",
            "\"reason\":\"none\",\"userMessageKey\":\"inference.capability.ready\",",
            "\"engineId\":\"fixture-engine\",\"modelVersion\":\"fixture-model-1\"}",
        ).joinToString("")
        val encoded = InferenceContractJson.encodeCapability(InferenceContractFixtures.readyCapability)

        assertEquals(expectedJson, encoded)
        assertEquals(
            InferenceContractFixtures.readyCapability,
            InferenceContractJson.decodeCapability(
                encoded.replaceFirst(
                    "{\"contractVersion\"",
                    "{\"futureCapabilityField\":true,\"contractVersion\"",
                ),
            ),
        )
    }

    @Test
    fun `capability and telemetry decoders reject incompatible versions`() {
        val capability = InferenceContractJson.encodeCapability(InferenceContractFixtures.readyCapability)
            .replace("photo-inference-engine/v1", "photo-inference-engine/v2")
        assertFailsWith<IllegalArgumentException> { InferenceContractJson.decodeCapability(capability) }

        val telemetry = InferenceContractJson.encodeTelemetry(InferenceContractFixtures.telemetryEvent)
            .replace("photo-inference-engine/v1", "photo-inference-engine/v2")
        assertFailsWith<IllegalArgumentException> { InferenceContractJson.decodeTelemetry(telemetry) }
    }

    @Test
    fun `telemetry is a controlled versioned allowlist without raw payload fields`() {
        val encoded = InferenceContractJson.encodeTelemetry(InferenceContractFixtures.telemetryEvent)
        val expectedJson = listOf(
            "{\"contractVersion\":\"photo-inference-engine/v1\",\"eventType\":\"attempt\",\"route\":\"local\",",
            "\"capabilityState\":\"ready\",\"outcome\":\"candidates\",\"durationBucket\":\"under-1s\",",
            "\"resourceBucket\":\"within-budget\"}",
        ).joinToString("")

        assertEquals(
            expectedJson,
            encoded,
        )
        assertEquals(
            InferenceContractFixtures.telemetryEvent,
            InferenceContractJson.decodeTelemetry(
                encoded.replaceFirst(
                    "{\"contractVersion\"",
                    "{\"futureTelemetryField\":true,\"contractVersion\"",
                ),
            ),
        )
        assertEquals(
            setOf(
                "contractVersion",
                "eventType",
                "route",
                "capabilityState",
                "outcome",
                "durationBucket",
                "resourceBucket",
            ),
            Json.parseToJsonElement(encoded).jsonObject.keys,
        )
    }
}
