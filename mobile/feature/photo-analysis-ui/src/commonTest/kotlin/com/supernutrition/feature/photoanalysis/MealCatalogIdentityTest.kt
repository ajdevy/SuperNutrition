package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MealCatalogIdentityTest {
    @Test
    fun `removing a catalog selection saves manual content without a binding`() = runComposeUiTest {
        var submitted: MealDraft? = null
        setContent {
            MealForm(
                MealFormConfig("Edit meal", uiCatalogMeal().toMealDraft(), editing = true),
                MealFormActions(
                    onSave = {
                        submitted = it
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_REMOVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertNull(submitted?.catalogBinding)
        assertEquals(uiCatalogProduct().displayName, submitted?.name)
    }

    @Test
    fun `editing product identity clears binding while note and grams remain authored`() = runComposeUiTest {
        var submitted: MealDraft? = null
        setContent {
            MealForm(
                MealFormConfig("Edit meal", uiCatalogMeal().toMealDraft(), editing = true),
                MealFormActions(
                    onSave = {
                        submitted = it
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().performTextReplacement("My cheese")
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SELECTED.value).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_INGREDIENTS.value).performScrollTo().performTextReplacement("Herbs")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NOTE.value).performScrollTo().performTextReplacement("No sauce")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertNull(submitted?.catalogBinding)
        assertEquals("Herbs", submitted?.ingredients)
        assertEquals("No sauce", submitted?.note)
        assertEquals("50", submitted?.portion)
    }
}
