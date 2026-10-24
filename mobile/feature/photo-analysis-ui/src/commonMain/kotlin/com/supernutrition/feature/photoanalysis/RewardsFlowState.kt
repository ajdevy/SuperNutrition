package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.supernutrition.domain.nutrition.RewardId

/** Both platform hosts can unwind a detail before leaving the shared Rewards destination. */
@Stable
class RewardsFlowState {
    var selectedReward by mutableStateOf<RewardId?>(null)
        private set
    val canGoBack: Boolean
        get() = selectedReward != null

    internal fun showReward(id: RewardId) {
        selectedReward = id
    }

    fun back(): Boolean {
        if (selectedReward == null) return false
        selectedReward = null
        return true
    }
}

@Composable
fun rememberRewardsFlowState(): RewardsFlowState = remember { RewardsFlowState() }
