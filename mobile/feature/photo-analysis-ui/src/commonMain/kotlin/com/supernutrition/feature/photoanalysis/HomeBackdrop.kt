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
import kotlin.math.roundToInt

private val LocalHomeBackdrop = staticCompositionLocalOf<HomeBackdropState?> { null }

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
    )
}

/** FillBounds is shared by the screen and its translated, background-only frosted layer. */
@Composable
private fun HomeGardenLayer(
    modifier: Modifier,
    sourceBounds: () -> Rect? = { null },
    targetPosition: () -> Offset = { Offset.Zero },
) {
    val image = imageResource(Res.drawable.refresh_home_garden)
    Canvas(modifier) {
        val source = sourceBounds()?.takeIf { it.width > 0 && it.height > 0 }
        val offset = if (source == null) Offset.Zero else source.topLeft - targetPosition()
        val targetSize = source?.size ?: size
        if (targetSize.width > 0 && targetSize.height > 0) {
            drawImage(
                image,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                dstSize = IntSize(targetSize.width.roundToInt(), targetSize.height.roundToInt()),
            )
        }
    }
}
