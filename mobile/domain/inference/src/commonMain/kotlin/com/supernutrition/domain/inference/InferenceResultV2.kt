package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * v2 result types.
 *
 * These are new rather than reused, which was not obvious until the v1 types
 * were tried. `InferenceProvenance` calls `requireContractV1` in its
 * initializer, so it rejects a v2 contract version outright; `InferenceRoute`
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
internal fun requireContractV2(value: String, message: String): String = value.also {
    require(it == INFERENCE_CONTRACT_V2) { message }
}

/**
 * Where a result came from. v2 adds `local-text`, because "local" alone cannot
 * distinguish an on-device vision engine from an on-device text engine, and
 * FR-6 reports their availability separately.
 */
@Serializable
enum class InferenceRouteV2 {
    @SerialName("local")
    LOCAL,

    @SerialName("local-text")
    LOCAL_TEXT,

    @SerialName("remote")
    REMOTE,
}

/** v1's abstention reasons plus the one only typed input can produce. */
@Serializable
enum class AbstentionReasonV2 {
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
data class InferenceProvenanceV2(
    val route: InferenceRouteV2,
    val engineId: String,
    val modelVersion: String,
    val calibrationVersion: String,
    val contractVersion: String,
) {
    init {
        requireNonBlankValue(engineId, "Provenance engine ID must not be blank")
        requireNonBlankValue(modelVersion, "Provenance model version must not be blank")
        requireNonBlankValue(calibrationVersion, "Provenance calibration version must not be blank")
        requireContractV2(contractVersion, "Provenance must use contract-v2")
    }
}

@Serializable
data class InferenceCandidateV2(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: Confidence,
    val unresolvedAssumptions: List<InferenceAssumption>,
    val provenance: InferenceProvenanceV2,
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
    val candidates: List<InferenceCandidateV2>,
) {
    init {
        requireNonBlankValue(groupId, "Item group ID must not be blank")
        require(candidates.isNotEmpty()) { "Item group must carry at least one candidate" }
        require(candidates.map(InferenceCandidateV2::rank).toSet().size == candidates.size) {
            "Candidate ranks must be unique within an item group"
        }
    }
}

/** What a v2 engine concluded. Identity and evidence only; never a number. */
@Serializable
sealed interface InferenceOutcomeV2 {
    @Serializable
    @SerialName("candidates")
    data class Candidates(val itemGroups: List<InferenceItemGroup>) : InferenceOutcomeV2 {
        init {
            require(itemGroups.isNotEmpty()) { "Candidate outcome must not be empty" }
            require(itemGroups.map(InferenceItemGroup::groupId).toSet().size == itemGroups.size) {
                "Item group IDs must be unique within a result"
            }
        }
    }

    @Serializable
    @SerialName("abstention")
    data class Abstention(
        val reason: AbstentionReasonV2,
        val userMessageKey: String,
        val provenance: InferenceProvenanceV2,
    ) : InferenceOutcomeV2 {
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
        val provenance: InferenceProvenanceV2,
    ) : InferenceOutcomeV2 {
        init {
            requireNonBlankValue(userMessageKey, "Failure user message key must not be blank")
        }
    }
}

/**
 * A v2 engine result, carrying the modality it answered for.
 *
 * Segmentation is enforced here rather than left to a caller, because the
 * contract makes it a property of the result: `source_span = "required"` for
 * text and `partial_segmentation = "reject"`. A text result whose groups are
 * only partly anchored is the FM-6 failure with provenance attached — some
 * items traceable to the user's words and some not — so it fails construction
 * rather than reaching a surface that would have to decide what to do with it.
 */
@Serializable
data class InferenceResultV2(
    val contractVersion: String,
    val modality: InferenceModality,
    val outcome: InferenceOutcomeV2,
) {
    init {
        requireContractV2(contractVersion, "Result must use contract-v2")
        val groups = (outcome as? InferenceOutcomeV2.Candidates)?.itemGroups.orEmpty()
        when (modality) {
            InferenceModality.TEXT -> require(groups.all { it.sourceSpan != null }) {
                "Every text item group must carry the span it derived from"
            }

            // A photo has no character offsets to point at. Segmentation
            // applies_to text in the contract, so a span here is a shape error
            // rather than extra detail.
            InferenceModality.PHOTO -> require(groups.all { it.sourceSpan == null }) {
                "A photo item group must not carry a text source span"
            }
        }
    }
}
