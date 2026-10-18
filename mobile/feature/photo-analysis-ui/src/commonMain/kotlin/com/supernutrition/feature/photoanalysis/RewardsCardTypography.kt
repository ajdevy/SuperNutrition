package com.supernutrition.feature.photoanalysis

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.nunito
import org.jetbrains.compose.resources.Font

/** The fixed canvas, measured canvas and fit check use the same typography. */
@Composable
internal fun rewardsCardTitleStyle(scale: Float) = rewardsCardTextStyle().copy(
    color = RewardsCardTitle,
    fontWeight = FontWeight.Bold,
    fontSize = 12.5f.scaledSp(scale),
    lineHeight = 15f.scaledSp(scale),
)

@Composable
internal fun rewardsCardSubtitleStyle(scale: Float) = rewardsCardTextStyle().copy(
    color = RewardsCardSubtitle,
    fontSize = 10.3f.scaledSp(scale),
    lineHeight = 13f.scaledSp(scale),
)

@Composable
private fun rewardsCardTextStyle(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(fontFamily = FontFamily(Font(Res.font.nunito))),
)
