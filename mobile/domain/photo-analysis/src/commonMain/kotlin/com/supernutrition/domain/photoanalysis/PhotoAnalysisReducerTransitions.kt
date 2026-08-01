package com.supernutrition.domain.photoanalysis

internal class PhotoAnalysisReducerTransitions(private val context: PhotoAnalysisReducerContext) {
    fun reduce(current: PhotoAnalysisState, event: PhotoAnalysisEvent): PhotoAnalysisReduction = when (current) {
        PhotoAnalysisState.Capture -> reduceCapture(event)
        is PhotoAnalysisState.Preparing -> reducePreparing(current, event)
        is PhotoAnalysisState.Ready -> reduceReady(current, event)
        is PhotoAnalysisState.Analyzing -> reduceAnalyzing(current, event)
        is PhotoAnalysisState.CandidateReview -> reduceCandidateReview(current, event)
        is PhotoAnalysisState.Recovery -> reduceRecovery(current, event)
        is PhotoAnalysisState.Backgrounded -> unchanged(current)
    }

    private fun reduceCapture(event: PhotoAnalysisEvent): PhotoAnalysisReduction = when (event) {
        is PhotoAnalysisEvent.PhotoCaptured -> {
            if (event.ref.attemptId == context.attemptId && context.leases.isActive(event.ref)) {
                unchanged(PhotoAnalysisState.Preparing(event.ref))
            } else {
                unchanged(PhotoAnalysisState.Capture)
            }
        }

        is PhotoAnalysisEvent.PreparationFailed -> unchanged(PhotoAnalysisState.Capture)

        PhotoAnalysisEvent.PermissionDenied -> unchanged(PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED))

        else -> unchanged(PhotoAnalysisState.Capture)
    }

    private fun reducePreparing(
        current: PhotoAnalysisState.Preparing,
        event: PhotoAnalysisEvent,
    ): PhotoAnalysisReduction = when (event) {
        is PhotoAnalysisEvent.PreparationSucceeded -> if (
            event.input.ref == current.ref && context.leases.isActive(event.input.ref)
        ) {
            PhotoAnalysisReduction(
                PhotoAnalysisState.Ready(event.input),
                listOf(PhotoAnalysisEffect.StoreInput(event.input)),
            )
        } else {
            unchanged(current)
        }

        is PhotoAnalysisEvent.PreparationFailed ->
            reduction(PhotoAnalysisState.Recovery(event.reason), PhotoAnalysisEffect.RevokePhoto(current.ref))

        is PhotoAnalysisEvent.ReplacePhoto -> replace(current, current.ref, event.ref)

        PhotoAnalysisEvent.Cancel -> cancel(current.ref)

        else -> unchanged(current)
    }

    private fun reduceReady(current: PhotoAnalysisState.Ready, event: PhotoAnalysisEvent): PhotoAnalysisReduction =
        when (event) {
            is PhotoAnalysisEvent.AnalysisStarted -> if (context.isLive(current.input.ref, current.input)) {
                unchanged(PhotoAnalysisState.Analyzing(current.input.ref, event.requestId))
            } else {
                reduction(
                    PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
                    PhotoAnalysisEffect.RevokePhoto(current.input.ref),
                )
            }

            is PhotoAnalysisEvent.ReplacePhoto -> replace(current, current.input.ref, event.ref)

            PhotoAnalysisEvent.Cancel -> cancel(current.input.ref)

            else -> unchanged(current)
        }

    private fun reduceAnalyzing(
        current: PhotoAnalysisState.Analyzing,
        event: PhotoAnalysisEvent,
    ): PhotoAnalysisReduction = when (event) {
        is PhotoAnalysisEvent.AnalysisSucceeded -> when {
            event.requestId != current.requestId -> unchanged(current)

            context.isLive(
                current.ref,
            ) -> unchanged(PhotoAnalysisState.CandidateReview(current.ref, current.requestId))

            else -> {
                reduction(
                    PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
                    PhotoAnalysisEffect.RevokePhoto(current.ref),
                )
            }
        }

        is PhotoAnalysisEvent.ReplacePhoto -> replace(current, current.ref, event.ref)

        PhotoAnalysisEvent.Cancel -> cancel(current.ref)

        else -> unchanged(current)
    }

    private fun reduceCandidateReview(
        current: PhotoAnalysisState.CandidateReview,
        event: PhotoAnalysisEvent,
    ): PhotoAnalysisReduction = when (event) {
        is PhotoAnalysisEvent.ReplacePhoto -> replace(current, current.ref, event.ref)
        PhotoAnalysisEvent.ScanAgain -> clearAndCapture(current.ref)
        PhotoAnalysisEvent.Cancel -> cancel(current.ref)
        else -> unchanged(current)
    }

    private fun reduceRecovery(
        current: PhotoAnalysisState.Recovery,
        event: PhotoAnalysisEvent,
    ): PhotoAnalysisReduction = when (event) {
        is PhotoAnalysisEvent.ReplacePhoto -> replace(current, current.ref, event.ref)
        PhotoAnalysisEvent.ScanAgain -> current.ref?.let(::clearAndCapture) ?: unchanged(PhotoAnalysisState.Capture)
        else -> unchanged(current)
    }

    private fun replace(fallback: PhotoAnalysisState, old: PhotoRef?, replacement: PhotoRef): PhotoAnalysisReduction {
        if (replacement.attemptId != context.attemptId ||
            !context.leases.isActive(replacement) ||
            replacement == old
        ) {
            return unchanged(fallback)
        }
        return if (old == null) {
            unchanged(PhotoAnalysisState.Preparing(replacement))
        } else {
            reduction(PhotoAnalysisState.Preparing(replacement), PhotoAnalysisEffect.RevokePhoto(old))
        }
    }

    private fun cancel(ref: PhotoRef): PhotoAnalysisReduction =
        reduction(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), PhotoAnalysisEffect.RevokePhoto(ref))

    private fun clearAndCapture(ref: PhotoRef): PhotoAnalysisReduction =
        reduction(PhotoAnalysisState.Capture, PhotoAnalysisEffect.RevokePhoto(ref))

    private fun unchanged(state: PhotoAnalysisState) = PhotoAnalysisReduction(state)

    private fun reduction(state: PhotoAnalysisState, effect: PhotoAnalysisEffect) =
        PhotoAnalysisReduction(state, listOf(effect))
}
