package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RewardDetailsScreen(
    card: RewardsCardModel,
    onBack: () -> Unit,
    modifier: Modifier,
    context: RewardsDetailsContext,
) {
    val screenCopy = context.copy
    val referenceDay = context.referenceDay ?: currentHistoryReferenceDay()
    val copy = screenCopy.cards.getValue(card.id)
    Column(
        modifier.fillMaxSize().background(ProfileBackground).padding(horizontal = 16.dp).padding(top = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MainContentMaxWidth).fillMaxWidth()) {
            DiaryHeader("", UiAutomationIds.REWARD_DETAILS_BACK, onBack, screenCopy.back)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = LocalMainNavigationOverlap.current + 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RewardDetailsHero(card, copy, screenCopy)
                DiaryCard(Modifier.automationId(UiAutomationIds.REWARD_DETAILS_PROGRESS)) {
                    DiaryBody(copy.progressTitle)
                    DiaryHeading(card.progressLabel)
                    RewardDetailsProgress(card.progressFraction)
                    RewardDetailsEvidence(card.id, context.confirmedFoods, referenceDay, screenCopy, context.savedMeals)
                }
                DiaryHeading(screenCopy.howItWorks, Modifier.automationId(UiAutomationIds.REWARD_DETAILS_HOW))
                DiaryCard { copy.rules.forEach { DiaryBody("• $it") } }
                DiaryHeading(screenCopy.whatCounts, Modifier.automationId(UiAutomationIds.REWARD_DETAILS_FOODS))
                DiaryCard {
                    copy.groups.forEach { (title, examples) ->
                        DiaryHeading(title)
                        DiaryBody(examples, secondary = true)
                    }
                }
                DiaryBody(
                    if (card.id == RewardId.SEVEN_DAYS_RECORDED) {
                        screenCopy.recordingDescription
                    } else {
                        screenCopy.exampleDescription
                    },
                    secondary = true,
                )
            }
        }
    }
}

@Composable
private fun RewardDetailsHero(card: RewardsCardModel, copy: RewardDetailCopy, screenCopy: RewardDetailsScreenCopy) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painterResource(currentRewardArtwork(card.id)),
            null,
            Modifier.size(120.dp),
            colorFilter = if (card.isEarned) {
                null
            } else {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.25f) })
            },
        )
        DiaryBody(copy.category, secondary = true)
        Text(
            card.title,
            color = ProfileInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.automationId(UiAutomationIds.REWARD_DETAILS_TITLE),
        )
        DiaryBody(card.description, secondary = true)
        Box(Modifier.background(Color(0xFFE1F3DA), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)) {
            DiaryBody(if (card.isEarned) card.stateLabel else screenCopy.inProgress)
        }
    }
}

@Composable
private fun RewardDetailsProgress(rawFraction: Float) {
    val fraction = rawFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFE7DFDA))) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(Color(0xFF7AB462)))
    }
}
