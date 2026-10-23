package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeHeaderSpacingTest {
    @Test
    fun `Home does not display the redundant wordmark`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { HomeScreen(layoutHomeModel()) }

        onNodeWithText("Super Nutrition", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Home does not display the weekday or date`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val model = layoutHomeModel()
        setContent { HomeScreen(model) }

        onNodeWithText(model.dateLabel, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.HOME_DATE.value, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Home retains screen identity logo and profile action`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val model = layoutHomeModel()
        var profileRequests = 0
        setContent { HomeScreen(model, onProfileRequested = { profileRequests++ }) }

        onNodeWithTag(UiAutomationIds.HOME_TITLE.value).assertIsDisplayed()
            .assertContentDescriptionEquals(model.title)
        onNodeWithText(model.rhythmBadgeLabel, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.HOME_PROFILE.value).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, profileRequests)
    }

    @Test
    fun `compact Home keeps a current separation between mascot and metrics`() = assertCardSpacing(
        viewport = Size(320f, 568f),
    )

    @Test
    fun `ordinary Home keeps a current separation between mascot and metrics`() = assertCardSpacing(
        viewport = Size(360f, 780f),
    )

    @Test
    fun `large Home keeps a current separation between mascot and metrics`() = assertCardSpacing(
        viewport = Size(412f, 915f),
    )

    @Test
    fun `enlarged text keeps the current separation between mascot and metrics`() = assertCardSpacing(
        viewport = Size(412f, 915f),
        fontScale = 1.3f,
    )

    @Test
    fun `double text keeps the section spacing when Home needs readable overflow`() = assertCardSpacing(
        viewport = Size(360f, 780f),
        fontScale = 2f,
    )

    @Test
    fun `tablet with double text keeps the same mascot and metric spacing`() = assertCardSpacing(
        viewport = Size(800f, 1280f),
        fontScale = 2f,
    )

    private fun assertCardSpacing(viewport: Size, fontScale: Float = 1f) = runSkikoComposeUiTest(
        size = viewport,
        density = Density(1f, fontScale),
    ) {
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 68.dp, bottom = 48.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }

        // The natural frames remain measurable when large text needs scrolling; clipped bounds
        // would report the viewport edge rather than the actual distance between the two cards.
        val header = onNodeWithTag(UiAutomationIds.HOME_HEADER.value).getUnclippedBoundsInRoot()
        val care = onNodeWithTag(UiAutomationIds.HOME_CARE_PROMPT.value).getUnclippedBoundsInRoot()
        val mascot = onNodeWithContentDescription(layoutHomeModel().mascotDescription).getUnclippedBoundsInRoot()
        val rhythm = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).getUnclippedBoundsInRoot()
        assertEquals(
            if (viewport.height - 116f < 700f) 29f else 35f,
            (care.top - header.bottom).value,
            1f,
            "Header/bubble gap drifted",
        )
        assertEquals(5f, (mascot.top - care.bottom).value, 1f, "Bubble/sprite gap drifted")
        assertEquals(18.5f, (rhythm.top - mascot.bottom).value, 1f, "Sprite/metrics gap drifted")
        assertEquals(header.left.value, rhythm.left.value, 1f, "Header and card start edges differ")
        assertEquals(header.right.value, rhythm.right.value, 1f, "Header and card end edges differ")
        val headerCenter = (header.left.value + header.right.value) / 2
        val careCenter = (care.left.value + care.right.value) / 2
        assertTrue(abs(headerCenter - careCenter) <= 16f, "Care bubble leaves its central design slot")
        assertEquals(rhythm.left.value, viewport.width - rhythm.right.value, 1f, "Home gutters are asymmetric")
        assertTrue(rhythm.left >= 16.dp, "Home cards lose the shared outer gutter: $rhythm")
        if (viewport.width <= 430f) assertEquals(16f, rhythm.left.value, 1f, "Phone gutter must remain 16dp")
    }
}
