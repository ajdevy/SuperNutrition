package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * v3 result types.
 *
 * These are new rather than reused, which was not obvious until the v1 types
 * were tried. `InferenceProvenance` calls `requireContractV1` in its
 * initializer, so it rejects a v3 contract version outright; `InferenceRoute`
 * has no `local-text`; and `AbstentionReason` has no
 * `description-unsegmentable`. v1 is released and immutable, so none of those
 * can be widened in place. The decision log recorded this as "modality-neutral
 * Kotlin equivalents rather than an added field — a wider break than
 * anticipated", and this file is that break.
 *
 * What is reused is everything with no version opinion: [Confidence],
 * [ConfidenceBand], [InferenceAssumption], [AssumptionCode], and
 * [InferenceFailureCode]. Duplicating those would create the second source of
 * truth this repository keeps paying for.
 */
internal fun requireContractV3(value: String, message: String): String = value.also {
    require(it == INFERENCE_CONTRACT_V3) { message }
}

/**
 * Where a result came from. v3 adds `local-text`, because "local" alone cannot
 * distinguish an on-device vision engine from an on-device text engine, and
 * FR-6 reports their availability separately.
 */
@Serializable
enum class InferenceRouteV3 {
    @SerialName("local")
    LOCAL,

    @SerialName("local-text")
    LOCAL_TEXT,

    @SerialName("remote")
    REMOTE,
}

/** v1's abstention reasons plus the one only typed input can produce. */
@Serializable
enum class AbstentionReasonV3 {
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

    @SerialName("invalid-input")
    INVALID_INPUT,

    /** The engine could not split the description into items (FM-6). */
    @SerialName("description-unsegmentable")
    DESCRIPTION_UNSEGMENTABLE,
}

@Serializable
data class InferenceProvenanceV3(
    val route: InferenceRouteV3,
    val engineId: String,
    val modelVersion: String,
    val taxonomyVersion: String,
    val calibrationVersion: String,
    val contractVersion: String,
) {
    init {
        requireNonBlankValue(engineId, "Provenance engine ID must not be blank")
        requireNonBlankValue(modelVersion, "Provenance model version must not be blank")
        requireNonBlankValue(taxonomyVersion, "Provenance taxonomy version must not be blank")
        requireNonBlankValue(calibrationVersion, "Provenance calibration version must not be blank")
        requireContractV3(contractVersion, "Provenance must use contract-v3")
    }
}

@Serializable
data class InferenceCandidateV3(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: Confidence,
    val unresolvedAssumptions: List<InferenceAssumption>,
    val provenance: InferenceProvenanceV3,
) {
    init {
        requireNonBlankValue(candidateId, "Candidate ID must not be blank")
        requireNonBlankValue(label, "Candidate label must not be blank")
        require(rank > 0) { "Candidate rank must be positive" }
    }
}

/**
 * Which words of the description an item group derived from.
 *
 * Character offsets into the description exactly as the user typed it. This is
 * what makes a proposed split editable with visible provenance rather than a
 * black box: the user can see that "two boiled eggs" produced this group.
 */
@Serializable
data class InferenceSourceSpan(val start: Int, val end: Int) {
    init {
        require(start >= 0) { "Source span start must not be negative" }
        require(end > start) { "Source span end must be after its start" }
    }
}

/**
 * One detected food item and the candidates proposed for it.
 *
 * The shape both modalities share: the photo path already produces multiple
 * candidates per meal (FR-13), and text returns one group per detected item so
 * a multi-item description cannot silently collapse to one (FM-6).
 */
@Serializable
data class InferenceItemGroup(
    val groupId: String,
    val sourceSpan: InferenceSourceSpan? = null,
    val candidates: List<InferenceCandidateV3>,
) {
    init {
        requireNonBlankValue(groupId, "Item group ID must not be blank")
        require(candidates.isNotEmpty()) { "Item group must carry at least one candidate" }
        require(candidates.map(InferenceCandidateV3::rank).toSet().size == candidates.size) {
            "Candidate ranks must be unique within an item group"
        }
        require(candidates.map(InferenceCandidateV3::rank).sorted() == (1..candidates.size).toList()) {
            "Candidate ranks must be contiguous and start at one"
        }
    }
}

/** What a v3 engine concluded. Identity and evidence only; never a number. */
@Serializable
sealed interface InferenceOutcomeV3 {
    @Serializable
    @SerialName("candidates")
    data class Candidates(val itemGroups: List<InferenceItemGroup>) : InferenceOutcomeV3 {
        init {
            require(itemGroups.isNotEmpty()) { "Candidate outcome must not be empty" }
            require(itemGroups.map(InferenceItemGroup::groupId).toSet().size == itemGroups.size) {
                "Item group IDs must be unique within a result"
            }
        }
    }

    /** Photo output preserves the mainline visible-component and mass evidence shape. */
    @Serializable
    @SerialName("components")
    data class Components(val components: List<InferenceV3VisibleComponent>) : InferenceOutcomeV3 {
        init {
            require(components.isNotEmpty()) { "Component outcome must not be empty" }
            require(components.map(InferenceV3VisibleComponent::componentId).toSet().size == components.size) {
                "Component IDs must be unique"
            }
        }
    }

    @Serializable
    @SerialName("abstention")
    data class Abstention(
        val reason: AbstentionReasonV3,
        val userMessageKey: String,
        val provenance: InferenceProvenanceV3,
    ) : InferenceOutcomeV3 {
        init {
            requireNonBlankValue(userMessageKey, "Abstention user message key must not be blank")
        }
    }

    @Serializable
    @SerialName("failure")
    data class Failure(
        val code: InferenceFailureCode,
        val retryable: Boolean,
        val userMessageKey: String,
        val provenance: InferenceProvenanceV3,
    ) : InferenceOutcomeV3 {
        init {
            requireNonBlankValue(userMessageKey, "Failure user message key must not be blank")
        }
    }
}

/**
 * A v3 engine result, carrying the modality it answered for.
 *
 * Segmentation is enforced here rather than left to a caller, because the
 * contract makes it a property of the result: `source_span = "required"` for
 * text and `partial_segmentation = "reject"`. A text result whose groups are
 * only partly anchored is the FM-6 failure with provenance attached — some
 * items traceable to the user's words and some not — so it fails construction
 * rather than reaching a surface that would have to decide what to do with it.
 */
@Serializable
data class InferenceResultV3(
    val contractVersion: String,
    val modality: InferenceModality,
    val outcome: InferenceOutcomeV3,
) {
    init {
        requireContractV3(contractVersion, "Result must use contract-v3")
        when (modality) {
            InferenceModality.TEXT -> when (outcome) {
                is InferenceOutcomeV3.Candidates -> {
                    require(outcome.itemGroups.all { it.sourceSpan != null }) {
                        "Every text item group must carry the span it derived from"
                    }
                }

                is InferenceOutcomeV3.Components ->
                    require(false) { "Text v3 results must not use photo component evidence" }

                is InferenceOutcomeV3.Abstention,
                is InferenceOutcomeV3.Failure,
                -> Unit
            }

            InferenceModality.PHOTO -> when (outcome) {
                is InferenceOutcomeV3.Components -> Unit

                is InferenceOutcomeV3.Candidates ->
                    require(false) { "Photo v3 results must not use text candidate groups" }

                is InferenceOutcomeV3.Abstention,
                is InferenceOutcomeV3.Failure,
                -> Unit
            }
        }
    }
}
