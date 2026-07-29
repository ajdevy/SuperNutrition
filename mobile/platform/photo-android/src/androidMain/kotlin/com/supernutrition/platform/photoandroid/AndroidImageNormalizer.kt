package com.supernutrition.platform.photoandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.supernutrition.core.config.PhotoNormalizationDefaults
import com.supernutrition.core.config.withoutJpegApplicationMetadata
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoInputNormalizer
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RawPhoto
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Decodes and re-encodes one image so EXIF/GPS/non-essential metadata cannot
 * travel into the common Photo Input. Orientation is supplied by the native
 * metadata reader and applied to pixels before the JPEG is emitted.
 */
class AndroidImageNormalizer(
    private val maxDimension: Int = PhotoNormalizationDefaults.MAX_DIMENSION,
) {
    private val common = PhotoInputNormalizer(digest = ::sha256)

    @JvmOverloads
    fun normalize(
        ref: PhotoRef,
        sourceBytes: ByteArray,
        sourceOrientation: ImageOrientation = sourceBytes.exifOrientation(),
    ): NormalizedPhotoInput {
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: error("Unable to decode captured image")
        val oriented = rotate(decoded, sourceOrientation)
        val bounded = bound(oriented)
        return try {
            val output = ByteArrayOutputStream()
            check(bounded.compress(Bitmap.CompressFormat.JPEG, PhotoNormalizationDefaults.JPEG_QUALITY, output)) {
                "Unable to re-encode captured image"
            }
            common.normalize(
                RawPhoto(
                    ref = ref,
                    bytes = output.toByteArray().withoutJpegApplicationMetadata(),
                    width = bounded.width,
                    height = bounded.height,
                    orientation = ImageOrientation.UP,
                    colorSpace = PhotoNormalizationDefaults.COLOR_SPACE,
                    metadataKeys = PhotoNormalizationDefaults.METADATA_KEYS,
                ),
            )
        } finally {
            if (!bounded.isRecycled) bounded.recycle()
            if (oriented !== bounded && !oriented.isRecycled) oriented.recycle()
            if (decoded !== bounded && decoded !== oriented && !decoded.isRecycled) decoded.recycle()
        }
    }

    private fun rotate(bitmap: Bitmap, orientation: ImageOrientation): Bitmap {
        val degrees = when (orientation) {
            ImageOrientation.UP -> 0f
            ImageOrientation.RIGHT -> 90f
            ImageOrientation.DOWN -> 180f
            ImageOrientation.LEFT -> 270f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bound(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * scale).toInt()),
            maxOf(1, (bitmap.height * scale).toInt()),
            true,
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte ->
            byte.toInt().and(0xff).toString(16).padStart(2, '0')
        }
}
