package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.RewardId

@Composable
internal fun RewardDetailsEvidence(
    id: RewardId,
    foods: List<ConfirmedConsumedFood>,
    referenceDay: Int,
    copy: RewardDetailsScreenCopy,
    savedMeals: List<SavedMeal> = emptyList(),
) {
    if (id == RewardId.SEVEN_DAYS_RECORDED) {
        RecordingRewardEvidence(foods, savedMeals, referenceDay, copy)
        return
    }
    val weekly = id in listOf(RewardId.THIRTY_PRODUCTS, RewardId.FERMENTED_THREE_DAYS)
    val startDay = if (weekly) (referenceDay - 6).coerceAtLeast(0) else referenceDay
    val relevant = foods.filter { it.localDay in startDay..referenceDay && it.countsFor(id) }
    if (relevant.isEmpty()) {
        DiaryBody(copy.noFoods, secondary = true)
        DiaryBody(copy.noFoodsDescription, secondary = true)
    } else {
        relevant.groupBy { MealHistoryId(it.localDay, it.mealId) }.forEach { (meal, entries) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DiaryBody(entries.map { it.displayName }.distinct().joinToString(", "))
                DiaryBody(mealHistoryDayLabel(meal.localDay, referenceDay, MealHistoryCopy()), secondary = true)
            }
        }
    }
}

private fun ConfirmedConsumedFood.countsFor(id: RewardId): Boolean = when (id) {
    RewardId.PROTEIN_ALL_DAY -> attributes.hasProtein
    RewardId.FATS_PRESENT -> attributes.fatGroup != null
    RewardId.FIBER_PRESENT -> attributes.fiberGroup != null
    RewardId.COLORS_OF_DAY -> attributes.color != null
    RewardId.FERMENTED_THREE_DAYS -> attributes.isFermented
    RewardId.THIRTY_PRODUCTS, RewardId.SEVEN_DAYS_RECORDED -> true
}
