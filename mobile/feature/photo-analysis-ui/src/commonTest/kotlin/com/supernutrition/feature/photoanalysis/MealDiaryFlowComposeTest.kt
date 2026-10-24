package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealDiaryFlowComposeTest {
    @Test
    fun `editing name does not reject unchanged structured ingredients containing commas`() = runComposeUiTest {
        val saved = diaryMealFixture().copy(ingredients = List(40) { "Rice, variety $it" })
        var saves = 0
        val state = ProfileFlowState(currentDay = { 10 })
        setContent {
            ProfileFlow(
                state,
                "",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(
                    savedMeals = listOf(saved),
                    onMealEdited = { _, _ ->
                        saves++
                        true
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("Corrected")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `saved identity opens unknown details edits explicitly and deletes after confirmation`() = runComposeUiTest {
        val meals = mutableStateOf(listOf(diaryMealFixture()))
        val state = ProfileFlowState(currentDay = { 10 })
        setContent {
            ProfileFlow(
                state,
                "Alex",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(
                    savedMeals = meals.value,
                    onMealEdited = { id, draft ->
                        meals.value = meals.value.map { if (it.id == id) it.copy(name = draft.name) else it }
                        true
                    },
                    onMealDeleted = { id ->
                        meals.value = meals.value.filterNot { it.id == id }
                        true
                    },
                ),
            )
        }
        onNodeWithText("1 meal saved").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_TITLE.value).assertTextEquals("Oatmeal")
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_NUTRITION_UNKNOWN.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_PER_100.value).performClick()
        onNodeWithText("100 kcal").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("Cancelled name")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo().performClick()
        assertEquals("Oatmeal", meals.value.single().name)
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("Apple oatmeal")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_TITLE.value).assertTextEquals("Apple oatmeal")
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE.value).performScrollTo().performClick()
        assertEquals(1, meals.value.size)
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE_CONFIRM.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_EMPTY.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_CLOSE.value).performClick()
        onNodeWithText("0 meals saved").assertIsDisplayed()
    }

    @Test
    fun `failed correction remains in editor and failed deletion keeps entry`() = runComposeUiTest {
        val state = ProfileFlowState(currentDay = { 10 })
        setContent {
            ProfileFlow(
                state,
                "",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(savedMeals = listOf(diaryMealFixture())),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE_CONFIRM.value).performClick()
        onNodeWithText("Couldn't delete this meal. Please try again.").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_BACK.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).assertIsDisplayed()
    }
}
