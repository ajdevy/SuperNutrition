package com.supernutrition.feature.photoanalysis

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_home_sprite
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

private const val MASCOT_FRAME_COUNT = 71
private const val MASCOT_COLUMNS = 9
private const val MASCOT_FPS = 12.0

/** One native timeline crops the exported sheet; reduced motion holds its first frame. */
@Composable
internal fun HomeAnimatedMascot(description: String) {
    val backdrop = LocalHomeBackdrop.current
    val sheet = imageResource(Res.drawable.refresh_home_sprite)
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val frame by produceState(0, scale) {
        value = 0
        if (scale <= 0f || !scale.isFinite()) return@produceState
        val start = withFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                value = homeMascotFrame(now - start, scale)
            }
        }
    }
    Canvas(
        Modifier.semantics { contentDescription = description }.onGloballyPositioned {
            backdrop?.mascotBounds = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        },
    ) {
        val height = size.height
        val width = height * 120.5f / 162.5f
        val left = (size.width - width) / 2
        val contact = Offset(size.width / 2f, height * HOME_MASCOT_CONTACT_FRACTION - height * 0.006f)
        val shadowRadius = width * 0.43f
        scale(1f, 0.11f, contact) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x48452D1D), Color.Transparent), contact, shadowRadius),
                radius = shadowRadius,
                center = contact,
            )
        }
        // The sheet is exported at 2x; each cell includes a 4dp transparent stride gap.
        val sourceX = (frame % MASCOT_COLUMNS * 249f).roundToInt()
        val sourceY = (frame / MASCOT_COLUMNS * 333f).roundToInt()
        clipRect(left, 0f, left + width, height) {
            drawImage(
                sheet,
                srcOffset = IntOffset(sourceX, sourceY),
                srcSize = IntSize(241, 325),
                dstOffset = IntOffset(left.roundToInt(), 0),
                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            )
        }
    }
}

/** Same frame schedule drives rendering and its boundary verification. */
internal fun homeMascotFrame(elapsedNanos: Long, motionScale: Float): Int =
    if (motionScale <= 0f || !motionScale.isFinite()) {
        0
    } else {
        (
            (
                elapsedNanos.coerceAtLeast(
                    0,
                ) / 1_000_000_000.0 * MASCOT_FPS / motionScale
                ).toLong() % MASCOT_FRAME_COUNT
            ).toInt()
    }
