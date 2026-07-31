package com.supernutrition.domain.inference

const val PHOTO_INFERENCE_CONTRACT_V1 = "photo-inference-engine/v1"

/** Shared domain validation for immutable, language-neutral contract values. */
const val SHA256_DIGEST_LENGTH = 64

fun isSha256Digest(value: String): Boolean =
    value.length == SHA256_DIGEST_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

fun requireSha256Digest(value: String, message: String): String = value.also {
    require(isSha256Digest(it)) { message }
}

fun requireNonBlankValue(value: String, message: String): String = value.also {
    require(it.isNotBlank()) { message }
}

internal fun requireContractV1(value: String, message: String): String = value.also {
    require(it == PHOTO_INFERENCE_CONTRACT_V1) { message }
}

private val OPAQUE_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")

/** Attempt identity shared by photo preparation and every Engine adapter. */
data class AttemptId(val value: String) {
    init {
        requireNonBlankValue(value, "AttemptId must not be blank")
    }
}

/** Opaque, attempt-leased photo identity. It is not a URI or platform object. */
class PhotoRef private constructor(val opaqueId: String, val attemptId: AttemptId) {
    override fun equals(other: Any?): Boolean =
        other is PhotoRef && opaqueId == other.opaqueId && attemptId == other.attemptId

    override fun hashCode(): Int = 31 * opaqueId.hashCode() + attemptId.hashCode()

    override fun toString(): String = "PhotoRef(redacted)"

    companion object {
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

/** Immutable snapshot captured before an Engine attempt begins. */
data class InferenceAttemptContext(
    val attemptId: AttemptId,
    val requestId: String,
    val configurationVersion: String,
    val configurationDigest: String,
) {
    init {
        requireNonBlankValue(requestId, "Inference request ID must not be blank")
        requireNonBlankValue(configurationVersion, "Configuration version must not be blank")
        requireSha256Digest(configurationDigest, "Configuration digest must be a lowercase SHA-256 digest")
    }
}

/** In-memory Engine input. Photo bytes remain behind the lease/content port. */
data class PhotoInferenceRequest(val photoRef: PhotoRef, val attemptContext: InferenceAttemptContext) {
    init {
        require(photoRef.attemptId == attemptContext.attemptId) {
            "PhotoRef and inference context must belong to the same attempt"
        }
    }
}

interface PhotoInferenceEngine {
    /** Every implementation must explicitly bind itself to the frozen contract release. */
    val contractVersion: String

    suspend fun capability(): EngineCapability

    suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult
}
