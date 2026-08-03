package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import com.supernutrition.domain.inference.V2AbstentionReason
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.BoundedDishIngredientFusion
import com.supernutrition.domain.photoanalysis.CandidateReviewDraft
import com.supernutrition.domain.photoanalysis.LocalAnalysisStage
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.RequestId
import kotlinx.coroutines.CancellationException

/** Result of one request-bound local analysis orchestration attempt. */
sealed interface PhotoAnalysisAnalysisResult {
    data class Review(val draft: CandidateReviewDraft) : PhotoAnalysisAnalysisResult

    data class Recovery(val reason: RecoveryReason) : PhotoAnalysisAnalysisResult

    data object Ignored : PhotoAnalysisAnalysisResult
}

/**
 * Runs the injected local v1 and v2 engines and feeds only typed events to the
 * photo-analysis reducer. It owns no UI, catalog, nutrition, network, or
 * selection state.
 */
class PhotoAnalysisAnalysisCoordinator(
    private val feature: PhotoAnalysisFeature,
    private val dishEngine: PhotoInferenceEngine,
    private val componentEngine: InferenceV2Engine,
    private val onStageChanged: (LocalAnalysisStage) -> Unit = {},
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
            val dishResult = dishEngine.analyze(PhotoInferenceRequest(ref, attemptContext))
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
