package com.supernutrition.platform.photoandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
class AndroidImageNormalizer(private val maxDimension: Int = 4096) {
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
        val output = ByteArrayOutputStream()
        check(bounded.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
            "Unable to re-encode captured image"
        }
        val bytes = output.toByteArray()
        return PhotoInputNormalizer(digest = ::sha256).normalize(
            RawPhoto(
                ref = ref,
                bytes = bytes,
                width = bounded.width,
                height = bounded.height,
                orientation = ImageOrientation.UP,
                colorSpace = "sRGB",
                metadataKeys = emptySet(),
            ),
        )
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
