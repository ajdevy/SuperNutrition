package com.supernutrition.domain.inference

/** Codec for `photo-inference-engine/v1`. Shares [INFERENCE_JSON] with v2. */
object InferenceContractJson {
    fun encode(result: PhotoInferenceResult): String =
        INFERENCE_JSON.encodeToString(PhotoInferenceResult.serializer(), result)

    fun decode(payload: String): PhotoInferenceResult =
        INFERENCE_JSON.decodeFromString(PhotoInferenceResult.serializer(), payload)

    fun encodeCapability(capability: EngineCapability): String =
        INFERENCE_JSON.encodeToString(EngineCapability.serializer(), capability)

    fun decodeCapability(payload: String): EngineCapability =
        INFERENCE_JSON.decodeFromString(EngineCapability.serializer(), payload)

    fun encodeTelemetry(event: InferenceTelemetryEvent): String =
        INFERENCE_JSON.encodeToString(InferenceTelemetryEvent.serializer(), event)

    fun decodeTelemetry(payload: String): InferenceTelemetryEvent =
        INFERENCE_JSON.decodeFromString(InferenceTelemetryEvent.serializer(), payload)
}
