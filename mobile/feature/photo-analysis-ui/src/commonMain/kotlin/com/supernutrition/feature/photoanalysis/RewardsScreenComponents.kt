package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.icon_chevron_left
import com.supernutrition.feature.photoanalysis.resources.nunito
import com.supernutrition.feature.photoanalysis.resources.rewards_background
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RewardsHeader(title: String, backLabel: String, scale: Float, onBackRequested: () -> Unit) {
    val visualSize = 38f.scaled(scale)
    val hitSize = maxOf(48.dp, visualSize)
    val hitExpansion = (hitSize - visualSize) / 2
    Box(
        modifier = Modifier
            .offset(16f.scaled(scale) - hitExpansion, 56.7f.scaled(scale) - hitExpansion)
            .size(hitSize)
            .clickable(onClick = onBackRequested)
            .semantics {
                contentDescription = backLabel
                role = Role.Button
            }
            .automationId(UiAutomationIds.REWARDS_BACK),
        contentAlignment = Alignment.Center,
    ) {
        RewardsBackVisual(scale)
    }
    Box(
        modifier = Modifier
            .offset(0f.scaled(scale), 64.4f.scaled(scale))
            .width(375f.scaled(scale))
            .padding(horizontal = 64f.scaled(scale)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = title,
            color = RewardsRedesignInk,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.31f.scaledSp(scale),
            lineHeight = 24f.scaledSp(scale),
            modifier = Modifier.automationId(UiAutomationIds.REWARDS_TITLE),
        )
    }
}

@Composable
private fun RewardsBackVisual(scale: Float) {
    Box(
        modifier = Modifier
            .size(38f.scaled(scale))
            .shadow(2f.scaled(scale), CircleShape, spotColor = Color(0x1A4B3C30))
            .clip(CircleShape)
            .background(RewardsCardBackground)
            .border(1f.scaled(scale), RewardsBackBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.icon_chevron_left),
            contentDescription = null,
            modifier = Modifier.size(20f.scaled(scale)),
        )
    }
}

@Composable
internal fun RewardsLevelHero(title: String, summary: String, scale: Float) {
    Layout(
        content = {
            RewardsHeroArtwork(scale)
            Text(
                text = title,
                color = RewardsHeroTitle,
                fontFamily = FontFamily(Font(Res.font.nunito)),
                fontWeight = FontWeight.Bold,
                fontSize = 14f.scaledSp(scale),
                lineHeight = 17f.scaledSp(scale),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.offset(141.3f.scaled(scale), 28.07f.scaled(scale)),
            )
            Text(
                text = summary,
                color = RewardsHeroText,
                fontFamily = FontFamily(Font(Res.font.nunito)),
                fontSize = 10.5f.scaledSp(scale),
                lineHeight = 13f.scaledSp(scale),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.offset(141.3f.scaled(scale), 55.9f.scaled(scale)),
            )
            RewardsHeroProgress(scale)
        },
        modifier = Modifier
            .offset(16f.scaled(scale), 112.3f.scaled(scale))
            .requiredSize(343f.scaled(scale), 122f.scaled(scale))
            .shadow(
                elevation = 3f.scaled(scale),
                shape = RoundedCornerShape(16f.scaled(scale)),
                clip = false,
                spotColor = Color(0x244B3C30),
            )
            .border(1f.scaled(scale), Color(0xDDE7D2), RoundedCornerShape(16f.scaled(scale)))
            .automationId(UiAutomationIds.REWARDS_LEVEL_HERO),
    ) { measurables, _ ->
        val frameWidth = 343f.scaled(scale).roundToPx()
        val frameHeight = 122f.scaled(scale).roundToPx()
        val childConstraints = Constraints(maxWidth = frameWidth, maxHeight = frameHeight)
        val placeables = measurables.map { it.measure(childConstraints) }
        layout(frameWidth, frameHeight) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
private fun RewardsHeroArtwork(scale: Float) {
    Image(
        painter = painterResource(Res.drawable.rewards_background),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16f.scaled(scale))),
    )
    RewardsHeroIllustration(
        scale = scale,
        modifier = Modifier
            .offset(0f.scaled(scale), (-20f).scaled(scale))
            .clip(RoundedCornerShape(bottomStart = 16f.scaled(scale))),
    )
}

@Composable
private fun RewardsHeroProgress(scale: Float) {
    Box(
        modifier = Modifier
            .offset(141.3f.scaled(scale), 87.2f.scaled(scale))
            .requiredSize(155f.scaled(scale), 8.8f.scaled(scale))
            .clip(RoundedCornerShape(5f.scaled(scale)))
            .background(RewardsTrack),
    ) {
        Box(
            modifier = Modifier
                .requiredSize(96f.scaled(scale), 8.8f.scaled(scale))
                .clip(RoundedCornerShape(5f.scaled(scale)))
                .background(RewardsRedesignGreen),
        )
    }
}

@Composable
internal fun RewardsSectionHeader(spec: RewardsSectionHeaderSpec, scale: Float) {
    Row(
        modifier = Modifier
            .offset(16f.scaled(scale), spec.top.scaled(scale))
            .requiredSize(343f.scaled(scale), 21f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spec.title,
            color = RewardsRedesignInk,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontWeight = FontWeight.Bold,
            fontSize = 15.5f.scaledSp(scale),
            lineHeight = 21f.scaledSp(scale),
            modifier = Modifier.automationId(spec.titleAutomationId),
        )
        val action = spec.action
        if (action != null) {
            Text(
                text = action,
                color = RewardsLink,
                fontFamily = FontFamily(Font(Res.font.nunito)),
                fontWeight = FontWeight.Bold,
                fontSize = 11f.scaledSp(scale),
                lineHeight = 16f.scaledSp(scale),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .width(220f.scaled(scale))
                    .automationId(requireNotNull(spec.actionAutomationId)),
            )
        }
    }
}

@Composable
internal fun RewardsVisualCard(spec: RewardsVisualCardSpec, card: RewardsCardModel?, scale: Float) {
    Box(
        modifier = Modifier
            .offset(spec.left.scaled(scale), spec.top.scaled(scale))
            .requiredSize(166f.scaled(scale), spec.height.scaled(scale))
            .shadow(2f.scaled(scale), RoundedCornerShape(14f.scaled(scale)), spotColor = Color(0x184B3C30))
            .clip(RoundedCornerShape(14f.scaled(scale)))
            .background(RewardsCardBackground)
            .border(1f.scaled(scale), RewardsBorder, RoundedCornerShape(14f.scaled(scale)))
            .automationId(UiAutomationIds.rewardsRow(spec.index))
            .semantics {
                contentDescription = rewardsVisualDescription(spec, card)
                stateDescription = rewardsStateLabel(card)
            },
    ) {
        RewardsCardMedal(spec, scale)
        RewardsCardText(spec, scale)
        RewardsCardProgress(spec, card, scale)
        RewardsCardState(spec, card, scale)
    }
}

@Composable
private fun RewardsCardMedal(spec: RewardsVisualCardSpec, scale: Float) {
    RewardsMedalImage(
        spec = spec,
        scale = scale,
        modifier = Modifier
            .offset(5f.scaled(scale), spec.medalTop.scaled(scale)),
    )
}

@Composable
private fun RewardsCardText(spec: RewardsVisualCardSpec, scale: Float) {
    Text(
        text = spec.copy.title,
        style = rewardsCardTitleStyle(scale),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .offset(spec.textLeft.scaled(scale), spec.textTop.scaled(scale))
            .width(94f.scaled(scale))
            .automationId(UiAutomationIds.rewardsRowTitle(spec.index)),
    )
    if (spec.subtitleTop > 0f) {
        Text(
            text = spec.copy.subtitle,
            style = rewardsCardSubtitleStyle(scale),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .offset(spec.textLeft.scaled(scale), spec.subtitleTop.scaled(scale))
                .width(91f.scaled(scale)),
        )
    }
}

@Composable
private fun RewardsCardProgress(spec: RewardsVisualCardSpec, card: RewardsCardModel?, scale: Float) {
    val compactProgress = rewardsProgressLabel(card)
    if (spec.subtitleTop > 0f) {
        Box(
            modifier = Modifier
                .requiredSize(1f.scaled(scale))
                .automationId(UiAutomationIds.rewardsRowProgress(spec.index)),
        )
    } else {
        Box(
            modifier = Modifier
                .offset(67f.scaled(scale), 56f.scaled(scale))
                .requiredSize(50f.scaled(scale), 5f.scaled(scale))
                .clip(RoundedCornerShape(3f.scaled(scale)))
                .background(RewardsTrack),
        ) {
            Box(
                modifier = Modifier
                    .requiredSize((50f * (card?.progressFraction ?: 0f)).scaled(scale), 5f.scaled(scale))
                    .clip(RoundedCornerShape(3f.scaled(scale)))
                    .background(RewardsRedesignGreen),
            )
        }
        Text(
            text = compactProgress,
            style = rewardsCardSubtitleStyle(scale).copy(color = RewardsProgressText),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            modifier = Modifier
                .offset(106f.scaled(scale), 48f.scaled(scale))
                .width(46f.scaled(scale))
                .automationId(UiAutomationIds.rewardsRowProgress(spec.index)),
        )
    }
}

@Composable
private fun RewardsCardState(spec: RewardsVisualCardSpec, card: RewardsCardModel?, scale: Float) {
    val stateTop = if (card?.isEarned == true) 2.3f else 1.1f
    RewardsStateImage(
        index = spec.index,
        card = card,
        scale = scale,
        modifier = Modifier.offset(138f.scaled(scale), stateTop.scaled(scale)),
    )
}
