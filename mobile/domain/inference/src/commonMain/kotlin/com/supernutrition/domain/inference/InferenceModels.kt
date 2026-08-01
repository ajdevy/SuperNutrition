package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        requireNonBlankValue(userMessageKey, "Capability user message key must not be blank")
        requireNonBlankValue(engineId, "Capability engine ID must not be blank")
        if (state == CapabilityState.READY) {
            require(reason == CapabilityReason.NONE) {
                "Ready capability must not report an unavailable reason"
            }
            requireNonBlankValue(modelVersion.orEmpty(), "Ready capability must identify its model version")
        } else {
            require(reason != CapabilityReason.NONE) {
                "Unavailable capability must report an unavailable reason"
            }
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
            require(candidates.isNotEmpty()) { "Candidate outcome must not be empty" }
            require(candidates.map(InferenceCandidate::rank).toSet().size == candidates.size) {
                "Candidate ranks must be unique"
            }
            require(candidates.map(InferenceCandidate::rank).sorted() == (1..candidates.size).toList()) {
                "Candidate ranks must be contiguous and start at one"
            }
            require(candidates.map(InferenceCandidate::candidateId).toSet().size == candidates.size) {
                "Candidate IDs must be unique"
            }
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
            requireNonBlankValue(userMessageKey, "Abstention message key must not be blank")
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
            requireNonBlankValue(userMessageKey, "Failure message key must not be blank")
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
        requireNonBlankValue(candidateId, "Candidate ID must not be blank")
        requireNonBlankValue(label, "Candidate label must not be blank")
        require(rank > 0) { "Candidate rank must be positive" }
    }
}

@Serializable
data class Confidence(val score: Double, val band: ConfidenceBand) {
    init {
        require(score.isFinite()) { "Confidence score must be finite" }
        require(score in 0.0..1.0) { "Confidence score must be between zero and one" }
        require(band == ConfidenceBand.fromScore(score)) {
            "Confidence band must match its score"
        }
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

    ;

    companion object {
        fun fromScore(score: Double): ConfidenceBand = when {
            score < 0.5 -> LOW
            score < 0.8 -> MEDIUM
            else -> HIGH
        }
    }
}

@Serializable
data class InferenceAssumption(val code: AssumptionCode, val userMessageKey: String) {
    init {
        requireNonBlankValue(userMessageKey, "Assumption message key must not be blank")
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
        requireNonBlankValue(engineId, "Provenance engine ID must not be blank")
        requireNonBlankValue(modelVersion, "Provenance model version must not be blank")
        requireNonBlankValue(calibrationVersion, "Provenance calibration version must not be blank")
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
