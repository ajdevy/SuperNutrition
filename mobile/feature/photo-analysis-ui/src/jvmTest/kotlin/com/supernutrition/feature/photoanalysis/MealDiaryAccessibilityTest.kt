package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MealDiaryAccessibilityTest {
    @Test
    fun `manual fields save validation and cancel remain reachable at 200 percent`() = runSkikoComposeUiTest(
        size = Size(360f, 780f),
        density = Density(1f, fontScale = 2f),
    ) {
        setContent {
            MealForm(MealFormConfig("Add by text"), MealFormActions(onSave = { false }, onCancel = {}))
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().performTextReplacement("Breakfast")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performScrollTo().performTextReplacement("200")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_INGREDIENTS.value)
            .performScrollTo().performTextReplacement("Oats, apple")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo().assertIsDisplayed()
    }
}
