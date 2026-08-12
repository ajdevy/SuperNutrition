package com.supernutrition.inference.localshared

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentModelActivation
import com.supernutrition.domain.inference.ComponentModelManifest
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceGenerationController
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Capability
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Input
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.V2AbstentionReason
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import kotlin.coroutines.cancellation.CancellationException

private const val UNAVAILABLE_MODEL_VERSION = "model-unavailable"
private const val UNAVAILABLE_TAXONOMY_VERSION = "taxonomy-unavailable"
private const val UNAVAILABLE_CALIBRATION_VERSION = "calibration-unavailable"

sealed interface LocalComponentRuntimeCapability {
    val manifest: ComponentModelManifest?

    data class Ready(override val manifest: ComponentModelManifest) : LocalComponentRuntimeCapability

    data class Unavailable(
        val state: CapabilityState,
        val reason: CapabilityReason,
        val userMessageKey: String = "inference.capability.${state.componentKey}",
        override val manifest: ComponentModelManifest? = null,
    ) : LocalComponentRuntimeCapability {
        init {
            require(state != CapabilityState.READY) {
                "Unavailable local component capability cannot be ready"
            }
            require(reason != CapabilityReason.NONE) {
                "Unavailable local component capability needs a reason"
            }
            require(userMessageKey.isNotBlank()) {
                "Local component capability message key must not be blank"
            }
        }
    }
}

data class LocalComponentRuntimeComponent(
    val componentId: String,
    val candidate: LocalizedCandidateIdentity,
    val confidence: CalibratedConfidence,
    val visibility: ComponentVisibility,
    val ambiguity: ComponentAmbiguity,
    val region: NormalizedVisibleRegion?,
    val mass: MassEvidence,
) {
    init {
        require(componentId.isNotBlank()) { "Local component ID must not be blank" }
    }
}

sealed interface LocalComponentRuntimeOutcome {
    data class Components(val components: List<LocalComponentRuntimeComponent>) : LocalComponentRuntimeOutcome

    data class Abstention(val reason: V2AbstentionReason, val userMessageKey: String) :
        LocalComponentRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "Local component abstention message key must not be blank" }
        }
    }

    data class Failure(val code: V2InferenceFailureCode, val retryable: Boolean, val userMessageKey: String) :
        LocalComponentRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "Local component failure message key must not be blank" }
        }
    }
}

interface LocalComponentInferenceRuntime {
    suspend fun capability(): LocalComponentRuntimeCapability

    suspend fun analyze(input: NormalizedPhotoInput, token: LocalInferenceAttemptToken): LocalComponentRuntimeOutcome

    /** Safe to call more than once for the same token. */
    fun release(token: LocalInferenceAttemptToken)

    /** Requests best-effort cancellation of a native operation. */
    fun cancel(token: LocalInferenceAttemptToken) = Unit
}

class LocalComponentInferenceInputException(message: String) : IllegalArgumentException(message)

/** Shared v2 local adapter. Platform wrappers choose only their engine identifier. */
class LocalComponentInferenceAdapter(
    private val photos: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
    private val runtime: LocalComponentInferenceRuntime,
    private val digest: (ByteArray) -> String,
    private val engineId: String,
) : InferenceV2Engine() {
    private val generations = InferenceGenerationController<LocalInferenceAttemptToken>(
        cancelToken = runtime::cancel,
        releaseToken = runtime::release,
    )

    override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2

    override suspend fun capability(): InferenceV2Capability = runtime.capability()
        .enforceProductionActivation()
        .toContractCapability(engineId)

    public override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result {
        val capability = runtime.capability().enforceProductionActivation()
        val provenance = capability.manifest?.toProvenance(engineId) ?: unavailableComponentProvenance(engineId)
        if (capability is LocalComponentRuntimeCapability.Unavailable) {
            return unavailableResult(request.input, capability, provenance)
        }
        if (request.input !is InferenceV2Input.Photo) {
            return unsupportedPhotoResult(request.input, provenance)
        }

        val input = try {
            readAndValidate(request)
        } catch (_: LocalComponentInferenceInputException) {
            return failure(
                input = request.input,
                code = V2InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                messageKey = "inference.failure.invalid_input",
                provenance = provenance,
            )
        }
        return try {
            val outcome = generations.run(
                attemptId = request.attemptContext.attemptId,
                tokenFactory = { generation ->
                    LocalInferenceAttemptToken(
                        attemptId = request.attemptContext.attemptId,
                        generation = generation,
                        gridCellIndex = request.attemptContext.gridCellIndex,
                    )
                },
            ) { token -> runtime.analyze(input, token) }
            outcome.toContractResult(provenance)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            failure(
                input = request.input,
                code = V2InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                retryable = true,
                messageKey = "inference.failure.component_runtime",
                provenance = provenance,
            )
        }
    }

    suspend fun cancel(attemptId: AttemptId) {
        generations.cancel(attemptId)
    }

    private fun readAndValidate(request: InferenceV2Request): NormalizedPhotoInput {
        val photoRef = (request.input as InferenceV2Input.Photo).photoRef
        if (!leases.isActive(photoRef)) {
            rejectInput("Photo content lease is not active for this attempt")
        }
        val input = photos.read(photoRef) ?: rejectInput("Photo content is unavailable for this attempt")
        if (input.ref != photoRef) {
            rejectInput("Photo content belongs to another attempt")
        }
        if (digest(input.bytes) != input.contentDigest) {
            rejectInput("Photo content digest does not match its lease")
        }
        return input
    }

    private fun rejectInput(message: String): Nothing = throw LocalComponentInferenceInputException(message)
}

private fun LocalComponentRuntimeCapability.toContractCapability(engineId: String): InferenceV2Capability =
    when (this) {
        is LocalComponentRuntimeCapability.Ready -> InferenceV2Capability(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            state = CapabilityState.READY,
            reason = CapabilityReason.NONE,
            userMessageKey = "inference.capability.ready",
            engineId = engineId,
            modelVersion = manifest.modelVersion,
            taxonomyVersion = manifest.taxonomyVersion,
            calibrationVersion = manifest.calibrationVersion,
        )

        is LocalComponentRuntimeCapability.Unavailable -> InferenceV2Capability(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            state = state,
            reason = reason,
            userMessageKey = userMessageKey,
            engineId = engineId,
            modelVersion = manifest?.modelVersion ?: UNAVAILABLE_MODEL_VERSION,
            taxonomyVersion = manifest?.taxonomyVersion ?: UNAVAILABLE_TAXONOMY_VERSION,
            calibrationVersion = manifest?.calibrationVersion ?: UNAVAILABLE_CALIBRATION_VERSION,
        )
    }

private fun LocalComponentRuntimeCapability.enforceProductionActivation(): LocalComponentRuntimeCapability =
    when (this) {
        is LocalComponentRuntimeCapability.Ready -> if (manifest.activation == ComponentModelActivation.PRODUCTION) {
            this
        } else {
            LocalComponentRuntimeCapability.Unavailable(
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.MODEL_MISSING,
                userMessageKey = "inference.capability.model-not-production",
                manifest = manifest,
            )
        }

        is LocalComponentRuntimeCapability.Unavailable -> this
    }

private fun unavailableResult(
    input: InferenceV2Input,
    capability: LocalComponentRuntimeCapability.Unavailable,
    provenance: InferenceV2Provenance,
): InferenceV2Result = failure(
    input = input,
    code = V2InferenceFailureCode.MODEL_UNAVAILABLE,
    retryable = capability.state.isRetryable,
    messageKey = capability.userMessageKey,
    provenance = provenance,
)

private fun unsupportedPhotoResult(input: InferenceV2Input, provenance: InferenceV2Provenance): InferenceV2Result =
    InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = input.modality,
        outcome = InferenceV2Outcome.Abstention(
            reason = V2AbstentionReason.UNSUPPORTED_CONTENT,
            userMessageKey = "inference.abstention.component_photo_only",
            provenance = provenance,
        ),
    )

private fun unavailableComponentProvenance(engineId: String): InferenceV2Provenance = InferenceV2Provenance(
    route = InferenceRoute.LOCAL,
    engineId = engineId,
    modelVersion = UNAVAILABLE_MODEL_VERSION,
    taxonomyVersion = UNAVAILABLE_TAXONOMY_VERSION,
    calibrationVersion = UNAVAILABLE_CALIBRATION_VERSION,
    contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
)

private fun LocalComponentRuntimeOutcome.toContractResult(provenance: InferenceV2Provenance): InferenceV2Result =
    InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = InferenceInputModality.PHOTO,
        outcome = when (this) {
            is LocalComponentRuntimeOutcome.Components -> InferenceV2Outcome.Components(
                components.map { component ->
                    VisibleComponent(
                        componentId = component.componentId,
                        candidate = component.candidate,
                        confidence = component.confidence,
                        visibility = component.visibility,
                        ambiguity = component.ambiguity,
                        region = component.region,
                        mass = component.mass,
                        provenance = provenance,
                    )
                },
            )

            is LocalComponentRuntimeOutcome.Abstention -> InferenceV2Outcome.Abstention(
                reason = reason,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )

            is LocalComponentRuntimeOutcome.Failure -> InferenceV2Outcome.Failure(
                code = code,
                retryable = retryable,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )
        },
    )

private fun failure(
    input: InferenceV2Input,
    code: V2InferenceFailureCode,
    retryable: Boolean,
    messageKey: String,
    provenance: InferenceV2Provenance,
): InferenceV2Result = InferenceV2Result(
    contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
    inputModality = input.modality,
    outcome = InferenceV2Outcome.Failure(code, retryable, messageKey, provenance),
)

private val CapabilityState.componentKey: String
    get() = name.lowercase().replace('_', '-')
