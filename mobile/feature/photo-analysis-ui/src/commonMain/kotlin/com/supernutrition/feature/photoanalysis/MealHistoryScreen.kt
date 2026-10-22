package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.profile_back
import org.jetbrains.compose.resources.painterResource

// Preserve the warm design tone with at least 4.5:1 small-text contrast on both light surfaces.
internal val HistoryMuted = Color(0xFF7D6B5F)

/** Dedicated session history. Queries and selected contents deliberately use in-memory state. */
@Composable
fun MealHistoryScreen(
    meals: List<MealHistoryMeal>,
    onBackRequested: () -> Unit,
    modifier: Modifier = Modifier,
    referenceDay: Int = currentHistoryReferenceDay(),
    copy: MealHistoryCopy = MealHistoryCopy(),
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MealHistoryId?>(null) }
    val visible = searchMealHistory(meals, query)
    Box(modifier.fillMaxSize().background(ProfileBackground).automationId(UiAutomationIds.MEAL_HISTORY_SCREEN)) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = MainContentMaxWidth).fillMaxSize()
                .padding(horizontal = 16.dp).padding(top = 7.dp),
        ) {
            MealHistoryHeader(copy, onBackRequested)
            Spacer(Modifier.height(5.dp))
            MealHistorySearch(query, { query = it }, copy)
            Spacer(Modifier.height(20.dp))
            MealHistoryList(visible, referenceDay, copy, meals.isEmpty()) { selected = it.id }
        }
    }
    meals.firstOrNull { it.id == selected }?.let { meal ->
        MealHistorySummary(meal, copy) { selected = null }
    }
}

@Composable
private fun MealHistoryHeader(copy: MealHistoryCopy, onBackRequested: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = copy.back, onClick = onBackRequested)
                .semantics { contentDescription = copy.back }.automationId(UiAutomationIds.MEAL_HISTORY_CLOSE),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(38.dp).shadow(
                    1.dp,
                    CircleShape,
                    ambientColor = Color(0x0A1C241E),
                    spotColor = Color(0x0A1C241E),
                ).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(Res.drawable.profile_back), null, Modifier.size(20.dp))
            }
        }
        Text(
            copy.title,
            modifier = Modifier.weight(1f).automationId(UiAutomationIds.MEAL_HISTORY_TITLE),
            color = ProfileInk,
            fontFamily = frauncesFont(),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun MealHistoryList(
    meals: List<MealHistoryMeal>,
    referenceDay: Int,
    copy: MealHistoryCopy,
    emptyLedger: Boolean,
    onMealSelected: (MealHistoryMeal) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().automationId(UiAutomationIds.MEAL_HISTORY_LIST),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (meals.isEmpty()) {
            item {
                MealHistoryEmpty(copy, emptyLedger)
            }
        }
        itemsIndexed(meals, key = { _, meal -> "${meal.id.localDay}:${meal.id.mealId}" }) { index, meal ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (index == 0 || meals[index - 1].id.localDay != meal.id.localDay) {
                    Text(
                        mealHistoryDayLabel(meal.id.localDay, referenceDay, copy),
                        color = HistoryMuted,
                        fontFamily = historyNunitoFont(FontWeight.SemiBold),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                MealHistoryCard(meal, index, copy) { onMealSelected(meal) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MealHistoryEmpty(copy: MealHistoryCopy, emptyLedger: Boolean) {
    Column(
        Modifier.fillMaxWidth().automationId(
            if (emptyLedger) UiAutomationIds.MEAL_HISTORY_EMPTY else UiAutomationIds.MEAL_HISTORY_NO_RESULTS,
        ).padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (emptyLedger) copy.emptyTitle else copy.noResultsTitle,
            color = ProfileInk,
            fontFamily = historyNunitoFont(FontWeight.Bold),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (emptyLedger) copy.emptyDescription else copy.noResultsDescription,
            color = ProfileSecondary,
            fontFamily = nunitoFont(),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}
