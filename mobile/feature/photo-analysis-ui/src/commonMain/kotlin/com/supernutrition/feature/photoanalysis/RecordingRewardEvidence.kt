package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.recordingStreakDays

/** Only the evaluator's current consecutive recording days supply rhythm evidence. */
@Composable
internal fun RecordingRewardEvidence(
    foods: List<ConfirmedConsumedFood>,
    savedMeals: List<SavedMeal>,
    referenceDay: Int,
    copy: RewardDetailsScreenCopy,
) {
    val byDay = mergedMealHistory(foods, savedMeals).groupBy { it.id.localDay }
    val creditedDays = recordingStreakDays(byDay.keys, referenceDay)
    if (creditedDays.isEmpty()) {
        DiaryBody(copy.noRecordings, secondary = true)
        DiaryBody(copy.noRecordingsDescription, secondary = true)
    } else {
        creditedDays.forEach { day ->
            byDay.getValue(day).forEach { meal ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiaryBody(meal.title)
                    DiaryBody(mealHistoryDayLabel(day, referenceDay, MealHistoryCopy()), secondary = true)
                }
            }
        }
    }
}
