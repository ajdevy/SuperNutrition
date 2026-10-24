package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileScreenComposeTest {
    private val copy = ProfileScreenCopy()

    @Test
    fun `unknown identity and empty history never invent user data`() = runComposeUiTest {
        var backRequested = false
        setContent {
            ProfileFlow(rememberProfileFlowState(), "", ProfileScreenActions({}, { backRequested = true }))
        }

        onNodeWithText("?").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PROFILE_NAME.value).assertIsDisplayed()
        onNodeWithText(copy.unknownName).assertIsDisplayed()
        onNodeWithText("0 meals saved").assertIsDisplayed()
        onNodeWithText("Anastasia").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithText(MealHistoryCopy().emptyTitle).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_CLOSE.value).performClick()
        onNodeWithTag(UiAutomationIds.PROFILE_CLOSE.value).performClick()
        assertTrue(backRequested)
    }

    @Test
    fun `name editing rejects blank input and sends a trimmed name to its session owner`() = runComposeUiTest {
        var savedName: String? = null
        setContent {
            var displayName by remember { mutableStateOf("") }
            ProfileScreen(
                displayName,
                emptyList(),
                ProfileScreenActions(
                    onNameChanged = {
                        displayName = it
                        savedName = it
                    },
                    onBackRequested = {},
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        onNode(hasSetTextAction()).performTextReplacement("   ")
        onNodeWithContentDescription(copy.save).assertIsNotEnabled()
        assertEquals(null, savedName)
        onNode(hasSetTextAction()).performTextReplacement("  Alex  ")
        onNodeWithContentDescription(copy.save).performClick()
        assertEquals("Alex", savedName)
        onNodeWithText("Alex").assertIsDisplayed()
        onNodeWithText("A").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        onNode(hasSetTextAction()).performTextReplacement("Other name")
        onNodeWithTag(UiAutomationIds.PROFILE_CLOSE.value).performClick()
        assertEquals("Alex", savedName)
    }

    @Test
    fun `unavailable documents and crash reports explain their state`() = runComposeUiTest {
        setContent { ProfileScreen("", emptyList(), ProfileScreenActions({}, {})) }
        val information = listOf(
            UiAutomationIds.PROFILE_PRIVACY to copy.privacyUnavailable,
            UiAutomationIds.PROFILE_TERMS to copy.termsUnavailable,
            UiAutomationIds.PROFILE_CRASH_REPORTS to copy.crashReportsUnavailable,
        )
        information.forEach { (id, explanation) ->
            onNodeWithTag(id.value).performClick()
            onNodeWithText(explanation).assertIsDisplayed()
            onNodeWithText(copy.done).performClick()
        }
        onNodeWithTag(UiAutomationIds.PROFILE_CRASH_REPORTS.value)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, copy.crashReportsState))
    }

    @Test
    fun `approved document actions are delegated to the host`() = runComposeUiTest {
        var privacyRequests = 0
        var termsRequests = 0
        setContent {
            ProfileScreen(
                "",
                emptyList(),
                ProfileScreenActions(
                    onNameChanged = {},
                    onBackRequested = {},
                    onPrivacyPolicyRequested = { privacyRequests++ },
                    onTermsRequested = { termsRequests++ },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PROFILE_PRIVACY.value).performClick()
        onNodeWithTag(UiAutomationIds.PROFILE_TERMS.value).performClick()
        assertEquals(1, privacyRequests)
        assertEquals(1, termsRequests)
        onNodeWithText(copy.privacyUnavailable).assertDoesNotExist()
        onNodeWithText(copy.termsUnavailable).assertDoesNotExist()
    }

    @Test
    fun `missing browser explains document unavailability without crashing`() = runComposeUiTest {
        setContent {
            ProfileScreen(
                "",
                emptyList(),
                ProfileScreenActions(
                    onNameChanged = {},
                    onBackRequested = {},
                    onPrivacyPolicyRequested = { throw IllegalArgumentException("No HTTPS browser") },
                    onTermsRequested = { throw IllegalArgumentException("No HTTPS browser") },
                ),
            )
        }
        listOf(
            UiAutomationIds.PROFILE_PRIVACY to copy.privacyUnavailable,
            UiAutomationIds.PROFILE_TERMS to copy.termsUnavailable,
        ).forEach { (id, message) ->
            onNodeWithTag(id.value).performClick()
            onNodeWithText(message).assertIsDisplayed()
            onNodeWithText(copy.done).performClick()
        }
    }

    @Test
    fun `avatar initials support blank names unicode and emoji`() {
        assertEquals("?", profileInitial("  "))
        assertEquals("É", profileInitial(" élise "))
        assertEquals("А", profileInitial("анна"))
        assertEquals("🥗", profileInitial("🥗 Cook"))
    }

    @Test
    fun `avatar preserves combining marks flags and joined emoji`() {
        assertEquals("E\u0301", profileInitial("e\u0301lise"))
        assertEquals("A\u0308\u0301", profileInitial("a\u0308\u0301nne"))
        assertEquals("কা", profileInitial("কাল"))
        assertEquals("🇱🇻", profileInitial("🇱🇻 Latvia"))
        assertEquals("🇱🇻", profileInitial("🇱🇻🇪🇪"))
        assertEquals("👩🏽‍💻", profileInitial("👩🏽‍💻 Developer"))
        assertEquals("👨‍👩‍👧‍👦", profileInitial("👨‍👩‍👧‍👦 Family"))
        assertEquals("❤️", profileInitial("❤️ Cook"))
        assertEquals("1️⃣", profileInitial("1️⃣ First"))
    }
}
