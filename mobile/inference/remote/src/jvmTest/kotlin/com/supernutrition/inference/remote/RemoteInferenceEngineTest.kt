package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract behaviour of the gateway Engine, with no HTTP stack involved.
 *
 * The transport is a seam precisely so these properties — consent binding,
 * envelope shape, error mapping, nutrition absence — are assertable without a
 * network or a running server.
 */
private const val ATTEMPT = "att_0000000000000001"
private val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

private class RecordingTransport(private val status: Int = 200, private val body: String = candidatesBody()) :
    GatewayTransport {
    var calls = 0
    var lastContext: String? = null
    var lastPayload: ByteArray? = null
    var lastAuthorization: String? = null

    override suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse {
        calls += 1
        lastContext = call.contextJson
        lastPayload = call.payload
        lastAuthorization = call.authorization
        return GatewayHttpResponse(status, body)
    }
}

private class ThrowingTransport : GatewayTransport {
    override suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse =
        throw IOException("connection refused")
}

private fun candidatesBody(score: Double = 0.93, requiresConfirmation: Boolean = false) = """
{"contractVersion":"mobile-api/v1","requestId":"req_x","attemptId":"$ATTEMPT",
 "resolvedMarketJurisdiction":"ZZ","resolvedProcessingRegion":"fixture-region",
 "outcome":{"outcomeType":"candidates","requiresConfirmation":$requiresConfirmation,
  "candidates":[{"candidateId":"cand_01","label":"Mixed salad","rank":1,
   "confidence":{"score":$score,"band":"high"},"unresolvedAssumptions":[],
   "provenance":{"route":"remote","engineId":"gateway-openrouter","modelVersion":"m",
    "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}]}}
""".trimIndent()

private fun errorBody(code: String, recoverable: Boolean) = """
{"error":{"gatewayCode":"$code","userMessageKey":"error.x","recoverable":$recoverable,
 "retryAfter":0,"correlationId":"c"}}
""".trimIndent()

private val GRANTED = RemoteConsentDecision(
            attemptId = ATTEMPT,
            payloadDigest = "sha256:" + "a".repeat(64),
            modality = "photo",
    granted = true,
    consentCopyVersion = "photo-consent/fixture-1",
    processingRegion = "fixture-region",
    marketJurisdiction = "ZZ",
    retentionVersion = "retention/2",
    recordedAt = "2026-08-03T10:00:00Z",
    expiresAt = "2026-08-03T10:15:00Z",
)

private fun engine(transport: GatewayTransport, consent: RemoteConsentDecision = GRANTED, bytes: ByteArray? = BYTES) =
    RemoteInferenceEngine(
        transport = transport,
        environment = GatewayEnvironment(
            baseUrl = "http://10.0.2.2:3000",
            claimedMarketJurisdiction = "ZZ",
            catalogCountry = "ZZ",
            locale = "en-GB",
            measurementSystem = "metric",
            configurationVersion = "cfg/1",
            authorization = "Bearer test.installation-0001",
        ),
        photoBytes = { bytes },
        digest = { "sha256:" + "a".repeat(64) },
        consent = { consent },
        idFactory = { "0000000000000001" },
        nowEpochMillis = { 1_785_751_500_000L },
    )

private fun request() = PhotoInferenceRequest(
    photoRef = PhotoRef.issue(AttemptId(ATTEMPT), "photo0000000001"),
    attemptContext = InferenceAttemptContext(
        attemptId = AttemptId(ATTEMPT),
        requestId = "req_0000000000000001",
        configurationVersion = "cfg/1",
        configurationDigest = "b".repeat(64),
    ),
)

class RemoteInferenceEngineTest {

    @Test
    fun bindsItselfToTheFrozenV1Contract() {
        assertEquals("photo-inference-engine/v1", engine(RecordingTransport()).contractVersion)
    }

    @Test
    fun mapsCandidatesFromTheGatewayEnvelope() = runBlocking {
        val result = engine(RecordingTransport()).analyze(request())
        val outcome = result.outcome
        assertTrue(outcome is InferenceOutcome.Candidates)
        assertEquals(1, outcome.candidates.size)
        assertEquals("Mixed salad", outcome.candidates[0].label)
        assertEquals(1, outcome.candidates[0].rank)
    }

    @Test
    fun sendsAConsentReceiptBoundToTheTransmittedBytes() = runBlocking {
        val transport = RecordingTransport()
        engine(transport).analyze(request())

        val context = assertNotNull(transport.lastContext)
        assertTrue(context.contains("\"digest\":\"sha256:"), "receipt must carry the payload digest")
        assertTrue(context.contains("\"attemptId\":\"$ATTEMPT\""))
        assertTrue(context.contains("\"decision\":\"granted\""))
        assertTrue(context.contains("\"retentionDays\":0"), "AD-8: nonzero retention is not expressible")
        assertTrue(context.contains("\"modality\":\"photo\""))
        // The bytes hashed are the bytes sent.
        assertEquals(BYTES.toList(), transport.lastPayload?.toList())
    }

    /** A declined decision must never reach the network (AD-40). */
    @Test
    fun neverTransfersWhenConsentIsNotGranted() = runBlocking {
        val transport = RecordingTransport()
        val result = engine(transport, consent = GRANTED.copy(granted = false)).analyze(request())

        assertEquals(0, transport.calls, "a declined decision reached the network")
        val outcome = result.outcome
        assertTrue(outcome is InferenceOutcome.Failure)
        assertEquals(InferenceFailureCode.INVALID_INPUT, outcome.code)
    }

    @Test
    fun neverTransfersWhenThePayloadIsAlreadyDisposed() = runBlocking {
        val transport = RecordingTransport()
        engine(transport, bytes = null).analyze(request())
        assertEquals(0, transport.calls)
    }

    @Test
    fun flagsLowConfidenceWithoutTreatingItAsFailure() = runBlocking {
        val transport = RecordingTransport(body = candidatesBody(score = 0.30, requiresConfirmation = true))
        val outcome = engine(transport).analyze(request()).outcome
        // Uncertainty is a question, not an error: candidates still come back.
        assertTrue(outcome is InferenceOutcome.Candidates)
        assertEquals(0.30, outcome.candidates[0].confidence.score)
    }

    @Test
    fun mapsAbstentionToAbstention() = runBlocking {
        val body = """
        {"contractVersion":"mobile-api/v1","requestId":"r","attemptId":"$ATTEMPT",
         "resolvedMarketJurisdiction":"ZZ","resolvedProcessingRegion":null,
         "outcome":{"outcomeType":"abstention","reasonCode":"no-food-detected",
          "userMessageKey":"inference.abstention.no_food_detected","manualAlternativeAvailable":true}}
        """.trimIndent()
        val outcome = engine(RecordingTransport(body = body)).analyze(request()).outcome
        assertTrue(outcome is InferenceOutcome.Abstention)
    }

    @Test
    fun mapsAnUnknownOutcomeTypeToARecoverableFailureRatherThanCrashing() = runBlocking {
        val body = """
        {"contractVersion":"mobile-api/v1","requestId":"r","attemptId":"$ATTEMPT",
         "resolvedMarketJurisdiction":"ZZ","outcome":{"outcomeType":"something-new-in-v2"}}
        """.trimIndent()
        val outcome = engine(RecordingTransport(body = body)).analyze(request()).outcome
        assertTrue(outcome is InferenceOutcome.Failure)
        assertTrue(outcome.retryable)
    }

    @Test
    fun mapsEveryTaxonomyCodeOntoTheClosedFailureSet() {
        val map = mapOf(
            "malformed-consent" to InferenceFailureCode.INVALID_INPUT,
            "consent-expired" to InferenceFailureCode.INVALID_INPUT,
            "unsupported-version" to InferenceFailureCode.INVALID_INPUT,
            "content-too-large" to InferenceFailureCode.INVALID_INPUT,
            "rate-limited" to InferenceFailureCode.RESOURCE_LIMIT,
            "budget-exceeded" to InferenceFailureCode.RESOURCE_LIMIT,
            "kill-switch" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "market-not-permitted" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "test-mode-forbidden" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "provider-unavailable" to InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            "totally-unknown-code" to InferenceFailureCode.TEMPORARY_UNAVAILABLE,
        )
        map.forEach { (code, expected) ->
            assertEquals(expected, RemoteInferenceEngine.gatewayCodeToFailure(code), code)
        }
    }

    @Test
    fun mapsAnHttpErrorBodyToATypedFailure() = runBlocking {
        val transport = RecordingTransport(status = 503, body = errorBody("kill-switch", true))
        val outcome = engine(transport).analyze(request()).outcome
        assertTrue(outcome is InferenceOutcome.Failure)
        assertEquals(InferenceFailureCode.MODEL_UNAVAILABLE, outcome.code)
        assertTrue(outcome.retryable)
    }

    /** A transport fault is a typed failure, never a leaked exception message. */
    @Test
    fun mapsATransportFaultToARecoverableFailure() = runBlocking {
        val outcome = engine(ThrowingTransport()).analyze(request()).outcome
        assertTrue(outcome is InferenceOutcome.Failure)
        assertTrue(outcome.retryable)
        assertTrue(!outcome.userMessageKey.contains("connection refused"))
    }

    @Test
    fun mapsAMalformedBodyToARecoverableFailure() = runBlocking {
        val outcome = engine(RecordingTransport(body = "not json at all")).analyze(request()).outcome
        assertTrue(outcome is InferenceOutcome.Failure)
    }

    /** AD-7: the Engine contract has nowhere to put a nutrient, and must not invent one. */
    @Test
    fun carriesNoNutritionEvenWhenTheGatewayMisbehaves() = runBlocking {
        val body = """
        {"contractVersion":"mobile-api/v1","requestId":"r","attemptId":"$ATTEMPT",
         "resolvedMarketJurisdiction":"ZZ",
         "outcome":{"outcomeType":"candidates","requiresConfirmation":false,
          "candidates":[{"candidateId":"c1","label":"Salad","rank":1,"calories":214,
           "confidence":{"score":0.9,"band":"high"},"unresolvedAssumptions":[],
           "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
            "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}]}}
        """.trimIndent()
        val outcome = engine(RecordingTransport(body = body)).analyze(request()).outcome
        assertTrue(outcome is InferenceOutcome.Candidates)
        // The unknown `calories` key is ignored; nothing nutrition-shaped survives.
        assertEquals("Salad", outcome.candidates[0].label)
        assertTrue(outcome.candidates[0].toString().lowercase().let { !it.contains("calorie") })
    }

    @Test
    fun sendsTheBearerCredentialOnTheAuthorizationHeader() = runBlocking {
        val transport = RecordingTransport()
        engine(transport).analyze(request())
        assertEquals("Bearer test.installation-0001", transport.lastAuthorization)
    }

    @Test
    fun reportsReadyCapability() = runBlocking {
        assertEquals("gateway-remote", engine(RecordingTransport()).capability().engineId)
    }
}
