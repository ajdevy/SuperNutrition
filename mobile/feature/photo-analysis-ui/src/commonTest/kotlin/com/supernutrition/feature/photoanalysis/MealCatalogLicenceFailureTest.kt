package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealCatalogLicenceFailureTest {
    @Test
    fun `platform licence failure is localized and source sheet can still close`() = verifyClose(false)

    @Test
    fun `host licence failure is localized and source sheet can still close`() = verifyClose(true)

    private fun verifyClose(hostOverride: Boolean) = runComposeUiTest {
        val catalog = UiCatalogFixture()
        val attempts = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                attempts += uri
                throw IllegalArgumentException("No browser installed")
            }
        }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                MealCatalogSourceButton(
                    catalog.provenance,
                    MealCatalogContext(
                        onOpenLink = if (hostOverride) handler::openUri else null,
                        copy = MealCatalogCopy(linkOpenFailed = "Откройте ссылку снова или закройте сведения."),
                    ),
                )
            }
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_LICENCE.value).performScrollTo().performClick()
        onNodeWithText("Откройте ссылку снова или закройте сведения.").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(catalog.provenance.licenceUrl), attempts)
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO_CLOSE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).performClick()
        onNodeWithText("Откройте ссылку снова или закройте сведения.").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_LICENCE.value).performScrollTo().assertIsDisplayed()
    }
}
