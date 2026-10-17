package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.icon_bean_vector
import com.supernutrition.feature.photoanalysis.resources.icon_droplet_vector
import com.supernutrition.feature.photoanalysis.resources.icon_footprints_vector
import com.supernutrition.feature.photoanalysis.resources.icon_leaf_today_vector
import com.supernutrition.feature.photoanalysis.resources.icon_leaf_vector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// The original Figma metric track is 151.75dp wide minus 9dp/10dp inner padding.
private const val DESIGN_METRIC_TRACK_WIDTH = 132.75f

@Composable
internal fun HomeRhythmCard(model: HomeScreenModel, compact: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().automationId(UiAutomationIds.HOME_SUMMARY)
            .semantics { contentDescription = homeRhythmDescription(model) }
            .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color(0x1F594D3D))
            .clip(RoundedCornerShape(24.dp)).background(HomeCream)
            .padding(if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        HomeRhythmHeading(model)
        HomeRhythmGrid(model, compact)
    }
}

@Composable
private fun HomeRhythmGrid(model: HomeScreenModel, compact: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth < 248.dp || LocalDensity.current.fontScale > 1.2f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            homeRhythmItems(model).chunked(columnCount).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { item ->
                        HomeMetricCard(
                            item.metric,
                            item.appearance,
                            Modifier.weight(1f).fillMaxHeight().automationId(item.automationId),
                            compact,
                        )
                    }
                }
            }
        }
    }
}

private fun homeRhythmItems(model: HomeScreenModel) = listOf(
    HomeRhythmItem(
        model.fiber,
        HomeMetricAppearance(
            Res.drawable.icon_leaf_today_vector,
            Color(0xFFDCEBD3),
            Color(0xFF7CB342),
            61f / DESIGN_METRIC_TRACK_WIDTH,
        ),
        UiAutomationIds.HOME_METRIC_FIBER,
    ),
    HomeRhythmItem(
        model.water,
        HomeMetricAppearance(
            Res.drawable.icon_droplet_vector,
            Color(0xFFD9EAF7),
            Color(0xFF3B8FD4),
            77f / DESIGN_METRIC_TRACK_WIDTH,
        ),
        UiAutomationIds.HOME_METRIC_WATER,
    ),
    HomeRhythmItem(
        model.protein,
        HomeMetricAppearance(
            Res.drawable.icon_bean_vector,
            Color(0xFFEADCF5),
            Color(0xFF8B4FD1),
            102f / DESIGN_METRIC_TRACK_WIDTH,
        ),
        UiAutomationIds.HOME_METRIC_PROTEIN,
    ),
    HomeRhythmItem(
        model.movement,
        HomeMetricAppearance(
            Res.drawable.icon_footprints_vector,
            Color(0xFFFBE3D0),
            Color(0xFFF2812F),
            91f / DESIGN_METRIC_TRACK_WIDTH,
        ),
        UiAutomationIds.HOME_METRIC_MOVEMENT,
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
    "${model.fiber.label} ${model.fiber.value}",
    "${model.water.label} ${model.water.value}",
    "${model.protein.label} ${model.protein.value}",
    "${model.movement.label} ${model.movement.value}",
).joinToString(". ")

@Composable
private fun HomeRhythmHeading(model: HomeScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(Res.drawable.icon_leaf_vector), null, Modifier.size(13.dp))
            Text(
                text = model.rhythmTitle,
                color = HomeBody,
                fontFamily = nunitoFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
            )
        }
        Text(
            text = model.rhythmSubtitle,
            color = HomeInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun HomeMetricCard(
    metric: HomeMetricModel,
    appearance: HomeMetricAppearance,
    modifier: Modifier,
    compact: Boolean,
) {
    Column(
        modifier = modifier.heightIn(min = if (compact) 54.dp else 60.dp)
            .clip(RoundedCornerShape(14.dp)).background(HomeCard)
            .border(1.dp, HomeLine, RoundedCornerShape(14.dp))
            .padding(if (compact) 8.dp else 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.padding(bottom = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(25.dp).clip(CircleShape).background(appearance.iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(appearance.icon), null, Modifier.size(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = metric.label,
                    color = HomeBody,
                    fontFamily = nunitoFont(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                HomeMetricValue(metric.value)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFFE7E4E0))) {
            Box(Modifier.fillMaxWidth(appearance.progressFraction).height(4.dp).background(appearance.accent))
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
                        fontSize = 9.sp,
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
        fontSize = 13.5.sp,
        lineHeight = 17.sp,
    )
}

private data class HomeMetricAppearance(
    val icon: DrawableResource,
    val iconBackground: Color,
    val accent: Color,
    val progressFraction: Float,
)
