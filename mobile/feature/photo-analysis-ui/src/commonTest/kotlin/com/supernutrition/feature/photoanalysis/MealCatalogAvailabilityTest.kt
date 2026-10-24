package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
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
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class MealCatalogAvailabilityTest {
    @Test
    fun `empty results loading unavailable and replacement preserve query`() = runComposeUiTest {
        val empty = UiCatalogFixture().apply { results = emptyList() }
        val state = mutableStateOf<MealCatalogState>(MealCatalogState.Ready(empty))
        setContent {
            MealForm(
                MealFormConfig("Add by text", catalog = MealCatalogContext(state.value)),
                MealFormActions(onSave = { true }, onCancel = {}),
            )
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese")
        val status = onNodeWithTag(UiAutomationIds.MEAL_CATALOG_STATUS.value)
        val noMatches = status.fetchSemanticsNode().config[SemanticsProperties.Text]
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_RESULTS.value).assertDoesNotExist()
        runOnIdle { state.value = MealCatalogState.Loading }
        val loading = status.fetchSemanticsNode().config[SemanticsProperties.Text]
        assertNotEquals(noMatches, loading)
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).assertDoesNotExist()
        runOnIdle { state.value = MealCatalogState.Unavailable }
        assertNotEquals(loading, status.fetchSemanticsNode().config[SemanticsProperties.Text])
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().assertIsDisplayed()
        runOnIdle { state.value = MealCatalogState.Ready(UiCatalogFixture()) }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().assertTextContains("cheese")
        onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performScrollTo().assertIsDisplayed()
    }
}
