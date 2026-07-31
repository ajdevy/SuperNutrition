package com.supernutrition.domain.photoanalysis

internal class PhotoAnalysisReducerLifecycle(private val context: PhotoAnalysisReducerContext) {
    fun backgroundSnapshot(current: PhotoAnalysisState): PhotoAnalysisState.Backgrounded =
        PhotoAnalysisState.Backgrounded(current.toBackgroundSnapshot())

    fun restore(snapshot: PhotoAnalysisBackgroundSnapshot): PhotoAnalysisState = when (snapshot) {
        PhotoAnalysisBackgroundSnapshot.Capture -> PhotoAnalysisState.Capture

        is PhotoAnalysisBackgroundSnapshot.Preparing -> restorePreparing(snapshot.ref)

        is PhotoAnalysisBackgroundSnapshot.Ready -> restoreInput(snapshot.ref, PhotoAnalysisState::Ready)

        is PhotoAnalysisBackgroundSnapshot.Analyzing -> restoreInput(snapshot.ref) {
            PhotoAnalysisState.Analyzing(snapshot.ref, snapshot.requestId)
        }

        is PhotoAnalysisBackgroundSnapshot.CandidateReview -> restoreInput(snapshot.ref) {
            PhotoAnalysisState.CandidateReview(snapshot.ref, snapshot.requestId)
        }

        is PhotoAnalysisBackgroundSnapshot.Recovery -> restoreRecovery(snapshot)
    }

    private fun restorePreparing(ref: PhotoRef): PhotoAnalysisState =
        if (context.isCurrentAttempt(ref) && context.leases.isActive(ref)) {
            PhotoAnalysisState.Preparing(ref)
        } else {
            PhotoAnalysisState.Capture
        }

    private fun restoreInput(
        ref: PhotoRef,
        stateFactory: (NormalizedPhotoInput) -> PhotoAnalysisState,
    ): PhotoAnalysisState {
        if (!context.isCurrentAttempt(ref)) return PhotoAnalysisState.Capture
        if (!context.leases.isActive(ref)) return failedRestore(ref)
        return context.sessions.read(ref)?.let(stateFactory) ?: failedRestore(ref)
    }

    private fun restoreRecovery(snapshot: PhotoAnalysisBackgroundSnapshot.Recovery): PhotoAnalysisState =
        if (snapshot.ref == null || context.isCurrentAttempt(snapshot.ref)) {
            PhotoAnalysisState.Recovery(snapshot.reason, snapshot.ref)
        } else {
            PhotoAnalysisState.Capture
        }

    private fun failedRestore(ref: PhotoRef): PhotoAnalysisState =
        PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, ref)
}
