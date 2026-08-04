package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Value semantics of the photo carriers, split out to keep each class focused. */
class PhotoAnalysisRawPhotoValueTest {
    private val attempt = AttemptId("photo-analysis-attempt")
    private val leases = PhotoLeaseStore()

    @Test
    fun `raw photo value operations compare every protected field`() {
        val ref = leases.issue(attempt, "raw-value")
        val otherRef = leases.issue(attempt, "raw-value-other")
        val photo = RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 10,
            height = 20,
            format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
        )

        assertEquals(photo, photo.copy())
        assertEquals(photo.hashCode(), photo.copy().hashCode())
        assertContentEquals(byteArrayOf(1, 2, 3), photo.bytes)
        assertFalse(photo.equals("not-a-photo"))
        assertFalse(photo == photo.copy(ref = otherRef))
        assertFalse(photo == photo.copy(bytes = byteArrayOf(9)))
        assertFalse(photo == photo.copy(width = 11))
        assertFalse(photo == photo.copy(height = 21))
        assertFalse(photo == photo.copy(format = photo.format.copy(orientation = ImageOrientation.RIGHT)))
        assertFalse(photo == photo.copy(format = photo.format.copy(colorSpace = "Display-P3")))
        assertFalse(photo == photo.copy(format = photo.format.copy(metadataKeys = setOf("EXIF"))))
    }
}
