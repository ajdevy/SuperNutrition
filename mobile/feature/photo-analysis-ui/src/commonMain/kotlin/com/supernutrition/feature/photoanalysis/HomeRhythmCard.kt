package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_colors
import com.supernutrition.feature.photoanalysis.resources.refresh_fats
import com.supernutrition.feature.photoanalysis.resources.refresh_fiber
import com.supernutrition.feature.photoanalysis.resources.refresh_protein
import com.supernutrition.feature.photoanalysis.resources.refresh_sun
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeRhythmCard(model: HomeScreenModel, onViewGoals: () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1F594D3D))
            .clip(RoundedCornerShape(20.dp)),
    ) {
        HomeGlassBackdrop(Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().automationId(UiAutomationIds.HOME_SUMMARY)
                .semantics { contentDescription = homeRhythmDescription(model) }
                .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .55f))
                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeRhythmHeading(model)
            HomeRhythmGrid(model, onViewGoals)
        }
    }
}

@Composable
private fun HomeRhythmGrid(model: HomeScreenModel, onViewGoals: () -> Unit) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            homeRhythmItems(model).forEach { item ->
                HomeMetricCard(item.metric, item.appearance, Modifier.automationId(item.automationId), onViewGoals)
            }
        },
    ) { measurables, constraints ->
        val columnGap = HomeMetricGap.roundToPx()
        val rowGap = HomeMetricGap.roundToPx()
        val pairedWidth = ((constraints.maxWidth - columnGap) / 2).coerceAtLeast(0)
        // Intrinsic widths preserve complete words while allowing values to wrap naturally.
        val columnCount = if (measurables.all { it.minIntrinsicWidth(Constraints.Infinity) <= pairedWidth }) {
            2
        } else {
            1
        }
        val columnWidth = (constraints.maxWidth - columnGap * (columnCount - 1)) / columnCount
        val rowHeights = measurables.chunked(columnCount).map { row ->
            row.maxOf { it.maxIntrinsicHeight(columnWidth) }
        }
        val cards = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(columnWidth, rowHeights[index / columnCount]))
        }
        val height = rowHeights.sum() + rowGap * (rowHeights.size - 1)
        layout(constraints.maxWidth, height) {
            var y = 0
            cards.chunked(columnCount).forEachIndexed { rowIndex, row ->
                row.forEachIndexed { column, card -> card.placeRelative(column * (columnWidth + columnGap), y) }
                y += rowHeights[rowIndex] + rowGap
            }
        }
    }
}

private fun homeRhythmItems(model: HomeScreenModel) = listOf(
    HomeRhythmItem(
        model.fiber,
        HomeMetricAppearance(
            Res.drawable.refresh_fiber,
            Color(0xFFE1F3DA),
            Color(0xFF7AB462),
        ),
        UiAutomationIds.HOME_METRIC_FIBER,
    ),
    HomeRhythmItem(
        model.colors,
        HomeMetricAppearance(
            Res.drawable.refresh_colors,
            Color(0xFFF8F4E9),
            Color(0xFF6B4F9E),
        ),
        UiAutomationIds.HOME_METRIC_COLORS,
    ),
    HomeRhythmItem(
        model.protein,
        HomeMetricAppearance(
            Res.drawable.refresh_protein,
            Color(0xFFEADCF5),
            Color(0xFF8B4FD1),
        ),
        UiAutomationIds.HOME_METRIC_PROTEIN,
    ),
    HomeRhythmItem(
        model.fats,
        HomeMetricAppearance(
            Res.drawable.refresh_fats,
            Color(0xFFFDF3DF),
            Color(0xFFD98324),
        ),
        UiAutomationIds.HOME_METRIC_FATS,
    ),
)

private data class HomeRhythmItem(
    val metric: HomeMetricModel,
    val appearance: HomeMetricAppearance,
    val automationId: UiAutomationId,
)

private fun homeRhythmDescription(model: HomeScreenModel) = listOf(
    model.rhythmTitle,
    model.rhythmSubtitle,
).joinToString(". ")

@Composable
private fun HomeRhythmHeading(model: HomeScreenModel) {
    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(Res.drawable.refresh_sun), null, Modifier.size(16.dp))
            Text(
                text = model.rhythmTitle,
                color = HomeBody,
                fontFamily = nunitoFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
        }
        Text(
            text = model.rhythmSubtitle,
            color = HomeInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun HomeMetricCard(
    metric: HomeMetricModel,
    appearance: HomeMetricAppearance,
    modifier: Modifier,
    onViewGoals: () -> Unit,
) {
    Column(
        modifier = modifier.heightIn(min = 98.dp).clickable(onClick = onViewGoals)
            .clip(RoundedCornerShape(12.dp)).background(Color.White)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.padding(bottom = HomeMetricGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(appearance.iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(appearance.icon), null, Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = metric.label,
                    color = HomeBody,
                    fontFamily = nunitoFont(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                HomeMetricValue(metric.value)
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFE7E4E0))) {
            metric.progressFraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.let { progress ->
                Box(Modifier.fillMaxWidth(progress).height(6.dp).background(appearance.accent))
            }
        }
    }
}

@Composable
private fun HomeMetricValue(value: String) {
    val valueParts = value.split(" / ", limit = 2)
    val unitFont = nunitoFont()
    Text(
        text = buildAnnotatedString {
            append(valueParts.firstOrNull().orEmpty())
            if (valueParts.size == 2) {
                withStyle(
                    SpanStyle(
                        color = HomeMutedText,
                        fontFamily = unitFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                ) {
                    append(" / ${valueParts[1]}")
                }
            }
        },
        color = HomeBody,
        fontFamily = frauncesFont(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
    )
}

private data class HomeMetricAppearance(val icon: DrawableResource, val iconBackground: Color, val accent: Color)
