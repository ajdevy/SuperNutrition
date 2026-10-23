package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeNavigationOverlayTest {
    @Test
    fun `Home fills the safe viewport beneath working floating controls`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val actions = mutableListOf<String>()
        setContent {
            PhotoAnalysisMainLayout(
                state = layoutBottomBarState(),
                actions = PhotoAnalysisMainBottomBarActions(
                    onHomeSelected = { actions += "home" },
                    onRewardsSelected = { actions += "rewards" },
                    onCameraRequested = { actions += "camera" },
                ),
                contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }
        val content = onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(content.top - 24f) <= 1f, "Top safe inset applied twice: $content")
        assertTrue(abs(content.bottom - 792f) <= 1f, "Empty canvas still covers Home: $content")
        listOf(UiAutomationIds.HOME_TAB, UiAutomationIds.REWARDS_TAB, UiAutomationIds.CAMERA_ACTION).forEach {
            val control = onNodeWithTag(it.value)
            val bounds = control.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top >= content.top && bounds.bottom <= content.bottom, "Unsafe controls: $bounds")
            control.performTouchInput { click(center) }
        }
        assertEquals(listOf("home", "rewards", "camera"), actions)
    }

    @Test
    fun `Home remains visible and scrollable beside navigation in a short wide viewport`() = runSkikoComposeUiTest(
        size = Size(600f, 240f),
        density = Density(1f, 2f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        val card = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val scroll = onNode(hasScrollAction())
        val bounds = scroll.fetchSemanticsNode().boundsInRoot
        val pill = onNodeWithTag(UiAutomationIds.BOTTOM_BAR_PILL.value).fetchSemanticsNode().boundsInRoot
        assertTrue(card.left < pill.left, "There is no visible card beside the pill: $card and $pill")
        // Probe the rhythm panel's own padding, outside the independently painted metric cards.
        val x = card.left + 4f
        val visibleTop = maxOf(card.top + 24f, pill.top)
        val visibleBottom = minOf(card.bottom - 24f, 240f - 12f)
        assertTrue(visibleTop < visibleBottom, "Blank canvas hides the Home card beside navigation: $card and $pill")
        val y = (visibleTop + visibleBottom) / 2
        val pixels = captureToImage().toPixelMap()
        assertTrue(pixels[x.toInt(), y.toInt()].alpha > 0f, "Home card area is blank beside the floating pill")
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val previous = range.value()
        scroll.performTouchInput {
            val start = Offset(x, y) - bounds.topLeft
            swipe(start, start - Offset(0f, 80f), 300)
        }
        assertTrue(range.value() > previous, "A swipe outside the floating pill cannot reach Home")
    }

    @Test
    fun `last current Home metric and goal action clear controls across screen sizes and font scales`() {
        listOf(Size(320f, 568f), Size(360f, 780f), Size(412f, 915f), Size(600f, 960f)).forEach { viewport ->
            listOf(1f, 2f).forEach { fontScale ->
                runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                    var goalRequests = 0
                    setContent {
                        PhotoAnalysisMainLayout(
                            layoutBottomBarState(),
                            layoutBottomBarActions(),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp),
                        ) { HomeScreen(layoutHomeModel(), onViewGoalsRequested = { goalRequests++ }) }
                    }
                    onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
                    val card = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val content = onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).fetchSemanticsNode().boundsInRoot
                    val controlsTop = listOf(
                        UiAutomationIds.CAMERA_ACTION,
                        UiAutomationIds.HOME_TAB,
                        UiAutomationIds.REWARDS_TAB,
                    ).minOf { onNodeWithTag(it.value).fetchSemanticsNode().boundsInRoot.top }
                    assertTrue(card.top >= content.top, "Final Home card is cropped at $viewport/$fontScale: $card")
                    assertTrue(
                        card.bottom <= controlsTop - 8f,
                        "Final Home card overlaps navigation at $viewport/$fontScale: $card and top=$controlsTop",
                    )
                    onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).assertIsDisplayed()
                    // Click the visible last metric; its parent panel owns the existing goals action.
                    onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).performTouchInput { click(center) }
                    onNodeWithText("Your goals").assertIsDisplayed()
                    assertEquals(1, goalRequests, "Goal action is unreachable at $viewport/$fontScale")
                }
            }
        }
    }

    @Test
    fun `Home keeps the previous compact content geometry when navigation becomes an overlay`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        setContent {
            Box(Modifier.requiredSize(361.dp, 782.dp - 39.dp - 22.dp - MainBottomBarCanvasHeight)) {
                HomeScreen(layoutHomeModel())
            }
        }
        val standaloneMascot = onNodeWithContentDescription("Capybara mascot").fetchSemanticsNode().boundsInRoot
        val standaloneCard = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 39.dp, bottom = 22.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }
        val mascot = onNodeWithContentDescription("Capybara mascot").fetchSemanticsNode().boundsInRoot
        val card = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        assertTrue(mascot.height in 100f..163.5f, "Overlay loses the meaningful sprite: $mascot")
        assertTrue(standaloneMascot.height in 100f..163.5f)
        assertTrue(abs(card.height - standaloneCard.height) <= 1f, "Overlay changed compact card spacing: $card")
    }
}
