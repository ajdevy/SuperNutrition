package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.unit.Dp
import com.supernutrition.core.ui.UiAutomationId
import org.jetbrains.compose.resources.DrawableResource

internal data class CameraArtwork(val resource: DrawableResource, val size: Dp, val artworkSize: Dp = size)

internal data class DishChoiceContent(val label: String, val description: String, val custom: Boolean = false)

internal data class ReviewButtonSpec(
    val label: String,
    val id: UiAutomationId,
    val filled: Boolean = false,
    val enabled: Boolean = true,
)
