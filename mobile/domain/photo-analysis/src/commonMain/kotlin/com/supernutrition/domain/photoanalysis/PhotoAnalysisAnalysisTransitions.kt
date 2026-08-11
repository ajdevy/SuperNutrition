package com.supernutrition.domain.photoanalysis

/** Owns request-bound local work updates while a photo analysis is in progress. */
internal class PhotoAnalysisAnalysisTransitions(private val context: PhotoAnalysisReducerContext) {
    fun reduce(current: PhotoAnalysisState.Analyzing, event: PhotoAnalysisEvent): PhotoAnalysisReduction =
        when (event) {
            is PhotoAnalysisEvent.LocalAnalysisStageChanged -> reduceStageChanged(current, event)
            is PhotoAnalysisEvent.ReviewEvidenceAvailable -> reduceReviewEvidence(current, event)
            is PhotoAnalysisEvent.ReviewEvidenceUnavailable -> reduceUnavailableEvidence(current, event)
            is PhotoAnalysisEvent.AnalysisSucceeded -> reduceLegacyCompletion(current, event)
            is PhotoAnalysisEvent.GridCellSelected -> prepareGridReanalysis(current.ref)
            is PhotoAnalysisEvent.ReplacePhoto -> replace(current, event.ref)
            PhotoAnalysisEvent.ScanAgain -> clearAndCapture(current.ref)
            PhotoAnalysisEvent.Cancel -> cancel(current.ref)
            else -> unchanged(current)
        }

    private fun reduceStageChanged(
        current: PhotoAnalysisState.Analyzing,
        event: PhotoAnalysisEvent.LocalAnalysisStageChanged,
    ): PhotoAnalysisReduction = when {
        event.requestId != current.requestId -> unchanged(current)
        context.isLive(current.ref) -> unchanged(current.copy(stage = event.stage))
        else -> failedAnalysis(current.ref)
    }

    private fun reduceReviewEvidence(
        current: PhotoAnalysisState.Analyzing,
        event: PhotoAnalysisEvent.ReviewEvidenceAvailable,
    ): PhotoAnalysisReduction = when {
        event.requestId != current.requestId -> unchanged(current)

        event.draft.mainDishProposals.isEmpty() && event.draft.photoEvidence.isEmpty() ->
            reduceUnavailableEvidence(
                current,
                PhotoAnalysisEvent.ReviewEvidenceUnavailable(
                    requestId = event.requestId,
                    reason = RecoveryReason.NO_FOOD,
                ),
            )

        context.isLive(current.ref) -> unchanged(
            PhotoAnalysisState.CandidateReview(current.ref, current.requestId, event.draft),
        )

        else -> failedAnalysis(current.ref)
    }

    private fun reduceUnavailableEvidence(
        current: PhotoAnalysisState.Analyzing,
        event: PhotoAnalysisEvent.ReviewEvidenceUnavailable,
    ): PhotoAnalysisReduction = when {
        event.requestId != current.requestId -> unchanged(current)

        context.isLive(current.ref) -> if (event.reason == RecoveryReason.NO_FOOD) {
            // Keep the captured photo visible so the user can inspect the failed
            // attempt and explicitly choose ScanAgain before opening the camera.
            unchanged(PhotoAnalysisState.Recovery(event.reason, current.ref))
        } else {
            reduction(
                PhotoAnalysisState.Recovery(event.reason),
                PhotoAnalysisEffect.RevokePhoto(current.ref),
            )
        }

        else -> failedAnalysis(current.ref)
    }

    private fun reduceLegacyCompletion(
        current: PhotoAnalysisState.Analyzing,
        event: PhotoAnalysisEvent.AnalysisSucceeded,
    ): PhotoAnalysisReduction = when {
        event.requestId != current.requestId -> unchanged(current)

        context.isLive(current.ref) -> reduction(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, current.ref),
            PhotoAnalysisEffect.RevokePhoto(current.ref),
        )

        else -> failedAnalysis(current.ref)
    }

    private fun replace(current: PhotoAnalysisState.Analyzing, replacement: PhotoRef): PhotoAnalysisReduction =
        if (replacement.attemptId != context.attemptId ||
            !context.leases.isActive(replacement) ||
            replacement == current.ref
        ) {
            unchanged(current)
        } else {
            reduction(
                PhotoAnalysisState.Preparing(replacement),
                PhotoAnalysisEffect.RevokePhoto(current.ref),
            )
        }

    private fun cancel(ref: PhotoRef): PhotoAnalysisReduction =
        reduction(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), PhotoAnalysisEffect.RevokePhoto(ref))

    private fun clearAndCapture(ref: PhotoRef): PhotoAnalysisReduction =
        reduction(PhotoAnalysisState.Capture, PhotoAnalysisEffect.RevokePhoto(ref))

    private fun prepareGridReanalysis(ref: PhotoRef): PhotoAnalysisReduction =
        context.sessions.read(ref)?.takeIf { context.isLive(ref, it) }?.let { input ->
            unchanged(PhotoAnalysisState.Ready(input))
        } ?: reduction(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, ref),
            PhotoAnalysisEffect.RevokePhoto(ref),
        )

    private fun failedAnalysis(ref: PhotoRef) = reduction(
        PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
        PhotoAnalysisEffect.RevokePhoto(ref),
    )
}
