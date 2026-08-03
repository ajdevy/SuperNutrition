package com.supernutrition.domain.nutrition

import com.supernutrition.core.config.MarketId
import com.supernutrition.core.config.MarketPolicy
import com.supernutrition.core.config.MarketPolicyRegistry
import com.supernutrition.core.config.RemotePermission
import com.supernutrition.core.config.ResidencyPosture
import com.supernutrition.core.config.TransferDecision
import com.supernutrition.core.config.TransferModality
import com.supernutrition.core.config.TransferRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RemoteTextEligibilityTest {
    private val resolvedMarket = MarketTextPolicy(
        remoteTextPermitted = true,
        textConsentCopyVersion = "text-transfer-v1",
        processingRegion = "us-east",
    )

    @Test
    fun `remote is offered after local abstains`() {
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            market = resolvedMarket,
        )

        assertEquals(
            RemoteTextDecision.Offer(
                reason = RemoteEligibilityReason.LOCAL_ABSTENTION,
                consentCopyVersion = "text-transfer-v1",
                processingRegion = "us-east",
            ),
            decision,
        )
    }

    @Test
    fun `remote is offered when no local engine exists`() {
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED),
            market = resolvedMarket,
        )

        assertEquals(RemoteEligibilityReason.LOCAL_UNAVAILABLE, (decision as RemoteTextDecision.Offer).reason)
    }

    @Test
    fun `remote is not offered after a successful local result`() {
        // AD-6's closed trigger set. Policy cannot invent a fourth reason, so a
        // confident local answer never becomes an upload prompt.
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Segmented(
                listOf(DetectedItem("food:egg-boiled", TextSpan(0, 10))),
            ),
            market = resolvedMarket,
        )

        assertEquals(RemoteTextDecision.NotOffered, decision)
    }

    @Test
    fun `a market that explicitly forbids remote text is not offered it`() {
        // A market can decline remote text outright, independent of whether its
        // data-residency position is otherwise settled. This is that decision,
        // not the residency-unresolved case -- see the next test for that one.
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            market = MarketTextPolicy(
                remoteTextPermitted = false,
                textConsentCopyVersion = null,
                processingRegion = null,
            ),
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }

    @Test
    fun `a market permitting remote text without text consent copy is not offered`() {
        // The photo disclosure says a smaller copy of the photo is sent, which
        // is false for a description. A market cannot borrow it.
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            market = MarketTextPolicy(
                remoteTextPermitted = true,
                textConsentCopyVersion = null,
                processingRegion = "us-east",
            ),
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }

    @Test
    fun `a market permitting remote text with an unresolved data-residency position is not offered`() {
        // OQ-5. Where a market's data-residency position is unsettled, remote
        // text is off rather than defaulting to the nearest provider region.
        // Distinct from the explicit-forbid test above: this market permits
        // remote text and has a text consent copy, but names no resolved
        // processing region -- so this is the only field the "unresolved"
        // branch can be attributed to, and deleting that branch alone would
        // fail this test specifically.
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            market = MarketTextPolicy(
                remoteTextPermitted = true,
                textConsentCopyVersion = "text-transfer-v1",
                processingRegion = null,
            ),
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }
}

/**
 * The registry-based [RemoteTextEligibility.decide] overload -- G-4's third
 * satisfaction criterion, a real caller that resolves the market from
 * [MarketPolicyRegistry] instead of a hand-built [MarketTextPolicy] -- plus
 * the journey from that offer through [MarketPolicyRegistry.mayTransfer]'s
 * transfer-time re-check, covered end to end as the gate asks for.
 */
class RemoteTextEligibilityRegistryTest {
    private val today = 20_000L
    private val marketId = MarketId("ZZ")

    private val base = MarketPolicy(
        marketId = marketId,
        jurisdiction = "fixture-jurisdiction",
        residencyPosture = ResidencyPosture.PERMITTED_WITH_REGION,
        permittedProcessingRegions = listOf("region-1"),
        photoRemotePermission = RemotePermission.PERMITTED,
        textRemotePermission = RemotePermission.PERMITTED,
        photoConsentCopyVersion = "photo-copy-v1",
        textConsentCopyVersion = "text-copy-v1",
        acceptedInputLanguages = listOf("en"),
        legalReviewApprover = "fixture-approver",
        legalReviewExpiresOnEpochDay = today + 365,
    )

    @Test
    fun `a market absent from the registry is not offered remote text`() {
        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            marketId = marketId,
            registry = MarketPolicyRegistry(emptyList()),
            todayEpochDay = today,
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }

    @Test
    fun `a resolved market that forbids text transfer is not offered it`() {
        val registry = MarketPolicyRegistry(listOf(base.copy(textRemotePermission = RemotePermission.FORBIDDEN)))

        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            marketId = marketId,
            registry = registry,
            todayEpochDay = today,
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }

    @Test
    fun `a resolved market permitting text with one region is offered`() {
        val registry = MarketPolicyRegistry(listOf(base))

        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            marketId = marketId,
            registry = registry,
            todayEpochDay = today,
        )

        assertEquals(
            RemoteTextDecision.Offer(
                reason = RemoteEligibilityReason.LOCAL_ABSTENTION,
                consentCopyVersion = "text-copy-v1",
                processingRegion = "region-1",
            ),
            decision,
        )
    }

    @Test
    fun `a market permitting more than one region is not offered -- which one is not resolved`() {
        val registry = MarketPolicyRegistry(
            listOf(base.copy(permittedProcessingRegions = listOf("region-1", "region-2"))),
        )

        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            marketId = marketId,
            registry = registry,
            todayEpochDay = today,
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }

    @Test
    fun `a market permitting text with no permitted region is not offered`() {
        val registry = MarketPolicyRegistry(listOf(base.copy(permittedProcessingRegions = emptyList())))

        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
            marketId = marketId,
            registry = registry,
            todayEpochDay = today,
        )

        assertEquals(RemoteTextDecision.NotPermitted, decision)
    }

    @Test
    fun `a successful local result is not offered escalation regardless of market policy`() {
        val registry = MarketPolicyRegistry(listOf(base))

        val decision = RemoteTextEligibility.decide(
            localOutcome = TextEngineOutcome.Segmented(listOf(DetectedItem("food:egg-boiled", TextSpan(0, 10)))),
            marketId = marketId,
            registry = registry,
            todayEpochDay = today,
        )

        assertEquals(RemoteTextDecision.NotOffered, decision)
    }

    @Test
    fun `an offer's own copy version is authorized by the same registry at transfer time`() {
        // The end-to-end journey G-4 asks for: an offer this decision granted
        // must also survive the independent transfer-time re-check, using the
        // exact copy version the offer carried -- not a hand-typed literal
        // that happens to match, which would prove nothing about the two
        // gates actually agreeing.
        val registry = MarketPolicyRegistry(listOf(base))

        val offer = assertIs<RemoteTextDecision.Offer>(
            RemoteTextEligibility.decide(
                localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
                marketId = marketId,
                registry = registry,
                todayEpochDay = today,
            ),
        )
        val transfer = registry.mayTransfer(marketId, TransferModality.TEXT, offer.consentCopyVersion, today)

        assertEquals(
            TransferDecision.Permitted(listOf("region-1"), "text-copy-v1"),
            transfer,
        )
    }

    @Test
    fun `presenting a stale copy version at transfer time is refused even though the offer looked fine`() {
        // Demonstrates why the two gates stay separate rather than collapsing
        // into one: the offer was genuinely fine when it was computed, but the
        // transfer-time re-check still refuses a copy version that does not
        // match what the market currently authorises -- catching a market
        // policy change (or a substituted copy) between offer and transfer.
        val registry = MarketPolicyRegistry(listOf(base))
        val offer = assertIs<RemoteTextDecision.Offer>(
            RemoteTextEligibility.decide(
                localOutcome = TextEngineOutcome.Abstained("description-unsegmentable"),
                marketId = marketId,
                registry = registry,
                todayEpochDay = today,
            ),
        )
        check(offer.consentCopyVersion == "text-copy-v1")

        val transfer = registry.mayTransfer(marketId, TransferModality.TEXT, "photo-copy-v1", today)

        assertEquals(TransferDecision.Refused(TransferRefusal.CONSENT_COPY_MISMATCH), transfer)
    }
}
