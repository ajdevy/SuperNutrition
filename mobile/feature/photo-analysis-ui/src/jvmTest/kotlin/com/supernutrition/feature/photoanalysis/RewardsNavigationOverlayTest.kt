package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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
class RewardsNavigationOverlayTest {
    @Test
    fun `Rewards fills the safe viewport beneath working floating controls`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val actions = mutableListOf<String>()
        setContent {
            PhotoAnalysisMainLayout(
                state = layoutBottomBarState(1),
                actions = PhotoAnalysisMainBottomBarActions(
                    onHomeSelected = { actions += "home" },
                    onRewardsSelected = { actions += "rewards" },
                    onCameraRequested = { actions += "camera" },
                ),
                contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp),
            ) { RewardsScreen(layoutRewardsModel()) }
        }
        val list = onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(list.top - 24f) <= 1f, "Top safe inset applied twice: $list")
        assertTrue(abs(list.bottom - 792f) <= 1f, "Empty canvas still covers Rewards: $list")
        listOf(UiAutomationIds.HOME_TAB, UiAutomationIds.REWARDS_TAB, UiAutomationIds.CAMERA_ACTION).forEach {
            val control = onNodeWithTag(it.value)
            val bounds = control.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top >= list.top && bounds.bottom <= list.bottom, "Unsafe control bounds: $bounds")
            control.performTouchInput { click(center) }
        }
        assertEquals(listOf("home", "rewards", "camera"), actions)
    }

    @Test
    fun `Rewards remains visible and scrollable beside navigation in a short wide viewport`() = runSkikoComposeUiTest(
        size = Size(600f, 240f),
        density = Density(1f, 2f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(layoutRewardsModel())
            }
        }
        val list = onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).performScrollToIndex(1)
        val card = onNodeWithTag(UiAutomationIds.rewardsRow(0).value).fetchSemanticsNode().boundsInRoot
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val pill = onNodeWithTag(UiAutomationIds.BOTTOM_BAR_PILL.value).fetchSemanticsNode().boundsInRoot
        assertTrue(card.left < pill.left, "There is no visible card beside the pill: $card and $pill")
        val x = (card.left + pill.left) / 2
        val pixels = captureToImage().toPixelMap()
        assertEquals(Color.White, pixels[x.toInt(), 200], "The blank canvas hides reward $card in viewport $bounds")
        val previous = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        list.performTouchInput {
            val start = Offset(x - bounds.left, bounds.height - 12f)
            swipe(start, start - Offset(0f, 80f), 300)
        }
        val current = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(current > previous, "A swipe outside the floating pill cannot reach Rewards")
    }
}
