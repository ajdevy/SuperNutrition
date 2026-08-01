package com.supernutrition.domain.inference

import kotlinx.serialization.json.Json

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

    /**
     * v2 shares this codec rather than getting its own. The discriminator,
     * null handling and unknown-field policy are contract-level decisions that
     * must not differ between releases, and two Json instances would be two
     * places for them to drift.
     */
    fun encodeV2(result: InferenceResultV2): String = json.encodeToString(InferenceResultV2.serializer(), result)

    fun decodeV2(payload: String): InferenceResultV2 = json.decodeFromString(InferenceResultV2.serializer(), payload)

    fun encodeTelemetry(event: InferenceTelemetryEvent): String =
        json.encodeToString(InferenceTelemetryEvent.serializer(), event)

    fun decodeTelemetry(payload: String): InferenceTelemetryEvent =
        json.decodeFromString(InferenceTelemetryEvent.serializer(), payload)

    fun encodeTelemetryV2(event: InferenceTelemetryEventV2): String =
        json.encodeToString(InferenceTelemetryEventV2.serializer(), event)

    fun decodeTelemetryV2(payload: String): InferenceTelemetryEventV2 =
        json.decodeFromString(InferenceTelemetryEventV2.serializer(), payload)
}
