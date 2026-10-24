package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CaptureTextDraftRetentionTest {
    @Test
    fun `analysis completion keeps the open text form and authored draft`() = runComposeUiTest {
        val model = mutableStateOf(
            PhotoAnalysisPresenterModel("Analyzing", PhotoAnalysisPresenterState.ANALYZING, emptyList()),
        )
        var submitted: MealDraft? = null
        setContent {
            PhotoAnalysisCaptureScreen(
                model.value,
                {},
                surface = PhotoAnalysisCaptureSurface(
                    PhotoAnalysisPresenterCopyFixtures.localized(),
                    onTextMealSaved = {
                        submitted = it
                        true
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_TEXT.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("My lunch")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_INGREDIENTS.value).performTextReplacement("Rice, beans")
        runOnIdle { model.value = cameraReviewFixture() }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).assertTextContains("My lunch")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals("Rice, beans", submitted?.ingredients)
    }

    @Test
    fun `background and resume retain the draft while cancel writes nothing`() = runComposeUiTest {
        val model = mutableStateOf(
            PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
        )
        var submissions = 0
        setContent {
            PhotoAnalysisCaptureScreen(
                model.value,
                {},
                surface = PhotoAnalysisCaptureSurface(
                    PhotoAnalysisPresenterCopyFixtures.localized(),
                    onTextMealSaved = {
                        submissions++
                        true
                    },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_TEXT.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performTextReplacement("Private breakfast")
        runOnIdle { model.value = model.value.copy(state = PhotoAnalysisPresenterState.BACKGROUNDED) }
        waitForIdle()
        runOnIdle { model.value = model.value.copy(state = PhotoAnalysisPresenterState.CAPTURE) }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).assertTextContains("Private breakfast")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo().performClick()
        assertEquals(0, submissions)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_TITLE.value).assertDoesNotExist()
    }
}
