package com.supernutrition.domain.inference

import kotlinx.serialization.Serializable

/**
 * What a v3 engine can do right now, per modality.
 *
 * v3 published `capability-ready.json` as a canonical fixture from the start and
 * had nothing that could decode it: [EngineCapability] calls `requireContractV1`
 * and carries no modality, so the fixture was reference data no binding could
 * read. `InferenceEngineV3` was worse off than the interface it replaces —
 * `PhotoInferenceEngine` has `capability()`, and v3 had `supportedModalities`
 * and nothing else, so a v3 engine could not report that its model was missing.
 *
 * Capability is per modality rather than per engine. An engine that serves both
 * can be ready for photos and still waiting on a text model; one answer for the
 * pair would have to be the pessimistic one, which would hide a working camera
 * behind a missing text model.
 *
 * The states and reasons themselves are v1's, unchanged, because E7.1 requires
 * exactly that. Only the envelope is new.
 */
@Serializable
data class EngineCapabilityV3(
    val contractVersion: String,
    val modality: InferenceModality,
    val state: CapabilityState,
    val reason: CapabilityReason,
    val userMessageKey: String,
    val engineId: String,
    val modelVersion: String,
    val taxonomyVersion: String,
    val calibrationVersion: String,
) {
    init {
        requireContractV3(contractVersion, "Capability must use contract-v3")
        requireNonBlankValue(userMessageKey, "Capability user message key must not be blank")
        requireNonBlankValue(engineId, "Capability engine ID must not be blank")
        requireNonBlankValue(modelVersion, "Capability model version must not be blank")
        requireNonBlankValue(taxonomyVersion, "Capability taxonomy version must not be blank")
        requireNonBlankValue(calibrationVersion, "Capability calibration version must not be blank")
        require((state == CapabilityState.READY) == (reason == CapabilityReason.NONE)) {
            "Capability state and reason must agree"
        }
    }
}
