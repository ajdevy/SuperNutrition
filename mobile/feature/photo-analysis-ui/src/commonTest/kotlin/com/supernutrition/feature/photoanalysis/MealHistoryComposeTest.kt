package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MealHistoryComposeTest {
    private val copy = MealHistoryCopy()

    @Test
    fun `empty history has a dedicated screen and returns to profile`() = runComposeUiTest {
        setContent { ProfileFlow(rememberProfileFlowState(), "Alex", emptyList(), ProfileScreenActions({}, {})) }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_EMPTY.value).assertIsDisplayed()
        onNodeWithText(copy.noResultsTitle).assertDoesNotExist()
        onNodeWithText("Grilled salmon").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_CLOSE.value).performClick()
        onNodeWithTag(UiAutomationIds.PROFILE_NAME.value).assertIsDisplayed()
    }

    @Test
    fun `search filters actual names and clears to the original entries`() = runComposeUiTest {
        val meals = confirmedMealHistory(
            listOf(
                profileConfirmedFood("1", "a", "Egg"),
                profileConfirmedFood("2", "a", "Toast"),
                profileConfirmedFood("3", "b", "Lentils"),
            ),
        )
        setContent { MealHistoryScreen(meals, {}, referenceDay = 10) }
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).performTextReplacement("  EGG   TOAST  ")
        onNodeWithText("Egg").assertIsDisplayed()
        onNodeWithText("Lentils").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).performTextReplacement("salmon")
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_NO_RESULTS.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_EMPTY.value).assertDoesNotExist()
        onNodeWithText(copy.clearSearch).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(1).value).assertIsDisplayed()
    }

    @Test
    fun `card opens a read only summary and does not invent time photo or nutrients`() = runComposeUiTest {
        val meals = confirmedMealHistory(
            listOf(profileConfirmedFood("1", "a", "Egg"), profileConfirmedFood("2", "a", "Toast")),
        )
        setContent { MealHistoryScreen(meals, {}, referenceDay = 10) }
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SUMMARY.value).assertIsDisplayed()
        onNodeWithText(copy.confirmedFoods).assertIsDisplayed()
        onNodeWithText(copy.photoUnavailable).assertIsDisplayed()
        onNodeWithText("100 kcal").assertDoesNotExist()
        onNodeWithText(copy.done).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SUMMARY.value).assertDoesNotExist()
        assertTrue(meals.single().metadata.timeLabel == null)
    }

    @Test
    fun `history callback is delegated by standalone profile`() = runComposeUiTest {
        var requested = false
        setContent {
            ProfileScreen("", emptyList(), ProfileScreenActions({}, {}, onHistoryRequested = { requested = true }))
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        assertTrue(requested)
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SCREEN.value).assertDoesNotExist()
    }
}
