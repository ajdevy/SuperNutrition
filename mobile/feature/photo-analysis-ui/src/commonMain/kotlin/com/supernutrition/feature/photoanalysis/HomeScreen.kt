package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

data class HomeScreenModel(
    val title: String,
    val greeting: String,
    val dailySummaryTitle: String,
    val dailySummaryValue: String,
    val dailySummaryDescription: String,
    val emptyTitle: String,
    val emptyDescription: String,
    val addFoodLabel: String,
)

@Composable
fun HomeScreen(
    model: HomeScreenModel,
    modifier: Modifier = Modifier,
    onAddFoodRequested: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(top = 16.dp)
                .automationId(UiAutomationIds.HOME_TITLE),
        )
        Text(
            text = model.greeting,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.automationId(UiAutomationIds.HOME_GREETING),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .automationId(UiAutomationIds.HOME_SUMMARY)
                .semantics {
                    contentDescription = listOf(
                        model.dailySummaryTitle,
                        model.dailySummaryValue,
                        model.dailySummaryDescription,
                    ).joinToString(". ")
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = model.dailySummaryTitle,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = model.dailySummaryValue,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = model.dailySummaryDescription,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = model.emptyTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.automationId(UiAutomationIds.HOME_EMPTY_TITLE),
        )
        Text(
            text = model.emptyDescription,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.automationId(UiAutomationIds.HOME_EMPTY_DESCRIPTION),
        )
        Button(
            onClick = onAddFoodRequested,
            modifier = Modifier
                .fillMaxWidth()
                .automationId(UiAutomationIds.HOME_ADD_FOOD),
        ) {
            Text(text = model.addFoodLabel)
        }
    }
}
