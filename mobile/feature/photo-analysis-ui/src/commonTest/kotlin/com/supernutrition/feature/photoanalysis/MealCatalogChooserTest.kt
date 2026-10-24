package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MealCatalogChooserTest {
    @Test
    fun `catalog source failure remains distinct from an empty product search`() = runComposeUiTest {
        setContent {
            MealForm(MealFormConfig("Add by text"), MealFormActions(onSave = { true }, onCancel = {}))
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_STATUS.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_RESULTS.value).assertDoesNotExist()
    }
}
