package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealCatalogCreditsTest {
    @Test
    fun `all source and licence links are accessible at200percent and closing preserves search`() = runComposeUiTest {
        val catalog = UiCatalogFixture()
        val opened = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MealForm(
                    MealFormConfig(
                        "Add by text",
                        catalog = MealCatalogContext(MealCatalogState.Ready(catalog), onOpenLink = opened::add),
                    ),
                    MealFormActions(onSave = { true }, onCancel = {}),
                )
            }
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese")
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_LICENCE.value).performScrollTo().performClick()
        catalog.provenance.attributions.forEach { credit ->
            onNodeWithContentDescription("${credit.name}: Source").performScrollTo().performClick()
            onNodeWithContentDescription("${credit.name}: Licence").performScrollTo().performClick()
        }
        assertEquals(
            listOf(catalog.provenance.sourceUrl, catalog.provenance.licenceUrl) +
                catalog.provenance.attributions.flatMap { listOf(it.sourceUrl, it.licenceUrl) },
            opened,
        )
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO_CLOSE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().assertTextContains("cheese")
    }
}
