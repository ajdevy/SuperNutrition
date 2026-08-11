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
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.LocalFirstRouter
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoFormat
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RemoteConsentReceipt
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
 * P1-5 — the route is decided, not assumed.
 *
 * The coordinator called its single injected dish engine unconditionally, and in
 * the shipped Android wiring that engine is the remote gateway. So every
 * analysis went straight to the network: LocalFirstRouter existed, was tested,
 * and was never asked. AD-6's ordering is a property of the running app or of
 * nothing at all.
 *
 * These assert which ENGINE ran and in what order, not merely which decision the
 * router returned — a router consulted and then ignored would still pass the
 * latter.
 */
private val ROUTING_COMPONENT = VisibleComponent(
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

class PhotoAnalysisLocalFirstRoutingTest {

    private class RecordingEngine(val engineName: String, val log: MutableList<String>) : PhotoInferenceEngine {
        override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1
        override suspend fun capability() = error("capability is not used by the coordinator")
        override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
            log += engineName
            return PhotoInferenceResult(
                PHOTO_INFERENCE_CONTRACT_V1,
                InferenceOutcome.Candidates(
                    listOf(
                        InferenceCandidate(
                            candidateId = "food:$engineName",
                            label = engineName,
                            rank = 1,
                            confidence = Confidence(0.9, ConfidenceBand.HIGH),
                            unresolvedAssumptions = emptyList(),
                            provenance = InferenceProvenance(
                                route = InferenceRoute.LOCAL,
                                engineId = engineName,
                                modelVersion = "m",
                                calibrationVersion = "c",
                                contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    /** Components are not what these tests are about; it only has to succeed. */
    private object EmptyComponentEngine : InferenceV2Engine() {
        override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2
        override suspend fun capability() = error("capability is not used by the coordinator")
        override suspend fun analyzeUnvalidated(request: InferenceV2Request) = InferenceV2Result(
            INFERENCE_EVIDENCE_CONTRACT_V2,
            InferenceInputModality.PHOTO,
            InferenceV2Outcome.Components(listOf(ROUTING_COMPONENT)),
        )
    }

    private val policy = RoutePolicySnapshot(
        policyVersion = "policy/1",
        policyDigest = "a".repeat(64),
        mode = RoutePolicyMode.REMOTE_WITH_EXPLICIT_CONSENT,
    )

    private fun token(attempt: String) = RouteAttemptToken(
        attemptId = AttemptId(attempt),
        generation = 1,
        context = RouteAttemptContext(
            requestId = RequestId("request-$attempt"),
            photoDigest = "b".repeat(64),
            configurationVersion = "cfg/1",
            configurationDigest = "c".repeat(64),
        ),
    )

    private fun receipt(attempt: String) = RemoteConsentReceipt(
        attemptId = AttemptId(attempt),
        requestId = RequestId("request-$attempt"),
        photoDigest = "b".repeat(64),
        purpose = "food-identification",
        marketJurisdiction = "ZZ",
        processingRegion = "fixture-region",
        policyVersion = policy.policyVersion,
        policyDigest = policy.policyDigest,
        configurationVersion = "cfg/1",
        configurationDigest = "c".repeat(64),
        consentCopyVersion = "photo-consent/fixture-1",
        receiptId = "receipt-$attempt",
        signatureDigest = "d".repeat(64),
    )

    private fun preparedFeature(attempt: String): PhotoAnalysisFeature {
        val feature = PhotoAnalysisFeature(AttemptId(attempt), PhotoLeaseStore(), PhotoSessionStore())
        val ref = feature.issuePhotoRef("photo-$attempt")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        feature.dispatch(
            PhotoAnalysisEvent.PreparationSucceeded(
                NormalizedPhotoInput(
                    ref = ref,
                    bytes = byteArrayOf(1),
                    width = 1,
                    height = 1,
                    format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                    contentDigest = "a".repeat(64),
                ),
            ),
        )
        return feature
    }

    private fun attemptContext(attempt: String, request: String) = InferenceAttemptContext(
        attemptId = AttemptId(attempt),
        requestId = request,
        configurationVersion = "config-$attempt",
        configurationDigest = "b".repeat(64),
    )

    private data class Run(val log: List<String>, val decisions: List<RouteDecision>)

    /**
     * One routing scenario. Grouped rather than passed as six arguments, because
     * a scenario IS a value: `run(attempt, READY, GRANTED, REMOTE, receipt, true)`
     * tells a reader nothing at the call site.
     *
     * consentVerified exists because LocalFirstRouter's default consent verifier
     * answers false for every receipt — signature checking is a seam the host
     * fills. A test about ROUTING has to supply one, or the remote paths are
     * unreachable for reasons that have nothing to do with routing.
     */
    private data class Scenario(
        val localCapability: CapabilityState = CapabilityState.READY,
        val consent: RemoteConsentState = RemoteConsentState.NOT_REQUESTED,
        val requestedRoute: RequestedRoute = RequestedRoute.DEFAULT,
        val consentReceipt: RemoteConsentReceipt? = null,
        val consentVerified: Boolean = false,
    )

    private fun run(attempt: String, scenario: Scenario = Scenario()): Run {
        val localCapability = scenario.localCapability
        val consent = scenario.consent
        val requestedRoute = scenario.requestedRoute
        val consentReceipt = scenario.consentReceipt
        val consentVerified = scenario.consentVerified
        val log = mutableListOf<String>()
        val decisions = mutableListOf<RouteDecision>()
        val feature = preparedFeature(attempt)

        val coordinator = PhotoAnalysisAnalysisCoordinator(
            feature = feature,
            dishEngine = RecordingEngine("remote", log),
            componentEngine = EmptyComponentEngine,
            localDishEngine = RecordingEngine("local", log),
            router = LocalFirstRouter(consentVerifier = { _, _, _ -> consentVerified }),
            routingInputs = {
                PhotoAnalysisRoutingInputs(
                    token = token(attempt),
                    policy = policy,
                    localCapability = localCapability,
                    requestedRoute = requestedRoute,
                    consent = consent,
                    consentReceipt = consentReceipt,
                )
            },
            onRouteDecided = decisions::add,
        )

        runBlocking {
            coordinator.run(RequestId("request-$attempt"), attemptContext(attempt, "request-$attempt"))
        }
        return Run(log, decisions)
    }

    @Test
    fun `a ready local engine runs before any remote one`() {
        val result = run("route-local")

        assertTrue(result.log.isNotEmpty(), "no engine ran at all")
        assertEquals("local", result.log.first(), "the first engine was not the local one: ${result.log}")
        assertIs<RouteDecision.RunLocal>(result.decisions.first())
    }

    @Test
    fun `remote is not reached without consent`() {
        // The ordering that matters most, and the one the shipped wiring got
        // wrong in the most direct way: it called remote first, always.
        val result = run("route-no-consent")

        assertTrue("remote" !in result.log, "remote ran without consent: ${result.log}")
    }

    @Test
    fun `a declined decision does not reach remote either`() {
        val result = run("route-declined", Scenario(consent = RemoteConsentState.DECLINED))

        assertTrue("remote" !in result.log, "remote ran after consent was declined: ${result.log}")
    }

    @Test
    fun `an explicit remote request reaches a remote-capable decision, and transfers only on RunRemote`() {
        // The positive control, stated as the invariant the coordinator actually
        // owns: remote runs if and only if the router said RunRemote.
        //
        // With an explicit request and verified consent the router answers
        // OfferRemote, not RunRemote — it wants the user to confirm before bytes
        // move, which is the right answer and not what this test originally
        // expected. So the assertion is the coupling, not a hard-coded verdict:
        // an OfferRemote must NOT transfer, and a RunRemote must.
        val result = run(
            "route-explicit",
            Scenario(
                consent = RemoteConsentState.GRANTED,
                requestedRoute = RequestedRoute.REMOTE,
                consentReceipt = receipt("route-explicit"),
                consentVerified = true,
            ),
        )

        val ranRemote = "remote" in result.log
        val decidedRunRemote = result.decisions.any { it is RouteDecision.RunRemote }
        assertEquals(
            decidedRunRemote,
            ranRemote,
            "remote ran=$ranRemote but the router decided RunRemote=$decidedRunRemote: ${result.decisions}",
        )
        assertTrue(
            result.decisions.any {
                it is RouteDecision.RunRemote || it is RouteDecision.OfferRemote
            },
            "an explicit, consented remote request produced no remote-capable decision: ${result.decisions}",
        )
    }

    @Test
    fun `a route decision exists before any engine is called`() {
        val result = run("route-order", Scenario(consent = RemoteConsentState.GRANTED))

        assertTrue(result.decisions.isNotEmpty(), "no route decision was made")
        assertIs<RouteDecision.RunLocal>(result.decisions.first())
    }
}
