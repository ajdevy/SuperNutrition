package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeScreenComposeTest {
    @Test
    fun `home screen exposes the Figma rhythm surface and add meal action`() = runComposeUiTest {
        var addFoodRequested = false
        val model = layoutHomeModel()

        setContent {
            Box(Modifier.requiredSize(375.dp, 812.dp)) {
                HomeScreen(model = model, onAddFoodRequested = { addFoodRequested = true })
            }
        }

        onNodeWithTag(UiAutomationIds.HOME_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).assertHeightIsEqualTo(812.dp)
        onNodeWithText("Super Nutrition").assertDoesNotExist()
        onNodeWithText(model.dateLabel).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.HOME_DATE.value).assertDoesNotExist()
        onNodeWithText(model.rhythmBadgeLabel).assertIsDisplayed()
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
        val model = layoutHomeModel()

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
