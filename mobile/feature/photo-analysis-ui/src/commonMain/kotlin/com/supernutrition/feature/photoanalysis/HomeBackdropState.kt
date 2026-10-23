package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** Draw-only scene inputs: neither rectangle changes foreground measurement or placement. */
internal class HomeBackdropState {
    var gardenBounds by mutableStateOf(Rect.Zero)
    var mascotBounds by mutableStateOf<Rect?>(null)
}
