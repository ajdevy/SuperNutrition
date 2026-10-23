package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NativePhoneHomeLayoutTest {
    @Test
    fun `ordinary phone keeps a substantial mascot with native insets`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 39.dp, bottom = 22.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }
        val mascot = onNodeWithContentDescription("Capybara mascot").fetchSemanticsNode().boundsInRoot
        assertTrue(mascot.height >= 100f, "Mascot shrinks to a thumbnail on the native phone: $mascot")
    }

    @Test
    fun `visible navigation pill stays close to the bottom safe edge`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 39.dp, bottom = 22.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }
        val pill = onNodeWithTag(UiAutomationIds.BOTTOM_BAR_PILL.value).fetchSemanticsNode().boundsInRoot
        // The current capsule is a native shape; its semantic bounds are its solid fill.
        val visibleBottom = pill.bottom
        val bottomGap = 782f - 22f - visibleBottom
        assertTrue(bottomGap in 0f..8f, "Navigation floats above its safe edge by $bottomGap dp")
    }

    @Test
    fun `last nutrition metric is complete in the first ordinary phone frame`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 39.dp, bottom = 22.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }
        val card = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).fetchSemanticsNode().boundsInRoot
        val content = onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).fetchSemanticsNode().boundsInRoot
        val action = onNodeWithText(
            layoutHomeModel().fats.label,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(card.bottom <= content.bottom - 8f, "Initial frame clips the final metric: $card in $content")
        assertTrue(action.top >= card.top + 8f && action.bottom <= card.bottom - 8f)
    }
}
