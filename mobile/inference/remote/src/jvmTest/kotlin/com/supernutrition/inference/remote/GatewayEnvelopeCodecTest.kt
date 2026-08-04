package com.supernutrition.inference.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire types' compatibility rules, exercised directly.
 *
 * `contracts/mobile-api/v1` fixes the client's decoding behaviour as
 * ignore-unknown / default-on-missing. Those rules live in the generated
 * serializers, so they are only actually verified by decoding envelopes that
 * omit optional fields and carry fields this client has never heard of — which
 * is what a v2 gateway talking to a v1 client looks like.
 */
class GatewayEnvelopeCodecTest {

    private val json = RemoteInferenceEngine.JSON

    @Test
    fun outcomeDefaultsEveryOptionalFieldWhenTheGatewayOmitsIt() {
        val wire = json.decodeFromString(OutcomeWire.serializer(), """{"outcomeType":"abstention"}""")

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
        val wire = json.decodeFromString(
            OutcomeWire.serializer(),
            """{"outcomeType":"failure","requiresConfirmation":true,"reasonCode":"r",
               "userMessageKey":"k","failureCode":"kill-switch","recoverable":true,
               "retryAfterSeconds":30,"candidates":[]}""",
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
        val wire = json.decodeFromString(
            GatewayResponseWire.serializer(),
            """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"att_a",
               "resolvedMarketJurisdiction":"ZZ","somethingFromV2":{"nested":true},
               "outcome":{"outcomeType":"candidates","unknownOutcomeField":1}}""",
        )

        assertEquals("ZZ", wire.resolvedMarketJurisdiction)
        assertNull(wire.resolvedProcessingRegion)
        assertEquals("candidates", wire.outcome.outcomeType)
    }

    @Test
    fun candidateDefaultsItsAssumptionListAndReadsProvenance() {
        val wire = json.decodeFromString(
            CandidateWire.serializer(),
            """{"candidateId":"cand_01","label":"Soup","rank":1,
               "confidence":{"score":0.5,"band":"medium"},
               "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
                             "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}""",
        )

        assertTrue(wire.unresolvedAssumptions.isEmpty())
        assertEquals("Soup", wire.label)
        assertEquals("remote", wire.provenance.route)
        assertEquals(0.5, wire.confidence.score)
    }

    @Test
    fun errorDefaultsRetryAfterAndToleratesAnAbsentCorrelationId() {
        val wire = json.decodeFromString(
            GatewayErrorBodyWire.serializer(),
            """{"error":{"gatewayCode":"rate-limited","userMessageKey":"k","recoverable":true}}""",
        )

        assertEquals(0, wire.error.retryAfter)
        assertNull(wire.error.correlationId)
        assertEquals("rate-limited", wire.error.gatewayCode)
    }

    @Test
    fun theRequestEnvelopeRoundTripsWithoutLosingAField() {
        val original = GatewayRequestWire(
            contractVersion = "mobile-api/v1",
            attemptId = "att_0000000000000001",
            effectId = "eff_0000000000000001",
            modality = "photo",
            claimedMarketJurisdiction = "ZZ",
            consentReceipt = ConsentReceiptWire(
                version = "1",
                attemptId = "att_0000000000000001",
                digest = "sha256:" + "a".repeat(64),
                modality = "photo",
                purpose = "food-identification",
                decision = "granted",
                marketJurisdiction = "ZZ",
                processingRegion = "fixture-region",
                consentCopyVersion = "photo-consent/fixture-1",
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
                priorMealSummary = "yesterday",
            ),
        )

        val decoded = json.decodeFromString(
            GatewayRequestWire.serializer(),
            json.encodeToString(GatewayRequestWire.serializer(), original),
        )

        assertEquals(original, decoded)
    }

    @Test
    fun theOptionalPriorMealSummaryIsAbsentRatherThanEmptyWhenUnset() {
        val context = json.decodeFromString(
            InferenceContextWire.serializer(),
            """{"catalogCountry":"ZZ","mealType":"unspecified","locale":"en-GB",
               "measurementSystem":"metric","configurationVersion":"cfg/1"}""",
        )

        assertNull(context.priorMealSummary)
    }

    @Test
    fun aResponseCarryingNutritionIsDecodedWithoutSurfacingIt() {
        // AD-7: the gateway must not send nutrition. If a misbehaving one does,
        // the wire type has nowhere to put it, so it cannot reach the domain.
        val wire = json.decodeFromString(
            GatewayResponseWire.serializer(),
            """{"contractVersion":"mobile-api/v1","requestId":"req_a","attemptId":"att_a",
               "resolvedMarketJurisdiction":"ZZ","resolvedProcessingRegion":"fixture-region",
               "outcome":{"outcomeType":"candidates","candidates":[
                 {"candidateId":"cand_01","label":"Soup","rank":1,"calories":420,
                  "confidence":{"score":0.9,"band":"high"},
                  "provenance":{"route":"remote","engineId":"e","modelVersion":"m",
                                "calibrationVersion":"c","contractVersion":"photo-inference-engine/v1"}}]}}""",
        )

        val encoded = Json.encodeToString(CandidateWire.serializer(), wire.outcome.candidates.single())
        assertTrue("calories" !in encoded, encoded)
    }
}
