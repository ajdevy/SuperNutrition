package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.graphics.Color
import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.reward_30_foods
import com.supernutrition.feature.photoanalysis.resources.reward_7_day_rhythm
import com.supernutrition.feature.photoanalysis.resources.reward_colors
import com.supernutrition.feature.photoanalysis.resources.reward_culture_keeper
import com.supernutrition.feature.photoanalysis.resources.reward_fiber
import com.supernutrition.feature.photoanalysis.resources.reward_healthy_fats
import com.supernutrition.feature.photoanalysis.resources.reward_protein

internal val RewardsPaper = Color(0xFFFBFCF8)
internal val RewardsInk = Color(0xFF27322C)
internal val RewardsMuted = Color(0xFF68766E)
internal val RewardsLine = Color(0xFFE5EAE2)
internal val RewardsGreen = Color(0xFF3D8A62)
internal val RewardsTextGreen = Color(0xFF31734F)

internal fun rewardIllustration(id: RewardId) = when (id) {
    RewardId.PROTEIN_ALL_DAY -> Res.drawable.reward_protein
    RewardId.FATS_PRESENT -> Res.drawable.reward_healthy_fats
    RewardId.FIBER_PRESENT -> Res.drawable.reward_fiber
    RewardId.COLORS_OF_DAY -> Res.drawable.reward_colors
    RewardId.THIRTY_PRODUCTS -> Res.drawable.reward_30_foods
    RewardId.FERMENTED_THREE_DAYS -> Res.drawable.reward_culture_keeper
    RewardId.SEVEN_DAYS_RECORDED -> Res.drawable.reward_7_day_rhythm
}
