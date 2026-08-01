package com.supernutrition.platform.photoios

import com.supernutrition.core.config.PhotoNormalizationDefaults
import com.supernutrition.core.config.withoutJpegApplicationMetadata
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoFormat
import com.supernutrition.domain.photoanalysis.PhotoInputNormalizer
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RawPhoto
import com.supernutrition.platform.photoios.captureinterop.SNCreateBoundedImage
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.SN_SHA256
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSThread
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import platform.posix.memcpy

/** iOS-only decode/re-encode seam; native rendering remains inside the bridge. */
@OptIn(ExperimentalForeignApi::class)
class IosImageNormalizer(
    private val digest: (ByteArray) -> String = ::iosSha256,
    private val maxDimension: Int = PhotoNormalizationDefaults.MAX_DIMENSION,
) {
    private val common = PhotoInputNormalizer(maxDimension = maxDimension, digest = digest)

    /**
     * The source-shape failures, kept together and away from [normalize]. They
     * are about the bytes we were handed rather than about normalizing them.
     */
    private fun requireUsableSource(sourceData: NSData) {
        if (sourceData.length == 0uL) {
            throw PhotoPreparationException("Photo bytes must not be empty")
        }
        if (sourceData.length > MAX_SOURCE_BYTES.toULong()) {
            throw PhotoPreparationException("Photo bytes exceed the iOS source limit")
        }
    }

    private fun decodeBounded(sourceData: NSData): UIImage = SNCreateBoundedImage(sourceData, maxDimension.toUInt())
        ?: throw PhotoPreparationException("iOS could not decode the selected image")

    /** Render and encode, the two ways producing the output image can fail. */
    private fun encodeNormalized(image: UIImage, width: Double, height: Double, scale: Double): ByteArray {
        val normalizedImage = drawOnMainThread(image, width, height, scale)
            ?: throw PhotoPreparationException("iOS could not render the selected image")
        val normalizedData = UIImageJPEGRepresentation(
            image = normalizedImage,
            compressionQuality = PhotoNormalizationDefaults.JPEG_QUALITY / 100.0,
        ) ?: throw PhotoPreparationException("iOS could not encode the normalized JPEG")
        return normalizedData.toByteArray()
    }

    fun normalize(ref: PhotoRef, sourceData: NSData): NormalizedPhotoInput {
        requireUsableSource(sourceData)

        val image = decodeBounded(sourceData)
        val (sourceWidth, sourceHeight) = image.size.useContents { width to height }
        if (sourceWidth <= 0.0 || sourceHeight <= 0.0) {
            throw PhotoPreparationException("iOS image has no drawable dimensions")
        }

        val renderScale = minOf(
            PhotoNormalizationDefaults.RENDER_SCALE,
            maxDimension.toDouble() / maxOf(sourceWidth, sourceHeight),
        )
        val outputWidth = maxOf(1, (sourceWidth * renderScale).toInt())
        val outputHeight = maxOf(1, (sourceHeight * renderScale).toInt())
        val rawBytes = encodeNormalized(image, sourceWidth, sourceHeight, renderScale)

        return common.normalize(
            RawPhoto(
                ref = ref,
                bytes = rawBytes.withoutJpegApplicationMetadata(),
                width = outputWidth,
                height = outputHeight,
                format = PhotoFormat(
                    ImageOrientation.UP,
                    PhotoNormalizationDefaults.COLOR_SPACE,
                    PhotoNormalizationDefaults.METADATA_KEYS,
                ),
            ),
        )
    }

    private fun drawOnMainThread(image: UIImage, width: Double, height: Double, scale: Double): UIImage? {
        fun draw(): UIImage? {
            UIGraphicsBeginImageContextWithOptions(
                size = CGSizeMake(width, height),
                opaque = false,
                scale = scale,
            )
            return try {
                image.drawInRect(CGRectMake(0.0, 0.0, width, height))
                UIGraphicsGetImageFromCurrentImageContext()
            } finally {
                UIGraphicsEndImageContext()
            }
        }

        if (NSThread.isMainThread) return draw()
        var rendered: UIImage? = null
        dispatch_sync(dispatch_get_main_queue()) { rendered = draw() }
        return rendered
    }
}

/** Uses the platform crypto implementation instead of maintaining SHA-256 here. */
@OptIn(ExperimentalForeignApi::class)
internal fun iosSha256(bytes: ByteArray): String {
    val digest = ByteArray(SHA256_DIGEST_LENGTH)
    digest.usePinned { output ->
        if (bytes.isEmpty()) {
            SN_SHA256(null, 0u, output.addressOf(0).reinterpret())
        } else {
            bytes.usePinned { input ->
                SN_SHA256(input.addressOf(0), bytes.size.toUInt(), output.addressOf(0).reinterpret())
            }
        }
    }
    return digest.joinToString(separator = "") { byte ->
        byte.toInt().and(0xff).toString(16).padStart(2, '0')
    }
}

private const val SHA256_DIGEST_LENGTH = 32
private const val MAX_SOURCE_BYTES = 10 * 1024 * 1024

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    val result = ByteArray(byteCount)
    if (result.isEmpty()) return result
    val source = bytes?.reinterpret<ByteVar>()
        ?: throw PhotoPreparationException("iOS returned empty normalized image bytes")
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), source, byteCount.toULong())
    }
    return result
}
