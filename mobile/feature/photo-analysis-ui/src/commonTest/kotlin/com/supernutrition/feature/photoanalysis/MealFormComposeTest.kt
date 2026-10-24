package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MealFormComposeTest {
    @Test
    fun `empty and invalid portions never reach the save callback`() = runComposeUiTest {
        var submitted: MealDraft? = null
        setContent {
            MealForm(
                config = MealFormConfig("Add by text"),
                actions = MealFormActions(
                    onSave = {
                        submitted = it
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithText("Enter a name for this meal.").assertIsDisplayed()
        assertNull(submitted)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().performTextReplacement("Oatmeal")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performTextReplacement("-3")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithText("Enter a positive portion in grams, or leave it empty.").performScrollTo().assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun `a failed save preserves the draft and cancel never submits`() = runComposeUiTest {
        var submissions = 0
        var cancellations = 0
        setContent {
            MealForm(
                config = MealFormConfig("Add by text"),
                actions = MealFormActions(
                    onSave = {
                        submissions++
                        false
                    },
                    onCancel = { cancellations++ },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("My breakfast")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_INGREDIENTS.value).performTextReplacement("Oats, apple")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().assertTextContains("My breakfast")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo().performClick()
        assertEquals(1, submissions)
        assertEquals(1, cancellations)
    }

    @Test
    fun `permission recovery exposes typed entry and closes only after accepted save`() = runComposeUiTest {
        var submitted: MealDraft? = null
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel(
                    "Camera permission required",
                    PhotoAnalysisPresenterState.RECOVERY,
                    emptyList(),
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    PhotoAnalysisPresenterCopyFixtures.localized(),
                    onTextMealSaved = {
                        submitted = it
                        true
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_TEXT.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("Breakfast")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals("Breakfast", submitted?.name)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_TITLE.value).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.CAPTURE_TEXT_HINT.value).assertDoesNotExist()
    }
}
