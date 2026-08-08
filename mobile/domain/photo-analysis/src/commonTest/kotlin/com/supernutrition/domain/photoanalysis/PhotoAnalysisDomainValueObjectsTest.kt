package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PhotoAnalysisDomainValueObjectsTest {
    private val attempt = AttemptId("attempt-value-objects")
    private val leases: PhotoLeaseStorePort = PhotoLeaseStore()
    private val sessions: PhotoSessionStorePort = PhotoSessionStore()
    private val normalizer = PhotoInputNormalizer(digest = { "a".repeat(64) })

    private fun normalized(ref: PhotoRef) = normalizer.normalize(
        RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
        ),
    )

    @Test
    fun `domain value objects keep defensive equality`() {
        val ref = leases.issue(attempt, "value-object-ref")
        val otherRef = leases.issue(attempt, "value-object-other-ref")
        val raw = RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
        )

        assertEquals(raw, raw.copy())
        assertEquals(raw.hashCode(), raw.copy().hashCode())
        assertFalse(raw == raw.copy(ref = otherRef))
        assertFalse(raw == raw.copy(bytes = byteArrayOf(9)))
        assertFalse(raw == raw.copy(width = 101))
        assertFalse(raw == raw.copy(height = 201))
        assertFalse(raw == raw.copy(format = raw.format.copy(orientation = ImageOrientation.RIGHT)))
        assertFalse(raw == raw.copy(format = raw.format.copy(colorSpace = "Display-P3")))
        assertFalse(raw == raw.copy(format = raw.format.copy(metadataKeys = setOf("EXIF"))))
        assertFalse((raw as Any).equals("raw"))

        val input = normalized(ref)
        val otherInput = NormalizedPhotoInput(
            ref = otherRef,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
            contentDigest = "a".repeat(64),
        )
        assertEquals(input, input.copy())
        assertEquals(input.hashCode(), input.copy().hashCode())
        assertFalse(input == input.copy(ref = otherRef))
        assertFalse(input == input.copy(bytes = byteArrayOf(9)))
        assertFalse(input == input.copy(width = 101))
        assertFalse(input == input.copy(height = 201))
        assertFalse(input == input.copy(contentDigest = "b".repeat(64)))
        assertFalse(input == otherInput)
        assertFalse((input as Any).equals("input"))
    }

    @Test
    fun `normalized photo input rejects invalid bytes and dimensions`() {
        val ref = leases.issue(attempt, "invalid-normalized-input")
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = ByteArray(0),
                width = 100,
                height = 200,
                format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                contentDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 0,
                height = 200,
                format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                contentDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 100,
                height = 0,
                format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                contentDigest = "a".repeat(64),
            )
        }
    }

    @Test
    fun `normalized photo input rejects invalid orientation and metadata`() {
        val ref = leases.issue(attempt, "invalid-normalized-metadata")
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 100,
                height = 200,
                format = PhotoFormat(ImageOrientation.RIGHT, "sRGB", emptySet()),
                contentDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 100,
                height = 200,
                format = PhotoFormat(ImageOrientation.UP, "Display-P3", emptySet()),
                contentDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 100,
                height = 200,
                format = PhotoFormat(ImageOrientation.UP, "sRGB", setOf("EXIF")),
                contentDigest = "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref = ref,
                bytes = byteArrayOf(1),
                width = 100,
                height = 200,
                format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                contentDigest = "not-a-digest",
            )
        }
    }

    @Test
    fun `request ids and normalizer reject invalid values`() {
        val ref = leases.issue(attempt, "request-value-ref")
        assertFailsWith<IllegalArgumentException> { RequestId(" ") }
        assertEquals(RequestId("request-value"), RequestId("request-value"))
        assertFalse(RequestId("request-value") == RequestId("other-request"))
        assertFalse((RequestId("request-value") as Any).equals("request-value"))
        assertEquals(RequestId("request-value").hashCode(), RequestId("request-value").value.hashCode())
        assertEquals("RequestId(redacted)", RequestId("request-value").toString())
        assertFailsWith<IllegalArgumentException> {
            PhotoInputNormalizer(maxBytes = 0, digest = { "a".repeat(64) })
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoInputNormalizer(maxDimension = 0, digest = { "a".repeat(64) })
        }
        assertFalse(sessions.remove(ref))
        assertEquals(0, sessions.clearAttempt(AttemptId("no-session")))
    }
}
