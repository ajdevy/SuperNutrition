package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

/** Read-only names from the selected confirmed record; no nutrition or metadata is inferred. */
@Composable
internal fun MealHistorySummary(meal: MealHistoryMeal, copy: MealHistoryCopy, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.automationId(UiAutomationIds.MEAL_HISTORY_SUMMARY),
        title = { Text(meal.title, fontFamily = frauncesFont()) },
        text = {
            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(copy.confirmedFoods, fontFamily = nunitoFont())
                Text(meal.ingredients.joinToString(", "), fontFamily = nunitoFont())
                Text(meal.metadata.timeLabel ?: copy.timeUnavailable, fontFamily = nunitoFont())
                if (meal.metadata.image == null) Text(copy.photoUnavailable, fontFamily = nunitoFont())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(copy.done, fontFamily = nunitoFont()) } },
    )
}
