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

internal val RewardsPaper = Color(0xFFF7F3EF)
internal val RewardsInk = Color(0xFF27322C)
internal val RewardsMuted = Color(0xFF68766E)
internal val RewardsLine = Color(0xFFE5EAE2)
internal val RewardsGreen = Color(0xFF3D8A62)
internal val RewardsTextGreen = Color(0xFF31734F)
internal val RewardsCardBackground = Color(0xFFFBF9F6)
internal val RewardsRedesignInk = Color(0xFF111111)
internal val RewardsCardTitle = Color(0xFF151515)
internal val RewardsCardSubtitle = Color(0xFF343233)
internal val RewardsProgressText = Color(0xFF302E2D)
internal val RewardsLink = Color(0xFF5D7120)
internal val RewardsBorder = Color(0xFFEAE4DA)
internal val RewardsBackBorder = Color(0xFFE8E2D8)
internal val RewardsRedesignGreen = Color(0xFF8DA324)
internal val RewardsTrack = Color(0xFFE7DFDA)
internal val RewardsHeroTitle = Color(0xFF111111)
internal val RewardsHeroText = Color(0xFF2E2C2A)

internal fun rewardsProgressLabel(card: RewardsCardModel?) = card?.progressLabel?.replace(" of ", "/") ?: "0/1"

internal fun rewardsStateLabel(card: RewardsCardModel?) = card?.stateLabel ?: "Ready when you are"

internal fun rewardsVisualDescription(spec: RewardsVisualCardSpec, card: RewardsCardModel?) = listOf(
    spec.copy.title,
    spec.copy.subtitle,
    card?.description ?: spec.copy.subtitle,
    rewardsProgressLabel(card),
    rewardsStateLabel(card),
).joinToString(". ")

internal fun rewardIllustration(id: RewardId) = when (id) {
    RewardId.PROTEIN_ALL_DAY -> Res.drawable.reward_protein
    RewardId.FATS_PRESENT -> Res.drawable.reward_healthy_fats
    RewardId.FIBER_PRESENT -> Res.drawable.reward_fiber
    RewardId.COLORS_OF_DAY -> Res.drawable.reward_colors
    RewardId.THIRTY_PRODUCTS -> Res.drawable.reward_30_foods
    RewardId.FERMENTED_THREE_DAYS -> Res.drawable.reward_culture_keeper
    RewardId.SEVEN_DAYS_RECORDED -> Res.drawable.reward_7_day_rhythm
}
