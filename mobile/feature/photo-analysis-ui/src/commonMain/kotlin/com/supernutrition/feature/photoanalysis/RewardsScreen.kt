package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

private val RewardsBackground = Color(0xFFF7F3EF)
private const val DESIGN_WIDTH = 375f
private const val DESIGN_HEIGHT = 960f

private typealias VisualCards = List<RewardsVisualCardCopy>
private typealias RewardCardsById = Map<RewardId, RewardsCardModel>

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

/** Native Compose rendering of Figma node 210:291 (Rewards / Main). */
@Composable
fun RewardsScreen(model: RewardsPresenterModel, modifier: Modifier = Modifier, onBackRequested: () -> Unit = {}) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RewardsBackground)
            .verticalScroll(scrollState)
            .automationId(UiAutomationIds.REWARDS_SCREEN)
            .semantics { contentDescription = model.title },
        contentAlignment = Alignment.TopCenter,
    ) {
        val scale = (maxWidth.value / DESIGN_WIDTH).coerceAtLeast(0.01f)
        RewardsCanvas(model, scale, onBackRequested)
    }
}

@Composable
private fun RewardsCanvas(model: RewardsPresenterModel, scale: Float, onBackRequested: () -> Unit) {
    val visualCards = model.visualCards.ifEmpty { defaultVisualCards() }
    val cardsByReward = model.cards.associateBy(RewardsCardModel::id)
    Box(
        modifier = Modifier
            .width((DESIGN_WIDTH * scale).dp)
            .height((DESIGN_HEIGHT * scale).dp),
    ) {
        RewardsHeader(model.title, model.backLabel.ifBlank { "Back" }, scale, onBackRequested)
        RewardsLevelHero(
            title = model.levelTitle.ifBlank { "Level 4 • Garden Guide" },
            summary = model.levelSummary.ifBlank { "7 rewards earned · 320 pts to next" },
            scale = scale,
        )
        RewardsSectionHeader(
            RewardsSectionHeaderSpec(model.earnedSectionTitle.ifBlank { "Earned" }).apply {
                action = model.earnedSeeAllLabel.ifBlank { "See all 7" }
                top = 254.02f
            },
            scale,
        )
        Box(
            modifier = Modifier
                .requiredSize((DESIGN_WIDTH * scale).dp, (DESIGN_HEIGHT * scale).dp)
                .automationId(UiAutomationIds.REWARDS_LIST),
        ) {
            RewardsCards(visualCards, cardsByReward, scale)
            RewardsSectionHeader(
                RewardsSectionHeaderSpec(model.inProgressSectionTitle.ifBlank { "In progress" }).apply {
                    titleAutomationId = UiAutomationIds.REWARDS_IN_PROGRESS_SECTION
                    actionAutomationId = null
                    top = 492.18f
                },
                scale,
            )
        }
    }
}

@Composable
private fun RewardsCards(visualCards: VisualCards, cardsByReward: RewardCardsById, scale: Float) {
    val earnedPositions = listOf(
        16f to 288.5f,
        193f to 288.5f,
        16f to 390f,
        193f to 390f,
    )
    visualCards.take(4).forEachIndexed { index, visualCard ->
        RewardsVisualCard(
            spec = RewardsVisualCardSpec(index, visualCard).apply {
                medal = medalFor(index)
                medalImageCropFor(index).apply {
                    medalImageScale = scale
                    medalImageOffsetXPercent = offsetXPercent
                    medalImageOffsetYPercent = offsetYPercent
                }
                left = earnedPositions[index].first
                top = earnedPositions[index].second
                height = 80f
                textLeft = 72f
                textTop = 27.47f
                subtitleTop = 45.77f
                medalContainerSize = 60f
                medalTop = 9f
            },
            card = cardsByReward[visualCard.rewardId],
            scale = scale,
        )
    }

    val progressPositions = listOf(
        16f to 527f,
        193f to 527f,
        16f to 622f,
        193f to 622f,
        16f to 717f,
        193f to 717f,
    )
    visualCards.drop(4).take(6).forEachIndexed { index, visualCard ->
        val absoluteIndex = index + 4
        RewardsVisualCard(
            spec = RewardsVisualCardSpec(absoluteIndex, visualCard).apply {
                medal = medalFor(absoluteIndex)
                medalImageCropFor(absoluteIndex).apply {
                    medalImageScale = scale
                    medalImageOffsetXPercent = offsetXPercent
                    medalImageOffsetYPercent = offsetYPercent
                }
                left = progressPositions[index].first
                top = progressPositions[index].second
                height = 74f
                textLeft = 64.5f
                textTop = 26.06f
                medalContainerSize = 56f
                medalTop = 8f
            },
            card = cardsByReward[visualCard.rewardId],
            scale = scale,
        )
    }
}

private fun defaultVisualCards() = listOf(
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

internal fun medalFor(index: Int): DrawableResource = when (index) {
    0, 9 -> Res.drawable.reward_medal_fiber_friend
    1, 7 -> Res.drawable.reward_medal_protein_pal
    2, 8 -> Res.drawable.reward_medal_rhythm_keeper
    3 -> Res.drawable.reward_medal_good_fats
    4 -> Res.drawable.reward_medal_culture_keeper
    5 -> Res.drawable.reward_medal_veggie_explorer
    else -> Res.drawable.reward_medal_rainbow_plate
}

private data class MedalImageCrop(
    val scale: Float,
    val offsetXPercent: Float,
    val offsetYPercent: Float,
)

private fun medalImageCropFor(index: Int): MedalImageCrop = when (index) {
    0, 9 -> MedalImageCrop(1.1421f, -7.01f, -7.19f)
    1, 7 -> MedalImageCrop(1.2367f, -11.64f, -12.43f)
    2, 8 -> MedalImageCrop(1.2139f, -10.65f, -10.94f)
    3 -> MedalImageCrop(1.2234f, -11.22f, -11.12f)
    4 -> MedalImageCrop(1.139f, -6.95f, -6.99f)
    5 -> MedalImageCrop(1.1742f, -8.38f, -8.52f)
    else -> MedalImageCrop(1.227f, -11.35f, -11.25f)
}
