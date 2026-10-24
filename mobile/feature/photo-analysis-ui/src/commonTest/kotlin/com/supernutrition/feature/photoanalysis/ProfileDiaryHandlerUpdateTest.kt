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
class ProfileDiaryHandlerUpdateTest {
    @Test
    fun `editing and deleting retry against updated host handlers without losing selected meal`() = runComposeUiTest {
        val saved = diaryMealFixture()
        val state = ProfileFlowState(currentDay = { 10 })
        val accept = mutableStateOf(false)
        val saves = mutableListOf<Pair<Boolean, String>>()
        val deletes = mutableListOf<Boolean>()
        setContent {
            val accepted = accept.value
            ProfileFlow(
                state,
                "",
                ProfileScreenActions({}, {}),
                diary = ProfileDiaryContext(
                    savedMeals = listOf(saved),
                    onMealEdited = { _, draft ->
                        saves += accepted to draft.name
                        accepted
                    },
                    onMealDeleted = {
                        deletes += accepted
                        accepted
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_EDIT.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("Correction")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo().assertIsDisplayed()
        runOnIdle { accept.value = true }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().assertTextEquals("Correction")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_TITLE.value).assertTextEquals(saved.name)
        runOnIdle { accept.value = false }
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE_CONFIRM.value).performClick()
        onNodeWithText(MealDetailsCopy().deleteFailed).assertIsDisplayed()
        runOnIdle { accept.value = true }
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_DELETE_CONFIRM.value).performClick()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).assertIsDisplayed()
        assertEquals(listOf(false to "Correction", true to "Correction"), saves)
        assertEquals(listOf(false, true), deletes)
    }
}
