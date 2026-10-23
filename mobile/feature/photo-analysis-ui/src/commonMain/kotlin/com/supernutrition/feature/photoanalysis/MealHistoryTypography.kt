package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.nunito
import org.jetbrains.compose.resources.Font

/** Use resource defaults for variations; declare the weight instead of relying on synthesis. */
@Composable
internal fun historyNunitoFont(weight: FontWeight): FontFamily = FontFamily(Font(Res.font.nunito, weight = weight))
