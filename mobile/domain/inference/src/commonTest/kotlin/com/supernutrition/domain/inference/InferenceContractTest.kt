package com.supernutrition.domain.inference

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InferenceContractTest {
    @Test
    fun `engine port explicitly binds to contract v1`() {
        val engine = object : PhotoInferenceEngine {
            override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1

            override suspend fun capability(): EngineCapability = InferenceContractFixtures.readyCapability

            override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult =
                InferenceContractFixtures.candidateResult
        }

        assertEquals(PHOTO_INFERENCE_CONTRACT_V1, engine.contractVersion)
    }

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
        val photoRef = PhotoRef.issue(attempt, "leased-photo-1")
        assertEquals(photoRef, PhotoRef.issue(attempt, "leased-photo-1"))
        assertFalse(photoRef == PhotoRef.issue(attempt, "leased-photo-2"))
        assertFalse(photoRef == PhotoRef.issue(AttemptId("other-attempt"), "leased-photo-1"))
        assertFalse((photoRef as Any?) == null)
        assertFalse((photoRef as Any).equals("leased-photo-1"))
        assertEquals(photoRef.hashCode(), PhotoRef.issue(attempt, "leased-photo-1").hashCode())
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
        assertFailsWith<IllegalArgumentException> {
            requireSha256Digest("a".repeat(63), "test")
        }
        assertFailsWith<IllegalArgumentException> {
            requireSha256Digest("g".repeat(64), "test")
        }
        assertEquals("0".repeat(64), requireSha256Digest("0".repeat(64), "test"))
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
            Json.parseToJsonElement(expectedJson),
            Json.parseToJsonElement(InferenceContractJson.encode(InferenceContractFixtures.candidateResult)),
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
    fun `serializers reject missing capability fields`() {
        val unavailableCapability = EngineCapability(
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.model_required",
            engineId = "fixture-engine",
            modelVersion = null,
        )
        val encodedCapability = InferenceContractJson.encodeCapability(unavailableCapability)
        listOf("contractVersion", "state", "reason", "userMessageKey", "engineId").forEach { field ->
            assertFailsWith<SerializationException> {
                InferenceContractJson.decodeCapability(
                    encodedCapability.replace("\"$field\":", "\"removed$field\":"),
                )
            }
        }
    }

    @Test
    fun `serializers reject missing result fields`() {
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
                    .replace("\"candidates\":", "\"removedCandidates\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.abstentionResult)
                    .replace("\"userMessageKey\":", "\"removedUserMessageKey\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.failureResult)
                    .replace("\"userMessageKey\":", "\"removedUserMessageKey\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
                    .replace("\"label\":", "\"removedLabel\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
                    .replace("\"band\":", "\"removedBand\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
                    .replace("\"calibrationVersion\":", "\"removedCalibrationVersion\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decode(
                InferenceContractJson.encode(InferenceContractFixtures.candidateResult)
                    .replace("\"userMessageKey\":", "\"removedUserMessageKey\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceContractJson.decodeTelemetry(
                InferenceContractJson.encodeTelemetry(InferenceContractFixtures.telemetryEvent)
                    .replace("\"resourceBucket\":", "\"removedResourceBucket\":"),
            )
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
        val orderedCandidates = InferenceOutcome.Candidates(
            listOf(
                candidate,
                candidate.copy(candidateId = "food:apple", rank = 2),
                candidate.copy(candidateId = "food:carrot", rank = 3),
            ),
        )
        assertEquals(3, orderedCandidates.candidates.size)
        assertFailsWith<IllegalArgumentException> {
            InferenceAssumption(AssumptionCode.MIXED_DISH, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceAssumption(AssumptionCode.MIXED_DISH, "")
        }
        assertEquals(
            " x",
            InferenceAssumption(AssumptionCode.MIXED_DISH, " x").userMessageKey,
        )
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
        assertFailsWith<IllegalArgumentException> {
            InferenceContractFixtures.readyCapability.copy(modelVersion = "")
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceContractFixtures.readyCapability.copy(modelVersion = " ")
        }
        assertEquals(
            " x",
            InferenceContractFixtures.readyCapability.copy(userMessageKey = " x").userMessageKey,
        )
        assertEquals(
            " x",
            InferenceContractFixtures.readyCapability.copy(engineId = " x").engineId,
        )
    }

    @Test
    fun `candidate invariants reject malformed values`() {
        val candidate = candidateFixture()
        val provenance = candidate.provenance

        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(
                candidateId = "",
                label = candidate.label,
                rank = candidate.rank,
                confidence = candidate.confidence,
                unresolvedAssumptions = emptyList(),
                provenance = provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(
                candidateId = " ",
                label = candidate.label,
                rank = candidate.rank,
                confidence = candidate.confidence,
                unresolvedAssumptions = emptyList(),
                provenance = provenance,
            )
        }
        assertEquals(" x", candidate.copy(candidateId = " x").candidateId)
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(
                candidateId = candidate.candidateId,
                label = "",
                rank = candidate.rank,
                confidence = candidate.confidence,
                unresolvedAssumptions = emptyList(),
                provenance = provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(
                candidateId = candidate.candidateId,
                label = " ",
                rank = candidate.rank,
                confidence = candidate.confidence,
                unresolvedAssumptions = emptyList(),
                provenance = provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> { candidate.copy(rank = 0) }
        assertFailsWith<IllegalArgumentException> { candidate.copy(rank = -1) }
        assertFailsWith<IllegalArgumentException> { InferenceOutcome.Candidates(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(
                listOf(candidate, candidate.copy(candidateId = "food:apple", rank = 1)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(
                listOf(candidate, candidate.copy(candidateId = candidate.candidateId, rank = 2)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(
                listOf(candidate, candidate.copy(candidateId = "food:apple", rank = 3)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(candidate, candidate.copy(rank = 2)))
        }
    }

    @Test
    fun `outcome messages reject blank values`() {
        val provenance = candidateFixture().provenance

        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Abstention(
                reason = AbstentionReason.IMAGE_QUALITY,
                userMessageKey = " ",
                provenance = provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Abstention(
                reason = AbstentionReason.IMAGE_QUALITY,
                userMessageKey = "",
                provenance = provenance,
            )
        }
        assertEquals(
            " x",
            InferenceOutcome.Abstention(
                reason = AbstentionReason.IMAGE_QUALITY,
                userMessageKey = " x",
                provenance = provenance,
            ).userMessageKey,
        )
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Failure(
                code = InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                userMessageKey = " ",
                provenance = provenance,
            )
        }
        assertEquals(
            " x",
            InferenceOutcome.Failure(
                code = InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                userMessageKey = " x",
                provenance = provenance,
            ).userMessageKey,
        )
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Failure(
                code = InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                userMessageKey = "",
                provenance = provenance,
            )
        }
    }

    @Test
    fun `confidence and provenance reject malformed values`() {
        val provenance = candidateFixture().provenance

        assertFailsWith<IllegalArgumentException> { Confidence(-0.01, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { Confidence(1.01, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { Confidence(Double.POSITIVE_INFINITY, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { Confidence(Double.NEGATIVE_INFINITY, ConfidenceBand.LOW) }
        assertEquals(0.0, Confidence(0.0, ConfidenceBand.LOW).score)
        assertEquals(1.0, Confidence(1.0, ConfidenceBand.HIGH).score)
        assertFailsWith<IllegalArgumentException> { provenance.copy(engineId = "") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(engineId = " ") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(modelVersion = "") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(modelVersion = " ") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(calibrationVersion = "") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(calibrationVersion = " ") }
        assertEquals(" x", provenance.copy(engineId = " x").engineId)
        assertEquals(" x", provenance.copy(modelVersion = " x").modelVersion)
        assertEquals(" x", provenance.copy(calibrationVersion = " x").calibrationVersion)
        assertFailsWith<IllegalArgumentException> { provenance.copy(contractVersion = "photo-inference-engine/v2") }
        assertEquals(
            PHOTO_INFERENCE_CONTRACT_V1,
            PhotoInferenceResult(
                PHOTO_INFERENCE_CONTRACT_V1,
                InferenceContractFixtures.candidateResult.outcome,
            ).contractVersion,
        )
        assertFailsWith<IllegalArgumentException> {
            PhotoInferenceResult("photo-inference-engine/v2", InferenceContractFixtures.candidateResult.outcome)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEvent(
                contractVersion = "photo-inference-engine/v2",
                eventType = InferenceTelemetryEventType.ATTEMPT,
                route = InferenceRoute.LOCAL,
                capabilityState = CapabilityState.READY,
                outcome = InferenceTelemetryOutcome.CANDIDATES,
                durationBucket = TelemetryDurationBucket.UNDER_1S,
                resourceBucket = TelemetryResourceBucket.WITHIN_BUDGET,
            )
        }
    }

    private fun candidateFixture(): InferenceCandidate = InferenceContractFixtures.candidateResult.outcome
        .let { it as InferenceOutcome.Candidates }
        .candidates
        .single()

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
        assertEquals(
            "fixture-model-1",
            validUnavailable.copy(modelVersion = "fixture-model-1").modelVersion,
        )
        assertEquals(
            validUnavailable,
            InferenceContractJson.decodeCapability(InferenceContractJson.encodeCapability(validUnavailable)),
        )

        assertFailsWith<IllegalArgumentException> {
            validUnavailable.copy(contractVersion = "photo-inference-engine/v2")
        }
        assertFailsWith<IllegalArgumentException> { validUnavailable.copy(userMessageKey = " ") }
        assertFailsWith<IllegalArgumentException> { validUnavailable.copy(userMessageKey = "") }
        assertFailsWith<IllegalArgumentException> { validUnavailable.copy(engineId = " ") }
        assertFailsWith<IllegalArgumentException> { validUnavailable.copy(engineId = "") }
        assertFailsWith<IllegalArgumentException> {
            InferenceContractFixtures.readyCapability.copy(modelVersion = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validUnavailable.copy(reason = CapabilityReason.NONE)
        }
    }

    @Test
    fun `confidence bands are derived from score boundaries`() {
        assertEquals(ConfidenceBand.LOW, ConfidenceBand.fromScore(0.0))
        assertEquals(ConfidenceBand.LOW, ConfidenceBand.fromScore(0.499999))
        assertEquals(ConfidenceBand.MEDIUM, ConfidenceBand.fromScore(0.5))
        assertEquals(ConfidenceBand.MEDIUM, ConfidenceBand.fromScore(0.799999))
        assertEquals(ConfidenceBand.HIGH, ConfidenceBand.fromScore(0.8))
        assertEquals(ConfidenceBand.HIGH, ConfidenceBand.fromScore(1.0))
        assertEquals(ConfidenceBand.MEDIUM, Confidence(0.5, ConfidenceBand.MEDIUM).band)
        assertEquals(ConfidenceBand.MEDIUM, Confidence(0.799999, ConfidenceBand.MEDIUM).band)

        assertFailsWith<IllegalArgumentException> { Confidence(0.49, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { Confidence(0.5, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { Confidence(0.8, ConfidenceBand.MEDIUM) }
    }

    @Test
    fun `canonical capability json is versioned and ignores unknown fields`() {
        val expectedJson = listOf(
            "{\"contractVersion\":\"photo-inference-engine/v1\",\"state\":\"ready\",",
            "\"reason\":\"none\",\"userMessageKey\":\"inference.capability.ready\",",
            "\"engineId\":\"fixture-engine\",\"modelVersion\":\"fixture-model-1\"}",
        ).joinToString("")
        val encoded = InferenceContractJson.encodeCapability(InferenceContractFixtures.readyCapability)

        assertEquals(Json.parseToJsonElement(expectedJson), Json.parseToJsonElement(encoded))
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
    fun `legacy generated serializers round trip branch boundary values`() {
        val json = Json { encodeDefaults = true }
        val jsonWithoutDefaults = Json { encodeDefaults = false }
        val candidate = candidateFixture()
        val candidates = InferenceOutcome.Candidates(listOf(candidate))
        val capability = InferenceContractFixtures.readyCapability
        val unavailable = EngineCapability(
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.model_required",
            engineId = "fixture-engine",
        )

        assertEquals(
            Confidence(0.5, ConfidenceBand.MEDIUM),
            json.decodeFromString(
                Confidence.serializer(),
                json.encodeToString(Confidence.serializer(), Confidence(0.5, ConfidenceBand.MEDIUM)),
            ),
        )
        assertEquals(
            capability,
            json.decodeFromString(
                EngineCapability.serializer(),
                json.encodeToString(EngineCapability.serializer(), capability),
            ),
        )
        assertEquals(
            unavailable,
            jsonWithoutDefaults.decodeFromString(
                EngineCapability.serializer(),
                jsonWithoutDefaults.encodeToString(EngineCapability.serializer(), unavailable),
            ),
        )
        assertEquals(
            candidate,
            json.decodeFromString(
                InferenceCandidate.serializer(),
                json.encodeToString(InferenceCandidate.serializer(), candidate),
            ),
        )
        assertEquals(
            candidates,
            json.decodeFromString(
                InferenceOutcome.Candidates.serializer(),
                json.encodeToString(InferenceOutcome.Candidates.serializer(), candidates),
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

        assertEquals(Json.parseToJsonElement(expectedJson), Json.parseToJsonElement(encoded))
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
