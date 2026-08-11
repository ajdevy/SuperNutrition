package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.inference.PhotoRef
import com.supernutrition.domain.inference.V2AbstentionReason
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.BoundedDishIngredientFusion
import com.supernutrition.domain.photoanalysis.CandidateReviewDraft
import com.supernutrition.domain.photoanalysis.LocalAnalysisStage
import com.supernutrition.domain.photoanalysis.LocalFirstRouter
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.RemoteConsentReceipt
import com.supernutrition.domain.photoanalysis.RemoteConsentState
import com.supernutrition.domain.photoanalysis.RequestId
import com.supernutrition.domain.photoanalysis.RequestedRoute
import com.supernutrition.domain.photoanalysis.RouteAttemptToken
import com.supernutrition.domain.photoanalysis.RouteDecision
import com.supernutrition.domain.photoanalysis.RoutePolicySnapshot
import kotlinx.coroutines.CancellationException

/** Result of one request-bound local analysis orchestration attempt. */
sealed interface PhotoAnalysisAnalysisResult {
    data class Review(val draft: CandidateReviewDraft) : PhotoAnalysisAnalysisResult

    data class Recovery(val reason: RecoveryReason) : PhotoAnalysisAnalysisResult

    data object Ignored : PhotoAnalysisAnalysisResult
}

/** What the coordinator needs in order to ASK the router, rather than assume. */
data class PhotoAnalysisRoutingInputs(
    val token: RouteAttemptToken,
    val policy: RoutePolicySnapshot,
    val localCapability: CapabilityState,
    val requestedRoute: RequestedRoute = RequestedRoute.DEFAULT,
    val consent: RemoteConsentState = RemoteConsentState.NOT_REQUESTED,
    val consentReceipt: RemoteConsentReceipt? = null,
)

/**
 * Runs the injected engines and feeds only typed events to the photo-analysis
 * reducer. It owns no UI, catalog, nutrition, or selection state.
 *
 * P1-5 — the route is DECIDED, not assumed. This class used to call the single
 * injected dish engine unconditionally. In the shipped Android wiring that engine
 * is the remote gateway, so every analysis went straight to the network:
 * LocalFirstRouter existed, was tested, and was never asked. AD-6's ordering is a
 * property of the running app or it is a property of nothing.
 *
 * The router is consulted first, and its decision selects the engine. A local
 * engine that is READY runs before any remote one; remote runs only when the
 * router says RunRemote, which requires either an allowed local terminal
 * condition or an explicit user request, plus granted consent.
 */
class PhotoAnalysisAnalysisCoordinator
@Suppress("LongParameterList")
constructor(
    private val feature: PhotoAnalysisFeature,
    private val dishEngine: PhotoInferenceEngine,
    private val componentEngine: InferenceV2Engine,
    private val onStageChanged: (LocalAnalysisStage) -> Unit = {},
    /**
     * The local dish engine, when the host has one. Null means this build has no
     * on-device dish identification, which the router is TOLD (as an unavailable
     * capability) rather than left to infer from the absence of a call.
     */
    private val localDishEngine: PhotoInferenceEngine? = null,
    private val router: LocalFirstRouter = LocalFirstRouter(),
    /**
     * Null keeps the pre-routing behaviour for hosts that have not supplied a
     * policy yet. It is explicit rather than defaulted so that a host which
     * forgets to route is visible here, instead of silently reaching the network.
     */
    private val routingInputs: (() -> PhotoAnalysisRoutingInputs)? = null,
    private val onRouteDecided: (RouteDecision) -> Unit = {},
) {
    suspend fun run(requestId: RequestId, attemptContext: InferenceAttemptContext): PhotoAnalysisAnalysisResult {
        val ready = feature.state as? PhotoAnalysisState.Ready ?: return PhotoAnalysisAnalysisResult.Ignored
        if (ready.input.ref.attemptId != attemptContext.attemptId || attemptContext.requestId != requestId.value) {
            return PhotoAnalysisAnalysisResult.Ignored
        }

        val ref = ready.input.ref
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(requestId))
        if (!isActive(requestId, ref)) return PhotoAnalysisAnalysisResult.Ignored

        try {
            dispatchStage(requestId, LocalAnalysisStage.FINDING_VISIBLE_FOODS)
            val componentResult = componentEngine.analyze(
                InferenceV2Request(
                    input = com.supernutrition.domain.inference.InferenceV2Input.Photo(ref),
                    attemptContext = attemptContext,
                ),
            )
            if (!isActive(requestId, ref)) return PhotoAnalysisAnalysisResult.Ignored

            val components = componentResult.componentsOrRecovery() ?: return recover(
                requestId,
                ref,
                componentResult.recoveryReason(),
            )
            dispatchStage(requestId, LocalAnalysisStage.IDENTIFYING_DISH)
            val dishResult = analyzeDish(PhotoInferenceRequest(ref, attemptContext))
                ?: return recover(requestId, ref, RecoveryReason.OFFLINE)
            if (!isActive(requestId, ref)) return PhotoAnalysisAnalysisResult.Ignored

            dispatchStage(requestId, LocalAnalysisStage.CHECKING_RESULTS)
            val draft = BoundedDishIngredientFusion.merge(
                dishes = dishResult.outcome.dishCandidates(),
                photoEvidence = components,
            )
            feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(requestId, draft))
            val review = feature.state as? PhotoAnalysisState.CandidateReview
            return if (review?.requestId == requestId) {
                PhotoAnalysisAnalysisResult.Review(draft)
            } else {
                PhotoAnalysisAnalysisResult.Ignored
            }
        } catch (cancellation: CancellationException) {
            if (isActive(requestId, ref)) feature.dispatch(PhotoAnalysisEvent.Cancel)
            throw cancellation
        } catch (_: Exception) {
            return recover(requestId, ref, RecoveryReason.CAPTURE_FAILED)
        }
    }

    /**
     * Asks the router which engine may run, then runs it.
     *
     * Null means no engine was permitted to run at all — the router blocked the
     * attempt, or it kept the result local with no local engine present. The
     * caller turns that into a recovery rather than a silent empty result.
     */
    private suspend fun analyzeDish(request: PhotoInferenceRequest): PhotoInferenceResult? {
        val inputs = routingInputs?.invoke()
            // No routing policy supplied by this host: behave as before rather
            // than invent a policy. Hosts that have one get local-first.
            ?: return dishEngine.analyze(request)

        val initial = router.initial(
            token = inputs.token,
            localCapability = inputs.localCapability,
            policy = inputs.policy,
            requestedRoute = inputs.requestedRoute,
            consent = inputs.consent,
            consentReceipt = inputs.consentReceipt,
        )
        onRouteDecided(initial)

        return when (initial) {
            is RouteDecision.RunLocal -> {
                val local = localDishEngine ?: return null
                val localResult = local.analyze(request)
                // Local ran; the router decides whether remote may follow. It is
                // asked with the actual result, so "remote after local" depends
                // on what local produced rather than on the coordinator's guess.
                val next = router.afterLocal(
                    token = initial.token,
                    result = localResult,
                    policy = inputs.policy,
                    consent = inputs.consent,
                    consentReceipt = inputs.consentReceipt,
                )
                onRouteDecided(next)
                if (next is RouteDecision.RunRemote) dishEngine.analyze(request) else localResult
            }

            is RouteDecision.RunRemote -> dishEngine.analyze(request)

            // KeepLocal with no local engine, or Blocked, means nothing may run.
            is RouteDecision.KeepLocal -> localDishEngine?.analyze(request)

            is RouteDecision.OfferRemote -> localDishEngine?.analyze(request)

            is RouteDecision.Blocked -> null
        }
    }

    private fun dispatchStage(requestId: RequestId, stage: LocalAnalysisStage) {
        val analyzing = feature.state as? PhotoAnalysisState.Analyzing
        if (analyzing?.requestId == requestId) {
            onStageChanged(stage)
            feature.dispatch(PhotoAnalysisEvent.LocalAnalysisStageChanged(requestId, stage))
        }
    }

    private fun recover(requestId: RequestId, ref: PhotoRef, reason: RecoveryReason): PhotoAnalysisAnalysisResult =
        if (isActive(requestId, ref)) {
            feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceUnavailable(requestId, reason))
            PhotoAnalysisAnalysisResult.Recovery(reason)
        } else {
            PhotoAnalysisAnalysisResult.Ignored
        }

    private fun isActive(requestId: RequestId, ref: PhotoRef): Boolean =
        (feature.state as? PhotoAnalysisState.Analyzing)?.let { it.requestId == requestId && it.ref == ref } == true
}

private fun InferenceV2Result.componentsOrRecovery(): List<VisibleComponent>? {
    val value = outcome
    return if (inputModality == InferenceInputModality.PHOTO && value is InferenceV2Outcome.Components) {
        value.components.takeIf { it.isNotEmpty() }
    } else {
        null
    }
}

private fun InferenceV2Result.recoveryReason(): RecoveryReason = when (val value = outcome) {
    is InferenceV2Outcome.Abstention -> when (value.reason) {
        V2AbstentionReason.IMAGE_QUALITY -> RecoveryReason.POOR_IMAGE

        V2AbstentionReason.IDENTITY_AMBIGUOUS,
        V2AbstentionReason.NO_FOOD_DETECTED,
        V2AbstentionReason.UNSUPPORTED_CONTENT,
        -> RecoveryReason.NO_FOOD
    }

    is InferenceV2Outcome.Failure -> when (value.code) {
        V2InferenceFailureCode.MODEL_UNAVAILABLE,
        V2InferenceFailureCode.RESOURCE_LIMIT,
        V2InferenceFailureCode.TEMPORARY_UNAVAILABLE,
        -> RecoveryReason.OFFLINE

        V2InferenceFailureCode.INVALID_INPUT -> RecoveryReason.CAPTURE_FAILED
    }

    is InferenceV2Outcome.Components -> RecoveryReason.CAPTURE_FAILED
}

private fun InferenceOutcome.dishCandidates() = when (this) {
    is InferenceOutcome.Candidates -> candidates

    is InferenceOutcome.Abstention,
    is InferenceOutcome.Failure,
    -> emptyList()
}
