package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** Keeps the same focused field while its scroll ancestor changes with the IME viewport. */
internal class PhotoAnalysisReviewNoteFocus {
    val focusRequester = FocusRequester()
    val noteRequester = BringIntoViewRequester()
    val footerRequester = BringIntoViewRequester()
    var focused by mutableStateOf(false)
    var footerHeightPx by mutableIntStateOf(0)
}

@Composable
internal fun KeepFocusedReviewNoteVisible(state: PhotoAnalysisReviewNoteFocus, viewportHeight: Dp) {
    val viewportHeightPx = with(LocalDensity.current) { viewportHeight.roundToPx() }
    LaunchedEffect(state.focused, viewportHeightPx, state.footerHeightPx) {
        if (state.focused) {
            // The new scroll modifier must be placed before it can receive a relocation request.
            withFrameNanos { }
            val requester = if (state.footerHeightPx <= viewportHeightPx) {
                state.footerRequester
            } else {
                state.noteRequester
            }
            requester.bringIntoView()
        }
    }
}
