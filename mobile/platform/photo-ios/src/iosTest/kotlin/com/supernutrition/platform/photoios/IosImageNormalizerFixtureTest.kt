package com.supernutrition.platform.photoios

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.platform.photoios.fixtureinterop.SNMakeDataFromBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

private const val FIXTURE_SHA256 =
    "4371a3c8d33d1503cc8904b5a9e78687194daa5525c6d8322e4e7be3d619120c"

@OptIn(ExperimentalForeignApi::class)
class IosImageNormalizerFixtureTest {
    @Test
    fun `fixture bytes match the canonical binary digest`() {
        val source = checkNotNull(SNMakeDataFromBase64(FIXTURE_BASE64))

        assertEquals(FIXTURE_SHA256, source.toByteArrayForTest().let(::iosSha256))
    }

    @Test
    fun `normalizer digest uses the sha256 test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            iosSha256("abc".encodeToByteArray()),
        )
    }

    @Test
    fun `exif gps fixture normalizes to bounded metadata free pixels`() {
        val source = checkNotNull(SNMakeDataFromBase64(FIXTURE_BASE64))
        val ref = PhotoRef.Companion.issue(AttemptId("ios-fixture-attempt"), "ios-fixture-photo")
        val normalized = IosImageNormalizer().normalize(ref, source)

        assertEquals(4, normalized.width)
        assertEquals(6, normalized.height)
        assertEquals(ImageOrientation.UP, normalized.orientation)
        assertEquals("sRGB", normalized.colorSpace)
        assertTrue(normalized.metadataKeys.isEmpty())
        assertEquals(64, normalized.contentDigest.length)
        assertNotEquals("a".repeat(64), normalized.contentDigest)
        assertEquals(0xff, normalized.bytes[0].toInt() and 0xff)
        assertEquals(0xd8, normalized.bytes[1].toInt() and 0xff)
        assertFalse(
            normalized.bytes.hasJpegMetadataApplicationSegment(),
            "normalized output still contains JPEG metadata application segment: ${normalized.bytes.jpegApplicationMarkers()}",
        )
    }

    private fun ByteArray.hasJpegMetadataApplicationSegment(): Boolean {
        return jpegApplicationMarkers().any { it in 0xe1..0xef }
    }

    private fun ByteArray.jpegApplicationMarkers(): List<Int> {
        val markers = mutableListOf<Int>()
        if (size < 4 || this[0] != 0xff.toByte() || this[1] != 0xd8.toByte()) return markers
        var index = 2
        while (index + 3 < size && this[index] == 0xff.toByte()) {
            val marker = this[index + 1].toInt() and 0xff
            if (marker == 0xda || marker == 0xd9) return markers
            val segmentLength = ((this[index + 2].toInt() and 0xff) shl 8) or
                (this[index + 3].toInt() and 0xff)
            if (marker in 0xe0..0xef) markers += marker
            if (segmentLength < 2) return markers
            index += 2 + segmentLength
        }
        return markers
    }

    private fun NSData.toByteArrayForTest(): ByteArray {
        val result = ByteArray(length.toInt())
        if (result.isEmpty()) return result
        val source = bytes?.reinterpret<ByteVar>() ?: return result
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, result.size.toULong())
        }
        return result
    }

    private companion object {
        const val FIXTURE_BASE64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/4QDYRXhpZgAATU0AKgAAAAgABAESAAMAAAABAAYAAAExAAIAAAAXAAAAPoglAAQAAAABAAAAVpKGAAEAAAATAAAAvAAAAABTdXBlck51dHJpdGlvbiBmaXh0dXJlAAAABAABAAIAAAACTgAAAAACAAUAAAADAAAAjAADAAIAAAACRQAAAAAEAAUAAAADAAAApAAAAAAAAAA3AAAAAQAAAAEAAAABAAAAAAAAAAEAAAAlAAAAAQAAAAEAAAABAAAAAAAAAAFHUFMgZml4dHVyZSBwYXlsb2FkAP/bAEMABAMDAwMCBAMDAwQEBAUGCgYGBQUGDAgJBwoODA8ODgwNDQ8RFhMPEBURDQ0TGhMVFxgZGRkPEhsdGxgdFhgZGP/bAEMBBAQEBgUGCwYGCxgQDRAYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGP/AABEIAAQABgMBIgACEQEDEQH/xAAfAAABBQEBAQEBAQAAAAAAAAAAAQIDBAUGBwgJCgv/xAC1EAACAQMDAgQDBQUEBAAAAX0BAgMABBEFEiExQQYTUWEHInEUMoGRoQgjQrHBFVLR8CQzYnKCCQoWFxgZGiUmJygpKjQ1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4eLj5OXm5+jp6vHy8/T19vf4+fr/xAAfAQADAQEBAQEBAQEBAAAAAAAAAQIDBAUGBwgJCgv/xAC1EQACAQIEBAMEBwUEBAABAncAAQIDEQQFITEGEkFRB2FxEyIygQgUQpGhscEJIzNS8BVictEKFiQ04SXxFxgZGiYnKCkqNTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqCg4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2dri4+Tl5ufo6ery8/T19vf4+fr/2gAMAwEAAhEDEQA/AOu+HltF/ZJ+X+EUUUV+eZnhKH1mfuL7kf0DnEn9cqa9T//Z"
    }
}
