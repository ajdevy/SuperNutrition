package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exhaustive mapping from the gateway's vocabulary onto the closed domain sets.
 *
 * These are total functions over a frozen taxonomy, so partial testing hides the
 * exact case that matters: a code that silently lands on the `else` branch and
 * tells the user "try again" about something that will never succeed.
 */
private const val MAP_ATTEMPT = "att_0000000000000001"

private class FixedTransport(private val status: Int, private val body: String) : GatewayTransport {
    override suspend fun postInferencePhoto(call: GatewayCall) = GatewayHttpResponse(status, body)
}

private fun mappingEngine(transport: GatewayTransport) = RemoteInferenceEngine(
    transport = transport,
    environment = GatewayEnvironment(
        baseUrl = "http://127.0.0.1:0",
        claimedMarketJurisdiction = "ZZ",
        catalogCountry = "ZZ",
        locale = "en-GB",
        measurementSystem = "metric",
        configurationVersion = "cfg/1",
        authorization = "Bearer test.installation-0001",
    ),
    photoBytes = { byteArrayOf(0xFF.toByte(), 0xD8.toByte()) },
    digest = { "sha256:" + "a".repeat(64) },
    consent = {
        RemoteConsentDecision(
            attemptId = MAP_ATTEMPT,
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
    },
    idFactory = { "0000000000000001" },
    nowEpochMillis = { 1_785_751_500_000L },
)

private fun mappingRequest() = PhotoInferenceRequest(
    photoRef = PhotoRef.issue(AttemptId(MAP_ATTEMPT), "photo0000000001"),
    attemptContext = InferenceAttemptContext(
        attemptId = AttemptId(MAP_ATTEMPT),
        requestId = "req_0000000000000001",
        configurationVersion = "cfg/1",
        configurationDigest = "b".repeat(64),
    ),
)

class GatewayOutcomeMappingTest {

    @Test
    fun everyTaxonomyCodeMapsToItsDeclaredFailureClass() {
        val expected = mapOf(
            "validation-error" to InferenceFailureCode.INVALID_INPUT,
            "invalid-modality" to InferenceFailureCode.INVALID_INPUT,
            "malformed-consent" to InferenceFailureCode.INVALID_INPUT,
            "consent-expired" to InferenceFailureCode.INVALID_INPUT,
            "consent-required" to InferenceFailureCode.INVALID_INPUT,
            "unsupported-media-type" to InferenceFailureCode.INVALID_INPUT,
            "content-too-large" to InferenceFailureCode.INVALID_INPUT,
            "unsupported-version" to InferenceFailureCode.INVALID_INPUT,
            "rate-limited" to InferenceFailureCode.RESOURCE_LIMIT,
            "budget-exceeded" to InferenceFailureCode.RESOURCE_LIMIT,
            "kill-switch" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "market-not-permitted" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "test-mode-forbidden" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "insufficient-scope" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "authentication-failed" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "session-expired" to InferenceFailureCode.MODEL_UNAVAILABLE,
            "token-replayed" to InferenceFailureCode.MODEL_UNAVAILABLE,
        )

        expected.forEach { (code, failure) ->
            assertEquals(failure, RemoteInferenceEngine.gatewayCodeToFailure(code), code)
        }
        // An unrecognised code is recoverable rather than terminal: a v2 gateway
        // adding a code must not strand a v1 client.
        assertEquals(
            InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            RemoteInferenceEngine.gatewayCodeToFailure("something-from-v2"),
        )
    }

    @Test
    fun outcomeLevelFailureCodesMapAcrossEveryBranch() {
        assertEquals(InferenceFailureCode.INVALID_INPUT, RemoteInferenceEngine.failureCodeFor("content-rejected"))
        assertEquals(InferenceFailureCode.RESOURCE_LIMIT, RemoteInferenceEngine.failureCodeFor("budget-exceeded"))
        assertEquals(InferenceFailureCode.RESOURCE_LIMIT, RemoteInferenceEngine.failureCodeFor("rate-limited"))
        assertEquals(InferenceFailureCode.MODEL_UNAVAILABLE, RemoteInferenceEngine.failureCodeFor("kill-switch"))
        assertEquals(InferenceFailureCode.TEMPORARY_UNAVAILABLE, RemoteInferenceEngine.failureCodeFor("unheard-of"))
        assertEquals(InferenceFailureCode.TEMPORARY_UNAVAILABLE, RemoteInferenceEngine.failureCodeFor(null))
    }

    @Test
    fun everyAssumptionCodeMapsAndUnknownOnesAreDropped() {
        assertEquals(AssumptionCode.PORTION_UNKNOWN, RemoteInferenceEngine.assumptionCodeFor("portion-unknown"))
        assertEquals(
            AssumptionCode.PREPARATION_UNKNOWN,
            RemoteInferenceEngine.assumptionCodeFor("preparation-unknown"),
        )
        assertEquals(
            AssumptionCode.HIDDEN_INGREDIENTS_UNKNOWN,
            RemoteInferenceEngine.assumptionCodeFor("hidden-ingredients-unknown"),
        )
        assertEquals(
            AssumptionCode.HIDDEN_INGREDIENTS_UNKNOWN,
            RemoteInferenceEngine.assumptionCodeFor("ingredient-hidden"),
        )
        assertEquals(AssumptionCode.MIXED_DISH, RemoteInferenceEngine.assumptionCodeFor("mixed-dish"))
        // Dropped, not coerced onto a neighbouring code that would mislead.
        assertNull(RemoteInferenceEngine.assumptionCodeFor("garnish-unknown"))
    }

    @Test
    fun anEmptyCandidateListBecomesAbstentionRatherThanAnEmptyReview() {
        val body = """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"$MAP_ATTEMPT",
            "resolvedMarketJurisdiction":"ZZ","outcome":{"outcomeType":"candidates","candidates":[]}}"""

        val outcome = runBlocking { mappingEngine(FixedTransport(200, body)).analyze(mappingRequest()) }.outcome

        assertTrue(outcome is InferenceOutcome.Abstention, "got $outcome")
    }

    @Test
    fun anAbstentionWithoutAMessageKeyFallsBackRatherThanRenderingBlank() {
        val body = """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"$MAP_ATTEMPT",
            "resolvedMarketJurisdiction":"ZZ","outcome":{"outcomeType":"abstention"}}"""

        val outcome = runBlocking { mappingEngine(FixedTransport(200, body)).analyze(mappingRequest()) }.outcome

        assertTrue(outcome is InferenceOutcome.Abstention)
        assertEquals("inference.abstention.no_food_detected", outcome.userMessageKey)
    }

    @Test
    fun anOutcomeFailureWithoutRecoverabilityDefaultsToTerminal() {
        val body = """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"$MAP_ATTEMPT",
            "resolvedMarketJurisdiction":"ZZ",
            "outcome":{"outcomeType":"failure","failureCode":"content-rejected"}}"""

        val outcome = runBlocking { mappingEngine(FixedTransport(200, body)).analyze(mappingRequest()) }.outcome

        assertTrue(outcome is InferenceOutcome.Failure)
        assertEquals(InferenceFailureCode.INVALID_INPUT, outcome.code)
        // Absent `recoverable` must not be read as "retry": a rejected image
        // will be rejected again.
        assertEquals(false, outcome.retryable)
    }

    @Test
    fun anUnparseableErrorBodyStillYieldsATypedFailure() {
        val outcome = runBlocking {
            mappingEngine(FixedTransport(503, "<html>gateway timeout</html>")).analyze(mappingRequest())
        }.outcome

        assertTrue(outcome is InferenceOutcome.Failure)
        assertEquals(InferenceFailureCode.TEMPORARY_UNAVAILABLE, outcome.code)
        assertTrue(outcome.retryable)
    }

    @Test
    fun anAssumptionThisClientCannotNameIsDroppedFromAnOtherwiseGoodCandidate() {
        val body = """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"$MAP_ATTEMPT",
            "resolvedMarketJurisdiction":"ZZ","outcome":{"outcomeType":"candidates","candidates":[
              {"candidateId":"cand_01","label":"Soup","rank":1,
               "confidence":{"score":0.9,"band":"high"},
               "unresolvedAssumptions":[{"code":"portion-unknown","userMessageKey":"a"},
                                        {"code":"from-v2","userMessageKey":"b"}],
               "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
                             "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}]}}"""

        val outcome = runBlocking { mappingEngine(FixedTransport(200, body)).analyze(mappingRequest()) }.outcome

        assertTrue(outcome is InferenceOutcome.Candidates)
        val assumptions = outcome.candidates.single().unresolvedAssumptions
        assertEquals(listOf(AssumptionCode.PORTION_UNKNOWN), assumptions.map { it.code })
    }
}
