package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.capybara
import com.supernutrition.feature.photoanalysis.resources.icon_badge_check
import com.supernutrition.feature.photoanalysis.resources.icon_chevron_left
import com.supernutrition.feature.photoanalysis.resources.icon_lock
import com.supernutrition.feature.photoanalysis.resources.nunito
import com.supernutrition.feature.photoanalysis.resources.rewards_background
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

private val RewardsCardBackground = Color(0xFFFBF9F6)
private val RewardsInk = Color(0xFF111111)
private val RewardsCardTitle = Color(0xFF151515)
private val RewardsCardSubtitle = Color(0xFF343233)
private val RewardsProgressText = Color(0xFF302E2D)
private val RewardsLink = Color(0xFF748D28)
private val RewardsBorder = Color(0xFFEAE4DA)
private val RewardsBackBorder = Color(0xFFE8E2D8)
private val RewardsGreen = Color(0xFF8DA324)
private val RewardsTrack = Color(0xFFE7DFDA)
private val RewardsHeroTitle = Color(0xFF111111)
private val RewardsHeroText = Color(0xFF2E2C2A)

@Composable
internal fun RewardsHeader(title: String, backLabel: String, scale: Float, onBackRequested: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(16f.scaled(scale), 56.7f.scaled(scale))
            .size(38f.scaled(scale))
            .shadow(2f.scaled(scale), CircleShape, spotColor = Color(0x1A4B3C30))
            .clip(CircleShape)
            .background(RewardsCardBackground)
            .border(1f.scaled(scale), RewardsBackBorder, CircleShape)
            .clickable(onClick = onBackRequested)
            .semantics {
                contentDescription = backLabel
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
        text = title,
        color = RewardsInk,
        fontFamily = FontFamily(Font(Res.font.nunito)),
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.31f.scaledSp(scale),
        lineHeight = 24f.scaledSp(scale),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .offset(147.5f.scaled(scale), 64.4f.scaled(scale))
            .requiredSize(80f.scaled(scale), 24f.scaled(scale))
            .automationId(UiAutomationIds.REWARDS_TITLE),
    )
}

@Composable
internal fun RewardsLevelHero(title: String, summary: String, scale: Float) {
    Layout(
        content = {
            Image(
                painter = painterResource(Res.drawable.rewards_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16f.scaled(scale))),
            )
            FigmaPlacedImage(
                resource = Res.drawable.capybara,
                contentScale = ContentScale.Crop,
                containerWidth = 170f.scaled(scale),
                containerHeight = 142f.scaled(scale),
                imageWidth = 194.6f.scaled(scale),
                imageHeight = 194.6f.scaled(scale),
                imageOffsetX = (-24.96f).scaled(scale),
                imageOffsetY = 7.34f.scaled(scale),
                modifier = Modifier
                    .offset(0f.scaled(scale), (-20f).scaled(scale))
                    .clip(RoundedCornerShape(bottomStart = 16f.scaled(scale))),
            )
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
                .background(RewardsGreen),
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
            color = RewardsInk,
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
    val stateLabel = card?.stateLabel ?: "Ready when you are"
    val compactProgress = card?.progressLabel?.replace(" of ", "/") ?: "0/1"
    val description = card?.description ?: spec.copy.subtitle
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
                contentDescription = listOf(
                    spec.copy.title,
                    spec.copy.subtitle,
                    description,
                    compactProgress,
                    stateLabel,
                ).joinToString(". ")
                stateDescription = stateLabel
            },
    ) {
        RewardsCardMedal(spec, scale)
        RewardsCardText(spec, scale)
        RewardsCardProgress(spec, card, scale)
        RewardsCardState(spec, card, stateLabel, scale)
    }
}

@Composable
private fun RewardsCardMedal(spec: RewardsVisualCardSpec, scale: Float) {
    FigmaPlacedImage(
        resource = spec.medal,
        contentScale = ContentScale.Crop,
        containerWidth = spec.medalContainerSize.scaled(scale),
        containerHeight = spec.medalContainerSize.scaled(scale),
        imageWidth = (spec.medalContainerSize * spec.medalImageScale).scaled(scale),
        imageHeight = (spec.medalContainerSize * spec.medalImageScale).scaled(scale),
        imageOffsetX = (spec.medalContainerSize * spec.medalImageOffsetXPercent / 100f).scaled(scale),
        imageOffsetY = (spec.medalContainerSize * spec.medalImageOffsetYPercent / 100f).scaled(scale),
        modifier = Modifier
            .offset(5f.scaled(scale), spec.medalTop.scaled(scale))
            .clipToBounds(),
    )
}

/** Places an exported Figma image at exact coordinates inside a clipped frame. */
@Composable
private fun FigmaPlacedImage(
    resource: org.jetbrains.compose.resources.DrawableResource,
    contentScale: ContentScale,
    containerWidth: Dp,
    containerHeight: Dp,
    imageWidth: Dp,
    imageHeight: Dp,
    imageOffsetX: Dp,
    imageOffsetY: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Image(
                painter = painterResource(resource),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.requiredSize(imageWidth, imageHeight),
            )
        },
        modifier = modifier
            .requiredSize(containerWidth, containerHeight)
            .clipToBounds(),
    ) { measurables, _ ->
        val containerWidthPx = containerWidth.roundToPx()
        val containerHeightPx = containerHeight.roundToPx()
        val image = measurables.single().measure(
            Constraints.fixed(imageWidth.roundToPx(), imageHeight.roundToPx()),
        )
        layout(containerWidthPx, containerHeightPx) {
            image.placeRelative(imageOffsetX.roundToPx(), imageOffsetY.roundToPx())
        }
    }
}

@Composable
private fun RewardsCardText(spec: RewardsVisualCardSpec, scale: Float) {
    Text(
        text = spec.copy.title,
        color = RewardsCardTitle,
        fontFamily = FontFamily(Font(Res.font.nunito)),
        fontWeight = FontWeight.Bold,
        fontSize = 12.5f.scaledSp(scale),
        lineHeight = 15f.scaledSp(scale),
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
            color = RewardsCardSubtitle,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontSize = 10.3f.scaledSp(scale),
            lineHeight = 13f.scaledSp(scale),
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
    val compactProgress = card?.progressLabel?.replace(" of ", "/") ?: "0/1"
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
                    .background(RewardsGreen),
            )
        }
        Text(
            text = compactProgress,
            color = RewardsProgressText,
            fontFamily = FontFamily(Font(Res.font.nunito)),
            fontSize = 10.3f.scaledSp(scale),
            lineHeight = 13f.scaledSp(scale),
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
private fun RewardsCardState(spec: RewardsVisualCardSpec, card: RewardsCardModel?, stateLabel: String, scale: Float) {
    val stateIcon = if (card?.isEarned == true) Res.drawable.icon_badge_check else Res.drawable.icon_lock
    val stateTop = if (card?.isEarned == true) 2.3f else 1.1f
    val stateSize = if (card?.isEarned == true) 20f else 22f
    Image(
        painter = painterResource(stateIcon),
        contentDescription = stateLabel,
        modifier = Modifier
            .offset(138f.scaled(scale), stateTop.scaled(scale))
            .size(stateSize.scaled(scale))
            .automationId(UiAutomationIds.rewardsRowState(spec.index)),
    )
}

private fun Float.scaled(scale: Float): Dp = (this * scale).dp

private fun Float.scaledSp(scale: Float) = (this * scale).sp
