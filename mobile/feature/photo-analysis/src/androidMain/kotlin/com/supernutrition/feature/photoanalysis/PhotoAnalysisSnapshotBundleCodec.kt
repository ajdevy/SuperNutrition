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
    private const val SNAPSHOT_KIND_KEY = "photo_analysis.snapshot.kind"
    private const val SNAPSHOT_ATTEMPT_KEY = "photo_analysis.snapshot.attempt"
    private const val SNAPSHOT_OPAQUE_KEY = "photo_analysis.snapshot.opaque"
    private const val SNAPSHOT_REQUEST_KEY = "photo_analysis.snapshot.request"
    private const val SNAPSHOT_REASON_KEY = "photo_analysis.snapshot.reason"

    private const val SNAPSHOT_CAPTURE = "capture"
    private const val SNAPSHOT_PREPARING = "preparing"
    private const val SNAPSHOT_READY = "ready"
    private const val SNAPSHOT_ANALYZING = "analyzing"
    private const val SNAPSHOT_CANDIDATE_REVIEW = "candidate_review"
    private const val SNAPSHOT_RECOVERY = "recovery"

    @JvmStatic
    fun encode(snapshot: PhotoAnalysisBackgroundSnapshot): Bundle = Bundle().apply {
        putString(SNAPSHOT_KIND_KEY, snapshot.kind)
        when (snapshot) {
            PhotoAnalysisBackgroundSnapshot.Capture -> Unit

            is PhotoAnalysisBackgroundSnapshot.Preparing -> putPhotoRef(snapshot.ref)

            is PhotoAnalysisBackgroundSnapshot.Ready -> putPhotoRef(snapshot.ref)

            is PhotoAnalysisBackgroundSnapshot.Analyzing -> {
                putPhotoRef(snapshot.ref)
                putString(SNAPSHOT_REQUEST_KEY, snapshot.requestId.value)
            }

            is PhotoAnalysisBackgroundSnapshot.CandidateReview -> {
                putPhotoRef(snapshot.ref)
                putString(SNAPSHOT_REQUEST_KEY, snapshot.requestId.value)
            }

            is PhotoAnalysisBackgroundSnapshot.Recovery -> {
                putString(SNAPSHOT_REASON_KEY, snapshot.reason.name)
                snapshot.ref?.let { ref -> putPhotoRef(ref) }
            }
        }
    }

    @JvmStatic
    fun decode(savedState: Bundle?, expectedAttempt: AttemptId): PhotoAnalysisBackgroundSnapshot? {
        val state = savedState ?: return null
        val kind = state.getString(SNAPSHOT_KIND_KEY) ?: return null
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
        val reason = getString(SNAPSHOT_REASON_KEY)
            ?.let { value -> runCatching { RecoveryReason.valueOf(value) }.getOrNull() }
            ?: return null
        return PhotoAnalysisBackgroundSnapshot.Recovery(reason, getPhotoRef(expectedAttempt))
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

    private fun Bundle.putPhotoRef(ref: PhotoRef) {
        putString(SNAPSHOT_ATTEMPT_KEY, ref.attemptId.value)
        putString(SNAPSHOT_OPAQUE_KEY, ref.opaqueId)
    }

    private fun Bundle.getPhotoRef(expectedAttempt: AttemptId): PhotoRef? {
        if (getString(SNAPSHOT_ATTEMPT_KEY) != expectedAttempt.value) return null
        val opaqueId = getString(SNAPSHOT_OPAQUE_KEY) ?: return null
        return runCatching { PhotoRef.issue(expectedAttempt, opaqueId) }.getOrNull()
    }
}
