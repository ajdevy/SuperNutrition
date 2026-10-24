package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

@Composable
fun rememberProfileFlowState(): ProfileFlowState = rememberSaveable(saver = ProfileFlowState.StateSaver) {
    ProfileFlowState()
}

/** Shared destination switching keeps native hosts free of a second history implementation. */
@Composable
fun ProfileFlow(
    state: ProfileFlowState,
    displayName: String,
    actions: ProfileScreenActions,
    modifier: Modifier = Modifier,
    diary: ProfileDiaryContext = ProfileDiaryContext(),
) {
    val savedState = rememberSaveableStateHolder()
    val meals = mergedMealHistory(diary.confirmedFoods, diary.savedMeals)
    val selected = meals.firstOrNull { it.id == state.selectedMealId }
    LaunchedEffect(state.selectedMealId, selected) {
        if (state.selectedMealId != null && selected == null) state.showHistory()
    }
    savedState.SaveableStateProvider(profileDestinationKey(state, selected)) {
        if (selected != null) {
            ProfileSelectedMeal(
                state,
                selected,
                ProfileMealActions(diary.onMealEdited, diary.onMealDeleted),
                modifier,
                diary.copy,
            )
        } else if (state.historyVisible) {
            MealHistoryScreen(
                meals = meals,
                onBackRequested = state::showProfile,
                modifier = modifier,
                presentation = MealHistoryPresentation(
                    referenceDay = state.referenceDay,
                    searchState = state.historySearch,
                ),
                onMealSelected = { state.showMeal(it.id) },
            )
        } else {
            ControlledProfileScreen(
                identity = ProfileScreenIdentity(displayName, diary.confirmedFoods, diary.savedMeals),
                actions = actions.copy(onHistoryRequested = state::showHistory),
                flowState = state,
                modifier = modifier,
            )
        }
    }
}

private fun profileDestinationKey(state: ProfileFlowState, selected: MealHistoryMeal?): String = when {
    state.mealEditing && selected != null -> "edit:${selected.id}"
    selected != null -> "meal:${selected.id}"
    state.historyVisible -> "history"
    else -> "profile"
}
