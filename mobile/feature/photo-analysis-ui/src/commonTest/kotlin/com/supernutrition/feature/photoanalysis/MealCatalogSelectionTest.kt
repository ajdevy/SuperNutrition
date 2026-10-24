package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MealCatalogSelectionTest {
    @Test
    fun `exact product selection requires grams and failed writes preserve the selected draft`() = runComposeUiTest {
        val source = UiCatalogFixture()
        var submitted: MealDraft? = null
        var accepted = false
        setContent {
            MealForm(
                MealFormConfig("Add by text", catalog = MealCatalogContext(MealCatalogState.Ready(source))),
                MealFormActions(
                    onSave = {
                        submitted = it
                        accepted
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese fried")
        onNodeWithText(source.provenance.sourceName).performScrollTo().assertIsDisplayed()
        onNodeWithText(source.provenance.coverageDescription).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.mealSearchRowDetail(0).value, useUnmergedTree = true)
            .performScrollTo().assertTextContains("fried", substring = true)
        onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertNull(submitted)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performScrollTo().performTextReplacement("50")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_ERROR.value).performScrollTo().assertIsDisplayed()
        val draft = assertNotNull(submitted)
        assertEquals(source.product.displayName, draft.name)
        assertEquals("", draft.ingredients)
        assertNotNull(draft.catalogBinding)
        accepted = true
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals(draft, submitted)
    }

    @Test
    fun `loading to ready and source retry retain authored name note and catalog query`() = runComposeUiTest {
        val source = UiCatalogFixture().apply { searchFailure = IllegalStateException("read failed") }
        val availability = mutableStateOf<MealCatalogState>(MealCatalogState.Loading)
        var retryCount = 0
        setContent {
            MealForm(
                MealFormConfig(
                    "Edit meal",
                    initialDraft = MealDraft("My meal", note = "Keep my note"),
                    editing = true,
                    catalog = MealCatalogContext(
                        availability.value,
                        onRetryRequested = {
                            retryCount++
                            source.searchFailure = null
                            availability.value = MealCatalogState.Ready(source)
                        },
                    ),
                ),
                MealFormActions(onSave = { true }, onCancel = {}),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese")
        runOnIdle { availability.value = MealCatalogState.Ready(source) }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_STATUS.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        assertEquals(1, retryCount)
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().assertTextContains("cheese")
        onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().assertTextContains("My meal")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NOTE.value).performScrollTo().assertTextContains("Keep my note")
    }
}
