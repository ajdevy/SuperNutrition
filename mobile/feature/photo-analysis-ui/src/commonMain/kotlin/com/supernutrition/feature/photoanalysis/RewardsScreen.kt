package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.reward_medal_culture_keeper
import com.supernutrition.feature.photoanalysis.resources.reward_medal_fiber_friend
import com.supernutrition.feature.photoanalysis.resources.reward_medal_good_fats
import com.supernutrition.feature.photoanalysis.resources.reward_medal_protein_pal
import com.supernutrition.feature.photoanalysis.resources.reward_medal_rainbow_plate
import com.supernutrition.feature.photoanalysis.resources.reward_medal_rhythm_keeper
import com.supernutrition.feature.photoanalysis.resources.reward_medal_veggie_explorer
import org.jetbrains.compose.resources.DrawableResource

internal class RewardsSectionHeaderSpec(val title: String, var action: String? = null) {
    var titleAutomationId = UiAutomationIds.REWARDS_EARNED_SECTION
    var actionAutomationId: com.supernutrition.core.ui.UiAutomationId? = UiAutomationIds.REWARDS_EARNED_LINK
    var top = 0f
}

internal class RewardsVisualCardSpec(val index: Int, val copy: RewardsVisualCardCopy) {
    lateinit var medal: DrawableResource
    var left = 0f
    var top = 0f
    var height = 0f
    var textLeft = 0f
    var textTop = 0f
    var subtitleTop = 0f
    var medalContainerSize = 0f
    var medalTop = 0f
    var medalImageScale = 1f
    var medalImageOffsetXPercent = 0f
    var medalImageOffsetYPercent = 0f
}

/** Current MVP Daily/Weekly presentation over actual reward identities and progress. */
@Composable
fun RewardsScreen(
    model: RewardsPresenterModel,
    modifier: Modifier = Modifier,
    onBackRequested: () -> Unit = {},
    copy: RewardsScreenCopy = RewardsScreenCopy(),
    details: RewardsDetailsContext = RewardsDetailsContext(),
) {
    val rememberedFlow = rememberRewardsFlowState()
    val flowState = details.flowState ?: rememberedFlow
    val selected = model.cards.firstOrNull { it.id == flowState.selectedReward }
    if (selected != null) {
        RewardDetailsScreen(selected, { flowState.back() }, modifier, details)
    } else {
        CurrentRewardsScreen(model, modifier, onBackRequested, copy, flowState::showReward)
    }
}

internal fun defaultVisualCards() = listOf(
    RewardsVisualCardCopy(RewardId.FIBER_PRESENT, "Fiber Friend", "Fiber goal · 3 days"),
    RewardsVisualCardCopy(RewardId.PROTEIN_ALL_DAY, "Protein Pal", "Protein · 5 meals"),
    RewardsVisualCardCopy(RewardId.SEVEN_DAYS_RECORDED, "Rhythm Keeper", "7 days in a row"),
    RewardsVisualCardCopy(RewardId.FATS_PRESENT, "Good Fats", "Fats goal · 3 days"),
    RewardsVisualCardCopy(RewardId.FERMENTED_THREE_DAYS, "Culture Keeper", "Culture · 3 days"),
    RewardsVisualCardCopy(RewardId.THIRTY_PRODUCTS, "Veggie Explorer", "30 different foods"),
    RewardsVisualCardCopy(RewardId.COLORS_OF_DAY, "Rainbow Plate", "Colors goal · 5 colors"),
    RewardsVisualCardCopy(RewardId.PROTEIN_ALL_DAY, "Protein Pro", "Protein goal · 5 meals"),
    RewardsVisualCardCopy(RewardId.SEVEN_DAYS_RECORDED, "Rhythm Master", "Rhythm goal · 7 days"),
    RewardsVisualCardCopy(RewardId.FIBER_PRESENT, "Fiber Legend", "Fiber goal · 3 days"),
)

internal fun rewardsVisualCardSpec(index: Int, copy: RewardsVisualCardCopy) = RewardsVisualCardSpec(index, copy).apply {
    medal = medalFor(index)
    val crop = medalImageCropFor(index)
    medalImageScale = crop.scale
    medalImageOffsetXPercent = crop.offsetXPercent
    medalImageOffsetYPercent = crop.offsetYPercent
}

internal fun medalFor(index: Int): DrawableResource = when (index) {
    0, 9 -> Res.drawable.reward_medal_fiber_friend
    1, 7 -> Res.drawable.reward_medal_protein_pal
    2, 8 -> Res.drawable.reward_medal_rhythm_keeper
    3 -> Res.drawable.reward_medal_good_fats
    4 -> Res.drawable.reward_medal_culture_keeper
    5 -> Res.drawable.reward_medal_veggie_explorer
    else -> Res.drawable.reward_medal_rainbow_plate
}

private data class MedalImageCrop(val scale: Float, val offsetXPercent: Float, val offsetYPercent: Float)

private fun medalImageCropFor(index: Int): MedalImageCrop = when (index) {
    0, 9 -> MedalImageCrop(1.1421f, -7.01f, -7.19f)
    1, 7 -> MedalImageCrop(1.2367f, -11.64f, -12.43f)
    2, 8 -> MedalImageCrop(1.2139f, -10.65f, -10.94f)
    3 -> MedalImageCrop(1.2234f, -11.22f, -11.12f)
    4 -> MedalImageCrop(1.139f, -6.95f, -6.99f)
    5 -> MedalImageCrop(1.1742f, -8.38f, -8.52f)
    else -> MedalImageCrop(1.227f, -11.35f, -11.25f)
}
