package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.GramRange
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.GateG5LowConfidenceThreshold
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.LocalFirstRouter
import com.supernutrition.domain.photoanalysis.LocalTerminalReason
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RemoteConsentState
import com.supernutrition.domain.photoanalysis.RequestId
import com.supernutrition.domain.photoanalysis.RequestedRoute
import com.supernutrition.domain.photoanalysis.RouteAttemptContext
import com.supernutrition.domain.photoanalysis.RouteAttemptToken
import com.supernutrition.domain.photoanalysis.RouteDecision
import com.supernutrition.domain.photoanalysis.RoutePolicyMode
import com.supernutrition.domain.photoanalysis.RoutePolicySnapshot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * P1-5 — the router decides the route, not the coordinator.
 *
 * The coordinator called the local engines directly and never consulted
 * [LocalFirstRouter]. Nothing went to a provider, because Epic 3 has no remote
 * path wired, so the fault could not be observed as a leak — which is precisely
 * why it needed a test rather than an argument. A routing policy that only one
 * caller happens to respect is not a policy; the next caller is where it fails.
 *
 * These prove the four things the handover asks for: local runs first on the
 * default route, remote is offered only after an allowed local terminal
 * condition or an explicit request, consent ordering is preserved, and the
 * decisions are observable.
 */
class PhotoAnalysisRoutingTest {

    // ── local first ────────────────────────────────────────────────────────

    @Test
    fun `a ready local engine runs first on the default route`() = runBlocking<Unit> {
        val feature = preparedFeature("route-default")
        val dish = RecordingDishEngine()
        val decisions = mutableListOf<RouteDecision>()

        val result = coordinator(feature, dish, decisions = decisions)
            .run(RequestId("req-route-default"), attemptContext("route-default"))

        assertIs<RouteDecision.RunLocal>(decisions.first())
        assertEquals(1, dish.calls, "the local dish engine must be the first thing asked")
        assertIs<PhotoAnalysisAnalysisResult.Review>(result)
    }

    @Test
    fun `an unready local engine does not silently run local anyway`() = runBlocking<Unit> {
        val feature = preparedFeature("route-unready")
        val dish = RecordingDishEngine()

        val result = coordinator(feature, dish, capability = CapabilityState.MODEL_REQUIRED)
            .run(RequestId("req-route-unready"), attemptContext("route-unready"))

        assertEquals(0, dish.calls, "no local engine may run when local capability is not ready")
        assertIs<PhotoAnalysisAnalysisResult.Recovery>(result)
    }

    // ── remote only after an allowed local terminal condition ──────────────

    @Test
    fun `remote is not offered while local succeeds`() = runBlocking<Unit> {
        val feature = preparedFeature("route-success")
        val decisions = mutableListOf<RouteDecision>()

        val result = coordinator(
            feature,
            RecordingDishEngine(),
            policy = remoteEnabledPolicy(),
            decisions = decisions,
        ).run(RequestId("req-route-success"), attemptContext("route-success"))

        assertIs<PhotoAnalysisAnalysisResult.Review>(result)
        assertTrue(
            decisions.none { it is RouteDecision.OfferRemote || it is RouteDecision.RunRemote },
            "a confident local answer is the answer; remote must not be offered over it",
        )
    }

    @Test
    fun `remote is offered once local abstains`() = runBlocking<Unit> {
        val feature = preparedFeature("route-abstain")
        val decisions = mutableListOf<RouteDecision>()

        val result = coordinator(
            feature,
            RecordingDishEngine(outcome = abstention()),
            policy = remoteEnabledPolicy(),
            decisions = decisions,
        ).run(RequestId("req-route-abstain"), attemptContext("route-abstain"))

        val offered = assertIs<PhotoAnalysisAnalysisResult.RemoteOffered>(result)
        assertIs<RouteDecision.OfferRemote>(offered.decision)
        assertTrue(decisions.first() is RouteDecision.RunLocal, "local still ran first")
    }

    @Test
    fun `remote stays closed when the policy is local only, even on abstention`() = runBlocking<Unit> {
        val feature = preparedFeature("route-localonly")
        val decisions = mutableListOf<RouteDecision>()

        coordinator(
            feature,
            RecordingDishEngine(outcome = abstention()),
            decisions = decisions,
        ).run(RequestId("req-route-localonly"), attemptContext("route-localonly"))

        assertTrue(
            decisions.none { it is RouteDecision.OfferRemote || it is RouteDecision.RunRemote },
            "LOCAL_ONLY must not offer remote whatever local returns",
        )
        assertEquals(
            LocalTerminalReason.REMOTE_DISABLED,
            (decisions.last() as RouteDecision.KeepLocal).reason,
        )
    }

    // ── consent ordering ───────────────────────────────────────────────────

    @Test
    fun `an explicit remote request without consent is an offer, never a run`() = runBlocking<Unit> {
        val feature = preparedFeature("route-explicit")
        val dish = RecordingDishEngine()

        val result = coordinator(
            feature,
            dish,
            policy = remoteEnabledPolicy(),
            requestedRoute = RequestedRoute.REMOTE,
        ).run(RequestId("req-route-explicit"), attemptContext("route-explicit"))

        val offered = assertIs<PhotoAnalysisAnalysisResult.RemoteOffered>(result)
        assertIs<RouteDecision.OfferRemote>(offered.decision)
        assertEquals(0, dish.calls, "an explicit remote request skips local, but still asks first")
    }

    @Test
    fun `a declined consent keeps the attempt local`() = runBlocking<Unit> {
        val feature = preparedFeature("route-declined")
        val decisions = mutableListOf<RouteDecision>()

        coordinator(
            feature,
            RecordingDishEngine(outcome = abstention()),
            policy = remoteEnabledPolicy(),
            consent = RemoteConsentState.DECLINED,
            decisions = decisions,
        ).run(RequestId("req-route-declined"), attemptContext("route-declined"))

        assertEquals(
            LocalTerminalReason.REMOTE_DECLINED,
            (decisions.last() as RouteDecision.KeepLocal).reason,
            "a decline is a decision, not a prompt to ask again",
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun remoteEnabledPolicy() = RoutePolicySnapshot(
        policyVersion = "route-policy-1",
        policyDigest = "a".repeat(64),
        mode = RoutePolicyMode.REMOTE_WITH_EXPLICIT_CONSENT,
        gateG5 = GateG5LowConfidenceThreshold(0.5, "g5-evidence-1"),
    )

    private fun localOnlyPolicy() = RoutePolicySnapshot(
        policyVersion = "route-policy-1",
        policyDigest = "a".repeat(64),
        mode = RoutePolicyMode.LOCAL_ONLY,
    )

    @Suppress("LongParameterList")
    private fun coordinator(
        feature: PhotoAnalysisFeature,
        dish: PhotoInferenceEngine,
        policy: RoutePolicySnapshot = localOnlyPolicy(),
        capability: CapabilityState = CapabilityState.READY,
        requestedRoute: RequestedRoute = RequestedRoute.DEFAULT,
        consent: RemoteConsentState = RemoteConsentState.NOT_REQUESTED,
        decisions: MutableList<RouteDecision> = mutableListOf(),
    ) = PhotoAnalysisAnalysisCoordinator(
        feature = feature,
        dishEngine = dish,
        componentEngine = componentEngine(),
        router = LocalFirstRouter(),
        routingInputs = { requestId ->
            PhotoAnalysisRoutingInputs(
                token = RouteAttemptToken(
                    attemptId = AttemptId(requestId.value.removePrefix("req-")),
                    generation = 1,
                    context = RouteAttemptContext(
                        requestId = requestId,
                        configurationVersion = "config-1",
                        configurationDigest = "b".repeat(64),
                        photoDigest = "c".repeat(64),
                    ),
                ),
                policy = policy,
                localCapability = capability,
                requestedRoute = requestedRoute,
                consent = consent,
            )
        },
        onRouteDecided = decisions::add,
    )

    private fun abstention() = InferenceOutcome.Abstention(
        reason = com.supernutrition.domain.inference.AbstentionReason.NO_FOOD_DETECTED,
        userMessageKey = "inference.abstention.no_food",
        provenance = provenance(),
    )

    private fun provenance() = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = "local-dish",
        modelVersion = "model-1",
        calibrationVersion = "calibration-1",
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )

    private inner class RecordingDishEngine(
        private val outcome: InferenceOutcome = InferenceOutcome.Candidates(
            listOf(
                InferenceCandidate(
                    candidateId = "food:egg-boiled",
                    label = "Egg, boiled",
                    rank = 1,
                    confidence = Confidence(0.92, ConfidenceBand.HIGH),
                    unresolvedAssumptions = emptyList(),
                    provenance = provenance(),
                ),
            ),
        ),
    ) : PhotoInferenceEngine {
        var calls: Int = 0
            private set

        override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1
        override suspend fun capability() = error("capability is not used by the coordinator")
        override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
            calls += 1
            return PhotoInferenceResult(PHOTO_INFERENCE_CONTRACT_V1, outcome)
        }
    }

    private fun componentEngine() = object : InferenceV2Engine() {
        override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2
        override suspend fun capability() = error("capability is not used by the coordinator")
        override suspend fun analyzeUnvalidated(request: InferenceV2Request) = InferenceV2Result(
            INFERENCE_EVIDENCE_CONTRACT_V2,
            InferenceInputModality.PHOTO,
            InferenceV2Outcome.Components(listOf(component())),
        )
    }

    private fun component() = VisibleComponent(
        componentId = "component-1",
        candidate = LocalizedCandidateIdentity("ingredient-1", "Rice", "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        region = NormalizedVisibleRegion(0.1, 0.1, 0.3, 0.3),
        mass = MassEvidence.Estimated(
            GramRange(100.0, 150.0),
            listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        provenance = InferenceV2Provenance(
            route = InferenceRoute.LOCAL,
            engineId = "component-engine",
            modelVersion = "component-model",
            taxonomyVersion = "component-taxonomy",
            calibrationVersion = "component-calibration",
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        ),
    )

    private fun attemptContext(attempt: String) = InferenceAttemptContext(
        attemptId = AttemptId(attempt),
        requestId = "req-$attempt",
        configurationVersion = "config-1",
        configurationDigest = "b".repeat(64),
    )

    private fun preparedFeature(attempt: String): PhotoAnalysisFeature {
        val leases = PhotoLeaseStore()
        val feature = PhotoAnalysisFeature(AttemptId(attempt), leases, PhotoSessionStore())
        val ref = feature.issuePhotoRef("photo-$attempt")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        feature.dispatch(
            PhotoAnalysisEvent.PreparationSucceeded(
                NormalizedPhotoInput(
                    ref = ref,
                    bytes = byteArrayOf(1),
                    width = 1,
                    height = 1,
                    orientation = ImageOrientation.UP,
                    colorSpace = "sRGB",
                    metadataKeys = emptySet(),
                    contentDigest = "d".repeat(64),
                ),
            ),
        )
        return feature
    }
}
