package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PHOTO_INFERENCE_CONTRACT_V1 = "photo-inference-engine/v1"

private val DIGEST_PATTERN = Regex("[a-f0-9]{64}")
private val OPAQUE_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")

private fun requireNonBlank(value: String, message: String) {
    if (value.isBlank()) {
        throw IllegalArgumentException(message)
    }
}

private fun requireContractV1(value: String, message: String) {
    if (value != PHOTO_INFERENCE_CONTRACT_V1) {
        throw IllegalArgumentException(message)
    }
}

private fun invalidContract(message: String): Nothing = throw IllegalArgumentException(message)

private fun requireReadyCapability(reason: CapabilityReason, modelVersion: String?) {
    if (reason != CapabilityReason.NONE) {
        throw IllegalArgumentException("Ready capability must not report an unavailable reason")
    }
    if (modelVersion.isNullOrBlank()) {
        throw IllegalArgumentException("Ready capability must identify its model version")
    }
}

private fun validateCandidates(candidates: List<InferenceCandidate>) {
    if (candidates.isEmpty()) {
        invalidContract("Candidate outcome must not be empty")
    }
    if (candidates.asSequence().map(InferenceCandidate::rank).toSet().size != candidates.size) {
        invalidContract("Candidate ranks must be unique")
    }
    if (candidates.asSequence().map(InferenceCandidate::rank).sorted().toList() !=
        (1..candidates.size).toList()
    ) {
        invalidContract("Candidate ranks must be contiguous and start at one")
    }
    if (candidates.asSequence().map(InferenceCandidate::candidateId).toSet().size != candidates.size) {
        invalidContract("Candidate IDs must be unique")
    }
}

private fun requireFiniteScore(score: Double) {
    if (!score.isFinite()) {
        throw IllegalArgumentException("Confidence score must be finite")
    }
}

private fun requireScoreInRange(score: Double) {
    if (score < 0.0) {
        throw IllegalArgumentException("Confidence score must be between zero and one")
    }
    if (score > 1.0) {
        throw IllegalArgumentException("Confidence score must be between zero and one")
    }
}

private fun requirePositiveRank(rank: Int) {
    if (rank <= 0) {
        throw IllegalArgumentException("Candidate rank must be positive")
    }
}

/** Attempt identity shared by photo preparation and every Engine adapter. */
data class AttemptId(val value: String) {
    init {
        requireNonBlank(value, "AttemptId must not be blank")
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
        requireNonBlank(requestId, "Inference request ID must not be blank")
        requireNonBlank(configurationVersion, "Configuration version must not be blank")
        require(DIGEST_PATTERN.matches(configurationDigest)) {
            "Configuration digest must be a lowercase SHA-256 digest"
        }
    }
}

/** In-memory Engine input. Photo bytes remain behind the lease/content port. */
data class PhotoInferenceRequest(val photoRef: PhotoRef, val attemptContext: InferenceAttemptContext) {
    init {
        if (photoRef.attemptId != attemptContext.attemptId) {
            throw IllegalArgumentException(
                "PhotoRef and inference context must belong to the same attempt",
            )
        }
    }
}

interface PhotoInferenceEngine {
    /** Every implementation must explicitly bind itself to the frozen contract release. */
    val contractVersion: String

    suspend fun capability(): EngineCapability

    suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult
}

@Serializable
data class EngineCapability(
    val contractVersion: String,
    val state: CapabilityState,
    val reason: CapabilityReason,
    val userMessageKey: String,
    val engineId: String,
    val modelVersion: String? = null,
) {
    init {
        requireContractV1(contractVersion, "Capability must use contract-v1")
        requireNonBlank(userMessageKey, "Capability user message key must not be blank")
        requireNonBlank(engineId, "Capability engine ID must not be blank")
        if (state == CapabilityState.READY) {
            requireReadyCapability(reason, modelVersion)
        }
    }
}

@Serializable
enum class CapabilityState {
    @SerialName("unavailable")
    UNAVAILABLE,

    @SerialName("model-required")
    MODEL_REQUIRED,

    @SerialName("downloading")
    DOWNLOADING,

    @SerialName("ready")
    READY,

    @SerialName("temporarily-unavailable")
    TEMPORARILY_UNAVAILABLE,

    @SerialName("unsupported")
    UNSUPPORTED,
}

@Serializable
enum class CapabilityReason {
    @SerialName("none")
    NONE,

    @SerialName("device-unsupported")
    DEVICE_UNSUPPORTED,

    @SerialName("model-missing")
    MODEL_MISSING,

    @SerialName("model-downloading")
    MODEL_DOWNLOADING,

    @SerialName("resource-budget")
    RESOURCE_BUDGET,

    @SerialName("temporary-unavailable")
    TEMPORARY_UNAVAILABLE,
}

@Serializable
data class PhotoInferenceResult(val contractVersion: String, val outcome: InferenceOutcome) {
    init {
        requireContractV1(contractVersion, "Inference result must use contract-v1")
    }
}

@Serializable
sealed class InferenceOutcome {
    @Serializable
    @SerialName("candidates")
    data class Candidates(val candidates: List<InferenceCandidate>) : InferenceOutcome() {
        init {
            validateCandidates(candidates)
        }
    }

    @Serializable
    @SerialName("abstention")
    data class Abstention(
        val reason: AbstentionReason,
        val userMessageKey: String,
        val provenance: InferenceProvenance,
    ) : InferenceOutcome() {
        init {
            requireNonBlank(userMessageKey, "Abstention message key must not be blank")
        }
    }

    @Serializable
    @SerialName("failure")
    data class Failure(
        val code: InferenceFailureCode,
        val retryable: Boolean,
        val userMessageKey: String,
        val provenance: InferenceProvenance,
    ) : InferenceOutcome() {
        init {
            requireNonBlank(userMessageKey, "Failure message key must not be blank")
        }
    }
}

@Serializable
data class InferenceCandidate(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: Confidence,
    val unresolvedAssumptions: List<InferenceAssumption>,
    val provenance: InferenceProvenance,
) {
    init {
        requireNonBlank(candidateId, "Candidate ID must not be blank")
        requireNonBlank(label, "Candidate label must not be blank")
        requirePositiveRank(rank)
    }
}

@Serializable
data class Confidence(val score: Double, val band: ConfidenceBand) {
    init {
        requireFiniteScore(score)
        requireScoreInRange(score)
    }
}

@Serializable
enum class ConfidenceBand {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
}

@Serializable
data class InferenceAssumption(val code: AssumptionCode, val userMessageKey: String) {
    init {
        requireNonBlank(userMessageKey, "Assumption message key must not be blank")
    }
}

@Serializable
enum class AssumptionCode {
    @SerialName("portion-unknown")
    PORTION_UNKNOWN,

    @SerialName("preparation-unknown")
    PREPARATION_UNKNOWN,

    @SerialName("hidden-ingredients-unknown")
    HIDDEN_INGREDIENTS_UNKNOWN,

    @SerialName("mixed-dish")
    MIXED_DISH,
}

@Serializable
data class InferenceProvenance(
    val route: InferenceRoute,
    val engineId: String,
    val modelVersion: String,
    val calibrationVersion: String,
    val contractVersion: String,
) {
    init {
        requireNonBlank(engineId, "Provenance engine ID must not be blank")
        requireNonBlank(modelVersion, "Provenance model version must not be blank")
        requireNonBlank(calibrationVersion, "Provenance calibration version must not be blank")
        requireContractV1(contractVersion, "Provenance must use contract-v1")
    }
}

@Serializable
enum class InferenceRoute {
    @SerialName("local")
    LOCAL,

    @SerialName("remote")
    REMOTE,
}

@Serializable
enum class AbstentionReason {
    @SerialName("identity-ambiguous")
    IDENTITY_AMBIGUOUS,

    @SerialName("no-food-detected")
    NO_FOOD_DETECTED,

    @SerialName("image-quality")
    IMAGE_QUALITY,

    @SerialName("portion-ambiguous")
    PORTION_AMBIGUOUS,

    @SerialName("unsupported-content")
    UNSUPPORTED_CONTENT,
}

@Serializable
enum class InferenceFailureCode {
    @SerialName("invalid-input")
    INVALID_INPUT,

    @SerialName("model-unavailable")
    MODEL_UNAVAILABLE,

    @SerialName("resource-limit")
    RESOURCE_LIMIT,

    @SerialName("temporary-unavailable")
    TEMPORARY_UNAVAILABLE,
}

/** Versioned, allowlisted operational telemetry with no photo or user text. */
@Serializable
data class InferenceTelemetryEvent(
    val contractVersion: String,
    val eventType: InferenceTelemetryEventType,
    val route: InferenceRoute,
    val capabilityState: CapabilityState,
    val outcome: InferenceTelemetryOutcome,
    val durationBucket: TelemetryDurationBucket,
    val resourceBucket: TelemetryResourceBucket,
) {
    init {
        requireContractV1(contractVersion, "Telemetry must use contract-v1")
    }
}

@Serializable
enum class InferenceTelemetryEventType {
    @SerialName("attempt")
    ATTEMPT,

    @SerialName("capability")
    CAPABILITY,
}

@Serializable
enum class InferenceTelemetryOutcome {
    @SerialName("candidates")
    CANDIDATES,

    @SerialName("abstention")
    ABSTENTION,

    @SerialName("failure")
    FAILURE,

    @SerialName("cancelled")
    CANCELLED,
}

@Serializable
enum class TelemetryDurationBucket {
    @SerialName("under-1s")
    UNDER_1S,

    @SerialName("1-to-5s")
    FROM_1_TO_5S,

    @SerialName("over-5s")
    OVER_5S,
}

@Serializable
enum class TelemetryResourceBucket {
    @SerialName("within-budget")
    WITHIN_BUDGET,

    @SerialName("near-budget")
    NEAR_BUDGET,

    @SerialName("over-budget")
    OVER_BUDGET,
}

object InferenceContractJson {
    private val json = Json {
        classDiscriminator = "outcomeType"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(result: PhotoInferenceResult): String = json.encodeToString(PhotoInferenceResult.serializer(), result)

    fun encodeCapability(capability: EngineCapability): String =
        json.encodeToString(EngineCapability.serializer(), capability)

    fun decodeCapability(payload: String): EngineCapability =
        json.decodeFromString(EngineCapability.serializer(), payload)

    fun decode(payload: String): PhotoInferenceResult =
        json.decodeFromString(PhotoInferenceResult.serializer(), payload)

    fun encodeTelemetry(event: InferenceTelemetryEvent): String =
        json.encodeToString(InferenceTelemetryEvent.serializer(), event)

    fun decodeTelemetry(payload: String): InferenceTelemetryEvent =
        json.decodeFromString(InferenceTelemetryEvent.serializer(), payload)
}
