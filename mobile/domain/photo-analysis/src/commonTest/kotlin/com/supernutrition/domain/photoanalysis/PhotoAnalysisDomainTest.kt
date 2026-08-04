package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoAnalysisDomainTest {
    private val attempt = AttemptId("attempt-1")
    private val leases: PhotoLeaseStorePort = PhotoLeaseStore()
    private val sessions: PhotoSessionStorePort = PhotoSessionStore()
    private val reducer = PhotoAnalysisReducer(leases, sessions, attempt)
    private val normalizer = PhotoInputNormalizer(digest = { "a".repeat(64) })

    private fun normalized(ref: PhotoRef) = normalizer.normalize(
        RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat.NORMALIZED,
        ),
    )

    @Test
    fun `normalizer accepts only the platform normalized contract`() {
        val ref = leases.issue(attempt, "photo-1")
        val output = normalized(ref)

        assertEquals(100, output.width)
        assertEquals(200, output.height)
        assertEquals(ImageOrientation.UP, output.orientation)
        assertEquals("sRGB", output.colorSpace)
        assertTrue(output.metadataKeys.isEmpty())
    }

    @Test
    fun `normalizer rejects unsanitized platform input`() {
        val ref = leases.issue(attempt, "photo-unsanitized-input")
        fun raw(
            orientation: ImageOrientation = ImageOrientation.UP,
            colorSpace: String = "sRGB",
            metadataKeys: Set<String> = emptySet(),
        ) = RawPhoto(ref, byteArrayOf(1, 2, 3), 7, 11, PhotoFormat(orientation, colorSpace, metadataKeys))

        assertFailsWith<PhotoPreparationException> { normalizer.normalize(raw(ImageOrientation.RIGHT)) }
        assertFailsWith<PhotoPreparationException> { normalizer.normalize(raw(colorSpace = "Display-P3")) }
        assertFailsWith<PhotoPreparationException> { normalizer.normalize(raw(metadataKeys = setOf("EXIF"))) }

        val output = normalizer.normalize(raw())
        assertEquals(7, output.width)
        assertEquals(11, output.height)
    }

    @Test
    fun `normalizer rejects empty oversized malformed and undigestible input`() {
        val ref = leases.issue(attempt, "photo-invalid-input")
        fun raw(bytes: ByteArray = byteArrayOf(1), width: Int = 1, height: Int = 1) = RawPhoto(
            ref = ref,
            bytes = bytes,
            width = width,
            height = height,
            format = PhotoFormat.NORMALIZED,
        )

        assertFailsWith<PhotoPreparationException> { normalizer.normalize(raw(ByteArray(0))) }
        assertFailsWith<PhotoPreparationException> {
            PhotoInputNormalizer(maxBytes = 1, digest = { "a".repeat(64) }).normalize(raw(byteArrayOf(1, 2)))
        }
        assertEquals(1, PhotoInputNormalizer(maxBytes = 1, digest = { "a".repeat(64) }).normalize(raw()).bytes.size)
        assertFailsWith<PhotoPreparationException> { normalizer.normalize(raw(width = 0)) }
        assertFailsWith<PhotoPreparationException> { normalizer.normalize(raw(height = 0)) }
        assertFailsWith<PhotoPreparationException> {
            PhotoInputNormalizer(maxDimension = 2, digest = { "a".repeat(64) }).normalize(raw(width = 3))
        }
        assertEquals(
            2,
            PhotoInputNormalizer(maxDimension = 2, digest = { "a".repeat(64) })
                .normalize(raw(width = 2, height = 2)).width,
        )
        assertFailsWith<PhotoPreparationException> {
            PhotoInputNormalizer(digest = { "not-a-digest" }).normalize(raw())
        }
    }

    @Test
    fun `RawPhoto keeps defensive equality`() {
        val ref = leases.issue(attempt, "value-object-ref")
        val otherRef = leases.issue(attempt, "value-object-other-ref")
        val raw = RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat.NORMALIZED,
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
    }

    @Test
    fun `NormalizedPhotoInput keeps defensive equality`() {
        val ref = leases.issue(attempt, "value-object-ref")
        val otherRef = leases.issue(attempt, "value-object-other-ref")
        val input = normalized(ref)
        val otherInput = NormalizedPhotoInput(
            ref = otherRef,
            bytes = byteArrayOf(1, 2, 3),
            width = 100,
            height = 200,
            format = PhotoFormat.NORMALIZED,
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
    fun `NormalizedPhotoInput rejects malformed bytes dimensions and digest`() {
        val ref = leases.issue(attempt, "value-object-ref")

        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                ByteArray(0),
                100,
                200,
                PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                byteArrayOf(1),
                0,
                200,
                PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                byteArrayOf(1),
                100,
                0,
                PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                byteArrayOf(1),
                100,
                200,
                PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
                "not-a-digest",
            )
        }
    }

    @Test
    fun `NormalizedPhotoInput rejects a format the normalizer should have applied`() {
        val ref = leases.issue(attempt, "value-object-ref")

        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                byteArrayOf(1),
                100,
                200,
                PhotoFormat(ImageOrientation.RIGHT, "sRGB", emptySet()),
                "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                byteArrayOf(1),
                100,
                200,
                PhotoFormat(ImageOrientation.UP, "Display-P3", emptySet()),
                "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedPhotoInput(
                ref,
                byteArrayOf(1),
                100,
                200,
                PhotoFormat(ImageOrientation.UP, "sRGB", setOf("EXIF")),
                "a".repeat(64),
            )
        }
    }

    @Test
    fun `RequestId compares by value and redacts its content`() {
        assertFailsWith<IllegalArgumentException> { RequestId(" ") }
        assertEquals(RequestId("request-value"), RequestId("request-value"))
        assertFalse(RequestId("request-value") == RequestId("other-request"))
        assertFalse((RequestId("request-value") as Any).equals("request-value"))
        assertEquals(RequestId("request-value").hashCode(), RequestId("request-value").value.hashCode())
        assertEquals("RequestId(redacted)", RequestId("request-value").toString())
    }

    @Test
    fun `PhotoInputNormalizer rejects non-positive bounds`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoInputNormalizer(maxBytes = 0, digest = { "a".repeat(64) })
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoInputNormalizer(maxDimension = 0, digest = { "a".repeat(64) })
        }
    }

    @Test
    fun `the session store reports a miss rather than inventing one`() {
        val ref = leases.issue(attempt, "value-object-ref")

        assertFalse(sessions.remove(ref))
        assertEquals(0, sessions.clearAttempt(AttemptId("no-session")))
    }

    @Test
    fun `restore rejects foreign expired and missing session content`() {
        val foreign = PhotoRef.issue(AttemptId("foreign-attempt"), "foreign-restore")
        assertEquals(
            PhotoAnalysisState.Capture,
            reducer.reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.RestoreFromSnapshot(PhotoAnalysisBackgroundSnapshot.Preparing(foreign)),
            ).state,
        )
        assertEquals(
            PhotoAnalysisState.Capture,
            reducer.reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.RestoreFromSnapshot(PhotoAnalysisBackgroundSnapshot.Ready(foreign)),
            ).state,
        )
        assertEquals(
            PhotoAnalysisState.Capture,
            reducer.reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.RestoreFromSnapshot(
                    PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CANCELLED, foreign),
                ),
            ).state,
        )

        val expired = leases.issue(attempt, "expired-restore")
        leases.revoke(expired)
        assertEquals(
            PhotoAnalysisState.Capture,
            reducer.reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.RestoreFromSnapshot(PhotoAnalysisBackgroundSnapshot.Preparing(expired)),
            ).state,
        )
        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, expired),
            reducer.reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.RestoreFromSnapshot(PhotoAnalysisBackgroundSnapshot.Ready(expired)),
            ).state,
        )

        val activeWithoutSession = leases.issue(attempt, "missing-session-restore")
        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, activeWithoutSession),
            reducer.reduce(
                PhotoAnalysisState.Capture,
                PhotoAnalysisEvent.RestoreFromSnapshot(
                    PhotoAnalysisBackgroundSnapshot.CandidateReview(
                        activeWithoutSession,
                        RequestId("missing-session-request"),
                    ),
                ),
            ).state,
        )
    }

    @Test
    fun `revoke attempt reports every revoked lease`() {
        val first = leases.issue(attempt, "photo-revoke-1")
        val second = leases.issue(attempt, "photo-revoke-2")
        val otherAttempt = leases.issue(AttemptId("attempt-2"), "photo-revoke-other")

        assertEquals(2, leases.revokeAttempt(attempt))
        assertFalse(leases.isActive(first))
        assertFalse(leases.isActive(second))
        assertTrue(leases.isActive(otherAttempt))
        assertFalse(leases.revoke(first))
        assertEquals(0, leases.revokeAttempt(attempt))
    }

    @Test
    fun `photo ref string never exposes its opaque identity`() {
        val ref = PhotoRef.issue(attempt, "private-photo-token")

        assertEquals("PhotoRef(redacted)", ref.toString())
        assertFalse(ref.toString().contains("private-photo-token"))
    }

    @Test
    fun `photo ref rejects uri shaped identities`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoRef.issue(attempt, "content://photos/private/meal-1")
        }
    }

    @Test
    fun `unleased photo ref cannot enter the reducer`() {
        val unleased = PhotoRef.issue(attempt, "photo-without-lease")

        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(unleased))
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
    }

    @Test
    fun `capture rejects a photo from another attempt`() {
        val foreign = PhotoRef.issue(AttemptId("attempt-foreign"), "foreign")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(foreign))
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
    }

    @Test
    fun `permission denial from capture is typed`() {
        reducer.dispatch(PhotoAnalysisEvent.PermissionDenied)
        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED),
            reducer.state,
        )
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("ignored")))
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED), reducer.state)
    }

    @Test
    fun `unleased replacement cannot revoke the current photo`() {
        val current = leases.issue(attempt, "photo-current")
        val unleasedReplacement = PhotoRef.issue(attempt, "photo-without-lease")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(current))

        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(unleasedReplacement))
        assertEquals(PhotoAnalysisState.Preparing(current), reducer.state)
        assertTrue(leases.isActive(current))
    }

    @Test
    fun `replacement cannot reuse the current photo lease`() {
        val current = leases.issue(attempt, "photo-current-only")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(current))

        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(current))
        assertEquals(PhotoAnalysisState.Preparing(current), reducer.state)
        assertTrue(leases.isActive(current))
    }

    @Test
    fun `preparation completion is ignored after the photo lease expires`() {
        val ref = leases.issue(attempt, "photo-expired-before-preparation")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        leases.revoke(ref)

        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(ref)))

        assertEquals(PhotoAnalysisState.Preparing(ref), reducer.state)
        assertNull(sessions.read(ref))
    }

    @Test
    fun `preparation ignores a result for a different lease`() {
        val current = leases.issue(attempt, "photo-preparing-current")
        val other = leases.issue(attempt, "photo-preparing-other")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(current))

        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(other)))

        assertEquals(PhotoAnalysisState.Preparing(current), reducer.state)
        assertNull(sessions.read(other))
    }

    @Test
    fun `normalizer owns a stable copy of input bytes`() {
        val ref = leases.issue(attempt, "photo-copy-boundary")
        val source = byteArrayOf(1, 2, 3)
        val output = normalizer.normalize(
            RawPhoto(
                ref = ref,
                bytes = source,
                width = 3,
                height = 2,
                format = PhotoFormat.NORMALIZED,
            ),
        )
        val expected = source.copyOf()

        source[0] = 99

        assertContentEquals(expected, output.bytes)
        val exposed = output.bytes
        exposed[0] = 88
        assertContentEquals(expected, output.bytes)
    }

    @Test
    fun `late analysis callback cannot reopen a replaced photo`() {
        val old = leases.issue(attempt, "old")
        val replacement = leases.issue(attempt, "replacement")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(old))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("request-old")))
        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(replacement))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("request-old")))

        assertEquals(PhotoAnalysisState.Preparing(replacement), reducer.state)
        assertFalse(leases.isActive(old))
        assertTrue(leases.isActive(replacement))
    }

    @Test
    fun `scan again clears the attempt lease and session`() {
        val ref = leases.issue(attempt, "photo-1")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("request-1")))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("request-1")))
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)

        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        assertFalse(leases.isActive(ref))
        assertEquals(null, sessions.read(ref))
    }

    @Test
    fun `preparation failure from capture state is ignored`() {
        reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))

        assertEquals(PhotoAnalysisState.Capture, reducer.state)
    }

    @Test
    fun `no food is a distinct recoverable reason`() {
        val ref = leases.issue(attempt, "no-food-photo")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.NO_FOOD))

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.NO_FOOD), reducer.state)
        assertFalse(leases.isActive(ref))
        assertNull(sessions.read(ref))
    }

    @Test
    fun `preparation failure clears any attempt content and remains idempotent`() {
        val ref = leases.issue(attempt, "failed-preparation")
        val input = normalized(ref)
        sessions.put(input)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))

        reducer.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)

        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        assertFalse(leases.isActive(ref))
        assertNull(sessions.read(ref))
        assertContentEquals(byteArrayOf(0, 0, 0), input.bytes)
    }

    @Test
    fun `expired ready session cannot start a new analysis`() {
        val ref = leases.issue(attempt, "expired-ready")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        leases.revoke(ref)

        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("expired-request")))

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            reducer.state,
        )
        assertNull(sessions.read(ref))
    }

    @Test
    fun `ready and analyzing states clean up when their live lease disappears`() {
        val readyRef = leases.issue(attempt, "ready-missing-session")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(readyRef))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(readyRef)))
        sessions.clearAttempt(attempt)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("missing-session")))
        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            reducer.state,
        )

        val analyzingRef = leases.issue(attempt, "analyzing-expired")
        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(analyzingRef))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(analyzingRef)))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("expired-analysis")))
        leases.revoke(analyzingRef)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("expired-analysis")))
        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED),
            reducer.state,
        )
    }
}
