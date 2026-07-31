package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalFirstRoutingTest {
    private val routeContext = RouteAttemptContext(
        requestId = RequestId("route-request"),
        configurationVersion = "config-1",
        configurationDigest = "c".repeat(64),
        photoDigest = "d".repeat(64),
    )
    private val token = RouteAttemptToken(AttemptId("route-attempt"), generation = 1, context = routeContext)
    private val policy = RoutePolicySnapshot("route-v1", "a".repeat(64), RoutePolicyMode.REMOTE_WITH_EXPLICIT_CONSENT)
    private val consentReceipt = RemoteConsentReceipt(
        attemptId = token.attemptId,
        requestId = routeContext.requestId,
        photoDigest = routeContext.photoDigest,
        purpose = "photo-analysis",
        marketJurisdiction = "US",
        processingRegion = "us-east",
        policyVersion = policy.policyVersion,
        policyDigest = policy.policyDigest,
        configurationVersion = routeContext.configurationVersion,
        configurationDigest = routeContext.configurationDigest,
        consentCopyVersion = "photo-consent-v1",
        receiptId = "receipt-1",
        signatureDigest = "e".repeat(64),
    )
    private val consentRouter = LocalFirstRouter(
        consentVerifier = RemoteConsentVerifier { receipt, routeToken, routePolicy ->
            receipt == consentReceipt && routeToken == token && routePolicy == policy
        },
    )
    private val g5Router = LocalFirstRouter(
        gateG5Verifier = GateG5EvidenceVerifier { threshold, _ -> threshold.evidenceId == "g5-report-1" },
    )
    private val provenance = InferenceProvenance(
        InferenceRoute.LOCAL,
        "fixture-engine",
        "fixture-model",
        "calibration",
        PHOTO_INFERENCE_CONTRACT_V1,
    )

    @Test
    fun `ready local capability is selected before remote`() {
        val decision = LocalFirstRouter().initial(token, CapabilityState.READY, policy)

        assertIs<RouteDecision.RunLocal>(decision)
        assertEquals("route-attempt:route-request:1:1", decision.decisionId)
    }

    @Test
    fun `unavailable local capability offers remote only with explicit consent`() {
        val router = LocalFirstRouter()

        val offer = router.initial(token, CapabilityState.MODEL_REQUIRED, policy)
        assertEquals(RemoteEligibilityReason.LOCAL_UNAVAILABLE, assertIs<RouteDecision.OfferRemote>(offer).reason)

        val run = consentRouter.initial(
            token,
            CapabilityState.MODEL_REQUIRED,
            policy,
            consent = RemoteConsentState.GRANTED,
            consentReceipt = consentReceipt,
        )
        assertEquals(RemoteEligibilityReason.LOCAL_UNAVAILABLE, assertIs<RouteDecision.RunRemote>(run).reason)

        val declined = router.initial(
            token,
            CapabilityState.MODEL_REQUIRED,
            policy,
            consent = RemoteConsentState.DECLINED,
        )
        assertEquals(LocalTerminalReason.REMOTE_DECLINED, assertIs<RouteDecision.KeepLocal>(declined).reason)
    }

    @Test
    fun `local-only policy never invents remote escalation`() {
        val localOnly = policy.copy(mode = RoutePolicyMode.LOCAL_ONLY)
        val decision = LocalFirstRouter().initial(token, CapabilityState.MODEL_REQUIRED, localOnly)

        assertEquals(LocalTerminalReason.REMOTE_DISABLED, assertIs<RouteDecision.KeepLocal>(decision).reason)
        val explicit = LocalFirstRouter().initial(token, CapabilityState.READY, localOnly, RequestedRoute.REMOTE)
        assertEquals(LocalTerminalReason.REMOTE_DISABLED, assertIs<RouteDecision.Blocked>(explicit).reason)
    }

    @Test
    fun `explicit local selection stays local when local capability is unavailable`() {
        val decision = LocalFirstRouter().initial(
            token,
            CapabilityState.MODEL_REQUIRED,
            policy,
            requestedRoute = RequestedRoute.LOCAL,
        )

        assertEquals(LocalTerminalReason.LOCAL_FAILURE, assertIs<RouteDecision.KeepLocal>(decision).reason)
    }

    @Test
    fun `explicit local selection runs when local capability is ready`() {
        assertIs<RouteDecision.RunLocal>(
            LocalFirstRouter().initial(
                token,
                CapabilityState.READY,
                policy,
                requestedRoute = RequestedRoute.LOCAL,
            ),
        )
    }

    @Test
    fun `explicit remote selection runs after consent`() {
        val decision = consentRouter.initial(
            token,
            CapabilityState.READY,
            policy,
            requestedRoute = RequestedRoute.REMOTE,
            consent = RemoteConsentState.GRANTED,
            consentReceipt = consentReceipt,
        )

        assertEquals(
            RemoteEligibilityReason.EXPLICIT_USER_REQUEST,
            assertIs<RouteDecision.RunRemote>(decision).reason,
        )
    }

    @Test
    fun `bare granted state cannot authorize remote execution`() {
        val decision = LocalFirstRouter().initial(
            token,
            CapabilityState.READY,
            policy,
            requestedRoute = RequestedRoute.REMOTE,
            consent = RemoteConsentState.GRANTED,
        )

        assertIs<RouteDecision.OfferRemote>(decision)
    }

    @Test
    fun `abstention and model unavailable are the only failure triggers`() {
        val router = LocalFirstRouter()
        val abstention = router.afterLocal(token, abstention(), policy)
        assertEquals(RemoteEligibilityReason.LOCAL_ABSTENTION, assertIs<RouteDecision.OfferRemote>(abstention).reason)

        val unavailable = router.afterLocal(token, failure(InferenceFailureCode.MODEL_UNAVAILABLE), policy)
        assertEquals(RemoteEligibilityReason.LOCAL_UNAVAILABLE, assertIs<RouteDecision.OfferRemote>(unavailable).reason)

        val otherFailure = router.afterLocal(token, failure(InferenceFailureCode.RESOURCE_LIMIT), policy)
        assertEquals(LocalTerminalReason.LOCAL_FAILURE, assertIs<RouteDecision.KeepLocal>(otherFailure).reason)
    }

    @Test
    fun `G5 low confidence is opt in and high confidence stays local`() {
        val thresholded = policy.copy(gateG5 = GateG5LowConfidenceThreshold(0.8, "g5-report-1"))
        val router = LocalFirstRouter()

        val low = g5Router.afterLocal(token, candidates(0.79, ConfidenceBand.MEDIUM), thresholded)
        assertEquals(RemoteEligibilityReason.LOCAL_LOW_CONFIDENCE, assertIs<RouteDecision.OfferRemote>(low).reason)

        val high = g5Router.afterLocal(token, candidates(0.91, ConfidenceBand.HIGH), thresholded)
        assertEquals(LocalTerminalReason.SUCCESS, assertIs<RouteDecision.KeepLocal>(high).reason)

        val beforeG5 = router.afterLocal(token, candidates(0.1, ConfidenceBand.LOW), policy)
        assertEquals(LocalTerminalReason.SUCCESS, assertIs<RouteDecision.KeepLocal>(beforeG5).reason)

        val unverified = LocalFirstRouter().afterLocal(token, candidates(0.1, ConfidenceBand.LOW), thresholded)
        assertEquals(LocalTerminalReason.SUCCESS, assertIs<RouteDecision.KeepLocal>(unverified).reason)
    }

    @Test
    fun `explicit remote request needs consent and carries typed policy evidence`() {
        val offer = assertIs<RouteDecision.OfferRemote>(
            LocalFirstRouter().initial(token, CapabilityState.READY, policy, RequestedRoute.REMOTE),
        )

        assertEquals(RemoteEligibilityReason.EXPLICIT_USER_REQUEST, offer.reason)
        assertEquals(policy.policyVersion, offer.policyVersion)
        assertEquals(policy.policyDigest, offer.policyDigest)
        assertTrue(offer.decisionId.isNotBlank())
    }

    @Test
    fun `explicit remote request can reopen consent after an earlier decline`() {
        val offer = assertIs<RouteDecision.OfferRemote>(
            LocalFirstRouter().initial(
                token,
                CapabilityState.READY,
                policy,
                requestedRoute = RequestedRoute.REMOTE,
                consent = RemoteConsentState.DECLINED,
            ),
        )

        assertEquals(RemoteEligibilityReason.EXPLICIT_USER_REQUEST, offer.reason)
    }

    @Test
    fun `attempt coordinator rejects stale and cancelled emissions`() {
        val coordinator = RouteAttemptCoordinator(AttemptId("attempt"))
        val first = coordinator.start(routeContext)
        val replacement = coordinator.replace(routeContext)

        assertFalse(coordinator.accepts(RouteDecision.RunLocal(first.nextDecision())))
        val accepted = RouteDecision.RunLocal(replacement.nextDecision())
        assertTrue(coordinator.accepts(accepted))
        assertFalse(coordinator.accepts(accepted))
        assertFalse(coordinator.accepts(RouteDecision.RunLocal(replacement.nextDecision())))
        coordinator.cancel()
        assertFalse(coordinator.accepts(RouteDecision.RunLocal(replacement.nextDecision())))
    }

    @Test
    fun `attempt coordinator rejects foreign context and local sequencing`() {
        val coordinator = RouteAttemptCoordinator(AttemptId("sequenced-attempt"))
        val first = coordinator.start(routeContext)

        assertFalse(
            coordinator.accepts(
                RouteDecision.RunLocal(first.nextDecision().copy(attemptId = AttemptId("foreign"))),
            ),
        )
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunLocal(
                    first.nextDecision().copy(context = routeContext.copy(photoDigest = "f".repeat(64))),
                ),
            ),
        )
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunLocal(first.nextDecision().copy(generation = 2)),
            ),
        )

        assertTrue(coordinator.accepts(RouteDecision.RunLocal(first.nextDecision())))
        assertFalse(coordinator.accepts(RouteDecision.RunLocal(first.nextDecision().nextDecision())))
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    first.nextDecision().nextDecision(),
                    RemoteEligibilityReason.EXPLICIT_USER_REQUEST,
                    consentReceipt,
                ),
            ),
        )
    }

    @Test
    fun `attempt coordinator permits remote after offer and stops terminal`() {
        val coordinator = RouteAttemptCoordinator(AttemptId("sequenced-attempt"))
        val replacement = coordinator.replace(routeContext)
        assertTrue(
            coordinator.accepts(
                RouteDecision.OfferRemote(
                    replacement.nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    policy.policyVersion,
                    policy.policyDigest,
                ),
            ),
        )
        assertTrue(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    replacement.nextDecision().nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    consentReceipt,
                ),
            ),
        )
        assertFalse(
            coordinator.accepts(
                RouteDecision.OfferRemote(
                    replacement.nextDecision().nextDecision().nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    policy.policyVersion,
                    policy.policyDigest,
                ),
            ),
        )
        assertFalse(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    replacement.nextDecision().nextDecision().nextDecision(),
                    RemoteEligibilityReason.LOCAL_ABSTENTION,
                    consentReceipt,
                ),
            ),
        )
    }

    @Test
    fun `attempt coordinator rejects after terminal and permits remote first`() {
        val coordinator = RouteAttemptCoordinator(AttemptId("sequenced-attempt"))
        val terminalAttempt = coordinator.replace(routeContext)
        assertTrue(
            coordinator.accepts(
                RouteDecision.Blocked(terminalAttempt.nextDecision(), LocalTerminalReason.REMOTE_DISABLED),
            ),
        )
        assertFalse(
            coordinator.accepts(
                RouteDecision.KeepLocal(terminalAttempt.nextDecision().nextDecision(), LocalTerminalReason.SUCCESS),
            ),
        )

        val remoteFirst = coordinator.replace(routeContext)
        assertTrue(
            coordinator.accepts(
                RouteDecision.RunRemote(
                    remoteFirst.nextDecision(),
                    RemoteEligibilityReason.EXPLICIT_USER_REQUEST,
                    consentReceipt,
                ),
            ),
        )
    }

    @Test
    fun `policy and G5 values reject malformed state`() {
        assertFailsWith<IllegalArgumentException> { RoutePolicySnapshot("v1", "bad", RoutePolicyMode.LOCAL_ONLY) }
        assertFailsWith<IllegalArgumentException> {
            RoutePolicySnapshot("", "a".repeat(64), RoutePolicyMode.LOCAL_ONLY)
        }
        assertFailsWith<IllegalArgumentException> { GateG5LowConfidenceThreshold(1.1, "g5") }
        assertFailsWith<IllegalArgumentException> { GateG5LowConfidenceThreshold(-0.1, "g5") }
        assertFailsWith<IllegalArgumentException> { GateG5LowConfidenceThreshold(0.5, "") }
        assertFailsWith<IllegalArgumentException> { RouteAttemptToken(AttemptId("a"), 0, routeContext) }
        assertFailsWith<IllegalArgumentException> { RouteAttemptToken(AttemptId("a"), 1, routeContext, -1) }
    }

    private fun candidates(score: Double, band: ConfidenceBand) = PhotoInferenceResult(
        PHOTO_INFERENCE_CONTRACT_V1,
        InferenceOutcome.Candidates(
            listOf(
                com.supernutrition.domain.inference.InferenceCandidate(
                    "food:apple",
                    "Apple",
                    1,
                    Confidence(score, band),
                    emptyList(),
                    provenance,
                ),
            ),
        ),
    )

    private fun abstention() = PhotoInferenceResult(
        PHOTO_INFERENCE_CONTRACT_V1,
        InferenceOutcome.Abstention(AbstentionReason.IMAGE_QUALITY, "inference.abstention.image_quality", provenance),
    )

    private fun failure(code: InferenceFailureCode) = PhotoInferenceResult(
        PHOTO_INFERENCE_CONTRACT_V1,
        InferenceOutcome.Failure(code, true, "inference.failure.$code", provenance),
    )
}
