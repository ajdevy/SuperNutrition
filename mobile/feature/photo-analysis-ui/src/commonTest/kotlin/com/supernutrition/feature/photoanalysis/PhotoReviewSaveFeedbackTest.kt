package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PhotoReviewSaveFeedbackTest {
    @Test
    fun `failed photo save shows feedback keeps choices and retries only on explicit Add`() = runComposeUiTest {
        val note = mutableStateOf("No sauce")
        var accepted = false
        var legacyCalls = 0
        val attempts = mutableListOf<PhotoAnalysisMealReviewConfirmation>()
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                surface = PhotoAnalysisCaptureSurface(
                    PhotoAnalysisPresenterCopyFixtures.localized(),
                    manualIngredientInput = note.value,
                    onManualIngredientInputChanged = { note.value = it },
                    onMealReviewConfirmed = { legacyCalls++ },
                    textEntryCopy = MealFormCopy(saveFailed = "Cannot save right now. Try again."),
                    onMealReviewSaveRequested = {
                        attempts += it
                        accepted
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).performClick()
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_REVIEW_SAVE_ERROR.value).assertIsDisplayed()
            .assertTextEquals("Cannot save right now. Try again.")
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).assertTextContains("No sauce")
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).performScrollTo().assertIsSelected()
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).performScrollTo().assertIsSelected()
        assertEquals(1, attempts.size)
        accepted = true
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed().performClick()
        assertEquals(2, attempts.size)
        assertEquals(attempts.first(), attempts.last())
        assertEquals(0, legacyCalls)
        onNodeWithTag(UiAutomationIds.MEAL_REVIEW_SAVE_ERROR.value).assertDoesNotExist()
    }

    @Test
    fun `overlong photo note keeps feedback and draft until a corrected retry succeeds`() = runComposeUiTest {
        val note = mutableStateOf("n".repeat(MealDraftLimits.NOTE_MAX_LENGTH + 1))
        val attempts = mutableListOf<PhotoAnalysisMealReviewConfirmation>()
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                surface = PhotoAnalysisCaptureSurface(
                    PhotoAnalysisPresenterCopyFixtures.localized(),
                    manualIngredientInput = note.value,
                    onManualIngredientInputChanged = { note.value = it },
                    onMealReviewSaveRequested = {
                        attempts += it
                        it.note.length <= MealDraftLimits.NOTE_MAX_LENGTH
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_REVIEW_SAVE_ERROR.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value).performScrollTo().assertIsSelected()
        assertEquals(MealDraftLimits.NOTE_MAX_LENGTH + 1, note.value.length)
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
            .assertIsDisplayed().performTextReplacement("Less sauce")
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed().performClick()
        assertEquals(2, attempts.size)
        assertEquals("Less sauce", attempts.last().note)
        onNodeWithTag(UiAutomationIds.MEAL_REVIEW_SAVE_ERROR.value).assertDoesNotExist()
    }
}
