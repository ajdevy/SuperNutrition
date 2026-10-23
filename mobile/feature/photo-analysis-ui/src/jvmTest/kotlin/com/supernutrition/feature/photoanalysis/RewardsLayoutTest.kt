package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardsLayoutTest {
    @Test
    fun `carousel cards align progress despite different description lengths`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        val model = layoutRewardsModel()
        setContent {
            RewardsScreen(
                model.copy(
                    cards = model.cards.map { card ->
                        card.copy(
                            description = if (card.id ==
                                RewardId.PROTEIN_ALL_DAY
                            ) {
                                "Short"
                            } else {
                                "A longer description with several lines."
                            },
                        )
                    },
                ),
            )
        }
        val progress = (0..1).map {
            onNodeWithTag(
                UiAutomationIds.rewardsRowProgress(it).value,
            ).fetchSemanticsNode().boundsInRoot
        }
        assertEquals(progress[0].top, progress[1].top, 1f)
    }

    @Test
    fun `ordinary phone presents four daily and three weekly cards in horizontal carousels`() = runSkikoComposeUiTest(
        size = Size(361f, 782f),
        density = Density(1f),
    ) {
        setContent { RewardsScreen(layoutRewardsModel()) }
        val first = onNodeWithTag(UiAutomationIds.rewardsRow(0).value).fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag(UiAutomationIds.rewardsRow(1).value).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertEquals(150f, first.width, 1f)
        assertEquals(10f, second.left - first.right, 1f)
        val daily = onNodeWithTag(UiAutomationIds.REWARDS_EARNED_SECTION.value).fetchSemanticsNode().boundsInRoot
        val weekly = onNodeWithTag(UiAutomationIds.REWARDS_IN_PROGRESS_SECTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(daily.bottom < first.top && weekly.top > first.bottom)
        (0..6).forEach { onNodeWithTag(UiAutomationIds.rewardsRow(it).value).performScrollTo().assertExists() }
        onNodeWithTag(UiAutomationIds.rewardsRow(7).value).assertDoesNotExist()
    }
}
