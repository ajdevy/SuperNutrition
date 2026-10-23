package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** Both visible garden and glass use this same measured source rectangle. */
internal class HomeBackdropState {
    var gardenBounds by mutableStateOf(Rect.Zero)
}
