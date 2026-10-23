package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReviewAcceptanceRegressionTest {
    @Test
    fun `RTL summary reserves the logical start for its actual mascot`() = assertRtlSummary(1f)

    @Test
    fun `RTL summary reserves its mascot side at double text`() = assertRtlSummary(2f)

    @Test
    fun `standalone Rewards has a visible working Back target`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        var requests = 0
        setContent { RewardsScreen(layoutRewardsModel(), onBackRequested = { requests++ }) }
        val back = onNodeWithTag(UiAutomationIds.REWARDS_BACK.value).assertIsDisplayed()
        val bounds = back.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= 48f && bounds.height >= 48f)
        back.performClick()
        assertEquals(1, requests)
    }

    @Test
    fun `standalone localized title at double text clears Back and remains complete`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f, 2f),
    ) {
        val title = "Ваши ежедневные награды за заботу о себе"
        setContent { RewardsScreen(layoutRewardsModel().copy(title = title)) }
        val heading = onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value)
        heading.assertCompleteRewardsText(title)
        val titleBounds = heading.fetchSemanticsNode().boundsInRoot
        val back = onNodeWithTag(UiAutomationIds.REWARDS_BACK.value).fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.left >= back.right, "Localized title overlaps the Back target: $titleBounds / $back")
        assertTrue(titleBounds.right <= 375f - 64f, "Standalone title lost its symmetric trailing clearance")
    }

    @Test
    fun `Rewards tab preserves the Figma header without a standalone Back target`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(layoutRewardsModel())
            }
        }
        onNodeWithTag(UiAutomationIds.REWARDS_BACK.value).assertDoesNotExist()
    }

    @Test
    fun `actual card progress counts are visible and complete`() = assertProgressCounts(1f)

    @Test
    fun `actual card progress counts remain complete at double text`() = assertProgressCounts(2f)

    @Test
    fun `inline name editing visibly explains its session scope`() = assertSessionHint(1f)

    @Test
    fun `inline session scope explanation wraps completely at double text`() = assertSessionHint(2f)

    @Test
    fun `inline name controls expose stable native automation targets`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { ProfileScreen("Alex", emptyList(), ProfileScreenActions({}, {})) }
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        listOf(
            UiAutomationIds.PROFILE_NAME_INPUT,
            UiAutomationIds.PROFILE_NAME_CLEAR,
            UiAutomationIds.PROFILE_NAME_SAVE,
        ).forEach { id ->
            onNodeWithTag(id.value).assertIsDisplayed()
        }
    }

    @Test
    fun `summary uses complete localized templates and their supplied word order`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f, 2f),
    ) {
        val model = currentDesignRewardsFixture()
        val copy = RewardsScreenCopy(
            daily = "Ежедневно",
            weekly = "За неделю",
            countTemplate = "{earned} из {total}",
            todaySummaryTemplate = "Наград сегодня: {earned}; всего {total}",
            weekSummaryTemplate = "Из {total} наград недели получено {earned}",
        )
        setContent { RewardsScreen(model, copy = copy) }
        onNodeWithText("Наград сегодня: 2; всего 4").assertCompleteRewardsText("Наград сегодня: 2; всего 4")
        onNodeWithText("Из 3 наград недели получено 1").assertCompleteRewardsText("Из 3 наград недели получено 1")
        onNodeWithTag(UiAutomationIds.REWARDS_EARNED_SECTION.value).assertCompleteRewardsText(copy.daily)
    }

    private fun assertRtlSummary(scale: Float) = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f, scale),
    ) {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RewardsScreen(layoutRewardsModel())
            }
        }
        val hero = onNodeWithTag(UiAutomationIds.REWARDS_LEVEL_HERO.value).fetchSemanticsNode().boundsInRoot
        val pixels = captureToImage().toPixelMap()
        var count = 0
        var horizontalSum = 0f
        for (y in hero.top.toInt() until hero.bottom.toInt()) {
            for (x in hero.left.toInt() until hero.right.toInt()) {
                val pixel = pixels[x, y]
                if (pixel.isMascotBrown()) {
                    count++
                    horizontalSum += x - hero.left
                }
            }
        }
        assertTrue(count > 100, "The actual mascot artwork is missing")
        assertTrue(horizontalSum / count > hero.width * .65f, "RTL art still occupies the summary text side")
    }

    private fun Color.isMascotBrown(): Boolean {
        val midTone = red > .35f && green > .15f && blue < .5f
        return midTone && red - blue > .2f && green - blue > .05f
    }

    private fun assertProgressCounts(scale: Float) = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f, scale),
    ) {
        val model = layoutRewardsModel().let { original ->
            original.copy(cards = original.cards.mapIndexed { index, card -> card.copy(progressLabel = "$index of 9") })
        }
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) { RewardsScreen(model) }
        }
        model.cards.forEach { card ->
            val count = onNodeWithText(card.progressLabel, useUnmergedTree = true)
            onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).revealRewardText(count)
                .assertCompleteRewardsText(card.progressLabel)
        }
    }

    private fun assertSessionHint(scale: Float) = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f, scale),
    ) {
        val copy = ProfileScreenCopy()
        setContent { ProfileScreen("Alex", emptyList(), ProfileScreenActions({}, {})) }
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        onNodeWithText(copy.sessionNameDescription).assertIsDisplayed()
            .assertCompleteRewardsText(copy.sessionNameDescription)
    }
}
