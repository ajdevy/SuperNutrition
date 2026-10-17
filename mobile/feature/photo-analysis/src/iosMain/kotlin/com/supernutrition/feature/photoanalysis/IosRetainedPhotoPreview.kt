package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.supernutrition.domain.photoanalysis.PhotoRef
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** Process-ephemeral native display copy; it never owns source photo bytes. */
@OptIn(ExperimentalForeignApi::class)
public class IosRetainedPhotoPreview(public val ref: PhotoRef, public val image: UIImage)

/** Renders the normalized image through a native UIImageView inside Compose. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun IosRetainedPhotoBackdrop(preview: IosRetainedPhotoPreview) {
    UIKitView(
        factory = {
            UIImageView().apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                clipsToBounds = true
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { imageView -> imageView.image = preview.image },
    )
}
