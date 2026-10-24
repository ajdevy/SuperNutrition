package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.domain.nutrition.RewardId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RhythmRewardEvidenceTest {
    @Test
    fun `rhythm rules credit explicit diary entries without requiring nutrition`() {
        val groups = rewardDetailCopy(RewardId.SEVEN_DAYS_RECORDED).groups.joinToString(" ") { it.second }
        assertFalse(groups.contains("Unmatched diary entries do not yet count"))
        assertTrue(groups.contains("diary", ignoreCase = true))
    }

    @Test
    fun `yesterday grace shows all seven credited recording days`() = runComposeUiTest {
        val foods = (3..9).map { day -> profileConfirmedFood("$day", "meal-$day", "Recorded day $day", day) }
        setContent {
            Column { RewardDetailsEvidence(RewardId.SEVEN_DAYS_RECORDED, foods, 10, RewardDetailsScreenCopy()) }
        }
        onNodeWithText("Recorded day 3").assertIsDisplayed()
        onNodeWithText("Recorded day 9").assertIsDisplayed()
    }

    @Test
    fun `disconnected recording days do not appear as credited streak evidence`() = runComposeUiTest {
        val foods = listOf(
            profileConfirmedFood("old", "old-meal", "Disconnected record", 7),
            profileConfirmedFood("current", "current-meal", "Current record", 10),
        )
        setContent {
            Column { RewardDetailsEvidence(RewardId.SEVEN_DAYS_RECORDED, foods, 10, RewardDetailsScreenCopy()) }
        }
        onNodeWithText("Current record").assertIsDisplayed()
        onNodeWithText("Disconnected record").assertDoesNotExist()
    }
}
