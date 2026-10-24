package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealCatalogUriHandlerTest {
    @Test
    fun `source links use the platform uri handler when no host override is supplied`() = runComposeUiTest {
        val catalog = UiCatalogFixture()
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                MealCatalogSourceButton(catalog.provenance, MealCatalogContext())
            }
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).performScrollTo().performClick()
        assertEquals(listOf(catalog.provenance.sourceUrl), opened)
    }
}
