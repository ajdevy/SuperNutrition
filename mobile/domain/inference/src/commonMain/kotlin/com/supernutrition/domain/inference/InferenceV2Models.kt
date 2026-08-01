@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

private fun isFiniteValue(value: Double): Boolean = value.isFinite()

private fun isUnitInterval(value: Double): Boolean = value >= 0.0 && value <= 1.0

private fun isPositiveUnitInterval(value: Double): Boolean = value > 0.0 && value <= 1.0

private fun isFiniteNonNegative(value: Double): Boolean = value.isFinite() && value >= 0.0

private fun fitsNormalizedBounds(left: Double, top: Double, width: Double, height: Double): Boolean =
    left + width <= 1.0 && top + height <= 1.0

@Serializable
data class InferenceV2Capability(
    val contractVersion: String,
    val state: CapabilityState,
    val reason: CapabilityReason,
    val userMessageKey: String,
    val engineId: String,
    val modelVersion: String,
    val taxonomyVersion: String,
    val calibrationVersion: String,
) {
    init {
        requireContractV2(contractVersion, "Capability must use inference-evidence/v2")
        requireV2Identifier(userMessageKey, "Capability message key must be a bounded identifier")
        requireV2Identifier(engineId, "Capability engine ID must be a bounded identifier")
        if (state == CapabilityState.READY) {
            requireV2(reason == CapabilityReason.NONE, "Ready capability must not report an unavailable reason")
            requireNonBlankValue(modelVersion, "Ready capability must identify its model version")
            requireNonBlankValue(taxonomyVersion, "Ready capability must identify its taxonomy version")
            requireNonBlankValue(calibrationVersion, "Ready capability must identify its calibration version")
        } else {
            requireV2(reason != CapabilityReason.NONE, "Unavailable capability must report an unavailable reason")
        }
    }
}

@Serializable
data class InferenceV2Result(
    val contractVersion: String,
    val inputModality: InferenceInputModality,
    val outcome: InferenceV2Outcome,
) {
    init {
        requireContractV2(contractVersion, "Inference result must use inference-evidence/v2")
        if (inputModality == InferenceInputModality.TEXT) {
            if (outcome is InferenceV2Outcome.Components) {
                outcome.components.forEach { component ->
                    requireV2(
                        component.mass is MassEvidence.NotEvaluable,
                        "Text input must never carry an estimated or abstained mass state",
                    )
                }
            }
        }
    }
}

@Serializable
enum class InferenceInputModality {
    @SerialName("photo")
    PHOTO,

    @SerialName("text")
    TEXT,
}

@Serializable
sealed interface InferenceV2Outcome {
    @Serializable
    @SerialName("components")
    data class Components(val components: List<VisibleComponent>) : InferenceV2Outcome {
        init {
            requireV2(components.isNotEmpty(), "Component outcome must not be empty")
            requireV2(
                components.map(VisibleComponent::componentId).toSet().size == components.size,
                "Component IDs must be unique",
            )
        }
    }

    @Serializable
    @SerialName("abstention")
    data class Abstention(
        val reason: V2AbstentionReason,
        val userMessageKey: String,
        val provenance: InferenceV2Provenance,
    ) : InferenceV2Outcome {
        init {
            requireV2Identifier(userMessageKey, "Abstention message key must be a bounded identifier")
        }
    }

    @Serializable
    @SerialName("failure")
    data class Failure(
        val code: V2InferenceFailureCode,
        val retryable: Boolean,
        val userMessageKey: String,
        val provenance: InferenceV2Provenance,
    ) : InferenceV2Outcome {
        init {
            requireV2Identifier(userMessageKey, "Failure message key must be a bounded identifier")
        }
    }
}

@Serializable
data class VisibleComponent(
    val componentId: String,
    val candidate: LocalizedCandidateIdentity,
    val confidence: CalibratedConfidence,
    val visibility: ComponentVisibility,
    val ambiguity: ComponentAmbiguity,
    val region: NormalizedVisibleRegion? = null,
    val mass: MassEvidence,
    val provenance: InferenceV2Provenance,
) {
    init {
        requireV2Identifier(componentId, "Component ID must be a bounded opaque identifier")
    }
}

@Serializable
data class LocalizedCandidateIdentity(
    val candidateId: String,
    val label: String,
    val locale: String,
) {
    init {
        requireV2Identifier(candidateId, "Candidate ID must be a bounded identifier")
        requireNonBlankValue(label, "Candidate label must not be blank")
        requireV2Locale(locale, "Candidate locale must be a bounded BCP-47-style tag")
    }
}

@Serializable
data class CalibratedConfidence(val score: Double, val band: ConfidenceBand) {
    init {
        requireV2(isFiniteValue(score), "Confidence score must be finite")
        requireV2(isUnitInterval(score), "Confidence score must be between zero and one")
        requireV2(band == ConfidenceBand.fromScore(score), "Confidence band must match its score")
    }
}

@Serializable
enum class ComponentVisibility {
    @SerialName("visible")
    VISIBLE,

    @SerialName("partially-occluded")
    PARTIALLY_OCCLUDED,
}

@Serializable
enum class ComponentAmbiguity {
    @SerialName("none")
    NONE,

    @SerialName("identity-ambiguous")
    IDENTITY_AMBIGUOUS,

    @SerialName("mixed-content")
    MIXED_CONTENT,
}

/** Normalized region evidence only; it is neither a mask nor a file or URI reference. */
@Serializable
data class NormalizedVisibleRegion(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    init {
        requireV2(
            doubleArrayOf(left, top, width, height).all(::isFiniteValue),
            "Normalized visible region values must be finite",
        )
        requireV2(
            doubleArrayOf(left, top).all(::isUnitInterval),
            "Normalized visible region origin must be in bounds",
        )
        requireV2(
            doubleArrayOf(width, height).all(::isPositiveUnitInterval),
            "Normalized visible region size must be positive and in bounds",
        )
        requireV2(
            fitsNormalizedBounds(left, top, width, height),
            "Normalized visible region must remain within image bounds",
        )
    }
}

@Serializable
@JsonClassDiscriminator("massType")
sealed interface MassEvidence {
    @Serializable
    @SerialName("estimated")
    data class Estimated(
        val range: GramRange,
        val calibrationAssumptions: List<MassCalibrationAssumption>,
    ) : MassEvidence {
        init {
            requireV2(
                calibrationAssumptions.isNotEmpty(),
                "Estimated mass must declare at least one calibration assumption",
            )
            requireV2(
                calibrationAssumptions.toSet().size == calibrationAssumptions.size,
                "Estimated mass calibration assumptions must be unique",
            )
        }
    }

    @Serializable
    @SerialName("not-evaluable")
    data class NotEvaluable(
        val reason: MassNotEvaluableReason,
        val userMessageKey: String,
    ) : MassEvidence {
        init {
            requireV2Identifier(userMessageKey, "Mass not-evaluable message key must be a bounded identifier")
        }
    }

    @Serializable
    @SerialName("abstained")
    data class Abstained(
        val reason: MassAbstentionReason,
        val userMessageKey: String,
    ) : MassEvidence {
        init {
            requireV2Identifier(userMessageKey, "Mass abstention message key must be a bounded identifier")
        }
    }
}

@Serializable
data class GramRange(val lowerGrams: Double, val upperGrams: Double) {
    init {
        requireV2(
            doubleArrayOf(lowerGrams, upperGrams).all(::isFiniteNonNegative),
            "Gram range bounds must be finite and non-negative",
        )
        requireV2(lowerGrams <= upperGrams, "Gram range lower bound must not exceed upper bound")
    }
}

@Serializable
enum class MassCalibrationAssumption {
    @SerialName("single-view-approximation")
    SINGLE_VIEW_APPROXIMATION,

    @SerialName("reference-geometry-available")
    REFERENCE_GEOMETRY_AVAILABLE,

    @SerialName("partial-occlusion")
    PARTIAL_OCCLUSION,
}

@Serializable
enum class MassNotEvaluableReason {
    @SerialName("ambiguous-portion")
    AMBIGUOUS_PORTION,

    @SerialName("mixed-or-occluded")
    MIXED_OR_OCCLUDED,

    @SerialName("unsupported-geometry")
    UNSUPPORTED_GEOMETRY,

    @SerialName("missing-calibration")
    MISSING_CALIBRATION,

    @SerialName("text-has-no-visible-portion")
    TEXT_HAS_NO_VISIBLE_PORTION,
}

@Serializable
enum class MassAbstentionReason {
    @SerialName("identity-ambiguous")
    IDENTITY_AMBIGUOUS,

    @SerialName("image-quality")
    IMAGE_QUALITY,

    @SerialName("unsupported-content")
    UNSUPPORTED_CONTENT,
}

@Serializable
data class InferenceV2Provenance(
    val route: InferenceRoute,
    val engineId: String,
    val modelVersion: String,
    val taxonomyVersion: String,
    val calibrationVersion: String,
    val contractVersion: String,
) {
    init {
        requireV2Identifier(engineId, "Provenance engine ID must be a bounded identifier")
        requireNonBlankValue(modelVersion, "Provenance model version must not be blank")
        requireNonBlankValue(taxonomyVersion, "Provenance taxonomy version must not be blank")
        requireNonBlankValue(calibrationVersion, "Provenance calibration version must not be blank")
        requireContractV2(contractVersion, "Provenance must use inference-evidence/v2")
    }
}

@Serializable
enum class V2AbstentionReason {
    @SerialName("identity-ambiguous")
    IDENTITY_AMBIGUOUS,

    @SerialName("no-food-detected")
    NO_FOOD_DETECTED,

    @SerialName("image-quality")
    IMAGE_QUALITY,

    @SerialName("unsupported-content")
    UNSUPPORTED_CONTENT,
}

@Serializable
enum class V2InferenceFailureCode {
    @SerialName("invalid-input")
    INVALID_INPUT,

    @SerialName("model-unavailable")
    MODEL_UNAVAILABLE,

    @SerialName("resource-limit")
    RESOURCE_LIMIT,

    @SerialName("temporary-unavailable")
    TEMPORARY_UNAVAILABLE,
}
