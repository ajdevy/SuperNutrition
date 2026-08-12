package com.supernutrition.domain.photoanalysis

internal class PhotoAnalysisReducerTransitions(private val context: PhotoAnalysisReducerContext) {
    private val analysisTransitions = PhotoAnalysisAnalysisTransitions(context)

    fun reduce(current: PhotoAnalysisState, event: PhotoAnalysisEvent): PhotoAnalysisReduction = when (current) {
        PhotoAnalysisState.Capture -> reduceCapture(event)
        is PhotoAnalysisState.Preparing -> reducePreparing(current, event)
        is PhotoAnalysisState.Ready -> reduceReady(current, event)
        is PhotoAnalysisState.Analyzing -> analysisTransitions.reduce(current, event)
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

        // P2-2 — the reason was discarded and the user was returned to a plain
        // capture screen, with nothing to say why the photo they just took did
        // not take. Recovery is the state that can say so, and it is already
        // where the same event goes once capture has succeeded.
        is PhotoAnalysisEvent.PreparationFailed -> unchanged(PhotoAnalysisState.Recovery(event.reason))

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

        PhotoAnalysisEvent.ScanAgain -> clearAndCapture(current.ref)

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

            is PhotoAnalysisEvent.GridCellSelected -> unchanged(current)

            is PhotoAnalysisEvent.ReplacePhoto -> replace(current, current.input.ref, event.ref)

            PhotoAnalysisEvent.ScanAgain -> clearAndCapture(current.input.ref)

            PhotoAnalysisEvent.Cancel -> cancel(current.input.ref)

            else -> unchanged(current)
        }

    private fun reduceCandidateReview(
        current: PhotoAnalysisState.CandidateReview,
        event: PhotoAnalysisEvent,
    ): PhotoAnalysisReduction = when (event) {
        is PhotoAnalysisEvent.GridCellSelected -> prepareGridReanalysis(current.ref)
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

        // P1-6 — the Android host dispatches PhotoCaptured after an import, from
        // Recovery as much as from Capture. Recovery accepted only ReplacePhoto
        // and ScanAgain, so the import fell to `else` and the user sat in
        // Recovery looking at a photo the app had already taken.
        is PhotoAnalysisEvent.PhotoCaptured ->
            if (event.ref.attemptId == context.attemptId && context.leases.isActive(event.ref)) {
                unchanged(PhotoAnalysisState.Preparing(event.ref))
            } else {
                unchanged(current)
            }

        else -> unchanged(current)
    }

    private fun prepareGridReanalysis(ref: PhotoRef): PhotoAnalysisReduction =
        context.sessions.read(ref)?.takeIf { context.isLive(ref, it) }?.let { input ->
            unchanged(PhotoAnalysisState.Ready(input))
        } ?: reduction(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, ref),
            PhotoAnalysisEffect.RevokePhoto(ref),
        )

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
}

internal fun unchanged(state: PhotoAnalysisState) = PhotoAnalysisReduction(state)

internal fun reduction(state: PhotoAnalysisState, effect: PhotoAnalysisEffect) =
    PhotoAnalysisReduction(state, listOf(effect))
