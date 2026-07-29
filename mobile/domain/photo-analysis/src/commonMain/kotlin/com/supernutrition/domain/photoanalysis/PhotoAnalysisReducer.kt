package com.supernutrition.domain.photoanalysis

sealed interface PhotoAnalysisEffect {
    data class StoreInput(val input: NormalizedPhotoInput) : PhotoAnalysisEffect
    data class RevokePhoto(val ref: PhotoRef) : PhotoAnalysisEffect
}

data class PhotoAnalysisReduction(
    val state: PhotoAnalysisState,
    val effects: List<PhotoAnalysisEffect> = emptyList(),
)

/** Stateful compatibility facade over a pure state/event reduction. */
class PhotoAnalysisReducer(
    leases: PhotoLeaseStorePort,
    sessions: PhotoSessionStorePort,
    attemptId: AttemptId,
) {
    private val context = PhotoAnalysisReducerContext(leases, sessions, attemptId)
    private val transitions = PhotoAnalysisReducerTransitions(context)
    private val lifecycle = PhotoAnalysisReducerLifecycle(context)

    var state: PhotoAnalysisState = PhotoAnalysisState.Capture
        private set

    /** Issues an opaque lease; platform URIs and native objects never enter the domain. */
    fun issuePhotoRef(opaqueId: String): PhotoRef = context.leases.issue(context.attemptId, opaqueId)

    fun dispatch(event: PhotoAnalysisEvent) {
        val reduction = reduce(state, event)
        reduction.effects.forEach(context::apply)
        state = reduction.state
    }

    /** Exposes the deterministic transition and effect decision for unit tests and adapters. */
    fun reduce(current: PhotoAnalysisState, event: PhotoAnalysisEvent): PhotoAnalysisReduction = when {
        event is PhotoAnalysisEvent.Backgrounded && current !is PhotoAnalysisState.Backgrounded ->
            PhotoAnalysisReduction(lifecycle.backgroundSnapshot(current))

        current is PhotoAnalysisState.Backgrounded && event is PhotoAnalysisEvent.Restored ->
            PhotoAnalysisReduction(lifecycle.restore(current.snapshot))

        current == PhotoAnalysisState.Capture && event is PhotoAnalysisEvent.RestoreFromSnapshot ->
            PhotoAnalysisReduction(lifecycle.restore(event.snapshot))

        else -> transitions.reduce(current, event)
    }

    /** Returns the opaque lifecycle snapshot without persisting photo bytes or native handles. */
    fun snapshotForPersistence(): PhotoAnalysisBackgroundSnapshot = when (val current = state) {
        is PhotoAnalysisState.Backgrounded -> current.snapshot
        else -> lifecycle.backgroundSnapshot(current).snapshot
    }
}

internal class PhotoAnalysisReducerContext(
    val leases: PhotoLeaseStorePort,
    val sessions: PhotoSessionStorePort,
    val attemptId: AttemptId,
) {
    fun isCurrentAttempt(ref: PhotoRef): Boolean = ref.attemptId == attemptId

    fun isLive(ref: PhotoRef, expected: NormalizedPhotoInput? = null): Boolean = isCurrentAttempt(ref) &&
        leases.isActive(ref) &&
        (expected == null || sessions.read(ref) == expected)

    fun apply(effect: PhotoAnalysisEffect) {
        when (effect) {
            is PhotoAnalysisEffect.StoreInput -> sessions.put(effect.input)
            is PhotoAnalysisEffect.RevokePhoto -> {
                leases.revoke(effect.ref)
                sessions.remove(effect.ref)
            }
        }
    }
}
