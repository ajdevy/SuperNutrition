package com.supernutrition.platform.photoios

import cnames.structs.CGImageDestination
import cnames.structs.__CFData
import cnames.structs.__CFString
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoInputNormalizer
import com.supernutrition.domain.photoanalysis.PhotoPreparationException
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RawPhoto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.posix.memcpy

/**
 * iOS-only decode and re-encode seam. Drawing the decoded image into a fresh
 * context applies UIImage's orientation before JPEG encoding and drops the
 * source metadata dictionary, including EXIF and GPS fields.
 */
@OptIn(ExperimentalForeignApi::class)
class IosImageNormalizer(
    private val digest: (ByteArray) -> String = ::iosSha256,
    private val maxDimension: Int = 4096,
) {
    private val common = PhotoInputNormalizer(
        maxDimension = maxDimension,
        digest = digest,
    )

    fun normalize(ref: PhotoRef, sourceData: NSData): NormalizedPhotoInput {
        require(sourceData.length > 0u) { "Photo bytes must not be empty" }

        val image = UIImage.imageWithData(sourceData)
            ?: throw PhotoPreparationException("iOS could not decode the selected image")
        val (sourceWidth, sourceHeight) = image.size.useContents { width to height }
        require(sourceWidth > 0.0 && sourceHeight > 0.0) {
            "iOS image has no drawable dimensions"
        }

        val sourceMax = maxOf(sourceWidth, sourceHeight)
        val scale = minOf(1.0, maxDimension.toDouble() / sourceMax)
        val targetWidth = maxOf(1, (sourceWidth * scale).toInt())
        val targetHeight = maxOf(1, (sourceHeight * scale).toInt())

        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()),
            opaque = false,
            scale = 1.0,
        )
        val normalizedImage = try {
            image.drawInRect(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
            UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        } ?: throw PhotoPreparationException("iOS could not render the selected image")

        val outputData = checkNotNull(CFDataCreateMutable(null, 0)) {
            "iOS could not allocate the normalized image buffer"
        }
        val jpegType = checkNotNull(CFBridgingRetain(UTTypeJPEG.identifier))
            .reinterpret<__CFString>()
        val destination = CGImageDestinationCreateWithData(
            data = outputData,
            type = jpegType,
            count = 1u,
            options = null,
        ) ?: run {
            CFRelease(jpegType)
            CFRelease(outputData)
            throw PhotoPreparationException("iOS could not create a metadata-free JPEG destination")
        }
        CFRelease(jpegType)
        val rawBytes = try {
            CGImageDestinationAddImage(destination, normalizedImage.CGImage, null)
            check(CGImageDestinationFinalize(destination)) {
                "iOS could not finalize the normalized image"
            }
            outputData.toByteArray()
        } finally {
            CFRelease(destination)
            CFRelease(outputData)
        }

        return common.normalize(
            RawPhoto(
                ref = ref,
                bytes = rawBytes.stripJpegApplicationMetadata(),
                width = targetWidth,
                height = targetHeight,
                orientation = com.supernutrition.domain.photoanalysis.ImageOrientation.UP,
                colorSpace = "sRGB",
                metadataKeys = emptySet(),
            ),
        )
    }
}

/** Small platform-local SHA-256 implementation; no payload leaves the iOS bridge. */
internal fun iosSha256(bytes: ByteArray): String {
    val constants = intArrayOf(
        0x428a2f98.toInt(), 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    val state = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )
    val bitLength = bytes.size.toLong() * 8L
    val paddedSize = ((bytes.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)
    bytes.copyInto(padded)
    padded[bytes.size] = 0x80.toByte()
    repeat(8) { index ->
        padded[paddedSize - 1 - index] = (bitLength ushr (index * 8)).toByte()
    }

    for (blockStart in padded.indices step 64) {
        val words = IntArray(64)
        for (index in 0 until 16) {
            val offset = blockStart + index * 4
            words[index] = ((padded[offset].toInt() and 0xff) shl 24) or
                ((padded[offset + 1].toInt() and 0xff) shl 16) or
                ((padded[offset + 2].toInt() and 0xff) shl 8) or
                (padded[offset + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val first = words[index - 15]
            val second = words[index - 2]
            val small0 = rotateRight(first, 7) xor rotateRight(first, 18) xor (first ushr 3)
            val small1 = rotateRight(second, 17) xor rotateRight(second, 19) xor (second ushr 10)
            words[index] = words[index - 16] + small0 + words[index - 7] + small1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (index in 0 until 64) {
            val big1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val choose = (e and f) xor (e.inv() and g)
            val temp1 = h + big1 + choose + constants[index] + words[index]
            val big0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = big0 + majority
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    val hex = "0123456789abcdef"
    return buildString(64) {
        state.forEach { value ->
            for (shift in 28 downTo 0 step 4) {
                append(hex[(value ushr shift) and 0x0f])
            }
        }
    }
}

private fun rotateRight(value: Int, distance: Int): Int =
    (value ushr distance) or (value shl (32 - distance))

/**
 * ImageIO can preserve an application segment from the decoded UIImage even
 * when the destination properties argument is null. Remove APP1..APP15 from
 * the encoded header so EXIF, GPS, ICC, and vendor metadata cannot cross the
 * normalization boundary. The scan stops at SOS, so entropy-coded bytes are
 * never interpreted as JPEG header markers.
 */
private fun ByteArray.stripJpegApplicationMetadata(): ByteArray {
    if (size < 4 || this[0] != 0xff.toByte() || this[1] != 0xd8.toByte()) return this

    val output = ArrayList<Byte>(size)
    fun appendRange(start: Int, endExclusive: Int) {
        for (index in start until endExclusive) output += this[index]
    }

    appendRange(0, 2)
    var index = 2
    while (index < size) {
        val segmentStart = index
        if (this[index] != 0xff.toByte()) {
            appendRange(index, size)
            break
        }

        while (index < size && this[index] == 0xff.toByte()) index++
        if (index >= size) {
            appendRange(segmentStart, size)
            break
        }

        val marker = this[index].toInt() and 0xff
        if (marker == 0xda) {
            appendRange(segmentStart, size)
            break
        }

        if (marker == 0xd8 || marker == 0xd9 || marker in 0xd0..0xd7) {
            appendRange(segmentStart, index + 1)
            index++
            continue
        }

        if (index + 2 >= size) {
            appendRange(segmentStart, size)
            break
        }
        val segmentLength = ((this[index + 1].toInt() and 0xff) shl 8) or
            (this[index + 2].toInt() and 0xff)
        if (segmentLength < 2) {
            appendRange(segmentStart, size)
            break
        }
        val segmentEnd = index + 1 + segmentLength
        if (segmentEnd > size) {
            appendRange(segmentStart, size)
            break
        }

        if (marker !in 0xe1..0xef) appendRange(segmentStart, segmentEnd)
        index = segmentEnd
    }

    return ByteArray(output.size) { output[it] }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<__CFData>.toByteArray(): ByteArray {
    val byteCount = CFDataGetLength(this).toInt()
    val result = ByteArray(byteCount)
    if (result.isEmpty()) return result
    val source = CFDataGetBytePtr(this)?.reinterpret<kotlinx.cinterop.ByteVar>()
        ?: throw PhotoPreparationException("iOS returned empty normalized image bytes")
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), source, byteCount.toULong())
    }
    return result
}
