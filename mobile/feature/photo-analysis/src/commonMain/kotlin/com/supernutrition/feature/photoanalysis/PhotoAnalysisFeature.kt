package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisReducer
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.domain.photoanalysis.RouteAttemptContext
import com.supernutrition.domain.photoanalysis.RouteAttemptToken
import com.supernutrition.domain.photoanalysis.RouteDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Composition-root feature coordinator; presenter and UI remain reusable seams. */
class PhotoAnalysisFeature(attemptId: AttemptId, leases: PhotoLeaseStorePort, sessions: PhotoSessionStorePort) {
    private val reducer = PhotoAnalysisReducer(
        leases = leases,
        sessions = sessions,
        attemptId = attemptId,
    )
    private val _state = MutableStateFlow(reducer.state)

    val state: PhotoAnalysisState
        get() = _state.value

    val stateFlow: StateFlow<PhotoAnalysisState> = _state.asStateFlow()

    fun dispatch(event: PhotoAnalysisEvent) {
        reducer.dispatch(event)
        _state.value = reducer.state
    }

    fun snapshotForPersistence(): PhotoAnalysisBackgroundSnapshot = reducer.snapshotForPersistence()

    fun issuePhotoRef(opaqueId: String): PhotoRef = reducer.issuePhotoRef(opaqueId)

    fun startRouteAttempt(context: RouteAttemptContext): RouteAttemptToken = reducer.startRouteAttempt(context)

    fun acceptRouteDecision(decision: RouteDecision): Boolean = reducer.acceptRouteDecision(decision)
}
