package com.supernutrition.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class PhotoNormalizationDefaultsTest {
    @Test
    fun `normalizer policy is shared and metadata stripping is deterministic`() {
        assertEquals(4096, PhotoNormalizationDefaults.MAX_DIMENSION)
        assertEquals(92, PhotoNormalizationDefaults.JPEG_QUALITY)
        assertEquals("sRGB", PhotoNormalizationDefaults.COLOR_SPACE)

        val jpeg = byteArrayOf(
            0xff.toByte(), 0xd8.toByte(),
            0xff.toByte(), 0xe0.toByte(), 0x00, 0x04, 0x4a, 0x46,
            0xff.toByte(), 0xe1.toByte(), 0x00, 0x04, 0x01, 0x02,
            0xff.toByte(), 0xda.toByte(), 0x00, 0x03, 0x11,
        )
        val sanitized = jpeg.withoutJpegApplicationMetadata()

        assertNotSame(jpeg, sanitized)
        assertEquals(
            listOf<Byte>(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xda.toByte(), 0x00, 0x03, 0x11),
            sanitized.toList(),
        )
    }
}
