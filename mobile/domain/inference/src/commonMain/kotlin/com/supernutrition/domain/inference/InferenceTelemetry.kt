package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
