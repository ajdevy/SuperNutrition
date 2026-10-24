package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.PartialNutrientProfile

/** Shared production Home: count means today's recorded meals, never a nutrient or streak estimate. */
fun liveHomeScreenModel(
    dateLabel: String,
    savedMealCount: Int,
    copy: LiveHomeCopy = LiveHomeCopy(),
    storageLoadFailed: Boolean = false,
    todayNutrients: PartialNutrientProfile? = null,
): HomeScreenModel {
    require(savedMealCount >= 0) { "Today's saved meal count must not be negative" }
    val countLabel = (if (savedMealCount == 1) copy.singularMealCount else copy.pluralMealCount)
        .replace("{count}", savedMealCount.toString())
    val nutrients = if (storageLoadFailed) null else todayNutrients
    return HomeScreenModel(
        title = copy.title,
        greeting = copy.greeting,
        dailySummaryTitle = copy.mealsToday,
        dailySummaryValue = if (storageLoadFailed) "—" else countLabel,
        dailySummaryDescription = if (storageLoadFailed) copy.historyLoadFailed else copy.historyDescription,
        emptyTitle = copy.diaryTitle,
        emptyDescription = copy.addDescription,
        addFoodLabel = copy.addMeal,
        dateLabel = dateLabel,
        careQuestion = when {
            storageLoadFailed -> copy.historyUnavailable
            savedMealCount == 0 -> copy.firstMealPrompt
            else -> copy.nextMealPrompt
        },
        rhythmTitle = copy.mealsToday,
        rhythmSubtitle = if (storageLoadFailed) copy.historyUnavailable else countLabel,
        rhythmBadgeLabel = copy.diaryBadge,
        fiber = HomeMetricModel(copy.fiber, nutrientValue(nutrients?.fiberGrams)),
        water = HomeMetricModel(copy.water, "—"),
        protein = HomeMetricModel(copy.protein, nutrientValue(nutrients?.proteinGrams)),
        movement = HomeMetricModel(copy.movement, "—"),
        colors = HomeMetricModel(copy.colors, "—"),
        fats = HomeMetricModel(copy.fats, nutrientValue(nutrients?.fatGrams)),
        nextStepTitle = copy.nextMeal,
        nextStepDescription = copy.captureDescription,
        nextStepAction = copy.addMeal,
        viewGoalsLabel = copy.aboutNutrition,
        goalsTitle = copy.nutritionTitle,
        goalsDescription = copy.nutritionDescription,
        goalsDoneLabel = copy.done,
        profileActionLabel = copy.openProfile,
        mascotDescription = copy.mascotDescription,
        saladDescription = copy.saladDescription,
    )
}
