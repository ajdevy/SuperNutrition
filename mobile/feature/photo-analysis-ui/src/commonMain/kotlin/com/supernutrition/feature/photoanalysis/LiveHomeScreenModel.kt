package com.supernutrition.feature.photoanalysis

/** Shared production Home: count means today's recorded meals, never a nutrient or streak estimate. */
fun liveHomeScreenModel(dateLabel: String, savedMealCount: Int, copy: LiveHomeCopy = LiveHomeCopy()): HomeScreenModel {
    require(savedMealCount >= 0) { "Today's saved meal count must not be negative" }
    val countLabel = (if (savedMealCount == 1) copy.singularMealCount else copy.pluralMealCount)
        .replace("{count}", savedMealCount.toString())
    return HomeScreenModel(
        title = copy.title,
        greeting = copy.greeting,
        dailySummaryTitle = copy.mealsToday,
        dailySummaryValue = countLabel,
        dailySummaryDescription = copy.historyDescription,
        emptyTitle = copy.diaryTitle,
        emptyDescription = copy.addDescription,
        addFoodLabel = copy.addMeal,
        dateLabel = dateLabel,
        careQuestion = if (savedMealCount == 0) copy.firstMealPrompt else copy.nextMealPrompt,
        rhythmTitle = copy.mealsToday,
        rhythmSubtitle = countLabel,
        rhythmBadgeLabel = copy.diaryBadge,
        fiber = HomeMetricModel(copy.fiber, "—"),
        water = HomeMetricModel(copy.water, "—"),
        protein = HomeMetricModel(copy.protein, "—"),
        movement = HomeMetricModel(copy.movement, "—"),
        colors = HomeMetricModel(copy.colors, "—"),
        fats = HomeMetricModel(copy.fats, "—"),
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
