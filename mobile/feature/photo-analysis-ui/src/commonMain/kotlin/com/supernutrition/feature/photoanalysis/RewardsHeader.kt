package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.rewards_hero_background
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RewardsHeader(model: RewardsPresenterModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = model.title,
            color = RewardsInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 29.sp,
            lineHeight = 32.sp,
            modifier = Modifier.automationId(UiAutomationIds.REWARDS_TITLE),
        )
        if (model.earnedSummary.isNotBlank()) RewardsEarnedSummary(model)
    }
}

@Composable
private fun RewardsEarnedSummary(model: RewardsPresenterModel) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 116.dp).clip(RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.rewards_hero_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = model.earnedSummary,
                color = RewardsGreen,
                fontFamily = frauncesFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = model.earnedSummaryLabel,
                color = RewardsInk,
                fontFamily = nunitoFont(),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
