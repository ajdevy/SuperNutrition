package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.profile_back
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_check
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_colors
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_fats
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_fermented
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_fiber
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_foods
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_protein
import com.supernutrition.feature.photoanalysis.resources.refresh_reward_rhythm
import com.supernutrition.feature.photoanalysis.resources.refresh_rewards_background
import com.supernutrition.feature.photoanalysis.resources.refresh_rewards_capybara
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource

private val DailyRewardIds = listOf(
    RewardId.PROTEIN_ALL_DAY,
    RewardId.FATS_PRESENT,
    RewardId.FIBER_PRESENT,
    RewardId.COLORS_OF_DAY,
)
private val WeeklyRewardIds = listOf(
    RewardId.SEVEN_DAYS_RECORDED,
    RewardId.THIRTY_PRODUCTS,
    RewardId.FERMENTED_THREE_DAYS,
)

@Composable
internal fun CurrentRewardsScreen(
    model: RewardsPresenterModel,
    modifier: Modifier,
    onBackRequested: () -> Unit,
    copy: RewardsScreenCopy,
    onRewardRequested: (RewardId) -> Unit,
) {
    val byId = model.cards.associateBy { it.id }
    val daily = DailyRewardIds.mapNotNull(byId::get)
    val weekly = WeeklyRewardIds.mapNotNull(byId::get)
    Box(
        modifier.fillMaxSize().background(RewardsPaper).automationId(UiAutomationIds.REWARDS_SCREEN)
            .semantics {
                contentDescription = model.title
                customActions = listOf(
                    CustomAccessibilityAction(model.backLabel.ifBlank { "Back" }) {
                        onBackRequested()
                        true
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).automationId(UiAutomationIds.REWARDS_LIST),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .padding(top = 7.dp, bottom = LocalMainNavigationOverlap.current + 16.dp),
            ) {
                CurrentRewardsHeader(model, onBackRequested)
                Spacer(Modifier.height(20.dp))
                CurrentRewardsSummary(daily, weekly, copy)
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrentRewardGroup(copy.daily, daily, copy, 0, onRewardRequested)
                    CurrentRewardGroup(copy.weekly, weekly, copy, DailyRewardIds.size, onRewardRequested)
                }
            }
        }
    }
}

@Composable
private fun CurrentRewardsHeader(model: RewardsPresenterModel, onBackRequested: () -> Unit) {
    val standalone = LocalMainNavigationOverlap.current == 0.dp
    Box(Modifier.fillMaxWidth().heightIn(min = if (standalone) 48.dp else 0.dp)) {
        Text(
            model.title,
            color = Color(0xFF111111),
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            modifier = Modifier.align(Alignment.Center).padding(
                horizontal = if (standalone) 64.dp else 16.dp,
                vertical = 9.dp,
            )
                .automationId(UiAutomationIds.REWARDS_TITLE),
        )
        if (standalone) {
            val label = model.backLabel.ifBlank { "Back" }
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onBackRequested)
                    .semantics { contentDescription = label }.automationId(UiAutomationIds.REWARDS_BACK),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(Res.drawable.profile_back), null, Modifier.size(20.dp))
            }
        }
    }
}

private fun rewardCount(cards: List<RewardsCardModel>, template: String) = template
    .replace("{earned}", cards.count { it.isEarned }.toString()).replace("{total}", cards.size.toString())

@Composable
private fun CurrentRewardsSummary(
    daily: List<RewardsCardModel>,
    weekly: List<RewardsCardModel>,
    copy: RewardsScreenCopy,
) {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().automationId(UiAutomationIds.REWARDS_LEVEL_HERO)) {
        Image(
            painterResource(Res.drawable.refresh_rewards_background),
            null,
            Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
        CurrentRewardsMascot(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxWidth().heightIn(
                min = 122.dp,
            ).padding(start = 141.3.dp, end = 16.dp, top = 24.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                rewardCount(daily, copy.todaySummaryTemplate),
                color = Color(0xFF111111),
                fontFamily = frauncesFont(),
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                rewardCount(weekly, copy.weekSummaryTemplate),
                color = Color(0xFF2E2C2A),
                fontFamily = nunitoFont(),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            val all = daily + weekly
            CurrentRewardProgress(if (all.isEmpty()) 0f else all.count { it.isEarned }.toFloat() / all.size)
        }
    }
}

@Composable
private fun CurrentRewardsMascot(modifier: Modifier) {
    val artwork = imageResource(Res.drawable.refresh_rewards_capybara)
    Canvas(modifier) {
        scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
            clipRect(0f, -20.dp.toPx(), 170.dp.toPx(), size.height) {
                drawImage(
                    artwork,
                    srcSize = IntSize(artwork.width, artwork.height),
                    dstOffset = IntOffset((-24.96).dp.roundToPx(), (-22.66).dp.roundToPx()),
                    dstSize = IntSize(194.6.dp.roundToPx(), 194.6.dp.roundToPx()),
                )
            }
        }
    }
}

@Composable
private fun CurrentRewardGroup(
    title: String,
    cards: List<RewardsCardModel>,
    copy: RewardsScreenCopy,
    startIndex: Int,
    onRewardRequested: (RewardId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color(0xFF111111),
                fontFamily = nunitoFont(),
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 25.sp,
                modifier = Modifier.weight(1f).automationId(
                    if (startIndex ==
                        0
                    ) {
                        UiAutomationIds.REWARDS_EARNED_SECTION
                    } else {
                        UiAutomationIds.REWARDS_IN_PROGRESS_SECTION
                    },
                ),
            )
            Text(
                rewardCount(cards, copy.countTemplate),
                color = Color(0xFF6E6A65),
                fontFamily = nunitoFont(),
                fontSize = 14.sp,
            )
        }
        val fontScale = LocalDensity.current.fontScale
        val cardWidth = (150 * fontScale.coerceIn(1f, 1.6f)).dp
        Layout(
            content = {
                cards.forEachIndexed { index, card ->
                    CurrentRewardCard(startIndex + index, card) { onRewardRequested(card.id) }
                }
            },
            modifier = Modifier.horizontalScroll(
                rememberScrollState(),
            ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) { measurables, _ ->
            val width = cardWidth.roundToPx()
            val gap = 10.dp.roundToPx()
            val height = maxOf(164.dp.roundToPx(), measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0)
            val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
            layout(width * placeables.size + gap * (placeables.size - 1).coerceAtLeast(0), height) {
                placeables.forEachIndexed { index, child -> child.placeRelative(index * (width + gap), 0) }
            }
        }
    }
}

@Composable
private fun CurrentRewardCard(index: Int, card: RewardsCardModel, onClick: () -> Unit) {
    Column(
        Modifier.automationId(UiAutomationIds.rewardsRow(index)).clickable(
            role = Role.Button,
            onClick = onClick,
        ).semantics {
            contentDescription = listOf(
                card.title,
                card.description,
                card.progressLabel,
                card.stateLabel,
            ).joinToString(". ")
            stateDescription = card.stateLabel
        }.shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color(0x0F1C241E))
            .background(Color.White, RoundedCornerShape(20.dp)).padding(12.dp),
    ) {
        CurrentRewardIllustration(card)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                card.title,
                color = Color(0xFF151515),
                fontFamily = nunitoFont(),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                modifier = Modifier.automationId(UiAutomationIds.rewardsRowTitle(index))
                    .semantics(mergeDescendants = true) {},
            )
            Text(
                card.description,
                color = Color(0xFF343233),
                fontFamily = nunitoFont(),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        CurrentRewardFooter(index, card)
    }
}

@Composable
private fun CurrentRewardFooter(index: Int, card: RewardsCardModel) {
    Column(
        Modifier.automationId(UiAutomationIds.rewardsRowState(index)).semantics(mergeDescendants = true) {
            stateDescription = card.stateLabel
        },
    ) {
        Text(
            card.progressLabel,
            color = Color(0xFF343233),
            fontFamily = nunitoFont(),
            fontSize = 10.sp,
            lineHeight = 12.sp,
        )
        Box(
            Modifier.automationId(UiAutomationIds.rewardsRowProgress(index)).semantics(mergeDescendants = true) {
                contentDescription = card.progressLabel
            },
        ) {
            CurrentRewardProgress(card.progressFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
        }
    }
}

@Composable
private fun CurrentRewardIllustration(card: RewardsCardModel) {
    Box {
        Image(
            painterResource(currentRewardArtwork(card.id)),
            null,
            Modifier.size(56.dp).alpha(if (card.isEarned) 1f else .9f),
            colorFilter = if (card.isEarned) {
                null
            } else {
                ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(.25f) },
                )
            },
        )
        if (card.isEarned) {
            Image(
                painterResource(Res.drawable.refresh_reward_check),
                null,
                Modifier.offset(x = 40.dp, y = (-3).dp).size(22.5.dp),
            )
        }
    }
}

@Composable
private fun CurrentRewardProgress(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(5.2.dp).background(Color(0xFFE7DFDA), CircleShape).clip(CircleShape)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(5.2.dp).background(Color(0xFF7AB462)))
    }
}

internal fun currentRewardArtwork(id: RewardId) = when (id) {
    RewardId.PROTEIN_ALL_DAY -> Res.drawable.refresh_reward_protein
    RewardId.FATS_PRESENT -> Res.drawable.refresh_reward_fats
    RewardId.FIBER_PRESENT -> Res.drawable.refresh_reward_fiber
    RewardId.COLORS_OF_DAY -> Res.drawable.refresh_reward_colors
    RewardId.SEVEN_DAYS_RECORDED -> Res.drawable.refresh_reward_rhythm
    RewardId.THIRTY_PRODUCTS -> Res.drawable.refresh_reward_foods
    RewardId.FERMENTED_THREE_DAYS -> Res.drawable.refresh_reward_fermented
}
