package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ManualIngredient
import com.supernutrition.domain.nutrition.ManualIngredientParser
import com.supernutrition.domain.nutrition.productionManualIngredientParser
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

data class PhotoAnalysisManualIngredientState(
    val query: String = "",
    val ingredients: List<ManualIngredient> = emptyList(),
    val unresolvedFragments: List<String> = emptyList(),
)

/** Composition-root feature coordinator; presenter and UI remain reusable seams. */
class PhotoAnalysisFeature(
    attemptId: AttemptId,
    leases: PhotoLeaseStorePort,
    sessions: PhotoSessionStorePort,
    private val manualIngredientParser: ManualIngredientParser = productionManualIngredientParser(),
) {
    private val reducer = PhotoAnalysisReducer(
        leases = leases,
        sessions = sessions,
        attemptId = attemptId,
    )
    private val _state = MutableStateFlow(reducer.state)

    val state: PhotoAnalysisState
        get() = _state.value

    val stateFlow: StateFlow<PhotoAnalysisState> = _state.asStateFlow()

    private val _manualIngredientStateFlow = MutableStateFlow(PhotoAnalysisManualIngredientState())
    val manualIngredientStateFlow: StateFlow<PhotoAnalysisManualIngredientState> =
        _manualIngredientStateFlow.asStateFlow()

    /** Lets host-owned background work continue against the retained domain state. */
    fun currentWorkState(): PhotoAnalysisState = reducer.currentWorkState()

    fun dispatch(event: PhotoAnalysisEvent) {
        reducer.dispatch(event)
        _state.value = reducer.state
        if (event == PhotoAnalysisEvent.Cancel) {
            _manualIngredientStateFlow.value = PhotoAnalysisManualIngredientState()
        }
    }

    fun updateManualIngredientQuery(query: String) {
        _manualIngredientStateFlow.value = _manualIngredientStateFlow.value.copy(
            query = query,
            unresolvedFragments = emptyList(),
        )
    }

    fun addManualIngredients() {
        val current = _manualIngredientStateFlow.value
        val result = manualIngredientParser.parse(current.query)
        if (result.ingredients.isEmpty() && result.unresolvedFragments.isEmpty()) return

        _manualIngredientStateFlow.value = current.copy(
            query = if (result.ingredients.isEmpty()) current.query else "",
            ingredients = (current.ingredients + result.ingredients)
                .distinctBy(ManualIngredient::canonicalItemId),
            unresolvedFragments = result.unresolvedFragments,
        )
    }

    fun snapshotForPersistence(): PhotoAnalysisBackgroundSnapshot = reducer.snapshotForPersistence()

    fun issuePhotoRef(opaqueId: String): PhotoRef = reducer.issuePhotoRef(opaqueId)

    fun startRouteAttempt(context: RouteAttemptContext): RouteAttemptToken = reducer.startRouteAttempt(context)

    fun acceptRouteDecision(decision: RouteDecision): Boolean = reducer.acceptRouteDecision(decision)
}
