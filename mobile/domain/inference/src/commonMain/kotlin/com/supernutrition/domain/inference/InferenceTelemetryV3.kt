package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * v3 operational telemetry for a completed identification attempt.
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
data class InferenceTelemetryEventV3(
    val contractVersion: String,
    val modality: InferenceModality,
    val eventName: TelemetryEventNameV3,
    val route: InferenceRouteV3,
    val capabilityTier: CapabilityState,
    val durationBucket: TelemetryDurationBucketV3,
    val resourceBucket: TelemetryResourceBucketV3,
    val terminalOutcome: InferenceV3TelemetryOutcome,
    val itemGroupCountBucket: TelemetryItemGroupCountBucket,
    val componentCountBucket: TelemetryComponentCountBucket,
    val engineId: String,
    val modelVersion: String,
    val taxonomyVersion: String,
    val calibrationVersion: String,
) {
    init {
        requireContractV3(contractVersion, "Telemetry must use contract-v3")
        requireNonBlankValue(engineId, "Telemetry engine ID must not be blank")
        requireNonBlankValue(modelVersion, "Telemetry model version must not be blank")
        requireNonBlankValue(taxonomyVersion, "Telemetry taxonomy version must not be blank")
        requireNonBlankValue(calibrationVersion, "Telemetry calibration version must not be blank")
        when (modality) {
            InferenceModality.TEXT -> require(route != InferenceRouteV3.LOCAL) {
                "A text attempt must not report route 'local'; that is the flattening local-text prevents"
            }

            InferenceModality.PHOTO -> require(route != InferenceRouteV3.LOCAL_TEXT) {
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
            result: InferenceResultV3,
            capabilityTier: CapabilityState,
            durationBucket: TelemetryDurationBucketV3,
            resourceBucket: TelemetryResourceBucketV3,
        ): InferenceTelemetryEventV3 {
            val provenance = result.outcome.reportingProvenance()
            return InferenceTelemetryEventV3(
                contractVersion = INFERENCE_CONTRACT_V3,
                modality = result.modality,
                eventName = TelemetryEventNameV3.ATTEMPT_COMPLETED,
                route = provenance.route,
                capabilityTier = capabilityTier,
                durationBucket = durationBucket,
                resourceBucket = resourceBucket,
                terminalOutcome = result.outcome.terminalOutcome(),
                itemGroupCountBucket = TelemetryItemGroupCountBucket.of(result.outcome.itemGroupCount()),
                componentCountBucket = TelemetryComponentCountBucket.of(result.outcome.componentCount()),
                engineId = provenance.engineId,
                modelVersion = provenance.modelVersion,
                taxonomyVersion = provenance.taxonomyVersion,
                calibrationVersion = provenance.calibrationVersion,
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
private fun InferenceOutcomeV3.reportingProvenance(): InferenceProvenanceV3 = when (this) {
    is InferenceOutcomeV3.Candidates -> {
        val provenances = itemGroups.flatMap { group -> group.candidates.map(InferenceCandidateV3::provenance) }
        val distinct = provenances.distinct()
        require(distinct.size == 1) {
            "A result spanning ${distinct.map(InferenceProvenanceV3::route)} needs one event per route, not one event"
        }
        distinct.single()
    }

    is InferenceOutcomeV3.Components -> {
        val distinct = components.map(InferenceV3VisibleComponent::provenance).distinct()
        require(distinct.size == 1) {
            "A result spanning ${distinct.map(InferenceProvenanceV3::route)} needs one event per route, not one event"
        }
        distinct.single()
    }

    is InferenceOutcomeV3.Abstention -> provenance

    is InferenceOutcomeV3.Failure -> provenance
}

private fun InferenceOutcomeV3.terminalOutcome(): InferenceV3TelemetryOutcome = when (this) {
    is InferenceOutcomeV3.Candidates -> InferenceV3TelemetryOutcome.CANDIDATES
    is InferenceOutcomeV3.Components -> InferenceV3TelemetryOutcome.COMPONENTS
    is InferenceOutcomeV3.Abstention -> InferenceV3TelemetryOutcome.ABSTENTION
    is InferenceOutcomeV3.Failure -> InferenceV3TelemetryOutcome.FAILURE
}

private fun InferenceOutcomeV3.itemGroupCount(): Int = (this as? InferenceOutcomeV3.Candidates)?.itemGroups?.size ?: 0

private fun InferenceOutcomeV3.componentCount(): Int = (this as? InferenceOutcomeV3.Components)?.components?.size ?: 0

@Serializable
enum class InferenceV3TelemetryOutcome {
    @SerialName("candidates")
    CANDIDATES,

    @SerialName("components")
    COMPONENTS,

    @SerialName("abstention")
    ABSTENTION,

    @SerialName("failure")
    FAILURE,

    @SerialName("cancelled")
    CANCELLED,
}

/**
 * Only the attempt event is bound. v1's capability event keeps v1's shape until
 * something needs it here; an enum entry nothing emits is a claim rather than a
 * contract.
 */
@Serializable
enum class TelemetryEventNameV3 {
    @SerialName("inference.attempt.completed")
    ATTEMPT_COMPLETED,
}

/** v3's boundaries, which are not v1's. Both vocabularies are pinned by their own release. */
@Serializable
enum class TelemetryDurationBucketV3 {
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
enum class TelemetryResourceBucketV3 {
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

@Serializable
enum class TelemetryComponentCountBucket {
    @SerialName("zero")
    ZERO,

    @SerialName("one")
    ONE,

    @SerialName("two-to-four")
    TWO_TO_FOUR,

    @SerialName("five-or-more")
    FIVE_OR_MORE,
    ;

    internal companion object {
        fun of(count: Int): TelemetryComponentCountBucket = when {
            count <= 0 -> ZERO
            count == 1 -> ONE
            count <= 4 -> TWO_TO_FOUR
            else -> FIVE_OR_MORE
        }
    }
}
