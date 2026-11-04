package com.supernutrition.domain.inference

import kotlinx.serialization.encodeToString

/**
 * Codec for `inference-evidence/v3`.
 *
 * Separate from [InferenceContractJson] rather than sharing one object with
 * `V3`-suffixed functions. Both releases are published and a caller migrates one
 * at a time, so which release a call speaks should be visible at the call site
 * rather than in a suffix that is easy to read past. They still share
 * [INFERENCE_JSON], because the encoding policy is not per release.
 */
object InferenceContractV3Json {
    fun encode(result: InferenceResultV3): String = INFERENCE_JSON.encodeToString(result)

    fun decode(payload: String): InferenceResultV3 = INFERENCE_JSON.decodeFromString(payload)

    fun encodeCapability(capability: EngineCapabilityV3): String = INFERENCE_JSON.encodeToString(capability)

    fun decodeCapability(payload: String): EngineCapabilityV3 = INFERENCE_JSON.decodeFromString(payload)

    fun encodeTelemetry(event: InferenceTelemetryEventV3): String = INFERENCE_JSON.encodeToString(event)

    fun decodeTelemetry(event: String): InferenceTelemetryEventV3 = INFERENCE_JSON.decodeFromString(event)
}
