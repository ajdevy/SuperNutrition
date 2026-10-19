package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Preserve the original 16.2pt foreground overlap of the 182.8pt mascot.
private const val MASCOT_PANEL_OVERLAP_FRACTION = 16.2f / 182.8f
private const val HEADER_INDEX = 0
private const val CARE_INDEX = 1
private const val MASCOT_INDEX = 2
private const val RHYTHM_INDEX = 3
private const val NEXT_STEP_INDEX = 4

@Immutable
internal data class HomeLayoutSettings(
    val viewportHeight: Dp,
    val navigationOverlap: Dp,
    val preferredMascotHeight: Dp,
    val contentMaxWidth: Dp,
)

/** Reserves natural controls and fixed section spacing before assigning decorative mascot height. */
@Composable
internal fun HomeMeasuredLayout(modifier: Modifier, settings: HomeLayoutSettings, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val naturalConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val header = measurables[HEADER_INDEX].measure(naturalConstraints)
        val care = measurables[CARE_INDEX].measure(naturalConstraints)
        val rhythm = measurables[RHYTHM_INDEX].measure(naturalConstraints)
        val nextStep = measurables[NEXT_STEP_INDEX].measure(naturalConstraints)
        val plan = homeVerticalPlan(
            fixedHeight = header.height + care.height + rhythm.height + nextStep.height,
            settings = settings,
        )
        val mascot = measurables[MASCOT_INDEX].measure(Constraints.fixed(constraints.maxWidth, plan.mascotHeight))
        val width = constraints.maxWidth
        layout(width, constraints.constrainHeight(plan.contentHeight)) {
            var y = plan.verticalPadding
            header.placeRelative((width - header.width) / 2, y)
            y += header.height + plan.itemGap
            care.placeRelative((width - care.width) / 2, y)
            y += care.height + plan.itemGap
            mascot.placeRelative(0, y)
            y += mascot.height - plan.mascotOverlap
            rhythm.placeRelative((width - rhythm.width) / 2, y)
            y += rhythm.height + plan.itemGap
            nextStep.placeRelative((width - nextStep.width) / 2, y)
        }
    }
}

private fun MeasureScope.homeVerticalPlan(fixedHeight: Int, settings: HomeLayoutSettings): HomeVerticalPlan {
    val viewport = settings.viewportHeight.roundToPx()
    val navigation = settings.navigationOverlap.roundToPx()
    val padding = HomePagePadding.roundToPx()
    val gap = HomeSectionGap.roundToPx()
    val minimumMascot = 120.dp.roundToPx()
    val preferredMascot = settings.preferredMascotHeight.roundToPx()
    val reservedHeight = navigation + fixedHeight + padding * 2 + gap * 3
    val mascotBudget = viewport - reservedHeight
    val fits = mascotBudget >= minimumMascot - mascotOverlap(minimumMascot)
    // A no-fit viewport keeps the preceding readable composition rather than squeezing information.
    val mascot = if (fits) {
        (mascotBudget / (1f - MASCOT_PANEL_OVERLAP_FRACTION)).toInt().coerceIn(minimumMascot, preferredMascot)
    } else {
        preferredMascot
    }
    val overlap = mascotOverlap(mascot)
    val naturalHeight = reservedHeight + mascot - overlap
    return HomeVerticalPlan(mascot, overlap, padding, gap, maxOf(viewport, naturalHeight))
}

private fun mascotOverlap(height: Int) = (height * MASCOT_PANEL_OVERLAP_FRACTION).roundToInt()

private data class HomeVerticalPlan(
    val mascotHeight: Int,
    val mascotOverlap: Int,
    val verticalPadding: Int,
    val itemGap: Int,
    val contentHeight: Int,
)
