package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealCatalogPagingTest {
    @Test
    fun `every result remains reachable through explicit Show more`() = runComposeUiTest {
        val catalog = UiCatalogFixture().apply {
            results = (0..29).map { index ->
                CatalogProductSearchResult(
                    product.copy(canonicalItemId = "food-$index", displayName = "Product $index"),
                )
            }
        }
        setContent {
            MealForm(
                MealFormConfig("Add by text", catalog = MealCatalogContext(MealCatalogState.Ready(catalog))),
                MealFormActions(onSave = { true }, onCancel = {}),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("product")
        onNodeWithTag(UiAutomationIds.mealSearchRow(20).value).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_MORE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.mealSearchRow(29).value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SELECTED.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().assertTextContains("Product 29")
    }

    @Test
    fun `long comma containing catalog names never enter the bounded ingredient field`() = runComposeUiTest {
        val product = uiCatalogProduct().copy(displayName = "Cheese, " + "x".repeat(126))
        val source = UiCatalogFixture(product)
        var saved: MealDraft? = null
        setContent {
            MealForm(
                MealFormConfig("Add by text", catalog = MealCatalogContext(MealCatalogState.Ready(source))),
                MealFormActions(
                    onSave = {
                        saved = it
                        true
                    },
                    onCancel = {},
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese")
        onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).performScrollTo().performTextReplacement("50")
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals(product.displayName, saved?.name)
        assertEquals("", saved?.ingredients)
    }
}
