package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeScreenComposeTest {
    @Test
    fun `home screen exposes the Figma rhythm surface and add meal action`() = runComposeUiTest {
        var addFoodRequested = false
        val model = HomeScreenModel(
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

        setContent {
            Box(Modifier.requiredSize(375.dp, 812.dp)) {
                HomeScreen(model = model, onAddFoodRequested = { addFoodRequested = true })
            }
        }

        onNodeWithTag(UiAutomationIds.HOME_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).assertHeightIsEqualTo(812.dp)
        onNodeWithText("Super Nutrition").assertIsDisplayed()
        onNodeWithText("Thursday, Aug 7").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).assertIsDisplayed()
        onNodeWithText("Today’s rhythm").assertIsDisplayed()
        onNodeWithText("Fiber").assertIsDisplayed()
        onNodeWithText("Water").assertIsDisplayed()
        onNodeWithText("Protein").assertIsDisplayed()
        onNodeWithText("Movement").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_EMPTY_TITLE.value).assertIsDisplayed()
        onNodeWithText("Your next helpful step").assertIsDisplayed()
        onNodeWithText("Add one leafy green to dinner").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).assertIsDisplayed().performClick()

        assertTrue(addFoodRequested)

        setContent {
            Box(Modifier.requiredSize(300.dp, 500.dp)) {
                HomeScreen(model = model)
            }
        }
        onNodeWithTag(UiAutomationIds.HOME_SCREEN.value)
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(500.dp)
    }

    @Test
    fun `home screen keeps goals and profile actions reachable`() = runComposeUiTest {
        var goalsRequested = false
        var profileRequested = false
        val model = HomeScreenModel(
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

        setContent {
            HomeScreen(
                model = model,
                onViewGoalsRequested = { goalsRequested = true },
                onProfileRequested = { profileRequested = true },
            )
        }

        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).performClick()
        onNodeWithText("Your goals").assertIsDisplayed()
        onNodeWithText("Done").performClick()
        onNodeWithTag(UiAutomationIds.HOME_PROFILE.value).performClick()

        assertTrue(goalsRequested)
        assertTrue(profileRequested)
    }
}
