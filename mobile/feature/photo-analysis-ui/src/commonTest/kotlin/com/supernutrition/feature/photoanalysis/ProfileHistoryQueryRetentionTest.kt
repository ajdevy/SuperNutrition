package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProfileHistoryQueryRetentionTest {
    @Test
    fun `returning from meal details preserves the query and filtered meals`() = runComposeUiTest {
        val state = ProfileFlowState(currentDay = { 10 })
        setContent {
            ProfileFlow(
                state,
                "",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(
                    savedMeals = listOf(
                        diaryMealFixture(),
                        diaryMealFixture("other").copy(name = "Lentils", ingredients = listOf("Lentils")),
                    ),
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).performTextReplacement("oat")
        onNodeWithText("Lentils").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_BACK.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).assertTextContains("oat")
        onNodeWithText("Oatmeal").assertIsDisplayed()
        onNodeWithText("Lentils").assertDoesNotExist()
    }
}
