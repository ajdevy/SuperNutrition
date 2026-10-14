package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Immutable

@Immutable
data class HomeMetricModel(val label: String, val value: String)

@Immutable
data class HomeScreenModel(
    val title: String,
    val greeting: String,
    val dailySummaryTitle: String,
    val dailySummaryValue: String,
    val dailySummaryDescription: String,
    val emptyTitle: String,
    val emptyDescription: String,
    val addFoodLabel: String,
    val dateLabel: String,
    val careQuestion: String,
    val rhythmTitle: String,
    val rhythmSubtitle: String,
    val rhythmBadgeLabel: String,
    val fiber: HomeMetricModel,
    val water: HomeMetricModel,
    val protein: HomeMetricModel,
    val movement: HomeMetricModel,
    val nextStepTitle: String,
    val nextStepDescription: String,
    val nextStepAction: String,
    val viewGoalsLabel: String,
    val goalsTitle: String,
    val goalsDescription: String,
    val goalsDoneLabel: String,
    val profileActionLabel: String,
    val mascotDescription: String,
    val saladDescription: String,
)
