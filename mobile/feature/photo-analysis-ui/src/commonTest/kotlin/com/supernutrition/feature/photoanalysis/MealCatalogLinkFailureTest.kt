package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
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
class MealCatalogLinkFailureTest {
    @Test
    fun `missing platform URL handler preserves selected draft and allows exact once retry`() = verifyFailure(false)

    @Test
    fun `failing host URL override preserves selected draft and allows exact once retry`() = verifyFailure(true)

    private fun verifyFailure(hostOverride: Boolean) = runComposeUiTest {
        val catalog = UiCatalogFixture()
        val draft = uiCatalogMeal().toMealDraft().copy(note = "Keep this note")
        var submitted: MealDraft? = null
        var available = false
        val attempts = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                attempts += uri
                require(available) { "No application can open this URI" }
                opened += uri
            }
        }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                MealForm(
                    MealFormConfig(
                        "Edit meal",
                        draft,
                        editing = true,
                        catalog = MealCatalogContext(
                            MealCatalogState.Ready(catalog),
                            onOpenLink = if (hostOverride) handler::openUri else null,
                        ),
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
        }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_OPEN.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().performTextReplacement("cheese")
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).performScrollTo().performClick()
        onNodeWithText("Couldn't open this link. Try the link again or close source information.")
            .performScrollTo().assertIsDisplayed()
        assertEquals(listOf(catalog.provenance.sourceUrl), attempts)
        assertEquals(emptyList(), opened)
        runOnIdle { available = true }
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SOURCE.value).performScrollTo().performClick()
        assertEquals(listOf(catalog.provenance.sourceUrl), opened)
        assertEquals(List(2) { catalog.provenance.sourceUrl }, attempts)
        onNodeWithText("Couldn't open this link. Try the link again or close source information.").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_INFO_CLOSE.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_QUERY.value).performScrollTo().assertTextEquals("cheese")
        onNodeWithTag(UiAutomationIds.MEAL_CATALOG_SELECTED.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NAME.value).performScrollTo().assertTextEquals(draft.name)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_PORTION.value).assertTextEquals(draft.portion)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_NOTE.value).performScrollTo().assertTextEquals(draft.note)
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        assertEquals(draft, submitted)
    }
}
