package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RewardDetailsComposeTest {
    @Test
    fun `all seven actual reward cards open dedicated details and return`() = runComposeUiTest {
        val model = layoutRewardsModel()
        val displayOrder = listOf(
            RewardId.PROTEIN_ALL_DAY,
            RewardId.FATS_PRESENT,
            RewardId.FIBER_PRESENT,
            RewardId.COLORS_OF_DAY,
            RewardId.SEVEN_DAYS_RECORDED,
            RewardId.THIRTY_PRODUCTS,
            RewardId.FERMENTED_THREE_DAYS,
        )
        setContent { RewardsScreen(model) }
        displayOrder.forEachIndexed { index, id ->
            onNodeWithTag(UiAutomationIds.rewardsRow(index).value).performScrollTo().performClick()
            onNodeWithTag(UiAutomationIds.REWARD_DETAILS_TITLE.value)
                .assertTextEquals(model.cards.single { it.id == id }.title)
            onNodeWithTag(UiAutomationIds.REWARD_DETAILS_PROGRESS.value).performScrollTo().assertIsDisplayed()
            onNodeWithText(
                if (id == RewardId.SEVEN_DAYS_RECORDED) "No recordings yet" else "No confirmed foods yet",
            ).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.REWARD_DETAILS_HOW.value).performScrollTo().assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.REWARD_DETAILS_FOODS.value).performScrollTo().assertIsDisplayed()
            onNodeWithText("Salmon grain bowl").assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.REWARD_DETAILS_BACK.value).performClick()
        }
    }
}
