package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_home_garden
import org.jetbrains.compose.resources.imageResource
import kotlin.math.ceil
import kotlin.math.floor

internal val LocalHomeBackdrop = staticCompositionLocalOf<HomeBackdropState?> { null }

@Composable
internal fun WithHomeBackdrop(content: @Composable () -> Unit) {
    val inherited = LocalHomeBackdrop.current
    val state = inherited ?: remember { HomeBackdropState() }
    CompositionLocalProvider(LocalHomeBackdrop provides state, content = content)
}

@Composable
internal fun HomeGardenBackground(modifier: Modifier) {
    val state = LocalHomeBackdrop.current
    HomeGardenLayer(
        modifier.onGloballyPositioned {
            state?.gardenBounds = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        },
        sourceBounds = { state?.gardenBounds },
        targetPosition = { state?.gardenBounds?.topLeft ?: Offset.Zero },
        mascotBounds = { state?.mascotBounds },
    )
}

@Composable
internal fun HomeGlassBackdrop(modifier: Modifier) {
    val state = LocalHomeBackdrop.current
    var position by remember { mutableStateOf(Offset.Zero) }
    HomeGardenLayer(
        modifier.onGloballyPositioned { position = it.positionInRoot() }
            .blur(15.dp, BlurredEdgeTreatment.Unbounded),
        sourceBounds = { state?.gardenBounds },
        targetPosition = { position },
        mascotBounds = { state?.mascotBounds },
    )
}

/** The screen and background-only frosted layer draw exactly the same anchored garden. */
@Composable
private fun HomeGardenLayer(
    modifier: Modifier,
    sourceBounds: () -> Rect? = { null },
    targetPosition: () -> Offset = { Offset.Zero },
    mascotBounds: () -> Rect? = { null },
) {
    val image = imageResource(Res.drawable.refresh_home_garden)
    Canvas(modifier) {
        val source = sourceBounds()?.takeIf { it.width > 0 && it.height > 0 }
        val origin = source?.topLeft ?: targetPosition()
        val imageBounds = homeGardenImageBounds(source?.size ?: size, mascotBounds()?.translate(-origin))
        val offset = origin - targetPosition() + imageBounds.topLeft
        if (imageBounds.width > 0 && imageBounds.height > 0) {
            // Round outward so the cover transform cannot leave a one-pixel seam.
            val left = floor(offset.x).toInt()
            val top = floor(offset.y).toInt()
            drawImage(
                image,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(
                    ceil(offset.x + imageBounds.width).toInt() - left,
                    ceil(offset.y + imageBounds.height).toInt() - top,
                ),
            )
        }
    }
}
