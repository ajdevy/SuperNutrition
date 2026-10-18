package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val DefaultRewardsTitles = listOf(
    "Fiber Friend",
    "Protein Pal",
    "Rhythm Keeper",
    "Good Fats",
    "Culture Keeper",
    "Veggie Explorer",
    "Rainbow Plate",
    "Protein Pro",
    "Rhythm Master",
    "Fiber Legend",
)

private val DefaultDisplayedRewardsSubtitles = listOf(
    "Fiber goal · 3 days",
    "Protein · 5 meals",
    "7 days in a row",
    "Fats goal · 3 days",
)

private enum class DefaultRewardsLabel { TITLE, SUBTITLE }

@OptIn(ExperimentalTestApi::class)
class RewardsTextReadabilityTest {
    @Test
    fun `normal Rewards header displays its complete name`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { RewardsScreen(layoutRewardsModel()) }
        onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value, useUnmergedTree = true)
            .assertIsDisplayed().assertCompleteRewardsText("Rewards")
    }

    @Test
    fun `normal long visual titles remain complete inside their cards`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val title = "A little care for your daily rhythm"
        val model = layoutRewardsModel().copy(
            visualCards = List(10) { index ->
                RewardsVisualCardCopy(RewardId.entries[index % RewardId.entries.size], title, "Helpful choice")
            },
        )
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) { RewardsScreen(model) }
        }
        model.visualCards.forEachIndexed { index, copy ->
            val row = onNodeWithTag(UiAutomationIds.rewardsRow(index).value).performScrollTo()
            val text = onNodeWithTag(UiAutomationIds.rewardsRowTitle(index).value, useUnmergedTree = true)
            text.assertIsDisplayed().assertCompleteRewardsText(copy.title)
            val cardBounds = row.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            assertTrue(textBounds.top >= cardBounds.top && textBounds.bottom <= cardBounds.bottom)
        }
        onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).performRewardsScrollToEnd()
        val finalCard = onNodeWithTag(UiAutomationIds.rewardsRow(9).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(finalCard.bottom < camera.top, "Normal long-copy card overlaps navigation: $finalCard and $camera")
    }

    @Test
    fun `normal Back retains a full touch target and working action`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        var clicks = 0
        setContent { RewardsScreen(layoutRewardsModel(), onBackRequested = { clicks++ }) }
        onNodeWithTag(UiAutomationIds.REWARDS_BACK.value)
            .assertIsDisplayed().assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `See all text has readable contrast on the actual Rewards backdrop`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { RewardsScreen(layoutRewardsModel()) }
        val link = onNodeWithTag(UiAutomationIds.REWARDS_EARNED_LINK.value).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        link.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color.luminance()
        val y = link.fetchSemanticsNode().boundsInRoot.center.y.toInt()
        val backdrop = captureToImage().toPixelMap()[4, y].luminance()
        val contrast = (maxOf(foreground, backdrop) + 0.05f) / (minOf(foreground, backdrop) + 0.05f)
        assertTrue(contrast >= 4.5f, "See all text contrast is $contrast, below 4.5")
    }

    @Test
    fun `default Rewards titles stay complete on a compact phone at double text size`() = assertReadableDefaults(
        Size(320f, 568f),
        DefaultRewardsLabel.TITLE,
    )

    @Test
    fun `default Rewards titles stay complete on an ordinary phone at double text size`() = assertReadableDefaults(
        Size(375f, 812f),
        DefaultRewardsLabel.TITLE,
    )

    @Test
    fun `default Rewards subtitles stay complete on a compact phone at double text size`() = assertReadableDefaults(
        Size(320f, 568f),
        DefaultRewardsLabel.SUBTITLE,
    )

    @Test
    fun `default Rewards subtitles stay complete on an ordinary phone at double text size`() = assertReadableDefaults(
        Size(375f, 812f),
        DefaultRewardsLabel.SUBTITLE,
    )

    private fun assertReadableDefaults(viewport: Size, kind: DefaultRewardsLabel) = runSkikoComposeUiTest(
        size = viewport,
        density = Density(1f, 2f),
    ) {
        val model = layoutRewardsModel()
        assertTrue(model.visualCards.isEmpty(), "The fixture must exercise Rewards' actual default visual copy")
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(model)
            }
        }
        val labels = if (kind == DefaultRewardsLabel.TITLE) DefaultRewardsTitles else DefaultDisplayedRewardsSubtitles
        labels.forEachIndexed { index, expected ->
            val row = onNodeWithTag(UiAutomationIds.rewardsRow(index).value).performScrollTo()
            val text = if (kind == DefaultRewardsLabel.TITLE) {
                onNodeWithTag(UiAutomationIds.rewardsRowTitle(index).value, useUnmergedTree = true)
            } else {
                onNodeWithText(expected, useUnmergedTree = true)
            }
            text.assertIsDisplayed().assertCompleteRewardsText(expected)
            val cardBounds = row.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            assertTrue(
                textBounds.top >= cardBounds.top && textBounds.bottom <= cardBounds.bottom,
                "Default $kind leaves its card: $expected at $textBounds in $cardBounds",
            )
        }
        onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).performRewardsScrollToEnd()
        val finalCard = onNodeWithTag(UiAutomationIds.rewardsRow(9).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val finalState = onNodeWithTag(UiAutomationIds.rewardsRowState(9).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(finalState.top >= finalCard.top && finalState.bottom <= finalCard.bottom)
        assertTrue(finalCard.bottom < camera.top, "Default final reward overlaps navigation: $finalCard and $camera")
    }
}
