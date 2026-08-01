package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * v2 operational telemetry for a completed identification attempt.
 *
 * v1's [InferenceTelemetryEvent] cannot carry this. It calls `requireContractV1`
 * in its initializer and its route is [InferenceRoute], which has no
 * `local-text` — so recording a text attempt through v1 means reporting it as
 * `local`, and an on-device text attempt becomes indistinguishable in a log from
 * an on-device photo attempt. That is the one distinction this route exists to
 * make, so the event is new rather than widened.
 *
 * What is reused is everything with no version opinion: [InferenceModality],
 * [CapabilityState], and [InferenceTelemetryOutcome]. Duplicating those would
 * create a second source of truth for values that mean the same thing in both
 * releases.
 *
 * Every field is an enum or a bucket except the two engine identifiers, which is
 * the property rather than a coincidence: there is no field a description could
 * be written into. Free text is `loggable = false` and
 * `telemetry_eligible = false` in the contract, and a shape with nowhere to put
 * it enforces that better than a rule asking a caller not to.
 */
@Serializable
data class InferenceTelemetryEventV2(
    val contractVersion: String,
    val modality: InferenceModality,
    val eventName: TelemetryEventNameV2,
    val route: InferenceRouteV2,
    val capabilityTier: CapabilityState,
    val durationBucket: TelemetryDurationBucketV2,
    val resourceBucket: TelemetryResourceBucketV2,
    val terminalOutcome: InferenceTelemetryOutcome,
    val itemGroupCountBucket: TelemetryItemGroupCountBucket,
    val engineId: String,
    val modelVersion: String,
) {
    init {
        requireContractV2(contractVersion, "Telemetry must use contract-v2")
        requireNonBlankValue(engineId, "Telemetry engine ID must not be blank")
        requireNonBlankValue(modelVersion, "Telemetry model version must not be blank")
        when (modality) {
            InferenceModality.TEXT -> require(route != InferenceRouteV2.LOCAL) {
                "A text attempt must not report route 'local'; that is the flattening local-text prevents"
            }

            InferenceModality.PHOTO -> require(route != InferenceRouteV2.LOCAL_TEXT) {
                "A photo attempt cannot have been answered by the text engine"
            }
        }
    }

    companion object {
        /**
         * Derives the event from the result it describes.
         *
         * The route, engine, outcome and item count are read off the result
         * rather than passed in, because every one of them is a fact the result
         * already settled and a parameter is a place for a caller to report
         * something else. Only the three measurements the result cannot know —
         * capability tier, duration, resource use — are supplied.
         */
        fun forAttempt(
            result: InferenceResultV2,
            capabilityTier: CapabilityState,
            durationBucket: TelemetryDurationBucketV2,
            resourceBucket: TelemetryResourceBucketV2,
        ): InferenceTelemetryEventV2 {
            val provenance = result.outcome.reportingProvenance()
            return InferenceTelemetryEventV2(
                contractVersion = INFERENCE_CONTRACT_V2,
                modality = result.modality,
                eventName = TelemetryEventNameV2.ATTEMPT_COMPLETED,
                route = provenance.route,
                capabilityTier = capabilityTier,
                durationBucket = durationBucket,
                resourceBucket = resourceBucket,
                terminalOutcome = result.outcome.terminalOutcome(),
                itemGroupCountBucket = TelemetryItemGroupCountBucket.of(result.outcome.itemGroupCount()),
                engineId = provenance.engineId,
                modelVersion = provenance.modelVersion,
            )
        }
    }
}

/**
 * The single provenance an event may report.
 *
 * A candidates outcome carries one provenance per candidate, and they normally
 * agree. When they do not, the result was assembled from more than one engine
 * and no single event describes it: reporting the first would silently attribute
 * every candidate to whichever engine happened to rank first. That needs one
 * event per route, which nothing asks for yet, so it fails here instead of
 * guessing.
 */
private fun InferenceOutcomeV2.reportingProvenance(): InferenceProvenanceV2 = when (this) {
    is InferenceOutcomeV2.Candidates -> {
        val provenances = itemGroups.flatMap { group -> group.candidates.map(InferenceCandidateV2::provenance) }
        val distinct = provenances.distinct()
        require(distinct.size == 1) {
            "A result spanning ${distinct.map(InferenceProvenanceV2::route)} needs one event per route, not one event"
        }
        distinct.single()
    }

    is InferenceOutcomeV2.Abstention -> provenance

    is InferenceOutcomeV2.Failure -> provenance
}

private fun InferenceOutcomeV2.terminalOutcome(): InferenceTelemetryOutcome = when (this) {
    is InferenceOutcomeV2.Candidates -> InferenceTelemetryOutcome.CANDIDATES
    is InferenceOutcomeV2.Abstention -> InferenceTelemetryOutcome.ABSTENTION
    is InferenceOutcomeV2.Failure -> InferenceTelemetryOutcome.FAILURE
}

private fun InferenceOutcomeV2.itemGroupCount(): Int = (this as? InferenceOutcomeV2.Candidates)?.itemGroups?.size ?: 0

/**
 * Only the attempt event is bound. v1's capability event keeps v1's shape until
 * something needs it here; an enum entry nothing emits is a claim rather than a
 * contract.
 */
@Serializable
enum class TelemetryEventNameV2 {
    @SerialName("inference.attempt.completed")
    ATTEMPT_COMPLETED,
}

/** v2's boundaries, which are not v1's. Both vocabularies are pinned by their own release. */
@Serializable
enum class TelemetryDurationBucketV2 {
    @SerialName("under-2s")
    UNDER_2S,

    @SerialName("2-to-5s")
    FROM_2_TO_5S,

    @SerialName("over-5s")
    OVER_5S,
}

/**
 * Absolute magnitude rather than v1's budget-relative naming, because
 * per-device-tier budgets are not set yet. A budget-relative label would bake a
 * threshold nobody has decided into a released contract, and an absolute bucket
 * can be compared against a budget once one exists.
 */
@Serializable
enum class TelemetryResourceBucketV2 {
    @SerialName("low")
    LOW,

    @SerialName("moderate")
    MODERATE,

    @SerialName("high")
    HIGH,
}

/**
 * How many items the description segmented into, bucketed.
 *
 * "This person logged seven items" is closer to a diary entry than to an
 * operational measurement. The operational question is only whether segmentation
 * produced nothing, one item, or many.
 */
@Serializable
enum class TelemetryItemGroupCountBucket {
    @SerialName("none")
    NONE,

    @SerialName("1")
    ONE,

    @SerialName("2-3")
    TWO_TO_THREE,

    @SerialName("4-plus")
    FOUR_PLUS,
    ;

    internal companion object {
        private const val FEW_UPPER_BOUND = 3

        fun of(count: Int): TelemetryItemGroupCountBucket = when {
            count <= 0 -> NONE
            count == 1 -> ONE
            count <= FEW_UPPER_BOUND -> TWO_TO_THREE
            else -> FOUR_PLUS
        }
    }
}
