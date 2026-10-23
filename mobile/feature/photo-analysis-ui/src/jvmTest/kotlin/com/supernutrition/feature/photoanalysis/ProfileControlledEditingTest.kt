package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileControlledEditingTest {
    @Test
    fun `host cancellation and header cancellation discard drafts while preserving the saved session name`() =
        runSkikoComposeUiTest(size = Size(375f, 812f), density = Density(1f)) {
            val state = ProfileFlowState()
            val savedName = mutableStateOf("Alex")
            var backRequests = 0
            setContent {
                ProfileFlow(
                    state,
                    savedName.value,
                    emptyList(),
                    ProfileScreenActions({ savedName.value = it }, { backRequests++ }),
                )
            }
            onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
            assertTrue(state.nameEditing)
            onNodeWithTag(UiAutomationIds.PROFILE_NAME_CLEAR.value).performClick()
            onNodeWithTag(UiAutomationIds.PROFILE_NAME_INPUT.value).performTextReplacement("  Marta  ")
            onNodeWithTag(UiAutomationIds.PROFILE_NAME_SAVE.value).performClick()
            assertEquals("Marta", savedName.value)
            assertFalse(state.nameEditing)
            onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
            onNodeWithTag(UiAutomationIds.PROFILE_NAME_INPUT.value).performTextReplacement("Unsaved host draft")
            runOnIdle { state.cancelNameEdit() }
            onNodeWithTag(UiAutomationIds.PROFILE_NAME_INPUT.value).assertDoesNotExist()
            onNodeWithText("Marta").assertIsDisplayed()
            assertFalse(state.historyVisible)
            onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
            onNodeWithTag(UiAutomationIds.PROFILE_NAME_INPUT.value).performTextReplacement("Unsaved header draft")
            onNodeWithTag(UiAutomationIds.PROFILE_CLOSE.value).performClick()
            assertFalse(state.nameEditing)
            assertEquals("Marta", savedName.value)
            assertEquals(0, backRequests)
            onNodeWithTag(UiAutomationIds.PROFILE_CLOSE.value).performClick()
            assertEquals(1, backRequests)
        }
}
