package com.supernutrition.feature.photoanalysis

import android.os.Bundle
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.RequestId

/**
 * Process-recreation codec for the Android host boundary.
 *
 * Only opaque attempt/request/recovery data is serialized. Photo bytes, URI
 * strings, leases, and native image handles are deliberately not part of the
 * schema and therefore cannot leak through saved instance state.
 */
object PhotoAnalysisSnapshotBundleCodec {
    const val SNAPSHOT_SCHEMA_VERSION_KEY = "photo_analysis.snapshot.version"
    const val SNAPSHOT_SCHEMA_VERSION = 1
    const val SNAPSHOT_KIND_KEY = "photo_analysis.snapshot.kind"
    const val SNAPSHOT_ATTEMPT_KEY = "photo_analysis.snapshot.attempt"
    const val SNAPSHOT_OPAQUE_KEY = "photo_analysis.snapshot.opaque"
    const val SNAPSHOT_REQUEST_KEY = "photo_analysis.snapshot.request"
    const val SNAPSHOT_REASON_KEY = "photo_analysis.snapshot.reason"

    const val SNAPSHOT_CAPTURE = "capture"
    const val SNAPSHOT_PREPARING = "preparing"
    const val SNAPSHOT_READY = "ready"
    const val SNAPSHOT_ANALYZING = "analyzing"
    const val SNAPSHOT_CANDIDATE_REVIEW = "candidate_review"
    const val SNAPSHOT_RECOVERY = "recovery"

    @JvmStatic
    fun encode(snapshot: PhotoAnalysisBackgroundSnapshot, expectedAttempt: AttemptId): Bundle = Bundle().apply {
        putInt(SNAPSHOT_SCHEMA_VERSION_KEY, SNAPSHOT_SCHEMA_VERSION)
        putString(SNAPSHOT_KIND_KEY, snapshot.kind)
        putString(SNAPSHOT_ATTEMPT_KEY, expectedAttempt.value)
        when (snapshot) {
            PhotoAnalysisBackgroundSnapshot.Capture -> Unit

            is PhotoAnalysisBackgroundSnapshot.Preparing -> putPhotoRef(expectedAttempt, snapshot.ref)

            is PhotoAnalysisBackgroundSnapshot.Ready -> putPhotoRef(expectedAttempt, snapshot.ref)

            is PhotoAnalysisBackgroundSnapshot.Analyzing -> {
                putPhotoRef(expectedAttempt, snapshot.ref)
                putString(SNAPSHOT_REQUEST_KEY, snapshot.requestId.value)
            }

            is PhotoAnalysisBackgroundSnapshot.CandidateReview -> {
                putPhotoRef(expectedAttempt, snapshot.ref)
                putString(SNAPSHOT_REQUEST_KEY, snapshot.requestId.value)
            }

            is PhotoAnalysisBackgroundSnapshot.Recovery -> {
                putString(SNAPSHOT_REASON_KEY, snapshot.reason.wireValue)
                snapshot.ref?.let { ref -> putPhotoRef(expectedAttempt, ref) }
            }
        }
    }

    @JvmStatic
    fun decode(savedState: Bundle?, expectedAttempt: AttemptId): PhotoAnalysisBackgroundSnapshot? {
        return runCatching { decodeInternal(savedState, expectedAttempt) }.getOrNull()
    }

    private fun decodeInternal(
        savedState: Bundle?,
        expectedAttempt: AttemptId,
    ): PhotoAnalysisBackgroundSnapshot? {
        val state = savedState ?: return null
        if (state.getInt(SNAPSHOT_SCHEMA_VERSION_KEY, -1) != SNAPSHOT_SCHEMA_VERSION) return null
        val kind = state.getString(SNAPSHOT_KIND_KEY) ?: return null
        if (state.getString(SNAPSHOT_ATTEMPT_KEY) != expectedAttempt.value) return null
        return when (kind) {
            SNAPSHOT_CAPTURE -> PhotoAnalysisBackgroundSnapshot.Capture

            SNAPSHOT_PREPARING -> state.decodeRef(expectedAttempt, PhotoAnalysisBackgroundSnapshot::Preparing)

            SNAPSHOT_READY -> state.decodeRef(expectedAttempt, PhotoAnalysisBackgroundSnapshot::Ready)

            SNAPSHOT_ANALYZING -> state.decodeRequest(expectedAttempt) { ref, requestId ->
                PhotoAnalysisBackgroundSnapshot.Analyzing(ref, requestId)
            }

            SNAPSHOT_CANDIDATE_REVIEW -> state.decodeRequest(expectedAttempt) { ref, requestId ->
                PhotoAnalysisBackgroundSnapshot.CandidateReview(ref, requestId)
            }

            SNAPSHOT_RECOVERY -> state.decodeRecovery(expectedAttempt)

            else -> null
        }
    }

    private fun Bundle.decodeRef(
        expectedAttempt: AttemptId,
        factory: (PhotoRef) -> PhotoAnalysisBackgroundSnapshot,
    ): PhotoAnalysisBackgroundSnapshot? = getPhotoRef(expectedAttempt)?.let(factory)

    private fun Bundle.decodeRequest(
        expectedAttempt: AttemptId,
        factory: (PhotoRef, RequestId) -> PhotoAnalysisBackgroundSnapshot,
    ): PhotoAnalysisBackgroundSnapshot? {
        val ref = getPhotoRef(expectedAttempt) ?: return null
        val requestId = getString(SNAPSHOT_REQUEST_KEY)?.takeIf(String::isNotBlank)
            ?.let(::RequestId)
            ?: return null
        return factory(ref, requestId)
    }

    private fun Bundle.decodeRecovery(expectedAttempt: AttemptId): PhotoAnalysisBackgroundSnapshot? {
        val reasonValue = getString(SNAPSHOT_REASON_KEY) ?: return null
        val reason = recoveryReason(reasonValue) ?: return null
        return PhotoAnalysisBackgroundSnapshot.Recovery(reason, getPhotoRef(expectedAttempt))
    }

    private val RecoveryReason.wireValue: String
        get() = when (this) {
            RecoveryReason.PERMISSION_DENIED -> "permission_denied"
            RecoveryReason.CAMERA_UNAVAILABLE -> "camera_unavailable"
            RecoveryReason.CAPTURE_FAILED -> "capture_failed"
            RecoveryReason.POOR_IMAGE -> "poor_image"
            RecoveryReason.NO_FOOD -> "no_food"
            RecoveryReason.OFFLINE -> "offline"
            RecoveryReason.CANCELLED -> "cancelled"
        }

    private fun recoveryReason(value: String): RecoveryReason? = when (value) {
        "permission_denied" -> RecoveryReason.PERMISSION_DENIED
        "camera_unavailable" -> RecoveryReason.CAMERA_UNAVAILABLE
        "capture_failed" -> RecoveryReason.CAPTURE_FAILED
        "poor_image" -> RecoveryReason.POOR_IMAGE
        "no_food" -> RecoveryReason.NO_FOOD
        "offline" -> RecoveryReason.OFFLINE
        "cancelled" -> RecoveryReason.CANCELLED
        else -> null
    }

    private val PhotoAnalysisBackgroundSnapshot.kind: String
        get() = when (this) {
            PhotoAnalysisBackgroundSnapshot.Capture -> SNAPSHOT_CAPTURE
            is PhotoAnalysisBackgroundSnapshot.Preparing -> SNAPSHOT_PREPARING
            is PhotoAnalysisBackgroundSnapshot.Ready -> SNAPSHOT_READY
            is PhotoAnalysisBackgroundSnapshot.Analyzing -> SNAPSHOT_ANALYZING
            is PhotoAnalysisBackgroundSnapshot.CandidateReview -> SNAPSHOT_CANDIDATE_REVIEW
            is PhotoAnalysisBackgroundSnapshot.Recovery -> SNAPSHOT_RECOVERY
        }

    private fun Bundle.putPhotoRef(expectedAttempt: AttemptId, ref: PhotoRef) {
        require(ref.attemptId == expectedAttempt) {
            "Snapshot photo reference must belong to the expected attempt"
        }
        putString(SNAPSHOT_ATTEMPT_KEY, ref.attemptId.value)
        putString(SNAPSHOT_OPAQUE_KEY, ref.opaqueId)
    }

    private fun Bundle.getPhotoRef(expectedAttempt: AttemptId): PhotoRef? {
        if (getString(SNAPSHOT_ATTEMPT_KEY) != expectedAttempt.value) return null
        val opaqueId = getString(SNAPSHOT_OPAQUE_KEY) ?: return null
        return runCatching { PhotoRef.issue(expectedAttempt, opaqueId) }.getOrNull()
    }
}
