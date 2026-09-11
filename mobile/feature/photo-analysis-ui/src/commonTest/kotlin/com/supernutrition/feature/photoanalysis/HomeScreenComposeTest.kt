package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeScreenComposeTest {
    @Test
    fun `home screen exposes a positive daily starting point and add meal action`() = runComposeUiTest {
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
        )

        setContent {
            HomeScreen(model = model, onAddFoodRequested = { addFoodRequested = true })
        }

        onNodeWithTag(UiAutomationIds.HOME_TITLE.value).assertIsDisplayed()
        onNodeWithText("Welcome back").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).assertIsDisplayed()
        onNodeWithText("Today's progress").assertIsDisplayed()
        onNodeWithText("Ready when you are").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_EMPTY_TITLE.value).assertIsDisplayed()
        onNodeWithText("Build your day").assertIsDisplayed()
        onNodeWithText("Notice the good choices you make.").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).assertIsDisplayed().performClick()

        assertTrue(addFoodRequested)
    }
}
