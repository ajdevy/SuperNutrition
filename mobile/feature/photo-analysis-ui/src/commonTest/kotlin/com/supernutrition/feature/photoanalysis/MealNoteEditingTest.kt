package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealNoteEditingTest {
    @Test
    fun `saved photo note corrections reopen while cancelling preserves the saved note`() = runComposeUiTest {
        val meals = mutableStateOf(listOf(diaryMealFixture().copy(source = SavedMealSource.PHOTO, note = "Less sauce")))
        val state = ProfileFlowState(currentDay = { 10 })
        setContent {
            ProfileFlow(
                state,
                "",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(
                    savedMeals = meals.value,
                    onMealEdited = { _, draft ->
                        meals.value = listOf(meals.value.single().copy(note = draft.note))
                        true
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithContentDescription("Note").performScrollTo().assertTextContains("Less sauce")
            .performTextReplacement("No sauce")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals("No sauce", meals.value.single().note)
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithContentDescription("Note").performScrollTo().assertTextContains("No sauce")
            .performTextReplacement("Cancelled note")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo().performClick()
        assertEquals("No sauce", meals.value.single().note)
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithContentDescription("Note").performScrollTo().assertTextContains("No sauce")
    }

    @Test
    fun `explicitly clearing a saved photo note survives save and reopen`() = runComposeUiTest {
        val meals = mutableStateOf(listOf(diaryMealFixture().copy(source = SavedMealSource.PHOTO, note = "Less sauce")))
        val state = ProfileFlowState(currentDay = { 10 })
        setContent {
            ProfileFlow(
                state,
                "",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(
                    savedMeals = meals.value,
                    onMealEdited = { _, draft ->
                        meals.value = listOf(meals.value.single().copy(note = draft.note))
                        true
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithContentDescription("Note").performScrollTo().performTextReplacement("")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals("", meals.value.single().note)
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithContentDescription("Note").performScrollTo().assertTextEquals("")
    }
}
