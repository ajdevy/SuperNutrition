package com.supernutrition.domain.inference

import kotlinx.serialization.json.Json

object InferenceV2ContractJson {
    private val json = Json {
        classDiscriminator = "outcomeType"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(result: InferenceV2Result): String = json.encodeToString(InferenceV2Result.serializer(), result)

    fun decode(payload: String): InferenceV2Result = json.decodeFromString(InferenceV2Result.serializer(), payload)

    fun encodeCapability(capability: InferenceV2Capability): String =
        json.encodeToString(InferenceV2Capability.serializer(), capability)

    fun decodeCapability(payload: String): InferenceV2Capability =
        json.decodeFromString(InferenceV2Capability.serializer(), payload)

    fun encodeTelemetry(event: InferenceV2TelemetryEvent): String =
        json.encodeToString(InferenceV2TelemetryEvent.serializer(), event)

    fun decodeTelemetry(payload: String): InferenceV2TelemetryEvent =
        json.decodeFromString(InferenceV2TelemetryEvent.serializer(), payload)
}
