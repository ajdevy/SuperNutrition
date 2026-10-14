package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardsLayoutTest {
    @Test
    fun `paired rewards align progress and state despite different description lengths`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        val original = layoutRewardsModel()
        val cards = original.cards.take(2).mapIndexed { index, card ->
            card.copy(
                description = if (index == 0) "A short description." else "A longer description with several lines.",
            )
        }
        setContent { RewardsScreen(original.copy(cards = cards)) }
        val progress = (0..1).map {
            onNodeWithTag(UiAutomationIds.rewardsRowProgress(it).value).fetchSemanticsNode().boundsInRoot
        }
        val states = (0..1).map {
            onNodeWithTag(UiAutomationIds.rewardsRowState(it).value).fetchSemanticsNode().boundsInRoot
        }
        assertTrue(abs(progress[0].top - progress[1].top) <= 1f, "Progress does not align: $progress")
        assertTrue(abs(states[0].top - states[1].top) <= 1f, "State does not align: $states")
    }

    @Test
    fun `ordinary phone presents rewards in balanced equal height pairs`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(
                    layoutRewardsModel().copy(
                        earnedSummary = "1 of 7",
                        earnedSummaryLabel = "Added to your rewards",
                    ),
                )
            }
        }
        val first = onNodeWithTag(UiAutomationIds.rewardsRow(0).value).fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag(UiAutomationIds.rewardsRow(1).value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(first.top - second.top) <= 1f, "Rewards remain a vertical list: $first and $second")
        assertTrue(abs(first.width - second.width) <= 1f && abs(first.height - second.height) <= 1f)
        assertTrue(abs(first.left - (361f - second.right)) <= 1f)
        assertTrue(second.left - first.right in 9f..11f, "Rewards pair has an uneven gap: $first and $second")
        val summary = onNodeWithText("1 of 7").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(summary.bottom < first.top, "Earned summary overlaps the reward grid: $summary and $first")
    }
}
