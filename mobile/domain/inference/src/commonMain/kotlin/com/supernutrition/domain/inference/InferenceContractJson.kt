package com.supernutrition.domain.inference

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object InferenceContractJson {
    private val json = Json {
        classDiscriminator = "outcomeType"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(result: PhotoInferenceResult): String = json.encodeToString(
        result,
    )

    fun encodeCapability(capability: EngineCapability): String = json.encodeToString(capability)

    fun decodeCapability(payload: String): EngineCapability = json.decodeFromString(payload)

    fun decode(payload: String): PhotoInferenceResult = json.decodeFromString(payload)

    fun encodeTelemetry(event: InferenceTelemetryEvent): String = json.encodeToString(event)

    fun decodeTelemetry(payload: String): InferenceTelemetryEvent = json.decodeFromString(payload)
}
