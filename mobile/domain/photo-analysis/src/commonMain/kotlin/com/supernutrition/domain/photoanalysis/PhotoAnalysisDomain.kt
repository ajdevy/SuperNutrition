package com.supernutrition.domain.photoanalysis

import com.supernutrition.core.config.ConfigurationSnapshot

data class AttemptId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class RequestId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

/** Opaque, attempt-leased photo identity. It is not a URI or platform object. */
class PhotoRef private constructor(val opaqueId: String, val attemptId: AttemptId) {
    override fun equals(other: Any?): Boolean =
        other is PhotoRef && opaqueId == other.opaqueId && attemptId == other.attemptId

    override fun hashCode(): Int = 31 * opaqueId.hashCode() + attemptId.hashCode()

    override fun toString(): String = "PhotoRef(redacted)"

    companion object {
        private val OPAQUE_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")

        fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef = PhotoRef(
            opaqueId = opaqueId.also {
                require(OPAQUE_ID_PATTERN.matches(it)) {
                    "PhotoRef identity must be a bounded opaque token"
                }
            },
            attemptId = attemptId,
        )
    }
}

class PhotoLeaseStore {
    private val active = mutableSetOf<PhotoRef>()

    fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef = PhotoRef.issue(attemptId, opaqueId).also(active::add)

    fun isActive(ref: PhotoRef): Boolean = ref in active

    /** Returns false on repeated cleanup; repeated cleanup is still safe. */
    fun revoke(ref: PhotoRef): Boolean = active.remove(ref)

    fun revokeAttempt(attemptId: AttemptId): Int {
        val before = active.size
        active.removeAll { it.attemptId == attemptId }
        return before - active.size
    }
}

enum class ImageOrientation {
    UP,
    RIGHT,
    DOWN,
    LEFT,
}

data class RawPhoto(
    val ref: PhotoRef,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val orientation: ImageOrientation,
    val colorSpace: String,
    val metadataKeys: Set<String>,
)

data class NormalizedPhotoInput(
    val ref: PhotoRef,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val orientation: ImageOrientation,
    val colorSpace: String,
    val metadataKeys: Set<String>,
    val contentDigest: String,
) {
    init {
        require(orientation == ImageOrientation.UP)
        require(colorSpace == "sRGB")
        require(metadataKeys.isEmpty())
        require(contentDigest.matches(Regex("[a-f0-9]{64}")))
    }
}

class PhotoPreparationException(message: String) : IllegalArgumentException(message)

/**
 * Common deterministic preparation boundary. Platform adapters must decode,
 * orient, bound, and re-encode bytes before entering this layer; the common
 * layer validates those invariants and owns the remaining size/digest bounds.
 * It never relabels unsanitized bytes as metadata-free output.
 */
class PhotoInputNormalizer(
    private val maxBytes: Int = 10 * 1024 * 1024,
    private val maxDimension: Int = 4096,
    private val digest: (ByteArray) -> String,
) {
    fun normalize(raw: RawPhoto): NormalizedPhotoInput {
        require(raw.bytes.isNotEmpty()) { "Photo bytes must not be empty" }
        require(raw.bytes.size <= maxBytes) { "Photo exceeds the size bound" }
        require(raw.width in 1..maxDimension) { "Photo width is outside the bound" }
        require(raw.height in 1..maxDimension) { "Photo height is outside the bound" }
        require(raw.orientation == ImageOrientation.UP) {
            "Platform adapter must apply orientation before common normalization"
        }
        require(raw.colorSpace == "sRGB") {
            "Platform adapter must convert the photo to sRGB before common normalization"
        }
        require(raw.metadataKeys.isEmpty()) {
            "Platform adapter must strip metadata before common normalization"
        }

        val normalizedBytes = raw.bytes.copyOf()
        val normalizedDigest = digest(normalizedBytes)
        require(normalizedDigest.matches(Regex("[a-f0-9]{64}"))) {
            "Platform digest must be SHA-256"
        }
        return NormalizedPhotoInput(
            ref = raw.ref,
            bytes = normalizedBytes,
            width = raw.width,
            height = raw.height,
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
            contentDigest = normalizedDigest,
        )
    }
}

class PhotoSessionStore {
    private val contents = mutableMapOf<PhotoRef, NormalizedPhotoInput>()

    fun put(input: NormalizedPhotoInput) {
        contents[input.ref] = input
    }

    fun read(ref: PhotoRef): NormalizedPhotoInput? = contents[ref]

    /** Idempotent terminal cleanup for success, failure, cancellation, and replacement. */
    fun clearAttempt(attemptId: AttemptId): Int {
        contents.values
            .filter { it.ref.attemptId == attemptId }
            .forEach { it.bytes.fill(0) }
        val before = contents.size
        contents.keys.removeAll { it.attemptId == attemptId }
        return before - contents.size
    }
}

sealed interface PhotoAnalysisState {
    data object Capture : PhotoAnalysisState
    data class Preparing(val ref: PhotoRef) : PhotoAnalysisState
    data class Ready(val input: NormalizedPhotoInput) : PhotoAnalysisState
    data class Analyzing(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisState
    data class CandidateReview(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisState
    data class Recovery(val reason: RecoveryReason, val ref: PhotoRef? = null) : PhotoAnalysisState
    data class Backgrounded(val snapshot: PhotoAnalysisBackgroundSnapshot) : PhotoAnalysisState
}

/** Process-safe snapshot: opaque identity only; photo bytes stay in the session store. */
sealed interface PhotoAnalysisBackgroundSnapshot {
    data object Capture : PhotoAnalysisBackgroundSnapshot
    data class Preparing(val ref: PhotoRef) : PhotoAnalysisBackgroundSnapshot
    data class Ready(val ref: PhotoRef) : PhotoAnalysisBackgroundSnapshot
    data class Analyzing(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisBackgroundSnapshot
    data class CandidateReview(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisBackgroundSnapshot
    data class Recovery(val reason: RecoveryReason, val ref: PhotoRef?) : PhotoAnalysisBackgroundSnapshot
}

enum class RecoveryReason {
    PERMISSION_DENIED,
    CAMERA_UNAVAILABLE,
    CAPTURE_FAILED,
    POOR_IMAGE,
    NO_FOOD,
    OFFLINE,
    CANCELLED,
}

sealed interface PhotoAnalysisEvent {
    data class PhotoCaptured(val ref: PhotoRef) : PhotoAnalysisEvent
    data class PreparationSucceeded(val input: NormalizedPhotoInput) : PhotoAnalysisEvent
    data class PreparationFailed(val reason: RecoveryReason) : PhotoAnalysisEvent
    data object PermissionDenied : PhotoAnalysisEvent
    data class AnalysisStarted(val requestId: RequestId) : PhotoAnalysisEvent
    data class AnalysisSucceeded(val requestId: RequestId) : PhotoAnalysisEvent
    data class ReplacePhoto(val ref: PhotoRef) : PhotoAnalysisEvent
    data object Cancel : PhotoAnalysisEvent
    data object ScanAgain : PhotoAnalysisEvent
    data object Backgrounded : PhotoAnalysisEvent
    data object Restored : PhotoAnalysisEvent
    data class RestoreFromSnapshot(val snapshot: PhotoAnalysisBackgroundSnapshot) : PhotoAnalysisEvent
}

data class AnalysisAttemptContext(val attemptId: AttemptId, val snapshot: ConfigurationSnapshot)
