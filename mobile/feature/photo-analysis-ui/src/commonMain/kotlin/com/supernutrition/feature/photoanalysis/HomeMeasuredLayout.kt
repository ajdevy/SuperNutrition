package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** Natural text is measured first; only decorative height shrinks on a fitting phone. */
@Composable
internal fun HomeMeasuredLayout(modifier: Modifier, settings: HomeLayoutSettings, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val natural = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val header = measurables[0].measure(natural)
        val care = measurables[1].measure(natural)
        val rhythm = measurables[3].measure(natural)
        val compact = settings.viewportHeight - settings.navigationOverlap < 620.dp
        val topPadding = (if (compact) 12.dp else 18.dp).roundToPx()
        val headerGap = (if (compact) 29.dp else 35.dp).roundToPx()
        val careGap = 5.dp.roundToPx()
        val cardGap = 18.5.dp.roundToPx()
        val bottom = settings.navigationOverlap.roundToPx() + 8.dp.roundToPx()
        val fixed = topPadding + header.height + care.height + rhythm.height + headerGap + careGap + cardGap + bottom
        val preferred = settings.preferredMascotHeight.roundToPx()
        val budget = settings.viewportHeight.roundToPx() - fixed
        val minimum = 100.dp.roundToPx()
        val mascotHeight = if (budget >= minimum) budget.coerceAtMost(preferred) else preferred
        val mascot = measurables[2].measure(Constraints.fixed(constraints.maxWidth, mascotHeight))
        val height = maxOf(settings.viewportHeight.roundToPx(), fixed + mascotHeight)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            val width = constraints.maxWidth
            var y = topPadding
            header.placeRelative(0, y)
            y += header.height + headerGap
            care.placeRelative((width - care.width) / 2 + 4.dp.roundToPx(), y)
            y += care.height + careGap
            mascot.placeRelative(0, y)
            y += mascot.height + cardGap
            rhythm.placeRelative(0, y)
        }
    }
}
