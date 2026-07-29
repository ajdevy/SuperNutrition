package com.supernutrition.platform.photoandroid

import androidx.exifinterface.media.ExifInterface
import com.supernutrition.domain.photoanalysis.ImageOrientation
import java.io.ByteArrayInputStream

/** Uses AndroidX for EXIF parsing; malformed or non-JPEG input is upright. */
internal fun ByteArray.exifOrientation(): ImageOrientation = runCatching {
    val orientation = ByteArrayInputStream(this).use { input ->
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> ImageOrientation.RIGHT
        ExifInterface.ORIENTATION_ROTATE_180 -> ImageOrientation.DOWN
        ExifInterface.ORIENTATION_ROTATE_270 -> ImageOrientation.LEFT
        else -> ImageOrientation.UP
    }
}.getOrDefault(ImageOrientation.UP)
