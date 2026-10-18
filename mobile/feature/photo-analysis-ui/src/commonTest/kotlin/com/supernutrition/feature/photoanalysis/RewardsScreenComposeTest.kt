package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardsScreenComposeTest {
    @Test
    fun `figma rewards surface exposes its header sections and ten cards`() = runComposeUiTest {
        val model = RewardsPresenterModel(
            title = "Rewards",
            cards = RewardId.entries.mapIndexed { index, id ->
                RewardsCardModel(
                    id = id,
                    title = "Reward $index",
                    description = "You recorded a helpful choice.",
                    progressLabel = "${if (index == 0) 2 else 0} of ${if (index == 0) 2 else 1}",
                    stateLabel = if (index == 0) "Added to your rewards" else "Ready when you are",
                    isEarned = index == 0,
                )
            },
        )
        setContent {
            Box(Modifier.requiredSize(375.dp, 812.dp)) { RewardsScreen(model) }
        }

        onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_BACK.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_LEVEL_HERO.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_EARNED_SECTION.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_EARNED_LINK.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_IN_PROGRESS_SECTION.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).assertIsDisplayed()
        (0 until 10).forEach { index ->
            onNodeWithTag(UiAutomationIds.rewardsRow(index).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.rewardsRowTitle(index).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.rewardsRowProgress(index).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.rewardsRowState(index).value).assertIsDisplayed()
        }

        assertTrue(model.cards.all { card -> !card.description.contains("fail", ignoreCase = true) })
    }
}
