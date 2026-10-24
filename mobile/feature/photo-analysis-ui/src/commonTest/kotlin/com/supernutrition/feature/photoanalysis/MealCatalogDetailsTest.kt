package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MealCatalogDetailsTest {
    @Test
    fun `details show sourced portions partial fields and per100 values`() = runComposeUiTest {
        val meal = mergedMealHistory(emptyList(), listOf(uiCatalogMeal()), UiCatalogFixture()).single()
        setContent { MealDetailsScreen(meal, MealDetailsActions(onBackRequested = {})) }
        onNodeWithText("USDA FoodData Central · SR Legacy 2018-04").performScrollTo().assertIsDisplayed()
        onNodeWithText("162.5 kcal").performScrollTo().assertIsDisplayed()
        onNodeWithText("12.26 g").performScrollTo().assertIsDisplayed()
        onNode(hasText("12.18 g") and hasAnySibling(hasText("Fat"))).performScrollTo().assertIsDisplayed()
        onNodeWithText("904 mg").performScrollTo().assertIsDisplayed()
        onNodeWithText("0.28 g").performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_NUTRITION_UNKNOWN.value).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_PER_100.value).performScrollTo().performClick()
        onNodeWithText("325 kcal").performScrollTo().assertIsDisplayed()
        onNodeWithText("24.51 g").performScrollTo().assertIsDisplayed()
        onNodeWithText("1808 mg").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `history includes sourced energy without inventing it for free text meals`() = runComposeUiTest {
        val meals = mergedMealHistory(
            emptyList(),
            listOf(uiCatalogMeal(), diaryMealFixture("manual")),
            UiCatalogFixture(),
        )
        setContent { MealHistoryScreen(meals, {}) }
        onNodeWithText("162.5 kcal", substring = true).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.mealHistoryRow(1).value).assertIsDisplayed()
    }
}
