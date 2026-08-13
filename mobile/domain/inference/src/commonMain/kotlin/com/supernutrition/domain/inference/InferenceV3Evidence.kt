@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Photo evidence carried by inference-evidence/v3. */
@Serializable
data class InferenceV3VisibleComponent(
    val componentId: String,
    val candidate: InferenceV3LocalizedCandidate,
    val confidence: Confidence,
    val visibility: InferenceV3ComponentVisibility,
    val ambiguity: InferenceV3ComponentAmbiguity,
    val region: InferenceV3NormalizedRegion? = null,
    val mass: InferenceV3MassEvidence,
    val provenance: InferenceProvenanceV3,
) {
    init {
        requireV3Identifier(componentId, "Component ID must be a bounded identifier")
        if (mass is InferenceV3MassEvidence.Estimated) {
            require(visibility != InferenceV3ComponentVisibility.PARTIALLY_OCCLUDED || region != null) {
                "Partially occluded estimated components must carry a visible region"
            }
        }
    }
}

@Serializable
data class InferenceV3LocalizedCandidate(val candidateId: String, val label: String, val locale: String) {
    init {
        requireV3Identifier(candidateId, "Candidate ID must be a bounded identifier")
        requireNonBlankValue(label, "Candidate label must not be blank")
        requireV3Locale(locale, "Candidate locale must be a bounded BCP-47-style tag")
    }
}

@Serializable
enum class InferenceV3ComponentVisibility {
    @SerialName("visible")
    VISIBLE,

    @SerialName("partially-occluded")
    PARTIALLY_OCCLUDED,
}

@Serializable
enum class InferenceV3ComponentAmbiguity {
    @SerialName("none")
    NONE,

    @SerialName("identity-ambiguous")
    IDENTITY_AMBIGUOUS,

    @SerialName("mixed-content")
    MIXED_CONTENT,
}

@Serializable
data class InferenceV3NormalizedRegion(val left: Double, val top: Double, val width: Double, val height: Double) {
    init {
        require(listOf(left, top, width, height).all { it.isFinite() }) {
            "Visible region values must be finite"
        }
        require(left in 0.0..1.0 && top in 0.0..1.0) {
            "Visible region origin must be within image bounds"
        }
        require(width > 0.0 && width <= 1.0 && height > 0.0 && height <= 1.0) {
            "Visible region dimensions must be positive and bounded"
        }
        require(left + width <= 1.0 + REGION_EPSILON && top + height <= 1.0 + REGION_EPSILON) {
            "Visible region must remain within image bounds"
        }
    }

    private companion object {
        const val REGION_EPSILON = 1e-9
    }
}

@Serializable
@JsonClassDiscriminator("massType")
sealed interface InferenceV3MassEvidence {
    @Serializable
    @SerialName("estimated")
    data class Estimated(
        val range: InferenceV3GramRange,
        val calibrationAssumptions: List<InferenceV3MassCalibrationAssumption>,
    ) : InferenceV3MassEvidence {
        init {
            require(calibrationAssumptions.isNotEmpty()) {
                "Estimated mass must declare at least one calibration assumption"
            }
            require(calibrationAssumptions.toSet().size == calibrationAssumptions.size) {
                "Estimated mass assumptions must be unique"
            }
        }
    }

    @Serializable
    @SerialName("not-evaluable")
    data class NotEvaluable(val reason: InferenceV3MassNotEvaluableReason, val userMessageKey: String) :
        InferenceV3MassEvidence {
        init {
            requireV3Identifier(userMessageKey, "Mass message key must be a bounded identifier")
        }
    }

    @Serializable
    @SerialName("abstained")
    data class Abstained(val reason: InferenceV3MassAbstentionReason, val userMessageKey: String) :
        InferenceV3MassEvidence {
        init {
            requireV3Identifier(userMessageKey, "Mass message key must be a bounded identifier")
        }
    }
}

@Serializable
data class InferenceV3GramRange(val lowerGrams: Double, val upperGrams: Double) {
    init {
        require(lowerGrams.isFinite() && upperGrams.isFinite()) {
            "Gram range bounds must be finite"
        }
        require(lowerGrams >= 0.0 && lowerGrams < upperGrams) {
            "Gram range must be non-negative and strictly ordered"
        }
    }
}

@Serializable
enum class InferenceV3MassCalibrationAssumption {
    @SerialName("single-view-approximation")
    SINGLE_VIEW_APPROXIMATION,

    @SerialName("reference-geometry-available")
    REFERENCE_GEOMETRY_AVAILABLE,

    @SerialName("partial-occlusion")
    PARTIAL_OCCLUSION,
}

@Serializable
enum class InferenceV3MassNotEvaluableReason {
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
enum class InferenceV3MassAbstentionReason {
    @SerialName("identity-ambiguous")
    IDENTITY_AMBIGUOUS,

    @SerialName("image-quality")
    IMAGE_QUALITY,

    @SerialName("unsupported-content")
    UNSUPPORTED_CONTENT,
}

internal fun requireV3Identifier(value: String, message: String): String = value.also {
    require(V3_IDENTIFIER_PATTERN.matches(it)) { message }
}

internal fun requireV3Locale(value: String, message: String): String = value.also {
    require(V3_LOCALE_PATTERN.matches(it)) { message }
}

private val V3_IDENTIFIER_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")
private val V3_LOCALE_PATTERN = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
