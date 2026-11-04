package com.supernutrition.domain.inference

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object InferenceV2ContractJson {
    private val json = Json {
        classDiscriminator = "outcomeType"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(result: InferenceV2Result): String = json.encodeToString(result)

    fun decode(payload: String): InferenceV2Result = json.decodeFromString(payload)

    fun encodeCapability(capability: InferenceV2Capability): String = json.encodeToString(capability)

    fun decodeCapability(payload: String): InferenceV2Capability = json.decodeFromString(payload)

    fun encodeTelemetry(event: InferenceV2TelemetryEvent): String = json.encodeToString(event)

    fun decodeTelemetry(payload: String): InferenceV2TelemetryEvent = json.decodeFromString(payload)
}
