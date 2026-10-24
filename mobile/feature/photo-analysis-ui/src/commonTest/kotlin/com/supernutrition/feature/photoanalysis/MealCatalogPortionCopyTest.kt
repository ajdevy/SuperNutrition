package com.supernutrition.feature.photoanalysis

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
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MealCatalogPortionCopyTest {
    @Test
    fun `selected product requires positive grams and removal restores optional whole meal help`() = runComposeUiTest {
        setContent {
            MealForm(
                MealFormConfig("Edit meal", uiCatalogMeal().toMealDraft(), editing = true),
                MealFormActions({ true }, {}),
            )
        }
        onNodeWithText("Required for the selected product. Enter a positive portion in grams.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("Optional, for the whole meal.").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_REMOVE.value).performScrollTo().performClick()
        onNodeWithText("Optional, for the whole meal.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Required for the selected product. Enter a positive portion in grams.").assertDoesNotExist()
    }

    @Test
    fun `bound portion error requires grams and manual error still allows an empty portion`() = runComposeUiTest {
        var submitted: MealDraft? = null
        setContent {
            MealForm(
                MealFormConfig(
                    "Edit meal",
                    uiCatalogMeal().toMealDraft().copy(portion = ""),
                    editing = true,
                    catalog = MealCatalogContext(MealCatalogState.Ready(UiCatalogFixture())),
                ),
                MealFormActions(
                    onSave = {
                        submitted = it
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo()
            .assertTextEquals("Enter a positive portion in grams for the selected product.")
        assertNull(submitted)
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_REMOVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performScrollTo().performTextReplacement("-3")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo()
            .assertTextEquals("Enter a positive portion in grams, or leave it empty.")
        assertNull(submitted)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performScrollTo().performTextReplacement("")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals("", submitted?.portion)
        assertNull(submitted?.catalogBinding)
    }

    @Test
    fun `selected product portion instructions and error use localized host copy`() = runComposeUiTest {
        setContent {
            MealForm(
                MealFormConfig(
                    "Edit meal",
                    uiCatalogMeal().toMealDraft().copy(portion = "0"),
                    catalog = MealCatalogContext(MealCatalogState.Ready(UiCatalogFixture())),
                ),
                MealFormActions({ error("Invalid grams must not be submitted") }, {}),
                copy = MealFormCopy(
                    selectedProductPortionDescription = "Порция выбранного продукта обязательна.",
                    selectedProductPortionInvalid = "Укажите положительный вес в граммах.",
                ),
            )
        }
        onNodeWithText("Порция выбранного продукта обязательна.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo()
            .assertTextEquals("Укажите положительный вес в граммах.")
    }
}
