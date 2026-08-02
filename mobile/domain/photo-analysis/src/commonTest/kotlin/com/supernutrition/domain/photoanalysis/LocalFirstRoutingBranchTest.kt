package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalFirstRoutingBranchTest {
    private val context = RouteAttemptContext(
        requestId = RequestId("branch-request"),
        configurationVersion = "config-branch",
        configurationDigest = "c".repeat(64),
        photoDigest = "d".repeat(64),
    )
    private val token = RouteAttemptToken(
        attemptId = AttemptId("branch-attempt"),
        generation = 1,
        context = context,
    )
    private val policy = RoutePolicySnapshot(
        policyVersion = "policy-branch",
        policyDigest = "a".repeat(64),
        mode = RoutePolicyMode.REMOTE_WITH_EXPLICIT_CONSENT,
    )
    private val receipt = RemoteConsentReceipt(
        attemptId = token.attemptId,
        requestId = context.requestId,
        photoDigest = context.photoDigest,
        purpose = "photo-analysis",
        marketJurisdiction = "US",
        processingRegion = "us-east",
        policyVersion = policy.policyVersion,
        policyDigest = policy.policyDigest,
        configurationVersion = context.configurationVersion,
        configurationDigest = context.configurationDigest,
        consentCopyVersion = "consent-branch",
        receiptId = "receipt-branch",
        signatureDigest = "e".repeat(64),
    )

    @Test
    fun `granted consent with an invalid receipt remains an offer`() {
        val router = LocalFirstRouter(
            consentVerifier = RemoteConsentVerifier { _, _, _ -> false },
        )

        val decision = router.initial(
            token = token,
            localCapability = CapabilityState.READY,
            policy = policy,
            requestedRoute = RequestedRoute.REMOTE,
            consent = RemoteConsentState.GRANTED,
            consentReceipt = receipt,
        )

        assertIs<RouteDecision.OfferRemote>(decision)
    }

    @Test
    fun `coordinator rejects a second local engine after local execution starts`() {
        val coordinator = RouteAttemptCoordinator(token.attemptId)
        val started = coordinator.start(context)

        assertTrue(coordinator.accepts(RouteDecision.RunLocal(started.nextDecision())))
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunLocal(started.nextDecision().nextDecision()),
            ),
        )
    }

    @Test
    fun `coordinator rejects remote without an offer but accepts it after an offer`() {
        val coordinator = RouteAttemptCoordinator(token.attemptId)
        val started = coordinator.start(context)
        val local = started.nextDecision()

        assertTrue(coordinator.accepts(RouteDecision.RunLocal(local)))
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    local.nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    receipt,
                ),
            ),
        )

        val offer = local.nextDecision().nextDecision()
        assertTrue(
            coordinator.accepts(
                RouteDecision.OfferRemote(
                    offer,
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    policy.policyVersion,
                    policy.policyDigest,
                ),
            ),
        )
        assertTrue(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    offer.nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    receipt,
                ),
            ),
        )
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    offer.nextDecision().nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    receipt,
                ),
            ),
        )
    }
}
