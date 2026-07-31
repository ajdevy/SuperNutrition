package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `an unresolved market is not offered remote text at all`() {
        // OQ-5. Where a market's data-residency position is unsettled, remote
        // text is off rather than defaulting to the nearest provider region.
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
    fun `a market permitting remote text without a processing region is not offered`() {
        // The resolved region must be shown before transfer, so an offer that
        // cannot name one cannot be made.
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
