package com.supernutrition.inference.localios

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentModelActivation
import com.supernutrition.domain.inference.ComponentModelManifest
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceInputModality
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

const val LOCAL_IOS_COMPONENT_ENGINE_ID = "local-ios-component"
private const val UNAVAILABLE_MODEL_VERSION = "model-unavailable"
private const val UNAVAILABLE_TAXONOMY_VERSION = "taxonomy-unavailable"
private const val UNAVAILABLE_CALIBRATION_VERSION = "calibration-unavailable"

sealed interface IosComponentRuntimeCapability {
    val manifest: ComponentModelManifest?

    data class Ready(override val manifest: ComponentModelManifest) : IosComponentRuntimeCapability

    data class Unavailable(
        val state: CapabilityState,
        val reason: CapabilityReason,
        val userMessageKey: String = "inference.capability.${state.key}",
        override val manifest: ComponentModelManifest? = null,
    ) : IosComponentRuntimeCapability {
        init {
            require(state != CapabilityState.READY) {
                "Unavailable iOS component capability cannot be ready"
            }
            require(reason != CapabilityReason.NONE) {
                "Unavailable iOS component capability needs a reason"
            }
            require(userMessageKey.isNotBlank()) {
                "iOS component capability message key must not be blank"
            }
        }
    }
}

data class IosComponentRuntimeComponent(
    val componentId: String,
    val candidate: LocalizedCandidateIdentity,
    val confidence: CalibratedConfidence,
    val visibility: ComponentVisibility,
    val ambiguity: ComponentAmbiguity,
    val region: NormalizedVisibleRegion?,
    val mass: MassEvidence,
) {
    init {
        require(componentId.isNotBlank()) { "iOS component ID must not be blank" }
    }
}

sealed interface IosComponentRuntimeOutcome {
    data class Components(val components: List<IosComponentRuntimeComponent>) : IosComponentRuntimeOutcome

    data class Abstention(val reason: V2AbstentionReason, val userMessageKey: String) :
        IosComponentRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "iOS component abstention message key must not be blank" }
        }
    }

    data class Failure(val code: V2InferenceFailureCode, val retryable: Boolean, val userMessageKey: String) :
        IosComponentRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "iOS component failure message key must not be blank" }
        }
    }
}

data class IosComponentInferenceAttemptToken(val attemptId: AttemptId, val generation: Long)

interface IosLocalComponentInferenceRuntime {
    suspend fun capability(): IosComponentRuntimeCapability

    suspend fun analyze(
        input: NormalizedPhotoInput,
        token: IosComponentInferenceAttemptToken,
    ): IosComponentRuntimeOutcome

    /** Safe to call more than once for the same token. */
    fun release(token: IosComponentInferenceAttemptToken)

    /** Requests best-effort cancellation of a native operation. */
    fun cancel(token: IosComponentInferenceAttemptToken) = Unit
}

class IosComponentInferenceInputException(message: String) : IllegalArgumentException(message)

/** v2 iOS adapter seam; the concrete detector/runtime remains adapter-owned. */
class LocalIosComponentInferenceEngine(
    private val photos: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
    private val runtime: IosLocalComponentInferenceRuntime,
    private val digest: (ByteArray) -> String,
) : InferenceV2Engine() {
    private var nextGeneration = 0L
    private val stateMutex = Mutex()
    private val activeGenerations = mutableMapOf<AttemptId, Long>()
    private val activeTokens = mutableMapOf<AttemptId, IosComponentInferenceAttemptToken>()

    override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2

    override suspend fun capability(): InferenceV2Capability = runtime.capability()
        .enforceProductionActivation()
        .toContractCapability()

    override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result {
        val capability = runtime.capability().enforceProductionActivation()
        val provenance = capability.manifest?.toProvenance(LOCAL_IOS_COMPONENT_ENGINE_ID)
            ?: unavailableProvenance()
        if (capability is IosComponentRuntimeCapability.Unavailable) {
            return unavailableResult(request.input, capability, provenance)
        }
        if (request.input !is InferenceV2Input.Photo) {
            return unsupportedPhotoResult(request.input, provenance)
        }

        val input = readAndValidate(request)
        val (token, previousToken) = stateMutex.withLock {
            val token = IosComponentInferenceAttemptToken(
                attemptId = request.attemptContext.attemptId,
                generation = ++nextGeneration,
            )
            val previousToken = activeTokens.put(token.attemptId, token)
            activeGenerations[token.attemptId] = token.generation
            token to previousToken
        }
        previousToken?.let(runtime::cancel)

        return try {
            val outcome = runtime.analyze(input, token)
            currentCoroutineContext().ensureActive()
            if (!stateMutex.withLock { activeGenerations[token.attemptId] == token.generation }) {
                throw CancellationException("iOS component result was superseded")
            }
            outcome.toContractResult(provenance)
        } catch (cancellation: CancellationException) {
            runtime.cancel(token)
            throw cancellation
        } catch (_: Exception) {
            failure(
                input = request.input,
                code = V2InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                retryable = true,
                messageKey = "inference.failure.component_runtime",
                provenance = provenance,
            )
        } finally {
            stateMutex.withLock {
                if (activeTokens[token.attemptId] == token) {
                    activeTokens.remove(token.attemptId)
                    activeGenerations.remove(token.attemptId)
                }
            }
            runtime.release(token)
        }
    }

    suspend fun cancel(attemptId: AttemptId) {
        val token = stateMutex.withLock {
            activeGenerations.remove(attemptId)
            activeTokens.remove(attemptId)
        }
        token?.let(runtime::cancel)
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

    private fun rejectInput(message: String): Nothing = throw IosComponentInferenceInputException(message)

    private fun unavailableProvenance(): InferenceV2Provenance = InferenceV2Provenance(
        route = com.supernutrition.domain.inference.InferenceRoute.LOCAL,
        engineId = LOCAL_IOS_COMPONENT_ENGINE_ID,
        modelVersion = UNAVAILABLE_MODEL_VERSION,
        taxonomyVersion = UNAVAILABLE_TAXONOMY_VERSION,
        calibrationVersion = UNAVAILABLE_CALIBRATION_VERSION,
        contractVersion = contractVersion,
    )
}

private fun IosComponentRuntimeCapability.toContractCapability(): InferenceV2Capability = when (this) {
    is IosComponentRuntimeCapability.Ready -> InferenceV2Capability(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = LOCAL_IOS_COMPONENT_ENGINE_ID,
        modelVersion = manifest.modelVersion,
        taxonomyVersion = manifest.taxonomyVersion,
        calibrationVersion = manifest.calibrationVersion,
    )

    is IosComponentRuntimeCapability.Unavailable -> InferenceV2Capability(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        state = state,
        reason = reason,
        userMessageKey = userMessageKey,
        engineId = LOCAL_IOS_COMPONENT_ENGINE_ID,
        modelVersion = this.manifest?.modelVersion ?: UNAVAILABLE_MODEL_VERSION,
        taxonomyVersion = this.manifest?.taxonomyVersion ?: UNAVAILABLE_TAXONOMY_VERSION,
        calibrationVersion = this.manifest?.calibrationVersion ?: UNAVAILABLE_CALIBRATION_VERSION,
    )
}

private fun IosComponentRuntimeCapability.enforceProductionActivation(): IosComponentRuntimeCapability = when (this) {
    is IosComponentRuntimeCapability.Ready -> if (manifest.activation == ComponentModelActivation.PRODUCTION) {
        this
    } else {
        IosComponentRuntimeCapability.Unavailable(
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.model-not-production",
            manifest = manifest,
        )
    }

    is IosComponentRuntimeCapability.Unavailable -> this
}

private fun unavailableResult(
    input: InferenceV2Input,
    capability: IosComponentRuntimeCapability.Unavailable,
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

private fun IosComponentRuntimeOutcome.toContractResult(provenance: InferenceV2Provenance): InferenceV2Result =
    InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = InferenceInputModality.PHOTO,
        outcome = when (this) {
            is IosComponentRuntimeOutcome.Components -> InferenceV2Outcome.Components(
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

            is IosComponentRuntimeOutcome.Abstention -> InferenceV2Outcome.Abstention(
                reason = reason,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )

            is IosComponentRuntimeOutcome.Failure -> InferenceV2Outcome.Failure(
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

private val CapabilityState.key: String
    get() = name.lowercase().replace('_', '-')

private val CapabilityState.isRetryable: Boolean
    get() = this == CapabilityState.DOWNLOADING || this == CapabilityState.TEMPORARILY_UNAVAILABLE
