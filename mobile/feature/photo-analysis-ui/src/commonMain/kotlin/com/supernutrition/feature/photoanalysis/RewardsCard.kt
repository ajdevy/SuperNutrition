package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import org.jetbrains.compose.resources.painterResource

private const val READY_REWARD_ALPHA = 0.68f

@Composable
internal fun RewardsCard(index: Int, card: RewardsCardModel, modifier: Modifier) {
    Column(
        modifier = modifier.automationId(UiAutomationIds.rewardsRow(index))
            .semantics {
                contentDescription = listOf(card.title, card.description, card.progressLabel, card.stateLabel)
                    .joinToString(". ")
                stateDescription = card.stateLabel
            }
            .clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(
                width = if (card.isEarned) 1.5.dp else 1.dp,
                color = if (card.isEarned) RewardsGreen else RewardsLine,
                shape = RoundedCornerShape(20.dp),
            ).padding(15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(rewardIllustration(card.id)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(84.dp).alpha(if (card.isEarned) 1f else READY_REWARD_ALPHA),
        )
        Text(
            text = card.title,
            color = RewardsInk,
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().automationId(UiAutomationIds.rewardsRowTitle(index)),
        )
        Text(
            text = card.description,
            color = RewardsMuted,
            fontFamily = nunitoFont(),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        RewardsCardProgress(index, card)
    }
}

@Composable
private fun RewardsCardProgress(index: Int, card: RewardsCardModel) {
    Text(
        text = card.progressLabel,
        color = RewardsTextGreen,
        fontFamily = nunitoFont(),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.automationId(UiAutomationIds.rewardsRowProgress(index)),
    )
    Text(
        text = card.stateLabel,
        color = if (card.isEarned) RewardsTextGreen else RewardsMuted,
        fontFamily = nunitoFont(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.automationId(UiAutomationIds.rewardsRowState(index)),
    )
}
