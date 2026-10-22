package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

@Composable
fun rememberProfileFlowState(): ProfileFlowState = rememberSaveable(saver = ProfileFlowState.StateSaver) {
    ProfileFlowState()
}

/** Shared destination switching keeps native hosts free of a second history implementation. */
@Composable
fun ProfileFlow(
    state: ProfileFlowState,
    displayName: String,
    confirmedFoods: List<ConfirmedConsumedFood>,
    actions: ProfileScreenActions,
    modifier: Modifier = Modifier,
) {
    val savedState = rememberSaveableStateHolder()
    val destination = if (state.historyVisible) "history" else "profile"
    savedState.SaveableStateProvider(destination) {
        if (state.historyVisible) {
            MealHistoryScreen(
                meals = confirmedMealHistory(confirmedFoods),
                onBackRequested = state::showProfile,
                modifier = modifier,
                referenceDay = state.referenceDay,
            )
        } else {
            ProfileScreen(
                displayName,
                confirmedFoods,
                actions.copy(onHistoryRequested = state::showHistory),
                modifier,
            )
        }
    }
}
