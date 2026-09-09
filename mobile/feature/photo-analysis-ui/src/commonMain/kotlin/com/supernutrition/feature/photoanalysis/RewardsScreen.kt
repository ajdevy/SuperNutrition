package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

/** Temporary list surface for the reward cards; the visual design can replace this later. */
@Composable
fun RewardsScreen(model: RewardsPresenterModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(top = 16.dp)
                .automationId(UiAutomationIds.REWARDS_TITLE),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .automationId(UiAutomationIds.REWARDS_LIST),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = model.cards,
                key = { _, card -> card.id.name },
            ) { index, card ->
                RewardsCard(index = index, card = card)
            }
        }
    }
}

@Composable
private fun RewardsCard(index: Int, card: RewardsCardModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .automationId(UiAutomationIds.rewardsRow(index))
            .semantics {
                contentDescription = listOf(
                    card.title,
                    card.description,
                    card.progressLabel,
                    card.stateLabel,
                ).joinToString(". ")
                stateDescription = card.stateLabel
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.automationId(UiAutomationIds.rewardsRowTitle(index)),
            )
            Text(text = card.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = card.progressLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.automationId(UiAutomationIds.rewardsRowProgress(index)),
            )
            Text(
                text = card.stateLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.automationId(UiAutomationIds.rewardsRowState(index)),
            )
        }
    }
}
