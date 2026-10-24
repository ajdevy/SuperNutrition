package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The selected record owns one details/editor surface and preserves failed-save drafts. */
@Composable
internal fun ProfileSelectedMeal(
    state: ProfileFlowState,
    meal: MealHistoryMeal,
    actions: ProfileMealActions,
    modifier: Modifier,
    copy: MealDiaryCopy,
) {
    if (state.mealEditing && meal.savedMeal != null) {
        MealForm(
            config = MealFormConfig(
                title = copy.form.editTitle,
                initialDraft = meal.savedMeal.toMealDraft(),
                existingMeal = meal.savedMeal,
                editing = true,
            ),
            actions = MealFormActions(
                onCancel = state::cancelMealEdit,
                onSave = { draft ->
                    actions.onEdited(meal.id.mealId, draft).also { saved -> if (saved) state.cancelMealEdit() }
                },
            ),
            modifier = modifier,
            copy = copy.form,
        )
    } else {
        MealDetailsScreen(
            meal = meal,
            actions = MealDetailsActions(
                onBackRequested = { state.back() },
                onEditRequested = if (meal.savedMeal != null) state::beginMealEdit else null,
                onDeleteRequested = if (meal.savedMeal != null) {
                    { actions.onDeleted(meal.id.mealId).also { deleted -> if (deleted) state.showHistory() } }
                } else {
                    null
                },
            ),
            modifier = modifier,
            referenceDay = state.referenceDay,
            copy = copy.details,
        )
    }
}
