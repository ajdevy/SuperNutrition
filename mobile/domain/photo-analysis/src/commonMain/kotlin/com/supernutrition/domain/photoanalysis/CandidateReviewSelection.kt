package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.VisibleComponent

/** Immutable evidence offered for one active photo-analysis review. */
data class CandidateReviewDraft(
    val mainDishProposals: List<InferenceCandidate>,
    val photoEvidence: List<VisibleComponent>,
    /** Dish-prior hypotheses; these are never visual evidence. */
    val compositionHints: List<DishIngredientHint> = emptyList(),
    val degradation: CandidateReviewDegradation = CandidateReviewDegradation.NONE,
) {
    init {
        require(mainDishProposals.size <= MAX_MAIN_DISH_PROPOSALS) {
            "Candidate review must contain at most $MAX_MAIN_DISH_PROPOSALS main-dish proposals"
        }
        require(mainDishProposals.map(InferenceCandidate::candidateId).distinct().size == mainDishProposals.size) {
            "Candidate review main-dish proposal IDs must be unique"
        }
        require(mainDishProposals.map(InferenceCandidate::rank).distinct().size == mainDishProposals.size) {
            "Candidate review main-dish proposal ranks must be unique"
        }
        require(mainDishProposals.zipWithNext().all { (previous, next) -> previous.rank < next.rank }) {
            "Candidate review main-dish proposals must be ordered by rank"
        }
        require(photoEvidence.map(VisibleComponent::componentId).distinct().size == photoEvidence.size) {
            "Candidate review photo-evidence component IDs must be unique"
        }
        require(compositionHints.size <= MAX_COMPOSITION_HINTS) {
            "Candidate review must contain at most $MAX_COMPOSITION_HINTS composition hints"
        }
        require(compositionHints.map(DishIngredientHint::ingredientId).distinct().size == compositionHints.size) {
            "Candidate review composition hint IDs must be unique"
        }
        require(
            compositionHints.zipWithNext().all { (previous, next) ->
                previous.support >= next.support
            },
        ) {
            "Candidate review composition hints must be ordered by support"
        }
    }

    private companion object {
        const val MAX_MAIN_DISH_PROPOSALS = 3
        const val MAX_COMPOSITION_HINTS = 24
    }
}

/** Typed signal that review evidence is usable but one local stage abstained. */
enum class CandidateReviewDegradation {
    NONE,
    DISH_CANDIDATES_UNAVAILABLE,
}

/** Only explicit session-scoped user choices; it has no catalog, serving, or nutrition fields. */
data class CandidateReviewSelection(
    val mainDishCandidateId: String? = null,
    val photoEvidenceComponentIds: Set<String> = emptySet(),
)

data class CandidateReviewState(
    val draft: CandidateReviewDraft,
    val selection: CandidateReviewSelection = CandidateReviewSelection(),
)

sealed interface CandidateReviewAction {
    data class SelectMainDish(val candidateId: String) : CandidateReviewAction

    data object ClearMainDish : CandidateReviewAction

    data class TogglePhotoEvidence(val componentId: String) : CandidateReviewAction

    data object ClearSelections : CandidateReviewAction
}

/** Stable IDs delivered to the future E5.3 serving/grounding boundary only after explicit confirmation. */
data class CandidateReviewConfirmation(val mainDishCandidateId: String, val photoEvidenceComponentIds: List<String>)

/** Pure, deterministic state changes for Candidate review. It never invents foods or nutrition. */
object CandidateReviewSelectionReducer {
    fun reduce(state: CandidateReviewState, action: CandidateReviewAction): CandidateReviewState = when (action) {
        is CandidateReviewAction.SelectMainDish -> selectMainDish(state, action.candidateId)

        CandidateReviewAction.ClearMainDish -> state.copy(
            selection = state.selection.copy(mainDishCandidateId = null),
        )

        is CandidateReviewAction.TogglePhotoEvidence -> togglePhotoEvidence(state, action.componentId)

        CandidateReviewAction.ClearSelections -> state.copy(selection = CandidateReviewSelection())
    }

    /** Returns null until the person explicitly chooses a proposed main dish. */
    fun confirm(state: CandidateReviewState): CandidateReviewConfirmation? {
        val mainDishCandidateId = state.selection.mainDishCandidateId ?: return null
        return CandidateReviewConfirmation(
            mainDishCandidateId = mainDishCandidateId,
            photoEvidenceComponentIds = state.draft.photoEvidence
                .map(VisibleComponent::componentId)
                .filter(state.selection.photoEvidenceComponentIds::contains),
        )
    }

    private fun selectMainDish(state: CandidateReviewState, candidateId: String): CandidateReviewState =
        if (state.draft.mainDishProposals.any { it.candidateId == candidateId }) {
            state.copy(selection = state.selection.copy(mainDishCandidateId = candidateId))
        } else {
            state
        }

    private fun togglePhotoEvidence(state: CandidateReviewState, componentId: String): CandidateReviewState {
        if (state.draft.photoEvidence.none { it.componentId == componentId }) return state
        val selected = state.selection.photoEvidenceComponentIds
        val next = if (componentId in selected) selected - componentId else selected + componentId
        return state.copy(selection = state.selection.copy(photoEvidenceComponentIds = next))
    }
}
