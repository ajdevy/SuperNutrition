package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.icon_chevron_left
import com.supernutrition.feature.photoanalysis.resources.nunito
import com.supernutrition.feature.photoanalysis.resources.rewards_background
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

/** Enlarged system text uses measured rows; its font scale is never overridden. */
@Composable
internal fun RewardsReadableCanvas(model: RewardsPresenterModel, scale: Float, onBackRequested: () -> Unit) {
    val visualCards = model.visualCards.ifEmpty { defaultVisualCards() }
    val cardsByReward = model.cards.associateBy(RewardsCardModel::id)
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = 16f.scaled(scale),
            end = 16f.scaled(scale),
            top = 56.7f.scaled(scale),
            bottom = LocalMainNavigationOverlap.current + 16f.scaled(scale),
        ),
        verticalArrangement = Arrangement.spacedBy(20f.scaled(scale)),
    ) {
        RewardsReadableHeader(model, scale, onBackRequested)
        RewardsReadableHero(model, scale)
        RewardsReadableSectionHeader(
            RewardsSectionHeaderSpec(model.earnedSectionTitle.ifBlank { "Earned" }).apply {
                action = model.earnedSeeAllLabel.ifBlank { "See all 7" }
            },
            scale,
        )
        Column(
            modifier = Modifier.fillMaxWidth().automationId(UiAutomationIds.REWARDS_LIST),
            verticalArrangement = Arrangement.spacedBy(12f.scaled(scale)),
        ) {
            visualCards.take(10).forEachIndexed { index, copy ->
                if (index == 4) {
                    RewardsReadableSectionHeader(
                        RewardsSectionHeaderSpec(model.inProgressSectionTitle.ifBlank { "In progress" }).apply {
                            titleAutomationId = UiAutomationIds.REWARDS_IN_PROGRESS_SECTION
                            actionAutomationId = null
                        },
                        scale,
                    )
                }
                RewardsReadableCard(rewardsVisualCardSpec(index, copy), cardsByReward[copy.rewardId], scale)
            }
        }
    }
}

@Composable
private fun RewardsReadableHeader(model: RewardsPresenterModel, scale: Float, onBackRequested: () -> Unit) {
    val backSize = maxOf(48.dp, 38f.scaled(scale))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(backSize)
                .shadow(2f.scaled(scale), CircleShape, spotColor = Color(0x1A4B3C30))
                .clip(CircleShape)
                .background(RewardsCardBackground)
                .border(1f.scaled(scale), RewardsBackBorder, CircleShape)
                .clickable(onClick = onBackRequested)
                .semantics {
                    contentDescription = model.backLabel.ifBlank { "Back" }
                    role = Role.Button
                }
                .automationId(UiAutomationIds.REWARDS_BACK),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.icon_chevron_left),
                contentDescription = null,
                modifier = Modifier.size(20f.scaled(scale)),
            )
        }
        Text(
            text = model.title,
            color = RewardsRedesignInk,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.31f.scaledSp(scale),
            lineHeight = 24f.scaledSp(scale),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).automationId(UiAutomationIds.REWARDS_TITLE),
        )
        Spacer(Modifier.size(backSize))
    }
}

@Composable
private fun RewardsReadableHero(model: RewardsPresenterModel, scale: Float) {
    val shape = RoundedCornerShape(16f.scaled(scale))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3f.scaled(scale), shape, spotColor = Color(0x244B3C30))
            .clip(shape)
            .background(RewardsCardBackground)
            .border(1f.scaled(scale), RewardsBorder, shape)
            .automationId(UiAutomationIds.REWARDS_LEVEL_HERO),
    ) {
        Image(
            painter = painterResource(Res.drawable.rewards_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier.padding(12f.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(12f.scaled(scale)),
            verticalAlignment = Alignment.Bottom,
        ) {
            RewardsHeroIllustration(scale * 0.75f)
            RewardsReadableHeroCopy(model, scale, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RewardsReadableHeroCopy(model: RewardsPresenterModel, scale: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8f.scaled(scale))) {
        Text(
            text = model.levelTitle.ifBlank { "Level 4 • Garden Guide" },
            color = RewardsHeroTitle,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontWeight = FontWeight.Bold,
            fontSize = 14f.scaledSp(scale),
            lineHeight = 17f.scaledSp(scale),
        )
        Text(
            text = model.levelSummary.ifBlank { "7 rewards earned · 320 pts to next" },
            color = RewardsHeroText,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontSize = 10.5f.scaledSp(scale),
            lineHeight = 13f.scaledSp(scale),
        )
        RewardsReadableProgressTrack(96f / 155f, scale)
    }
}

@Composable
private fun RewardsReadableSectionHeader(spec: RewardsSectionHeaderSpec, scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spec.title,
            color = RewardsRedesignInk,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontWeight = FontWeight.Bold,
            fontSize = 15.5f.scaledSp(scale),
            lineHeight = 21f.scaledSp(scale),
            modifier = Modifier.weight(1f).automationId(spec.titleAutomationId),
        )
        spec.action?.let { action ->
            Text(
                text = action,
                color = RewardsLink,
                fontFamily = FontFamily(Font(Res.font.nunito)),
                fontWeight = FontWeight.Bold,
                fontSize = 11f.scaledSp(scale),
                lineHeight = 16f.scaledSp(scale),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).automationId(requireNotNull(spec.actionAutomationId)),
            )
        }
    }
}
