package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

private val SaveFeedbackPadding = 8.dp

@Composable
internal fun MealReviewSaveFeedback(copy: MealFormCopy) {
    Text(
        copy.saveFailed,
        style = reviewSaveFeedbackStyle(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = SaveFeedbackPadding)
            .automationId(UiAutomationIds.MEAL_REVIEW_SAVE_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Include wrapped error text when choosing between the pinned and compact scrolling footer. */
@Composable
internal fun reviewSaveFeedbackHeight(failed: Boolean, copy: MealFormCopy, width: Dp): Dp {
    if (!failed) return 0.dp
    val density = LocalDensity.current
    val height = rememberTextMeasurer().measure(
        text = copy.saveFailed,
        style = reviewSaveFeedbackStyle(),
        constraints = Constraints(maxWidth = with(density) { width.roundToPx().coerceAtLeast(1) }),
    ).size.height
    return with(density) { height.toDp() } + SaveFeedbackPadding * 2
}

@Composable
private fun reviewSaveFeedbackStyle() = reviewTextStyle(14f).copy(textAlign = TextAlign.Center)
