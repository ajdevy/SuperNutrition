package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val RETAKE_WEIGHT = 158.5f
private const val ADD_WEIGHT = 174.5f
private val ActionMinimumHeight = 48.dp
private val ActionHorizontalPadding = 12.dp
private val ActionVerticalPadding = 10.dp
private val ActionGap = 10.dp

/** Measure labels at their actual weighted widths before giving both actions an equal height. */
@Composable
internal fun ReviewActions(retake: ReviewButtonSpec, add: ReviewButtonSpec, onRetake: () -> Unit, onAdd: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current.merge(reviewTextStyle(18f)).copy(textAlign = TextAlign.Center)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val available = with(density) { (maxWidth - ActionGap).roundToPx().coerceAtLeast(0) }
        val retakeWidth = (available * RETAKE_WEIGHT / (RETAKE_WEIGHT + ADD_WEIGHT)).roundToInt()
        val horizontalPadding = with(density) { (ActionHorizontalPadding * 2).roundToPx() }
        val labels = listOf(retake.label to retakeWidth, add.label to (available - retakeWidth))
        val textHeight = labels.maxOf { (label, width) ->
            measurer.measure(
                text = label,
                style = style,
                constraints = Constraints(maxWidth = (width - horizontalPadding - 1).coerceAtLeast(1)),
            ).size.height
        }
        val height = with(density) {
            maxOf(
                ActionMinimumHeight.roundToPx(),
                textHeight + (ActionVerticalPadding * 2).roundToPx(),
            ).toDp()
        }
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(ActionGap)) {
            ReviewAction(retake, style, onRetake, Modifier.weight(RETAKE_WEIGHT).fillMaxHeight())
            ReviewAction(add, style, onAdd, Modifier.weight(ADD_WEIGHT).fillMaxHeight())
        }
    }
}

@Composable
private fun ReviewAction(spec: ReviewButtonSpec, style: TextStyle, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier.heightIn(min = ActionMinimumHeight).automationId(spec.id).clip(shape)
            .background(
                if (spec.filled) {
                    ReviewGreen.copy(alpha = if (spec.enabled) 1f else 0.5f)
                } else {
                    Color(0xFF1A1915).copy(alpha = 0.92f)
                },
            )
            .border(1.dp, if (spec.filled) Color(0xFFA7AD9D) else Color(0xFFF0EEEE), shape)
            .clickable(enabled = spec.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ActionHorizontalPadding, vertical = ActionVerticalPadding),
        contentAlignment = Alignment.Center,
    ) { Text(spec.label, style = style) }
}
