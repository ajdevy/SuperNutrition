package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
internal data class HomeLayoutSettings(
    val viewportHeight: Dp,
    val navigationOverlap: Dp,
    val preferredMascotHeight: Dp,
    val contentMaxWidth: Dp,
)
