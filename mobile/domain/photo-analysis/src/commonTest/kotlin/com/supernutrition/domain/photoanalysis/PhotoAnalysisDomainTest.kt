package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
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
        ) = RawPhoto(ref, byteArrayOf(1, 2, 3), 7, 11, orientation, colorSpace, metadataKeys)

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
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
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
                .normalize(raw(width = 2, height = 2))
                .width,
        )
        assertFailsWith<PhotoPreparationException> {
            PhotoInputNormalizer(digest = { "not-a-digest" }).normalize(raw())
        }
    }

    @Test
    fun `raw photo value operations compare every protected field`() {
        val ref = leases.issue(attempt, "raw-value")
        val otherRef = leases.issue(attempt, "raw-value-other")
        val photo = RawPhoto(
            ref = ref,
            bytes = byteArrayOf(1, 2, 3),
            width = 10,
            height = 20,
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
        )

        assertEquals(photo, photo.copy())
        assertEquals(photo.hashCode(), photo.copy().hashCode())
        assertContentEquals(byteArrayOf(1, 2, 3), photo.bytes)
        assertFalse(photo.equals("not-a-photo"))
        assertFalse(photo == photo.copy(ref = otherRef))
        assertFalse(photo == photo.copy(bytes = byteArrayOf(9)))
        assertFalse(photo == photo.copy(width = 11))
        assertFalse(photo == photo.copy(height = 21))
        assertFalse(photo == photo.copy(orientation = ImageOrientation.RIGHT))
        assertFalse(photo == photo.copy(colorSpace = "Display-P3"))
        assertFalse(photo == photo.copy(metadataKeys = setOf("EXIF")))
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
                orientation = ImageOrientation.UP,
                colorSpace = "sRGB",
                metadataKeys = emptySet(),
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
    fun `scan again clears a photo from every pre-review state`() {
        val preparingRef = leases.issue(attempt, "scan-again-preparing")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(preparingRef))
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        assertFalse(leases.isActive(preparingRef))

        val readyRef = leases.issue(attempt, "scan-again-ready")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(readyRef))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(readyRef)))
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        assertFalse(leases.isActive(readyRef))

        val analyzingRef = leases.issue(attempt, "scan-again-analyzing")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(analyzingRef))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(analyzingRef)))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("scan-again-request")))
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        assertFalse(leases.isActive(analyzingRef))
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

    @Test
    fun `background and restore keeps a prepared photo when the lease is alive`() {
        val ref = leases.issue(attempt, "photo-background-ready")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))

        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisState.Backgrounded(
                PhotoAnalysisBackgroundSnapshot.Ready(ref),
            ),
            reducer.state,
        )

        reducer.dispatch(PhotoAnalysisEvent.Restored)

        assertEquals(PhotoAnalysisState.Ready(input), reducer.state)
        assertTrue(leases.isActive(ref))
        assertEquals(input, sessions.read(ref))
    }

    @Test
    fun `restore from analyzing preserves the in progress request`() {
        val ref = leases.issue(attempt, "photo-background-analysis")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("request-background")))

        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(PhotoAnalysisState.Analyzing(ref, RequestId("request-background")), reducer.state)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("request-background")))

        assertEquals(PhotoAnalysisState.CandidateReview(ref, RequestId("request-background")), reducer.state)
    }

    @Test
    fun `background snapshot covers every live state and is idempotent`() {
        val captureSnapshot = PhotoAnalysisReducer(
            PhotoLeaseStore(),
            PhotoSessionStore(),
            AttemptId("snapshot-capture"),
        ).snapshotForPersistence()
        assertEquals(PhotoAnalysisBackgroundSnapshot.Capture, captureSnapshot)

        val preparingRef = leases.issue(attempt, "snapshot-preparing")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(preparingRef))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.Preparing(preparingRef),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(PhotoAnalysisState.Preparing(preparingRef), reducer.state)

        val readyRef = leases.issue(attempt, "snapshot-ready")
        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(readyRef))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(readyRef)))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.Ready(readyRef),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("snapshot-request")))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("snapshot-request")))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.CandidateReview(readyRef, RequestId("snapshot-request")),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(
            PhotoAnalysisState.CandidateReview(readyRef, RequestId("snapshot-request")),
            reducer.state,
        )

        reducer.dispatch(PhotoAnalysisEvent.Cancel)
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals(
            PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CANCELLED, null),
            reducer.snapshotForPersistence(),
        )
        reducer.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reducer.state)
    }

    @Test
    fun `late analysis callback after cancellation cannot reopen a result`() {
        val ref = leases.issue(attempt, "cancelled-photo")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("cancelled-request")))
        reducer.dispatch(PhotoAnalysisEvent.Cancel)
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("cancelled-request")))

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reducer.state)
        assertFalse(leases.isActive(ref))
        assertNull(sessions.read(ref))
    }

    @Test
    fun `revoking one lease does not clear another session`() {
        val first = leases.issue(attempt, "photo-revoke-one")
        val second = leases.issue(attempt, "photo-revoke-two")
        sessions.put(normalized(first))
        sessions.put(normalized(second))

        val reduction = reducer.reduce(
            PhotoAnalysisState.Ready(normalized(first)),
            PhotoAnalysisEvent.Cancel,
        )
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reduction.state)
        assertEquals(listOf(PhotoAnalysisEffect.RevokePhoto(first)), reduction.effects)
        reduction.effects.forEach { effect ->
            if (effect is PhotoAnalysisEffect.RevokePhoto) {
                leases.revoke(effect.ref)
                sessions.remove(effect.ref)
            }
        }

        assertNull(sessions.read(first))
        assertEquals(normalized(second), sessions.read(second))
    }

    @Test
    fun `process recreation with an expired lease cannot resurrect photo bytes`() {
        val ref = leases.issue(attempt, "photo-expired-background")
        val input = normalized(ref)
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(input))
        reducer.dispatch(PhotoAnalysisEvent.Backgrounded)
        val snapshot = assertIs<PhotoAnalysisState.Backgrounded>(reducer.state).snapshot

        leases.revoke(ref)
        sessions.clearAttempt(attempt)
        val recreated = PhotoAnalysisReducer(
            leases = leases,
            sessions = sessions,
            attemptId = attempt,
        )
        recreated.dispatch(PhotoAnalysisEvent.RestoreFromSnapshot(snapshot))

        assertEquals(
            PhotoAnalysisState.Recovery(RecoveryReason.CAPTURE_FAILED, ref),
            recreated.state,
        )
        assertEquals(null, sessions.read(ref))
    }

    @Test
    fun `recovery and candidate review support replacement cancellation and empty scan`() {
        val ref = leases.issue(attempt, "review-photo")
        reducer.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        reducer.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized(ref)))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisStarted(RequestId("review-request")))
        reducer.dispatch(PhotoAnalysisEvent.AnalysisSucceeded(RequestId("review-request")))
        val reviewReplacement = leases.issue(attempt, "review-replacement")
        reducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(reviewReplacement))
        assertEquals(PhotoAnalysisState.Preparing(reviewReplacement), reducer.state)
        reducer.dispatch(PhotoAnalysisEvent.Cancel)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), reducer.state)

        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)
        reducer.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisState.Capture, reducer.state)

        val recoveryRef = leases.issue(attempt, "recovery-photo")
        val recoveryReducer = PhotoAnalysisReducer(leases, sessions, attempt)
        recoveryReducer.dispatch(
            PhotoAnalysisEvent.RestoreFromSnapshot(
                PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CAPTURE_FAILED, recoveryRef),
            ),
        )
        val replacement = leases.issue(attempt, "recovery-replacement")
        recoveryReducer.dispatch(PhotoAnalysisEvent.ReplacePhoto(replacement))
        assertEquals(PhotoAnalysisState.Preparing(replacement), recoveryReducer.state)
        recoveryReducer.dispatch(PhotoAnalysisEvent.Cancel)
        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), recoveryReducer.state)
    }
}
