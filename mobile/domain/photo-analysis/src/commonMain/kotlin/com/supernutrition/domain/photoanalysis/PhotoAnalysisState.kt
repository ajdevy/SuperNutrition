package com.supernutrition.domain.photoanalysis

import com.supernutrition.core.config.ConfigurationSnapshot

sealed interface PhotoAnalysisState {
    data object Capture : PhotoAnalysisState
    data class Preparing(val ref: PhotoRef) : PhotoAnalysisState
    data class Ready(val input: NormalizedPhotoInput) : PhotoAnalysisState
    data class Analyzing(
        val ref: PhotoRef,
        val requestId: RequestId,
        val stage: LocalAnalysisStage = LocalAnalysisStage.FINDING_VISIBLE_FOODS,
    ) : PhotoAnalysisState

    data class CandidateReview(
        val ref: PhotoRef,
        val requestId: RequestId,
        val draft: CandidateReviewDraft = CandidateReviewDraft(emptyList(), emptyList()),
    ) : PhotoAnalysisState
    data class Recovery(val reason: RecoveryReason, val ref: PhotoRef? = null) : PhotoAnalysisState
    data class Backgrounded(val snapshot: PhotoAnalysisBackgroundSnapshot) : PhotoAnalysisState
}

/** Process-safe snapshot: opaque identity only; photo bytes stay in the session store. */
sealed interface PhotoAnalysisBackgroundSnapshot {
    data object Capture : PhotoAnalysisBackgroundSnapshot
    data class Preparing(val ref: PhotoRef) : PhotoAnalysisBackgroundSnapshot
    data class Ready(val ref: PhotoRef) : PhotoAnalysisBackgroundSnapshot
    data class Analyzing(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisBackgroundSnapshot
    data class CandidateReview(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisBackgroundSnapshot
    data class Recovery(val reason: RecoveryReason, val ref: PhotoRef?) : PhotoAnalysisBackgroundSnapshot
}

enum class RecoveryReason {
    PERMISSION_DENIED,
    CAMERA_UNAVAILABLE,
    CAPTURE_FAILED,
    POOR_IMAGE,
    NO_FOOD,
    OFFLINE,
    CANCELLED,
}

/** Truthful, bounded local work stages shown while photo evidence is being prepared for review. */
enum class LocalAnalysisStage {
    FINDING_VISIBLE_FOODS,
    ESTIMATING_PORTIONS,
    CHECKING_RESULTS,
}

sealed interface PhotoAnalysisEvent {
    data class PhotoCaptured(val ref: PhotoRef) : PhotoAnalysisEvent
    data class PreparationSucceeded(val input: NormalizedPhotoInput) : PhotoAnalysisEvent

    /**
     * [diagnostic] records what actually went wrong. It is for support and
     * telemetry, never for display — [reason] is what the user is shown, and a
     * decoder's message is not a sentence anyone should read in a capture flow.
     *
     * It exists because the Android host caught preparation failures and
     * dropped them, so a failed capture reached the user as a bare reason with
     * no record of its cause anywhere.
     */
    data class PreparationFailed(val reason: RecoveryReason, val diagnostic: String? = null) : PhotoAnalysisEvent
    data object PermissionDenied : PhotoAnalysisEvent
    data class AnalysisStarted(val requestId: RequestId) : PhotoAnalysisEvent
    data class AnalysisSucceeded(val requestId: RequestId) : PhotoAnalysisEvent
    data class LocalAnalysisStageChanged(val requestId: RequestId, val stage: LocalAnalysisStage) : PhotoAnalysisEvent

    /** Typed, separate dish proposals and visible component evidence for the review boundary. */
    data class ReviewEvidenceAvailable(val requestId: RequestId, val draft: CandidateReviewDraft) : PhotoAnalysisEvent

    /** A factual review outcome when no editable component evidence can be offered. */
    data class ReviewEvidenceUnavailable(val requestId: RequestId, val reason: RecoveryReason) : PhotoAnalysisEvent

    data class ReplacePhoto(val ref: PhotoRef) : PhotoAnalysisEvent
    data object Cancel : PhotoAnalysisEvent
    data object ScanAgain : PhotoAnalysisEvent
    data object Backgrounded : PhotoAnalysisEvent
    data object Restored : PhotoAnalysisEvent
    data class RestoreFromSnapshot(val snapshot: PhotoAnalysisBackgroundSnapshot) : PhotoAnalysisEvent
}

data class AnalysisAttemptContext(val attemptId: AttemptId, val snapshot: ConfigurationSnapshot)

internal fun PhotoAnalysisState.toBackgroundSnapshot(): PhotoAnalysisBackgroundSnapshot = when (this) {
    PhotoAnalysisState.Capture -> PhotoAnalysisBackgroundSnapshot.Capture
    is PhotoAnalysisState.Preparing -> PhotoAnalysisBackgroundSnapshot.Preparing(ref)
    is PhotoAnalysisState.Ready -> PhotoAnalysisBackgroundSnapshot.Ready(input.ref)
    is PhotoAnalysisState.Analyzing -> PhotoAnalysisBackgroundSnapshot.Analyzing(ref, requestId)
    is PhotoAnalysisState.CandidateReview -> PhotoAnalysisBackgroundSnapshot.CandidateReview(ref, requestId)
    is PhotoAnalysisState.Recovery -> PhotoAnalysisBackgroundSnapshot.Recovery(reason, ref)
    is PhotoAnalysisState.Backgrounded -> snapshot
}
