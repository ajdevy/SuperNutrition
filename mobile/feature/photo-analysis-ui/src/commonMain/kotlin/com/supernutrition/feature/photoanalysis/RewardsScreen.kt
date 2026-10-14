package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

/** The recovered illustrated reward collection, backed by the current rewards snapshot. */
@Composable
fun RewardsScreen(model: RewardsPresenterModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(RewardsPaper)) {
        val columnCount = if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.2f) 1 else 2
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = MainContentMaxWidth)
                .fillMaxSize().padding(horizontal = MainScreenGutter).automationId(UiAutomationIds.REWARDS_LIST),
            contentPadding = PaddingValues(bottom = LocalMainBottomBarInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "rewards-header") {
                Box(Modifier.padding(bottom = 2.dp)) { RewardsHeader(model) }
            }
            itemsIndexed(
                items = model.cards.chunked(columnCount),
                key = { _, row -> row.first().id.name },
            ) { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEachIndexed { columnIndex, card ->
                        RewardsCard(
                            index = rowIndex * columnCount + columnIndex,
                            card = card,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
