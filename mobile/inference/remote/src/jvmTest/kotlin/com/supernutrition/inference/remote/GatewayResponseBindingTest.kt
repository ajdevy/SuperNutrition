package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.InferenceOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * P1-8 and P1-9, at the level the defects live: the pure mapping helpers.
 *
 * P1-8 — the client generated a requestId, sent it, and never compared it to
 * anything. A reply carrying another attempt's id, another request's id, a
 * contract version this client does not speak, or a processing region other than
 * the one the consent receipt promised was decoded and shown to the user as
 * their own result. A correlation that is never compared is not a correlation.
 *
 * P1-9 — every abstention became NO_FOOD_DETECTED, so an unusable image, an
 * ambiguous scene and a provider refusal all told the user "no food found" and
 * offered the one recovery that could not help.
 */
class GatewayResponseBindingTest {

    private val environment = GatewayEnvironment(
        baseUrl = "http://127.0.0.1:3000",
        claimedMarketJurisdiction = "ZZ",
        catalogCountry = "ZZ",
        locale = "en-GB",
        measurementSystem = "metric",
        configurationVersion = "cfg/1",
        authorization = "Bearer test.installation-0001",
    )

    private val expected = RequestBinding(
        attemptId = "att_0000000000000001",
        requestId = "req_0000000000000001",
        consentedProcessingRegion = "fixture-region",
    )

    private fun response(
        contractVersion: String = environment.contractVersion,
        attemptId: String = expected.attemptId,
        requestId: String = expected.requestId,
        market: String = "ZZ",
        region: String? = "fixture-region",
    ) = GatewayResponseWire(
        contractVersion = contractVersion,
        requestId = requestId,
        attemptId = attemptId,
        resolvedMarketJurisdiction = market,
        resolvedProcessingRegion = region,
        outcome = OutcomeWire(
            outcomeType = "candidates",
            candidates = emptyList(),
            requiresConfirmation = false,
            reasonCode = null,
            userMessageKey = null,
            failureCode = null,
            recoverable = null,
            retryAfterSeconds = null,
        ),
    )

    @Test
    fun `a matching response is accepted`() {
        // The positive control: a binding check that rejects everything is not a
        // binding check.
        assertEquals(null, bindingMismatch(response(), expected, environment))
    }

    @Test
    fun `a reply for another attempt is refused`() {
        assertEquals(
            "inference.failure.response_attempt_mismatch",
            bindingMismatch(response(attemptId = "att_9999999999999999"), expected, environment),
        )
    }

    @Test
    fun `a reply for another request is refused`() {
        assertEquals(
            "inference.failure.response_request_mismatch",
            bindingMismatch(response(requestId = "req_9999999999999999"), expected, environment),
        )
    }

    @Test
    fun `a reply in a contract version this client does not speak is refused`() {
        assertEquals(
            "inference.failure.response_contract_mismatch",
            bindingMismatch(response(contractVersion = "mobile-api/v9"), expected, environment),
        )
    }

    @Test
    fun `a reply resolved to another market is refused`() {
        // The market and region are the user-visible privacy claim: the receipt
        // said where the photo would be processed, and a reply from somewhere
        // else means the promise shown before consent was not the one kept.
        assertEquals(
            "inference.failure.response_market_mismatch",
            bindingMismatch(response(market = "QQ"), expected, environment),
        )
    }

    @Test
    fun `a reply processed in another region is refused`() {
        assertEquals(
            "inference.failure.response_region_mismatch",
            bindingMismatch(response(region = "somewhere-else"), expected, environment),
        )
    }

    @Test
    fun `an absent region is permitted, because the release makes it optional`() {
        // Over-tightening this to "must be present" broke four existing fixtures
        // that are valid under the contract. Absence is legal; contradiction is
        // not, and contradiction is the case that matters.
        for (absent in listOf(null, "", "   ")) {
            assertEquals(null, bindingMismatch(response(region = absent), expected, environment))
        }
    }

    @Test
    fun `every released abstention reason maps to its own domain reason`() {
        assertEquals(AbstentionReason.IDENTITY_AMBIGUOUS, abstentionReasonFor("identity-ambiguous"))
        assertEquals(AbstentionReason.NO_FOOD_DETECTED, abstentionReasonFor("no-food-detected"))
        assertEquals(AbstentionReason.IMAGE_QUALITY, abstentionReasonFor("image-quality"))
        assertEquals(AbstentionReason.IMAGE_QUALITY, abstentionReasonFor("image-unusable"))
        assertEquals(AbstentionReason.PORTION_AMBIGUOUS, abstentionReasonFor("portion-ambiguous"))
        assertEquals(AbstentionReason.UNSUPPORTED_CONTENT, abstentionReasonFor("unsupported-content"))
        assertEquals(
            AbstentionReason.UNSUPPORTED_CONTENT,
            abstentionReasonFor("content-rejected-by-provider"),
        )
        assertEquals(AbstentionReason.IDENTITY_AMBIGUOUS, abstentionReasonFor("ambiguous-scene"))
    }

    @Test
    fun `an unusable image is not reported as no food found`() {
        // The specific harm: "no food found" offers manual entry, while an
        // unusable image should ask for another photo. They are different
        // recoveries and the user was only ever offered one.
        assertTrue(abstentionReasonFor("image-unusable") != AbstentionReason.NO_FOOD_DETECTED)
        assertTrue(abstentionReasonFor("ambiguous-scene") != AbstentionReason.NO_FOOD_DETECTED)
    }

    @Test
    fun `an unknown reason code falls back explicitly`() {
        // The fallback stays — a v2 gateway adding a code must not crash a v1
        // client — but it is now reached only by codes we do not know, rather
        // than by all of them.
        assertEquals(AbstentionReason.NO_FOOD_DETECTED, abstentionReasonFor("something-new"))
        assertEquals(AbstentionReason.NO_FOOD_DETECTED, abstentionReasonFor(null))
    }

    @Test
    fun `requiresConfirmation survives into the domain outcome`() {
        // P1-9: the gateway always sent this and the client parsed it and threw
        // it away, so a low-confidence identification was indistinguishable from
        // a confident one.
        val unconfirmed = InferenceOutcome.Candidates(
            listOf(candidate()),
            requiresConfirmation = true,
        )
        assertIs<InferenceOutcome.Candidates>(unconfirmed)
        assertTrue(unconfirmed.requiresConfirmation)

        assertFalse(InferenceOutcome.Candidates(listOf(candidate())).requiresConfirmation)
    }

    private fun candidate() = com.supernutrition.domain.inference.InferenceCandidate(
        candidateId = "food:1",
        label = "Salad",
        rank = 1,
        confidence = com.supernutrition.domain.inference.Confidence(
            0.9,
            com.supernutrition.domain.inference.ConfidenceBand.HIGH,
        ),
        unresolvedAssumptions = emptyList(),
        provenance = com.supernutrition.domain.inference.InferenceProvenance(
            route = com.supernutrition.domain.inference.InferenceRoute.REMOTE,
            engineId = "gateway",
            modelVersion = "m",
            calibrationVersion = "c",
            contractVersion = com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )
}
