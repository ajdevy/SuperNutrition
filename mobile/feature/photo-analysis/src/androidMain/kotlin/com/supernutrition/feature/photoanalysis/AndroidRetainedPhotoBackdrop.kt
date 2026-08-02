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
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
