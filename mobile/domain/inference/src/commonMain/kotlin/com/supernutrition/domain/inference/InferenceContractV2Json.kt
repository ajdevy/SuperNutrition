package com.supernutrition.domain.inference

/**
 * Codec for `inference-engine/v2`.
 *
 * Separate from [InferenceContractJson] rather than sharing one object with
 * `V2`-suffixed functions. Both releases are published and a caller migrates one
 * at a time, so which release a call speaks should be visible at the call site
 * rather than in a suffix that is easy to read past. They still share
 * [INFERENCE_JSON], because the encoding policy is not per release.
 */
object InferenceContractV2Json {
    fun encode(result: InferenceResultV2): String =
        INFERENCE_JSON.encodeToString(InferenceResultV2.serializer(), result)

    fun decode(payload: String): InferenceResultV2 =
        INFERENCE_JSON.decodeFromString(InferenceResultV2.serializer(), payload)

    fun encodeCapability(capability: EngineCapabilityV2): String =
        INFERENCE_JSON.encodeToString(EngineCapabilityV2.serializer(), capability)

    fun decodeCapability(payload: String): EngineCapabilityV2 =
        INFERENCE_JSON.decodeFromString(EngineCapabilityV2.serializer(), payload)

    fun encodeTelemetry(event: InferenceTelemetryEventV2): String =
        INFERENCE_JSON.encodeToString(InferenceTelemetryEventV2.serializer(), event)

    fun decodeTelemetry(event: String): InferenceTelemetryEventV2 =
        INFERENCE_JSON.decodeFromString(InferenceTelemetryEventV2.serializer(), event)
}
