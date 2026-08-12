package com.supernutrition.domain.inference

import kotlinx.serialization.Serializable

/**
 * What a v2 engine can do right now, per modality.
 *
 * v2 published `capability-ready.json` as a canonical fixture from the start and
 * had nothing that could decode it: [EngineCapability] calls `requireContractV1`
 * and carries no modality, so the fixture was reference data no binding could
 * read. `InferenceEngineV3` was worse off than the interface it replaces —
 * `PhotoInferenceEngine` has `capability()`, and v2 had `supportedModalities`
 * and nothing else, so a v2 engine could not report that its model was missing.
 *
 * Capability is per modality rather than per engine. An engine that serves both
 * can be ready for photos and still waiting on a text model; one answer for the
 * pair would have to be the pessimistic one, which would hide a working camera
 * behind a missing text model.
 *
 * The states and reasons themselves are v1's, unchanged, because E7.1 requires
 * exactly that. Only the envelope is new.
 */
@Serializable(with = EngineCapabilityV2Serializer::class)
data class EngineCapabilityV3(
    val contractVersion: String,
    val modality: InferenceModality,
    val state: CapabilityState,
    val reason: CapabilityReason,
    val userMessageKey: String,
    val engineId: String,
    val modelVersion: String? = null,
) {
    init {
        requireContractV3(contractVersion, "Capability must use contract-v2")
        requireNonBlankValue(userMessageKey, "Capability user message key must not be blank")
        requireNonBlankValue(engineId, "Capability engine ID must not be blank")
        if (state == CapabilityState.READY) {
            require(reason == CapabilityReason.NONE) {
                "Ready capability must not report an unavailable reason"
            }
            requireNonBlankValue(modelVersion.orEmpty(), "Ready capability must identify its model version")
        } else {
            require(reason != CapabilityReason.NONE) {
                "Unavailable capability must report an unavailable reason"
            }
        }
    }
}
