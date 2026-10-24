package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

/** Optional detail evidence and navigation; absent state and date use the current shared session. */
data class RewardsDetailsContext(
    val confirmedFoods: List<ConfirmedConsumedFood> = emptyList(),
    val referenceDay: Int? = null,
    val flowState: RewardsFlowState? = null,
    val copy: RewardDetailsScreenCopy = RewardDetailsScreenCopy(),
    val savedMeals: List<SavedMeal> = emptyList(),
)
