package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.RewardId

internal fun layoutHomeModel() = HomeScreenModel(
    title = "Home",
    greeting = "Welcome back",
    dailySummaryTitle = "Today's progress",
    dailySummaryValue = "Ready when you are",
    dailySummaryDescription = "Add a meal whenever you are ready.",
    emptyTitle = "Build your day",
    emptyDescription = "Notice the good choices you make.",
    addFoodLabel = "Add a meal",
    dateLabel = "Thursday, Aug 7",
    careQuestion = "What kind of care do you need today?",
    rhythmTitle = "Today’s rhythm",
    rhythmSubtitle = "You’re building a good day",
    rhythmBadgeLabel = "6 day rhythm",
    fiber = HomeMetricModel("Fiber", "12 / 25 g"),
    water = HomeMetricModel("Water", "1.2 / 2 L"),
    protein = HomeMetricModel("Protein", "72 / 90 g"),
    movement = HomeMetricModel("Movement", "32 / 45 min"),
    nextStepTitle = "Your next helpful step",
    nextStepDescription = "Add one leafy green to dinner",
    nextStepAction = "Do it",
    viewGoalsLabel = "View goals",
    goalsTitle = "Your goals",
    goalsDescription = "Fiber 25 g · Water 2 L · Protein 90 g · Movement 45 min",
    goalsDoneLabel = "Done",
    profileActionLabel = "Open profile",
    mascotDescription = "Capybara mascot",
    saladDescription = "Salad",
)

internal fun layoutRewardsModel() = RewardsPresenterModel(
    title = "Rewards",
    cards = RewardId.entries.mapIndexed { index, id ->
        RewardsCardModel(
            id = id,
            title = "Reward ${index + 1}",
            description = "You recorded a helpful choice.",
            progressLabel = "${if (index == 0) 2 else 0} of ${if (index == 0) 2 else 1}",
            stateLabel = if (index == 0) "Added to your rewards" else "Ready when you are",
            isEarned = index == 0,
        )
    },
)

internal fun layoutBottomBarState(selectedTab: Int = 0) = PhotoAnalysisMainBottomBarState(
    selectedTab = selectedTab,
    homeLabel = "Home",
    rewardsLabel = "Rewards",
    cameraLabel = "Take photo",
)

internal fun layoutBottomBarActions() = PhotoAnalysisMainBottomBarActions({}, {}, {})
