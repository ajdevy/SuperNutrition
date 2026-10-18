package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.supernutrition.core.ui.UiAutomationIds

@Composable
internal fun RewardsReadableCard(spec: RewardsVisualCardSpec, card: RewardsCardModel?, scale: Float) {
    val earnedPosition = spec.index < 4
    spec.medalContainerSize = if (earnedPosition) 60f else 56f
    val shape = RoundedCornerShape(14f.scaled(scale))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (if (earnedPosition) 80f else 74f).scaled(scale))
            .shadow(2f.scaled(scale), shape, spotColor = Color(0x184B3C30))
            .clip(shape)
            .background(RewardsCardBackground)
            .border(1f.scaled(scale), RewardsBorder, shape)
            .automationId(UiAutomationIds.rewardsRow(spec.index))
            .semantics {
                contentDescription = rewardsVisualDescription(spec, card)
                stateDescription = rewardsStateLabel(card)
            }
            .padding(8f.scaled(scale)),
        horizontalArrangement = Arrangement.spacedBy(10f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RewardsMedalImage(spec, scale)
        RewardsReadableCardCopy(spec, card, scale, Modifier.weight(1f))
        RewardsStateImage(spec.index, card, scale, Modifier.align(Alignment.Top))
    }
}

@Composable
private fun RewardsReadableCardCopy(
    spec: RewardsVisualCardSpec,
    card: RewardsCardModel?,
    scale: Float,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4f.scaled(scale))) {
        Text(
            text = spec.copy.title,
            style = rewardsCardTitleStyle(scale),
            modifier = Modifier.automationId(UiAutomationIds.rewardsRowTitle(spec.index)),
        )
        if (spec.index < 4) {
            Text(
                text = spec.copy.subtitle,
                style = rewardsCardSubtitleStyle(scale),
            )
            Box(Modifier.size(1f.scaled(scale)).automationId(UiAutomationIds.rewardsRowProgress(spec.index)))
        } else {
            RewardsReadableCardProgress(spec, card, scale)
        }
    }
}

@Composable
private fun RewardsReadableCardProgress(spec: RewardsVisualCardSpec, card: RewardsCardModel?, scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RewardsReadableProgressTrack(card?.progressFraction ?: 0f, scale, Modifier.weight(1f))
        Text(
            text = rewardsProgressLabel(card),
            style = rewardsCardSubtitleStyle(scale).copy(color = RewardsProgressText),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).automationId(UiAutomationIds.rewardsRowProgress(spec.index)),
        )
    }
}

@Composable
internal fun RewardsReadableProgressTrack(progress: Float, scale: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3f.scaled(scale))
    Box(modifier.fillMaxWidth().height(5f.scaled(scale)).clip(shape).background(RewardsTrack)) {
        Box(Modifier.fillMaxWidth(progress).height(5f.scaled(scale)).clip(shape).background(RewardsRedesignGreen))
    }
}
