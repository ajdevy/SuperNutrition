package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.fraunces
import com.supernutrition.feature.photoanalysis.resources.nunito
import org.jetbrains.compose.resources.Font

internal val MainScreenGutter = 24.dp
internal val MainContentMaxWidth = 430.dp
internal val HomeBackgroundColor = Color(0xFFF6F1EA)
internal val HomeInk = Color(0xFF1E4A2E)
internal val HomeBody = Color(0xFF33302C)
internal val HomeMuted = Color(0xFFA08D80)
internal val HomeMutedText = Color(0xFF75665B)
internal val HomeCream = Color(0xFFFAF6F4)
internal val HomeCard = Color(0xFFFDFCFB)
internal val HomeLine = Color(0xFFF0EDEA)
internal val ChipGreen = Color(0xFFDDEAE3)
internal val ChipLilac = Color(0xFFEDE7F6)
internal val ChipLilacLine = Color(0xFFDFC9E5)
internal val HomeAmber = Color(0xFFD98324)
internal val HomeAmberText = Color(0xFF93520F)

@Composable
internal fun frauncesFont() = FontFamily(Font(Res.font.fraunces))

@Composable
internal fun nunitoFont() = FontFamily(Font(Res.font.nunito))
