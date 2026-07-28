package com.supernutrition.domain.photoanalysis

internal class PhotoAnalysisReducerTransitions(private val context: PhotoAnalysisReducerContext) {
    fun reduce(current: PhotoAnalysisState, event: PhotoAnalysisEvent): PhotoAnalysisState = when (current) {
        PhotoAnalysisState.Capture -> reduceCapture(event)
        is PhotoAnalysisState.Preparing -> reducePreparing(current, event)
        is PhotoAnalysisState.Ready -> reduceReady(current, event)
        is PhotoAnalysisState.Analyzing -> reduceAnalyzing(current, event)
        is PhotoAnalysisState.CandidateReview -> reduceCandidateReview(current, event)
        is PhotoAnalysisState.Recovery -> reduceRecovery(current, event)
        is PhotoAnalysisState.Backgrounded -> current
    }

    private fun reduceCapture(event: PhotoAnalysisEvent): PhotoAnalysisState = when (event) {
        is PhotoAnalysisEvent.PhotoCaptured -> {
            check(event.ref.attemptId == context.attemptId) { "Photo belongs to another attempt" }
            check(context.leases.isActive(event.ref)) { "Photo lease is not active" }
            PhotoAnalysisState.Preparing(event.ref)
        }

        is PhotoAnalysisEvent.PreparationFailed -> PhotoAnalysisState.Recovery(event.reason)

        PhotoAnalysisEvent.PermissionDenied -> PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED)

        else -> PhotoAnalysisState.Capture
    }

    private fun reducePreparing(current: PhotoAnalysisState.Preparing, event: PhotoAnalysisEvent): PhotoAnalysisState =
        when (event) {
            is PhotoAnalysisEvent.PreparationSucceeded -> if (
                event.input.ref == current.ref && context.leases.isActive(event.input.ref)
            ) {
                context.sessions.put(event.input)
                PhotoAnalysisState.Ready(event.input)
            } else {
                current
            }

            is PhotoAnalysisEvent.PreparationFailed -> {
                context.revoke(current.ref)
                PhotoAnalysisState.Recovery(event.reason)
            }

            is PhotoAnalysisEvent.ReplacePhoto -> replace(current.ref, event.ref)

            PhotoAnalysisEvent.Cancel -> cancel(current.ref)

            else -> current
        }

    private fun reduceReady(current: PhotoAnalysisState.Ready, event: PhotoAnalysisEvent): PhotoAnalysisState =
        when (event) {
            is PhotoAnalysisEvent.AnalysisStarted -> if (context.isLive(current.input.ref, current.input)) {
                PhotoAnalysisState.Analyzing(current.input.ref, event.requestId)
            } else {
                context.revoke(current.input.ref)
                PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED)
            }

            is PhotoAnalysisEvent.ReplacePhoto -> replace(current.input.ref, event.ref)

            PhotoAnalysisEvent.Cancel -> cancel(current.input.ref)

            else -> current
        }

    private fun reduceAnalyzing(current: PhotoAnalysisState.Analyzing, event: PhotoAnalysisEvent): PhotoAnalysisState =
        when (event) {
            is PhotoAnalysisEvent.AnalysisSucceeded -> when {
                event.requestId != current.requestId -> current

                context.isLive(current.ref) -> PhotoAnalysisState.CandidateReview(current.ref, current.requestId)

                else -> {
                    context.revoke(current.ref)
                    PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED)
                }
            }

            is PhotoAnalysisEvent.ReplacePhoto -> replace(current.ref, event.ref)

            PhotoAnalysisEvent.Cancel -> cancel(current.ref)

            else -> current
        }

    private fun reduceCandidateReview(
        current: PhotoAnalysisState.CandidateReview,
        event: PhotoAnalysisEvent,
    ): PhotoAnalysisState = when (event) {
        is PhotoAnalysisEvent.ReplacePhoto -> replace(current.ref, event.ref)
        PhotoAnalysisEvent.ScanAgain -> clearAndCapture(current.ref)
        PhotoAnalysisEvent.Cancel -> cancel(current.ref)
        else -> current
    }

    private fun reduceRecovery(current: PhotoAnalysisState.Recovery, event: PhotoAnalysisEvent): PhotoAnalysisState =
        when (event) {
            is PhotoAnalysisEvent.ReplacePhoto -> replace(current.ref, event.ref)
            PhotoAnalysisEvent.ScanAgain -> current.ref?.let(::clearAndCapture) ?: PhotoAnalysisState.Capture
            else -> current
        }

    private fun replace(old: PhotoRef?, replacement: PhotoRef): PhotoAnalysisState {
        check(replacement.attemptId == context.attemptId) { "Replacement belongs to another attempt" }
        check(context.leases.isActive(replacement)) { "Replacement photo lease is not active" }
        check(replacement != old) { "Replacement must be a new photo lease" }
        old?.let(context::revoke)
        return PhotoAnalysisState.Preparing(replacement)
    }

    private fun cancel(ref: PhotoRef): PhotoAnalysisState {
        context.revoke(ref)
        return PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED)
    }

    private fun clearAndCapture(ref: PhotoRef): PhotoAnalysisState {
        context.revoke(ref)
        return PhotoAnalysisState.Capture
    }
}
