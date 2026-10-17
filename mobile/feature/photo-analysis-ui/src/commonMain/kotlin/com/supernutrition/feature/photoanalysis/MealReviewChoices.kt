package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal typealias MealReviewChoices = PhotoAnalysisMealReviewSelection

internal fun MealReviewChoices.toggleComponent(id: String) = copy(componentIds = componentIds.toggle(id))

/** Native hosts own session state; standalone renderers retain their fallback across composition restoration. */
@Composable
internal fun PhotoAnalysisCaptureSurface.withRetainedMealReviewChoices(
    state: PhotoAnalysisPresenterState,
): PhotoAnalysisCaptureSurface {
    var fallback by rememberSaveable(stateSaver = mealReviewChoicesSaver) { mutableStateOf(MealReviewChoices()) }
    LaunchedEffect(state) {
        fallback = when (state) {
            PhotoAnalysisPresenterState.CAPTURE, PhotoAnalysisPresenterState.PREPARING -> MealReviewChoices()
            PhotoAnalysisPresenterState.READY, PhotoAnalysisPresenterState.ANALYZING -> fallback.withoutPhotoEvidence()
            else -> fallback
        }
    }
    return copy(
        mealReviewSelection = mealReviewSelection ?: fallback,
        onMealReviewSelectionChanged = { selection ->
            if (mealReviewSelection == null) fallback = selection
            onMealReviewSelectionChanged(selection)
        },
    )
}

private val mealReviewChoicesSaver = listSaver<MealReviewChoices, Any>(
    save = { choices ->
        listOf(choices.dishId.orEmpty(), choices.customMeal, choices.componentIds.size) +
            choices.componentIds + choices.pantryIds
    },
    restore = { saved ->
        val componentCount = saved[2] as Int
        MealReviewChoices(
            dishId = (saved[0] as String).takeIf(String::isNotEmpty),
            customMeal = saved[1] as Boolean,
            componentIds = saved.drop(3).take(componentCount).map { it as String }.toSet(),
            pantryIds = saved.drop(3 + componentCount).map { it as String }.toSet(),
        )
    },
)

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
