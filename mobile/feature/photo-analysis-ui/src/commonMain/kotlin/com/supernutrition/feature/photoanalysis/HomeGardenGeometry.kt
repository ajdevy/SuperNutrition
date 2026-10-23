package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

// The exported garden is 473x1024: its central sill ends at row452 and sand starts at455.
private val GardenImageSize = Size(473f, 1024f)
internal const val HOME_GARDEN_FLOOR_FRACTION = 455f / 1024f

// All 71 animation frames have the same opaque bottom (exclusive row319 of a325px cell).
internal const val HOME_MASCOT_CONTACT_FRACTION = 319f / 325f
private const val FLOOR_CLEARANCE_IN_MASCOT_HEIGHTS = 0.36f

/** One cover/crop rectangle ties the decorative floor to the naturally measured paws. */
internal fun homeGardenImageBounds(viewport: Size, mascotBounds: Rect?): Rect {
    if (!viewport.hasFiniteArea()) return Rect.Zero
    val coverScale = maxOf(viewport.width / GardenImageSize.width, viewport.height / GardenImageSize.height)
    val mascot = mascotBounds?.takeIf { it.size.hasFiniteArea() }
    val floor = mascot?.let {
        val contact = it.top + it.height * HOME_MASCOT_CONTACT_FRACTION
        val clearance = it.height.coerceAtMost(viewport.height) * FLOOR_CLEARANCE_IN_MASCOT_HEIGHTS
        (contact.coerceIn(0f, viewport.height) - clearance).coerceIn(0f, viewport.height)
    }
    val scale = if (floor == null) {
        coverScale
    } else {
        maxOf(
            coverScale,
            floor / (GardenImageSize.height * HOME_GARDEN_FLOOR_FRACTION),
            (viewport.height - floor) / (GardenImageSize.height * (1f - HOME_GARDEN_FLOOR_FRACTION)),
        )
    }
    val imageSize = GardenImageSize * scale
    val left = (viewport.width - imageSize.width) / 2f
    val top = floor?.minus(imageSize.height * HOME_GARDEN_FLOOR_FRACTION)
        ?: ((viewport.height - imageSize.height) / 2f)
    return Rect(Offset(left, top), imageSize)
}

private fun Size.hasFiniteArea(): Boolean = width.isFinite() && height.isFinite() && width > 0f && height > 0f
