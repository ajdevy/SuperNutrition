package com.supernutrition.domain.photoanalysis

/** One reducer owns replacement/cancellation and rejects stale engine callbacks. */
class PhotoAnalysisReducer(leases: PhotoLeaseStore, sessions: PhotoSessionStore, attemptId: AttemptId) {
    private val context = PhotoAnalysisReducerContext(leases, sessions, attemptId)
    private val transitions = PhotoAnalysisReducerTransitions(context)
    private val lifecycle = PhotoAnalysisReducerLifecycle(context)

    var state: PhotoAnalysisState = PhotoAnalysisState.Capture
        private set

    /** Issues an opaque lease; platform URIs and native objects never enter the domain. */
    fun issuePhotoRef(opaqueId: String): PhotoRef = context.leases.issue(context.attemptId, opaqueId)

    fun dispatch(event: PhotoAnalysisEvent) {
        val current = state
        state = when {
            event is PhotoAnalysisEvent.Backgrounded && current !is PhotoAnalysisState.Backgrounded ->
                lifecycle.backgroundSnapshot(current)

            current is PhotoAnalysisState.Backgrounded && event is PhotoAnalysisEvent.Restored ->
                lifecycle.restore(current.snapshot)

            current == PhotoAnalysisState.Capture && event is PhotoAnalysisEvent.RestoreFromSnapshot ->
                lifecycle.restore(event.snapshot)

            else -> transitions.reduce(current, event)
        }
    }

    /** Returns the opaque lifecycle snapshot without persisting photo bytes or native handles. */
    fun snapshotForPersistence(): PhotoAnalysisBackgroundSnapshot = when (val current = state) {
        is PhotoAnalysisState.Backgrounded -> current.snapshot
        else -> lifecycle.backgroundSnapshot(current).snapshot
    }
}

internal class PhotoAnalysisReducerContext(
    val leases: PhotoLeaseStore,
    val sessions: PhotoSessionStore,
    val attemptId: AttemptId,
) {
    fun isCurrentAttempt(ref: PhotoRef): Boolean = ref.attemptId == attemptId

    fun isLive(ref: PhotoRef, expected: NormalizedPhotoInput? = null): Boolean = isCurrentAttempt(ref) &&
        leases.isActive(ref) &&
        (expected == null || sessions.read(ref) == expected)

    fun revoke(ref: PhotoRef) {
        leases.revoke(ref)
        sessions.clearAttempt(attemptId)
    }
}
