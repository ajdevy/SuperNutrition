package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class MealCatalogRevalidationTest {
    @Test
    fun `each Save validates the current source and keeps the chosen draft during failure`() = runComposeUiTest {
        val catalog = UiCatalogFixture()
        val availability = mutableStateOf<MealCatalogState>(MealCatalogState.Unavailable)
        var saves = 0
        setContent {
            MealForm(
                MealFormConfig(
                    "Add by text",
                    initialDraft = uiCatalogMeal().toMealDraft(),
                    catalog = MealCatalogContext(availability.value),
                ),
                MealFormActions(
                    onSave = {
                        saves++
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals(0, saves)
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SELECTED.value).performScrollTo().assertExists()
        runOnIdle { availability.value = MealCatalogState.Ready(catalog) }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `historical binding survives note and gram correction while its source is unavailable`() = runComposeUiTest {
        val saved = uiCatalogMeal()
        var submitted: MealDraft? = null
        setContent {
            MealForm(
                MealFormConfig("Edit meal", saved.toMealDraft(), editing = true, existingMeal = saved),
                MealFormActions(
                    onSave = {
                        submitted = it
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performScrollTo().performTextReplacement("75")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NOTE.value).performScrollTo().performTextReplacement("Corrected note")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        val draft = assertNotNull(submitted)
        assertEquals(saved.catalogBinding, draft.catalogBinding)
        assertEquals("75", draft.portion)
        assertEquals("Corrected note", draft.note)
    }

    @Test
    fun `missing selected record preserves draft until explicit removal permits manual save`() = runComposeUiTest {
        val catalog = UiCatalogFixture().apply { lookupResult = null }
        var submitted: MealDraft? = null
        setContent {
            MealForm(
                MealFormConfig(
                    "Add by text",
                    initialDraft = uiCatalogMeal().toMealDraft(),
                    catalog = MealCatalogContext(MealCatalogState.Ready(catalog)),
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
        assertEquals(null, submitted)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo()
            .assertTextContains("Choose this product again", substring = true)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo()
            .assertTextEquals(catalog.product.displayName)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).assertTextEquals("50")
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_REMOVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        val draft = assertNotNull(submitted)
        assertEquals(catalog.product.displayName, draft.name)
        assertEquals(null, draft.catalogBinding)
    }
}
