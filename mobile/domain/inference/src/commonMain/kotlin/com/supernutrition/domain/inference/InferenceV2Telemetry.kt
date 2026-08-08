package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Allowlisted operational buckets only; component labels, grams, photos, and text are excluded. */
@Serializable
data class InferenceV2TelemetryEvent(
    val contractVersion: String,
    val eventType: InferenceTelemetryEventType,
    val route: InferenceRoute,
    val capabilityState: CapabilityState,
    val outcome: InferenceV2TelemetryOutcome,
    val durationBucket: TelemetryDurationBucket,
    val resourceBucket: TelemetryResourceBucket,
    val componentCountBucket: ComponentCountBucket,
) {
    init {
        requireEvidenceContractV2(contractVersion, "Telemetry must use inference-evidence/v2")
    }
}

@Serializable
enum class InferenceV2TelemetryOutcome {
    @SerialName("components")
    COMPONENTS,

    @SerialName("abstention")
    ABSTENTION,

    @SerialName("failure")
    FAILURE,

    @SerialName("cancelled")
    CANCELLED,
}

@Serializable
enum class ComponentCountBucket {
    @SerialName("zero")
    ZERO,

    @SerialName("one")
    ONE,

    @SerialName("two-to-four")
    TWO_TO_FOUR,

    @SerialName("five-or-more")
    FIVE_OR_MORE,
}
