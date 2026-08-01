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

    fun encodeTelemetry(event: InferenceTelemetryEvent): String =
        json.encodeToString(InferenceTelemetryEvent.serializer(), event)

    fun decodeTelemetry(payload: String): InferenceTelemetryEvent =
        json.decodeFromString(InferenceTelemetryEvent.serializer(), payload)
}
