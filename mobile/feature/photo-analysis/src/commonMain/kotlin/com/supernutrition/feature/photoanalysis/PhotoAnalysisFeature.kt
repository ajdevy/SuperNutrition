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
    val mealDiary: MealDiary = MealDiary(),
) {
    private val mealReviewDiaryRecorder = MealReviewDiaryRecorder(mealDiary)
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

    private val _confirmedMealReview = MutableStateFlow<PhotoAnalysisMealReviewConfirmation?>(null)
    val confirmedMealReview: StateFlow<PhotoAnalysisMealReviewConfirmation?> = _confirmedMealReview.asStateFlow()

    private val _mealReviewSelection = MutableStateFlow(PhotoAnalysisMealReviewSelection())
    val mealReviewSelection: StateFlow<PhotoAnalysisMealReviewSelection> = _mealReviewSelection.asStateFlow()

    /** Lets host-owned background work continue against the retained domain state. */
    fun currentWorkState(): PhotoAnalysisState = reducer.currentWorkState()

    fun dispatch(event: PhotoAnalysisEvent) {
        val previous = currentWorkState()
        reducer.dispatch(event)
        _state.value = reducer.state
        if (previous != currentWorkState() && event.resetsMealReview()) {
            _manualIngredientStateFlow.value = PhotoAnalysisManualIngredientState()
            _confirmedMealReview.value = null
            _mealReviewSelection.value = PhotoAnalysisMealReviewSelection()
            mealReviewDiaryRecorder.reset()
        } else if (previous != currentWorkState() && event is PhotoAnalysisEvent.GridCellSelected) {
            _confirmedMealReview.value = null
            _mealReviewSelection.value = _mealReviewSelection.value.withoutPhotoEvidence()
        }
    }

    /** Stores UI choices against the retained review, including while its renderer is backgrounded. */
    fun updateMealReviewSelection(selection: PhotoAnalysisMealReviewSelection): Boolean {
        val draft = when (val current = currentWorkState()) {
            is PhotoAnalysisState.CandidateReview -> current.draft
            is PhotoAnalysisState.Ready -> null
            else -> return false
        }
        if (selection.customMeal && selection.dishId != null) return false
        val validDishIds = draft?.mainDishProposals.orEmpty().map { it.candidateId }
        if (selection.dishId != null && selection.dishId !in validDishIds) return false
        val validComponentIds = draft?.photoEvidence.orEmpty().map { it.componentId }
        if (selection.componentIds.any { it !in validComponentIds }) return false
        _mealReviewSelection.value = selection.copy(
            componentIds = selection.componentIds.toSet(),
            pantryIds = selection.pantryIds.toSet(),
        )
        return true
    }

    /** Retains the user's reviewed draft for this session, without inventing nutrition or catalog matches. */
    fun confirmMealReview(confirmation: PhotoAnalysisMealReviewConfirmation): Boolean {
        val draft = when (val current = currentWorkState()) {
            is PhotoAnalysisState.CandidateReview -> current.draft
            is PhotoAnalysisState.Ready -> null
            else -> return false
        }
        val dish = confirmation.dishCandidateId?.let { id ->
            draft?.mainDishProposals?.firstOrNull { it.candidateId == id } ?: return false
        }
        val validComponentIds = draft?.photoEvidence.orEmpty().map { it.componentId }
        if (confirmation.photoEvidenceComponentIds.any { it !in validComponentIds }) return false
        val added = confirmation.addedIngredientLabels.map(String::trim).filter(String::isNotEmpty).distinct()
        val note = confirmation.note.trim()
        val hasUserContent =
            confirmation.photoEvidenceComponentIds.isNotEmpty() || added.isNotEmpty() || note.isNotEmpty()
        if (dish == null && !hasUserContent) {
            return false
        }
        _confirmedMealReview.value = confirmation.copy(
            dishLabel = dish?.label ?: confirmation.dishLabel,
            photoEvidenceComponentIds = validComponentIds.filter { it in confirmation.photoEvidenceComponentIds },
            addedIngredientLabels = added,
            note = note,
        )
        return true
    }

    fun saveReviewedMeal(confirmation: PhotoAnalysisMealReviewConfirmation): Boolean {
        if (!confirmMealReview(confirmation)) return false
        return mealReviewDiaryRecorder.save(
            checkNotNull(_confirmedMealReview.value),
            (currentWorkState() as? PhotoAnalysisState.CandidateReview)?.draft,
        )
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

private fun PhotoAnalysisEvent.resetsMealReview(): Boolean = when (this) {
    PhotoAnalysisEvent.Cancel,
    PhotoAnalysisEvent.ScanAgain,
    is PhotoAnalysisEvent.PhotoCaptured,
    is PhotoAnalysisEvent.ReplacePhoto,
    -> true

    else -> false
}
