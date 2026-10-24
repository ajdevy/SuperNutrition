package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileNamePersistenceComposeTest {
    @Test
    fun `IME rejects invalid and empty names then preserves failed draft for current host retry`() = runComposeUiTest {
        val copy = ProfileScreenCopy()
        val accept = mutableStateOf(false)
        val attempts = mutableListOf<String>()
        var legacyCalls = 0
        setContent {
            val saveAccepted = accept.value
            ProfileScreen(
                "",
                emptyList(),
                ProfileScreenActions(
                    { legacyCalls++ },
                    {},
                    namePersistence = ProfileNamePersistence(save = {
                        attempts += it
                        saveAccepted
                    }),
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        val input = onNodeWithTag(UiAutomationIds.PROFILE_NAME_INPUT.value)
        input.performImeAction()
        assertEquals(emptyList(), attempts)
        input.performTextReplacement("N".repeat(ProfileNamePolicy.MAX_LENGTH + 1))
        onNodeWithText(copy.nameInvalid).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PROFILE_NAME_SAVE.value).assertIsNotEnabled()
        input.performImeAction()
        assertEquals(emptyList(), attempts)
        input.performTextReplacement("  Nora  ")
        input.performImeAction()
        onNodeWithText(copy.nameSaveFailed).assertIsDisplayed()
        input.assertTextEquals("  Nora  ")
        runOnIdle { accept.value = true }
        input.performImeAction()
        input.assertDoesNotExist()
        assertEquals(listOf("Nora", "Nora"), attempts)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `editing a failed name clears its error and cancel never writes the correction`() = runComposeUiTest {
        var attempts = 0
        setContent {
            ProfileScreen(
                "Alex",
                emptyList(),
                ProfileScreenActions(
                    {},
                    {},
                    namePersistence = ProfileNamePersistence(save = {
                        attempts++
                        false
                    }),
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        val input = onNodeWithTag(UiAutomationIds.PROFILE_NAME_INPUT.value)
        input.performTextReplacement("Nora")
        input.performImeAction()
        onNodeWithText(ProfileScreenCopy().nameSaveFailed).assertIsDisplayed()
        input.performTextReplacement("Other")
        onNodeWithText(ProfileScreenCopy().nameSaveFailed).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.PROFILE_CLOSE.value).performClick()
        input.assertDoesNotExist()
        onNodeWithText("Alex").assertIsDisplayed()
        assertEquals(1, attempts)
    }
}
