package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.capybara
import com.supernutrition.feature.photoanalysis.resources.icon_badge_check
import com.supernutrition.feature.photoanalysis.resources.icon_lock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Places an exported Figma image at exact coordinates inside a clipped frame. */
@Composable
internal fun FigmaPlacedImage(
    resource: DrawableResource,
    contentScale: ContentScale,
    frame: RewardsImageFrame,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Image(
                painter = painterResource(resource),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.requiredSize(frame.imageSize.width, frame.imageSize.height),
            )
        },
        modifier = modifier
            .requiredSize(frame.containerSize.width, frame.containerSize.height)
            .clipToBounds(),
    ) { measurables, _ ->
        val containerWidth = frame.containerSize.width.roundToPx()
        val containerHeight = frame.containerSize.height.roundToPx()
        val image = measurables.single().measure(
            Constraints.fixed(frame.imageSize.width.roundToPx(), frame.imageSize.height.roundToPx()),
        )
        layout(containerWidth, containerHeight) {
            image.placeRelative(frame.imageOffset.x.roundToPx(), frame.imageOffset.y.roundToPx())
        }
    }
}

@Composable
internal fun RewardsHeroIllustration(scale: Float, modifier: Modifier = Modifier) {
    FigmaPlacedImage(
        resource = Res.drawable.capybara,
        contentScale = ContentScale.Crop,
        frame = RewardsImageFrame(
            containerSize = DpSize(170f.scaled(scale), 142f.scaled(scale)),
            imageSize = DpSize(194.6f.scaled(scale), 194.6f.scaled(scale)),
            imageOffset = DpOffset((-24.96f).scaled(scale), 7.34f.scaled(scale)),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun RewardsMedalImage(spec: RewardsVisualCardSpec, scale: Float, modifier: Modifier = Modifier) {
    FigmaPlacedImage(
        resource = spec.medal,
        contentScale = ContentScale.Crop,
        frame = RewardsImageFrame(
            containerSize = DpSize(spec.medalContainerSize.scaled(scale), spec.medalContainerSize.scaled(scale)),
            imageSize = DpSize(
                (spec.medalContainerSize * spec.medalImageScale).scaled(scale),
                (spec.medalContainerSize * spec.medalImageScale).scaled(scale),
            ),
            imageOffset = DpOffset(
                (spec.medalContainerSize * spec.medalImageOffsetXPercent / 100f).scaled(scale),
                (spec.medalContainerSize * spec.medalImageOffsetYPercent / 100f).scaled(scale),
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun RewardsStateImage(index: Int, card: RewardsCardModel?, scale: Float, modifier: Modifier = Modifier) {
    val stateIcon = if (card?.isEarned == true) Res.drawable.icon_badge_check else Res.drawable.icon_lock
    val stateSize = if (card?.isEarned == true) 20f else 22f
    Image(
        painter = painterResource(stateIcon),
        contentDescription = rewardsStateLabel(card),
        modifier = modifier
            .size(stateSize.scaled(scale))
            .automationId(UiAutomationIds.rewardsRowState(index)),
    )
}

internal fun Float.scaled(scale: Float): Dp = (this * scale).dp

internal fun Float.scaledSp(scale: Float) = (this * scale).sp
