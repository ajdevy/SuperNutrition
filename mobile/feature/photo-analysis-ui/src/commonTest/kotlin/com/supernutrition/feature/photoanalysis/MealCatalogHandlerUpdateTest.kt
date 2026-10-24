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
class MealCatalogHandlerUpdateTest {
    @Test
    fun `retry uses current host handler and source recovery preserves query and manual draft`() = runComposeUiTest {
        val handler = mutableStateOf(0)
        val source = mutableStateOf<MealCatalogState>(MealCatalogState.Unavailable)
        val calls = mutableListOf<Int>()
        setContent {
            val generation = handler.value
            MealForm(
                MealFormConfig(
                    "Edit meal",
                    MealDraft("Home meal", "42", note = "Keep this note"),
                    editing = true,
                    catalog = MealCatalogContext(source.value, onRetryRequested = { calls += generation }),
                ),
                MealFormActions({ true }, {}),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese")
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        runOnIdle { handler.value = 1 }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        assertEquals(listOf(0, 1), calls)
        runOnIdle { source.value = MealCatalogState.Ready(UiCatalogFixture()) }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().assertTextEquals("cheese")
        onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SELECTED.value).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().assertTextEquals("Home meal")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).assertTextEquals("42")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NOTE.value).performScrollTo().assertTextEquals("Keep this note")
    }

    @Test
    fun `open source info uses latest provenance and link handler after source replacement`() = runComposeUiTest {
        val generation = mutableStateOf(0)
        val opened = mutableListOf<Pair<Int, String>>()
        val provenance = UiCatalogFixture().provenance
        setContent {
            val current = generation.value
            MealCatalogSourceButton(
                provenance.copy(sourceName = "USDA source $current", sourceUrl = "https://example.test/$current"),
                MealCatalogContext(onOpenLink = { opened += current to it }),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).performScrollTo().performClick()
        runOnIdle { generation.value = 1 }
        onNodeWithText("USDA source 1").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).performScrollTo().performClick()
        assertEquals(listOf(0 to "https://example.test/0", 1 to "https://example.test/1"), opened)
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO_CLOSE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).assertDoesNotExist()
    }
}
