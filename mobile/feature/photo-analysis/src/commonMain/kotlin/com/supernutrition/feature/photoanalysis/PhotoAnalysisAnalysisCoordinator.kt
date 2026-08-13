package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceCandidate
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
import com.supernutrition.domain.photoanalysis.CandidateReviewDegradation
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

    /**
     * The router concluded this attempt may go to a provider, and it has not.
     *
     * Offering is where the coordinator stops. Running remote needs consent the
     * user has not given at this point, and executing a transfer from here would
     * be the thing AD-8 and the consent ordering exist to prevent.
     */
    data class RemoteOffered(val decision: RouteDecision) : PhotoAnalysisAnalysisResult
}

/**
 * What the routing authority needs to decide one attempt.
 *
 * Supplied by the host rather than assembled here: the policy snapshot, the
 * capability and the consent state are all things the coordinator observes, not
 * things it owns.
 */
data class PhotoAnalysisRoutingInputs(
    val token: RouteAttemptToken,
    val policy: RoutePolicySnapshot,
    val localCapability: CapabilityState,
    val requestedRoute: RequestedRoute = RequestedRoute.DEFAULT,
    val consent: RemoteConsentState = RemoteConsentState.NOT_REQUESTED,
    val consentReceipt: RemoteConsentReceipt? = null,
)

/**
 * Runs the injected local v1 and v2 engines and feeds only typed events to the
 * photo-analysis reducer. It owns no UI, catalog, nutrition, network, or
 * selection state.
 */
@Suppress("LongParameterList")
class PhotoAnalysisAnalysisCoordinator(
    private val feature: PhotoAnalysisFeature,
    private val dishEngine: PhotoInferenceEngine,
    private val componentEngine: InferenceV2Engine,
    private val onStageChanged: (LocalAnalysisStage) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
    /**
     * P1-5 — the routing authority. The coordinator ran the engines directly and
     * never asked it, so the deterministic local-first policy held only for as
     * long as this one caller happened to agree with it.
     */
    private val router: LocalFirstRouter = LocalFirstRouter(),
    /**
     * Null means no host policy is available, and the attempt stays local. That
     * is the same outcome a LOCAL_ONLY policy produces, so a host that supplies
     * nothing gets today's behaviour rather than an unrouted one.
     */
    private val routingInputs: (RequestId) -> PhotoAnalysisRoutingInputs? = { null },
    private val onRouteDecided: (RouteDecision) -> Unit = {},
) {
    suspend fun run(requestId: RequestId, attemptContext: InferenceAttemptContext): PhotoAnalysisAnalysisResult {
        val ready = feature.currentWorkState() as? PhotoAnalysisState.Ready
            ?: return PhotoAnalysisAnalysisResult.Ignored
        if (ready.input.ref.attemptId != attemptContext.attemptId || attemptContext.requestId != requestId.value) {
            return PhotoAnalysisAnalysisResult.Ignored
        }

        val ref = ready.input.ref
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(requestId))
        if (!isActive(requestId, ref)) return PhotoAnalysisAnalysisResult.Ignored

        val routing = routingInputs(requestId)
        openingRoute(routing, requestId, ref)?.let { return it }

        try {
            return analyzeLocally(requestId, attemptContext, ref, routing)
        } catch (cancellation: CancellationException) {
            if (isActive(requestId, ref)) feature.dispatch(PhotoAnalysisEvent.Cancel)
            throw cancellation
        } catch (error: IllegalArgumentException) {
            onFailure(error)
            return recover(requestId, ref, RecoveryReason.CAPTURE_FAILED)
        } catch (error: IllegalStateException) {
            onFailure(error)
            return recover(requestId, ref, RecoveryReason.CAPTURE_FAILED)
        }
    }

    /** The local pipeline: components, then dish, then fusion into a review draft. */
    private suspend fun analyzeLocally(
        requestId: RequestId,
        attemptContext: InferenceAttemptContext,
        ref: PhotoRef,
        routing: PhotoAnalysisRoutingInputs?,
    ): PhotoAnalysisAnalysisResult {
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
        val dishResult = dishEngine.analyze(PhotoInferenceRequest(ref, attemptContext))
        if (!isActive(requestId, ref)) return PhotoAnalysisAnalysisResult.Ignored

        val dishResolution = dishResult.outcome.toDishResolution()
        val availableDishes = dishResolution as? DishResolution.Available
            ?: return recover(requestId, ref, RecoveryReason.ANALYSIS_FAILED)

        routeAfterLocal(routing, dishResult)?.let { return it }

        dispatchStage(requestId, LocalAnalysisStage.CHECKING_RESULTS)
        val draft = BoundedDishIngredientFusion.merge(
            dishes = availableDishes.dishes,
            photoEvidence = components,
            degradation = availableDishes.degradation,
        )
        feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(requestId, draft))
        val review = feature.currentWorkState() as? PhotoAnalysisState.CandidateReview
        return if (review?.requestId == requestId) {
            PhotoAnalysisAnalysisResult.Review(draft)
        } else {
            PhotoAnalysisAnalysisResult.Ignored
        }
    }

    /**
     * The routing authority's opening decision, or null to run local.
     *
     * Null routing inputs mean the host supplied no policy, which produces the
     * same outcome as a LOCAL_ONLY one: the attempt stays local.
     */
    private fun openingRoute(
        routing: PhotoAnalysisRoutingInputs?,
        requestId: RequestId,
        ref: PhotoRef,
    ): PhotoAnalysisAnalysisResult? {
        val decision = routing?.let {
            router.initial(
                token = it.token,
                localCapability = it.localCapability,
                policy = it.policy,
                requestedRoute = it.requestedRoute,
                consent = it.consent,
                consentReceipt = it.consentReceipt,
            ).also(onRouteDecided)
        } ?: return null

        return when (decision) {
            // The only decision that reaches a local engine.
            is RouteDecision.RunLocal -> null

            // Offered, not run. Consent has not been given at this point, and a
            // transfer from here is what the consent ordering exists to stop.
            is RouteDecision.OfferRemote, is RouteDecision.RunRemote ->
                PhotoAnalysisAnalysisResult.RemoteOffered(decision)

            is RouteDecision.KeepLocal, is RouteDecision.Blocked ->
                recover(requestId, ref, RecoveryReason.ANALYSIS_FAILED)
        }
    }

    /**
     * The local terminal condition, which is the only point remote may be
     * considered from — never before local has had its turn.
     */
    private fun routeAfterLocal(
        routing: PhotoAnalysisRoutingInputs?,
        dishResult: PhotoInferenceResult,
    ): PhotoAnalysisAnalysisResult? {
        val decision = routing?.let {
            router.afterLocal(
                token = it.token,
                result = dishResult,
                policy = it.policy,
                consent = it.consent,
                consentReceipt = it.consentReceipt,
            ).also(onRouteDecided)
        } ?: return null

        return if (decision is RouteDecision.OfferRemote || decision is RouteDecision.RunRemote) {
            PhotoAnalysisAnalysisResult.RemoteOffered(decision)
        } else {
            null
        }
    }

    private fun dispatchStage(requestId: RequestId, stage: LocalAnalysisStage) {
        val analyzing = feature.currentWorkState() as? PhotoAnalysisState.Analyzing
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
        (feature.currentWorkState() as? PhotoAnalysisState.Analyzing)?.let {
            it.requestId == requestId && it.ref == ref
        } == true
}

private sealed interface DishResolution {
    data class Available(val dishes: List<InferenceCandidate>, val degradation: CandidateReviewDegradation) :
        DishResolution

    data object Unavailable : DishResolution
}

private fun InferenceOutcome.toDishResolution(): DishResolution = when (this) {
    is InferenceOutcome.Candidates -> DishResolution.Available(candidates, CandidateReviewDegradation.NONE)

    is InferenceOutcome.Abstention -> DishResolution.Available(
        dishes = emptyList(),
        degradation = CandidateReviewDegradation.DISH_CANDIDATES_UNAVAILABLE,
    )

    is InferenceOutcome.Failure -> DishResolution.Unavailable
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
        -> RecoveryReason.ANALYSIS_FAILED

        V2InferenceFailureCode.INVALID_INPUT -> RecoveryReason.CAPTURE_FAILED
    }

    is InferenceV2Outcome.Components -> RecoveryReason.CAPTURE_FAILED
}
