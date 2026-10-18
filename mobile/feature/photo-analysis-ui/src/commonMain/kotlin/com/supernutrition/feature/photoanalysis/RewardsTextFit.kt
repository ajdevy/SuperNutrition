package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

/** Keeps Figma geometry only when the actual visual copy fits its fixed text frames. */
@Composable
internal fun needsReadableRewardsCanvas(model: RewardsPresenterModel, scale: Float): Boolean {
    val density = LocalDensity.current
    if (density.fontScale > 1f) return true
    val measurer = rememberTextMeasurer()
    val titleStyle = rewardsCardTitleStyle(scale)
    val subtitleStyle = rewardsCardSubtitleStyle(scale)
    val titleWidth = with(density) { 94f.scaled(scale).roundToPx() }
    val subtitleWidth = with(density) { 91f.scaled(scale).roundToPx() }
    return model.visualCards.ifEmpty { defaultVisualCards() }.take(10).withIndex().any { (index, copy) ->
        !measurer.fitsRewardsText(copy.title, titleStyle, titleWidth) ||
            (index < 4 && !measurer.fitsRewardsText(copy.subtitle, subtitleStyle, subtitleWidth))
    }
}

private fun TextMeasurer.fitsRewardsText(copy: String, style: TextStyle, width: Int): Boolean {
    val layout = measure(text = copy, style = style, softWrap = false, maxLines = 1)
    return layout.lineCount == 1 && !layout.isLineEllipsized(0) &&
        layout.getLineEnd(0, visibleEnd = true) == copy.length &&
        layout.getLineRight(0) - layout.getLineLeft(0) <= width + 1f
}
