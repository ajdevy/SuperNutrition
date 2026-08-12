package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/** Renders the process-ephemeral native preview supplied by [AndroidRetainedPhotoPreviewStore]. */
@Composable
internal fun AndroidRetainedPhotoBackdrop(preview: AndroidRetainedPhotoPreview) {
    if (!preview.bitmap.isRecycled) {
        Image(
            bitmap = preview.bitmap.asImageBitmap(),
            contentDescription = null,
            // PreviewView.ScaleType.FILL_CENTER uses the same center-crop
            // geometry while the camera is live, so capture does not jump when
            // the bitmap replaces the preview in the stable photo slot.
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
