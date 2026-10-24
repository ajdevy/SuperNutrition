package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.domain.nutrition.RewardId
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SavedMealRhythmDetailsTest {
    @Test
    fun `saved diary records supply all credited rhythm dates without confirmed foods`() = runComposeUiTest {
        val state = RewardsFlowState().apply { showReward(RewardId.SEVEN_DAYS_RECORDED) }
        val saved = (3..9).map { day ->
            diaryMealFixture("meal-$day").copy(localDay = day, name = "Diary day $day")
        }
        setContent {
            RewardsScreen(
                layoutRewardsModel(),
                details = RewardsDetailsContext(referenceDay = 10, flowState = state, savedMeals = saved),
            )
        }
        onNodeWithText("Diary day 3").performScrollTo().assertIsDisplayed()
        onNodeWithText("Jan 4").performScrollTo().assertIsDisplayed()
        onNodeWithText("Diary day 9").performScrollTo().assertIsDisplayed()
        onNodeWithText("Yesterday · Jan 10").performScrollTo().assertIsDisplayed()
        onNodeWithText("No confirmed foods yet").assertDoesNotExist()
    }

    @Test
    fun `saved diary labels never become nutrient reward evidence`() = runComposeUiTest {
        val state = RewardsFlowState().apply { showReward(RewardId.FATS_PRESENT) }
        setContent {
            RewardsScreen(
                layoutRewardsModel(),
                details = RewardsDetailsContext(
                    referenceDay = 10,
                    flowState = state,
                    savedMeals = listOf(diaryMealFixture().copy(name = "Unclassified avocado")),
                ),
            )
        }
        onNodeWithText("No confirmed foods yet").performScrollTo().assertIsDisplayed()
        onNodeWithText("Unclassified avocado").assertDoesNotExist()
    }
}
