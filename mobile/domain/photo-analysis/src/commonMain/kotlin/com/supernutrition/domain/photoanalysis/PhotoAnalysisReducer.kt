package com.supernutrition.domain.photoanalysis

sealed interface PhotoAnalysisEffect {
    data class StoreInput(val input: NormalizedPhotoInput) : PhotoAnalysisEffect
    data class RevokePhoto(val ref: PhotoRef) : PhotoAnalysisEffect
}

data class PhotoAnalysisReduction(val state: PhotoAnalysisState, val effects: List<PhotoAnalysisEffect> = emptyList())

/** Stateful compatibility facade over a pure state/event reduction. */
class PhotoAnalysisReducer(leases: PhotoLeaseStorePort, sessions: PhotoSessionStorePort, attemptId: AttemptId) {
    private val context = PhotoAnalysisReducerContext(leases, sessions, attemptId)
    private val transitions = PhotoAnalysisReducerTransitions(context)
    private val lifecycle = PhotoAnalysisReducerLifecycle(context)
    private val routeCoordinator = RouteAttemptCoordinator(attemptId)
    private var inMemoryBackgroundState: PhotoAnalysisState? = null

    var state: PhotoAnalysisState = PhotoAnalysisState.Capture
        private set

    /** Issues an opaque lease; platform URIs and native objects never enter the domain. */
    fun issuePhotoRef(opaqueId: String): PhotoRef = context.leases.issue(context.attemptId, opaqueId)

    fun dispatch(event: PhotoAnalysisEvent) {
        val previous = state
        val retainedState = if (previous is PhotoAnalysisState.Backgrounded && event is PhotoAnalysisEvent.Restored) {
            inMemoryBackgroundState
        } else {
            null
        }
        val reduction = retainedState?.let { PhotoAnalysisReduction(lifecycle.restoreInMemory(it)) }
            ?: reduce(state, event)
        state = reduction.state
        when (event) {
            PhotoAnalysisEvent.Backgrounded -> if (previous !is PhotoAnalysisState.Backgrounded) {
                inMemoryBackgroundState = previous
            }

            PhotoAnalysisEvent.Restored,
            is PhotoAnalysisEvent.RestoreFromSnapshot,
            is PhotoAnalysisEvent.ReplacePhoto,
            PhotoAnalysisEvent.Cancel,
            PhotoAnalysisEvent.ScanAgain,
            -> inMemoryBackgroundState = null

            else -> Unit
        }
        if (state != previous && event.invalidatesRoute()) {
            routeCoordinator.cancel()
        }
        reduction.effects.forEach(context::apply)
    }

    fun startRouteAttempt(context: RouteAttemptContext): RouteAttemptToken = routeCoordinator.start(context)

    fun acceptRouteDecision(decision: RouteDecision): Boolean = routeCoordinator.accepts(decision)

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

private fun PhotoAnalysisEvent.invalidatesRoute(): Boolean = when (this) {
    is PhotoAnalysisEvent.ReplacePhoto,
    PhotoAnalysisEvent.Cancel,
    PhotoAnalysisEvent.ScanAgain,
    is PhotoAnalysisEvent.GridCellSelected,
    -> true

    else -> false
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
