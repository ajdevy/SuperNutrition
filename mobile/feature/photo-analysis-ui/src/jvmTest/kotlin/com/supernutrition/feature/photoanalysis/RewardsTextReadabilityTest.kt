package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardsTextReadabilityTest {
    @Test
    fun `normal Rewards header displays its complete name`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { RewardsScreen(layoutRewardsModel()) }
        onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value).assertCompleteRewardsText("Rewards")
    }

    @Test
    fun `normal long actual titles remain complete inside their cards`() = assertReadable(Size(375f, 812f), 1f, true)

    @Test
    fun `native Back callback remains exposed as an accessibility action`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        var requests = 0
        setContent { RewardsScreen(layoutRewardsModel(), onBackRequested = { requests++ }) }
        assertTrue(
            onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single().action(),
        )
        assertEquals(1, requests)
    }

    @Test
    fun `Daily label has readable contrast on the actual Rewards backdrop`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { RewardsScreen(layoutRewardsModel()) }
        val label = onNodeWithTag(UiAutomationIds.REWARDS_EARNED_SECTION.value)
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color.luminance()
        val y = label.fetchSemanticsNode().boundsInRoot.center.y.toInt()
        val backdrop = captureToImage().toPixelMap()[4, y].luminance()
        assertTrue((maxOf(foreground, backdrop) + .05f) / (minOf(foreground, backdrop) + .05f) >= 4.5f)
    }

    @Test
    fun `actual Rewards titles remain complete on compact phone at double text`() = assertReadable(
        Size(320f, 568f),
        2f,
        true,
    )

    @Test
    fun `actual Rewards titles remain complete on ordinary phone at double text`() = assertReadable(
        Size(375f, 812f),
        2f,
        true,
    )

    @Test
    fun `actual Rewards descriptions remain complete on compact phone at double text`() = assertReadable(
        Size(320f, 568f),
        2f,
        false,
    )

    @Test
    fun `actual Rewards descriptions remain complete on ordinary phone at double text`() = assertReadable(
        Size(375f, 812f),
        2f,
        false,
    )

    private fun assertReadable(viewport: Size, scale: Float, titles: Boolean) = runSkikoComposeUiTest(
        size = viewport,
        density = Density(1f, scale),
    ) {
        val title = "A little care for your daily rhythm"
        val model = layoutRewardsModel().let { model ->
            model.copy(
                cards = model.cards.map {
                    it.copy(title = "$title ${it.id}", description = "A complete localized description for ${it.id}")
                },
            )
        }
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(1),
                layoutBottomBarActions(),
            ) { RewardsScreen(model) }
        }
        val ordered = listOf(
            com.supernutrition.domain.nutrition.RewardId.PROTEIN_ALL_DAY,
            com.supernutrition.domain.nutrition.RewardId.FATS_PRESENT,
            com.supernutrition.domain.nutrition.RewardId.FIBER_PRESENT,
            com.supernutrition.domain.nutrition.RewardId.COLORS_OF_DAY,
            com.supernutrition.domain.nutrition.RewardId.SEVEN_DAYS_RECORDED,
            com.supernutrition.domain.nutrition.RewardId.THIRTY_PRODUCTS,
            com.supernutrition.domain.nutrition.RewardId.FERMENTED_THREE_DAYS,
        )
        ordered.forEachIndexed { index, id ->
            val card = model.cards.single { it.id == id }
            val expected = if (titles) card.title else card.description
            val target = if (titles) {
                onNodeWithTag(UiAutomationIds.rewardsRowTitle(index).value, useUnmergedTree = true)
            } else {
                onNodeWithText(expected, useUnmergedTree = true)
            }
            onNodeWithTag(
                UiAutomationIds.REWARDS_LIST.value,
            ).revealRewardText(target).assertCompleteRewardsText(expected)
        }
    }
}
