package com.supernutrition.inference.remote

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire types' compatibility rules, exercised directly.
 *
 * `contracts/mobile-api/v1` fixes the client's decoding behaviour as
 * ignore-unknown / default-on-missing. Since [GatewayCodec] applies those rules
 * by hand, they are only actually verified by decoding envelopes that omit
 * optional fields and carry fields this client has never heard of — which is
 * what a v2 gateway talking to a v1 client looks like.
 */
class GatewayEnvelopeCodecTest {

    private val json = RemoteInferenceEngine.JSON

    private fun node(text: String) = json.parseToJsonElement(text).jsonObject

    @Test
    fun outcomeDefaultsEveryOptionalFieldWhenTheGatewayOmitsIt() {
        val wire = GatewayCodec.decodeOutcome(node("""{"outcomeType":"abstention"}"""))

        assertEquals("abstention", wire.outcomeType)
        assertTrue(wire.candidates.isEmpty())
        assertEquals(false, wire.requiresConfirmation)
        assertNull(wire.reasonCode)
        assertNull(wire.userMessageKey)
        assertNull(wire.failureCode)
        assertNull(wire.recoverable)
        assertNull(wire.retryAfterSeconds)
    }

    @Test
    fun outcomeReadsEveryOptionalFieldWhenTheGatewaySendsIt() {
        val wire = GatewayCodec.decodeOutcome(
            node(
                """{"outcomeType":"failure","requiresConfirmation":true,"reasonCode":"r",
                    "userMessageKey":"k","failureCode":"kill-switch","recoverable":true,
                    "retryAfterSeconds":30,"candidates":[]}""",
            ),
        )

        assertEquals(true, wire.requiresConfirmation)
        assertEquals("r", wire.reasonCode)
        assertEquals("k", wire.userMessageKey)
        assertEquals("kill-switch", wire.failureCode)
        assertEquals(true, wire.recoverable)
        assertEquals(30, wire.retryAfterSeconds)
    }

    @Test
    fun aFieldThisClientHasNeverHeardOfIsIgnoredRatherThanFatal() {
        // A v2 gateway adding a field must not break a v1 client.
        val wire = GatewayCodec.decodeResponse(
            node(
                """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"att_a",
                    "resolvedMarketJurisdiction":"ZZ","somethingFromV2":{"nested":true},
                    "outcome":{"outcomeType":"candidates","unknownOutcomeField":1}}""",
            ),
        )

        assertEquals("ZZ", wire.resolvedMarketJurisdiction)
        assertNull(wire.resolvedProcessingRegion)
        assertEquals("candidates", wire.outcome.outcomeType)
    }

    @Test
    fun candidateDefaultsItsAssumptionListAndReadsProvenance() {
        val wire = GatewayCodec.decodeCandidate(
            node(
                """{"candidateId":"cand_01","label":"Soup","rank":1,
                    "confidence":{"score":0.5,"band":"medium"},
                    "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
                                  "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}""",
            ),
        )

        assertTrue(wire.unresolvedAssumptions.isEmpty())
        assertEquals("Soup", wire.label)
        assertEquals("remote", wire.provenance.route)
        assertEquals(0.5, wire.confidence.score)
    }

    @Test
    fun candidateReadsAssumptionsWhenThePayloadCarriesThem() {
        val wire = GatewayCodec.decodeCandidate(
            node(
                """{"candidateId":"cand_01","label":"Soup","rank":2,
                    "confidence":{"score":0.5,"band":"medium"},
                    "unresolvedAssumptions":[{"code":"portion-unknown","userMessageKey":"k"}],
                    "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
                                  "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}""",
            ),
        )

        assertEquals(listOf("portion-unknown"), wire.unresolvedAssumptions.map { it.code })
        assertEquals(2, wire.rank)
    }

    @Test
    fun errorDefaultsRetryAfterAndToleratesAnAbsentCorrelationId() {
        val wire = GatewayCodec.decodeErrorBody(
            node("""{"error":{"gatewayCode":"rate-limited","userMessageKey":"k","recoverable":true}}"""),
        )

        assertEquals(0, wire.error.retryAfter)
        assertNull(wire.error.correlationId)
        assertEquals("rate-limited", wire.error.gatewayCode)
    }

    @Test
    fun errorReadsRetryAfterAndCorrelationIdWhenPresent() {
        val wire = GatewayCodec.decodeErrorBody(
            node(
                """{"error":{"gatewayCode":"rate-limited","userMessageKey":"k","recoverable":false,
                    "retryAfter":42,"correlationId":"corr-1"}}""",
            ),
        )

        assertEquals(42, wire.error.retryAfter)
        assertEquals("corr-1", wire.error.correlationId)
        assertEquals(false, wire.error.recoverable)
    }

    @Test
    fun theRequestEnvelopeEncodesEveryFieldItWasGiven() {
        val encoded = GatewayCodec.encodeRequest(fullRequest()).toString()

        listOf(
            "\"contractVersion\":\"mobile-api/v1\"",
            "\"attemptId\":\"att_0000000000000001\"",
            "\"effectId\":\"eff_0000000000000001\"",
            "\"modality\":\"photo\"",
            "\"claimedMarketJurisdiction\":\"ZZ\"",
            "\"processingRegion\":\"fixture-region\"",
            "\"consentCopyVersion\":\"photo-consent/fixture-1\"",
            "\"retentionDays\":0",
            "\"priorMealSummary\":\"yesterday\"",
        ).forEach { assertTrue(it in encoded, "missing $it in $encoded") }
    }

    @Test
    fun anAbsentOptionalIsOmittedRatherThanEncodedAsNull() {
        // The gateway validates types, so an explicit null where it expects a
        // string is a rejection rather than an absence.
        val encoded = GatewayCodec.encodeRequest(request(withOptionals = false)).toString()

        assertTrue("null" !in encoded, encoded)
        assertTrue("claimedMarketJurisdiction" !in encoded, encoded)
        assertTrue("priorMealSummary" !in encoded, encoded)
    }

    @Test
    fun aResponseCarryingNutritionHasNowhereToPutIt() {
        // AD-7: the gateway must not send nutrition. If a misbehaving one does,
        // the wire type has no field for it, so it cannot reach the domain.
        val wire = GatewayCodec.decodeResponse(
            node(
                """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"att_a",
                    "resolvedMarketJurisdiction":"ZZ","resolvedProcessingRegion":"fixture-region",
                    "outcome":{"outcomeType":"candidates","candidates":[
                      {"candidateId":"cand_01","label":"Soup","rank":1,"calories":420,
                       "confidence":{"score":0.9,"band":"high"},
                       "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
                                     "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}]}}""",
            ),
        )

        val candidate = wire.outcome.candidates.single()
        assertEquals("Soup", candidate.label)
        assertTrue("calories" !in candidate.toString())
        assertEquals("fixture-region", wire.resolvedProcessingRegion)
    }

    private fun fullRequest() = request(withOptionals = true)

    private fun request(withOptionals: Boolean) = GatewayRequestWire(
        contractVersion = "mobile-api/v1",
        attemptId = "att_0000000000000001",
        effectId = "eff_0000000000000001",
        modality = "photo",
        claimedMarketJurisdiction = if (withOptionals) "ZZ" else null,
        consentReceipt = ConsentReceiptWire(
            version = "1",
            attemptId = "att_0000000000000001",
            digest = "sha256:" + "a".repeat(64),
            modality = "photo",
            purpose = "food-identification",
            decision = "granted",
            marketJurisdiction = "ZZ",
            processingRegion = if (withOptionals) "fixture-region" else null,
            consentCopyVersion = if (withOptionals) "photo-consent/fixture-1" else null,
            retentionVersion = "retention/2",
            retentionDays = 0,
            configurationVersion = "cfg/1",
            recordedAt = "2026-08-03T10:00:00Z",
            expiresAt = "2026-08-03T10:15:00Z",
        ),
        inferenceContext = InferenceContextWire(
            catalogCountry = "ZZ",
            mealType = "unspecified",
            locale = "en-GB",
            measurementSystem = "metric",
            configurationVersion = "cfg/1",
            priorMealSummary = if (withOptionals) "yesterday" else null,
        ),
    )
}
