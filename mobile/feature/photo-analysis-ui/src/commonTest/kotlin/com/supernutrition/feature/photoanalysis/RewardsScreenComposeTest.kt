package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RewardsScreenComposeTest {
    @Test
    fun `standalone rewards exposes Back Daily Weekly and exactly seven actual identities`() = runComposeUiTest {
        setContent { RewardsScreen(layoutRewardsModel()) }
        onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_BACK.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_EARNED_LINK.value).assertDoesNotExist()
        onNodeWithText("Daily").assertIsDisplayed()
        onNodeWithText("Weekly").assertIsDisplayed()
        (0 until 7).forEach { index ->
            onNodeWithTag(UiAutomationIds.rewardsRow(index).value).performScrollTo().assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.rewardsRowTitle(index).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.rewardsRowProgress(index).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.rewardsRowState(index).value).assertIsDisplayed()
        }
        onNodeWithTag(UiAutomationIds.rewardsRow(7).value).assertDoesNotExist()
    }
}
