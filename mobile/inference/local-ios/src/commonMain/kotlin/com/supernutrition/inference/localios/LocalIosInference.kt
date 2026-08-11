package com.supernutrition.inference.localios

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.EngineCapability
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceGenerationController
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.inference.requireSha256Digest
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import kotlin.coroutines.cancellation.CancellationException

const val LOCAL_IOS_ENGINE_ID = "local-ios"
private const val UNAVAILABLE_MODEL_VERSION = "model-unavailable"
private const val UNAVAILABLE_CALIBRATION_VERSION = "calibration-unavailable"

enum class IosModelOwnership {
    APP_MANAGED,
    PLATFORM_MANAGED,
}

enum class IosModelVerification {
    VERIFIED,
    UNVERIFIED,
}

data class IosModelProvenance(
    val modelVersion: String,
    val integrityIdentity: String,
    val compatibilityTier: String,
    val calibrationVersion: String,
    val ownership: IosModelOwnership,
    val rollbackTargetVersion: String? = null,
    val verification: IosModelVerification = IosModelVerification.UNVERIFIED,
) {
    init {
        require(verification == IosModelVerification.VERIFIED) {
            "iOS model provenance must carry verified runtime evidence"
        }
        require(modelVersion.isNotBlank()) { "iOS model version must not be blank" }
        require(integrityIdentity.isNotBlank()) { "iOS model integrity identity must not be blank" }
        require(compatibilityTier.isNotBlank()) { "iOS model compatibility tier must not be blank" }
        require(calibrationVersion.isNotBlank()) { "iOS model calibration version must not be blank" }
        when (ownership) {
            IosModelOwnership.APP_MANAGED -> {
                requireSha256Digest(
                    integrityIdentity,
                    "App-managed iOS model integrity identity must be a lowercase SHA-256 digest",
                )
                rollbackTargetVersion?.let {
                    require(it.isNotBlank()) { "iOS model rollback target must not be blank" }
                }
            }

            IosModelOwnership.PLATFORM_MANAGED -> require(rollbackTargetVersion == null) {
                "Platform-managed iOS models cannot claim app rollback"
            }
        }
    }

    fun toInferenceProvenance(): InferenceProvenance = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = LOCAL_IOS_ENGINE_ID,
        modelVersion = modelVersion,
        calibrationVersion = calibrationVersion,
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )
}

sealed interface IosRuntimeCapability {
    val model: IosModelProvenance?

    data class Ready(override val model: IosModelProvenance) : IosRuntimeCapability

    data class Unavailable(
        val state: CapabilityState,
        val reason: CapabilityReason,
        val userMessageKey: String = "inference.capability.${state.key}",
        override val model: IosModelProvenance? = null,
    ) : IosRuntimeCapability {
        init {
            require(state != CapabilityState.READY) {
                "Unavailable iOS runtime capability cannot be ready"
            }
            require(reason != CapabilityReason.NONE) {
                "Unavailable iOS runtime capability must have a reason"
            }
            require(userMessageKey.isNotBlank()) { "iOS capability user message key must not be blank" }
        }
    }
}

sealed interface IosRuntimeOutcome {
    data class Candidates(val candidates: List<IosRuntimeCandidate>) : IosRuntimeOutcome

    data class Abstention(val reason: AbstentionReason, val userMessageKey: String) : IosRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "iOS abstention message key must not be blank" }
        }
    }

    data class Failure(val code: InferenceFailureCode, val retryable: Boolean, val userMessageKey: String) :
        IosRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "iOS failure message key must not be blank" }
        }
    }
}

data class IosRuntimeCandidate(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: Confidence,
    val unresolvedAssumptions: List<InferenceAssumption>,
) {
    init {
        require(candidateId.isNotBlank()) { "iOS candidate ID must not be blank" }
        require(label.isNotBlank()) { "iOS candidate label must not be blank" }
        require(rank > 0) { "iOS candidate rank must be positive" }
    }
}

data class IosInferenceAttemptToken(val attemptId: AttemptId, val generation: Long)

interface IosLocalInferenceRuntime {
    suspend fun capability(): IosRuntimeCapability

    suspend fun analyze(input: NormalizedPhotoInput, token: IosInferenceAttemptToken): IosRuntimeOutcome

    /** Must be safe to call more than once for one token. */
    fun release(token: IosInferenceAttemptToken)

    /** Requests best-effort cancellation of an in-flight native operation. */
    fun cancel(token: IosInferenceAttemptToken) = Unit
}

class IosInferenceInputException(message: String) : IllegalArgumentException(message)

/** Contract-v1 iOS adapter; routing and consent remain outside this module. */
class LocalIosInferenceEngine(
    private val photos: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
    private val runtime: IosLocalInferenceRuntime,
    private val digest: (ByteArray) -> String,
) : PhotoInferenceEngine {
    private val generations = InferenceGenerationController<IosInferenceAttemptToken>(
        cancelToken = runtime::cancel,
        releaseToken = runtime::release,
    )

    override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1

    override suspend fun capability(): EngineCapability = runtime.capability().toEngineCapability()

    override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
        val runtimeCapability = runtime.capability()
        val provenance = runtimeCapability.model?.toInferenceProvenance() ?: unavailableProvenance()
        if (runtimeCapability is IosRuntimeCapability.Unavailable) {
            return PhotoInferenceResult(
                contractVersion = contractVersion,
                outcome = InferenceOutcome.Failure(
                    code = InferenceFailureCode.MODEL_UNAVAILABLE,
                    retryable = runtimeCapability.state.isRetryable,
                    userMessageKey = runtimeCapability.userMessageKey,
                    provenance = provenance,
                ),
            )
        }

        val input = try {
            readAndValidate(request)
        } catch (_: IosInferenceInputException) {
            return PhotoInferenceResult(
                contractVersion = contractVersion,
                outcome = InferenceOutcome.Failure(
                    code = InferenceFailureCode.INVALID_INPUT,
                    retryable = false,
                    userMessageKey = "inference.failure.invalid_input",
                    provenance = provenance,
                ),
            )
        }
        return try {
            val outcome = generations.run(
                attemptId = request.attemptContext.attemptId,
                tokenFactory = { generation ->
                    IosInferenceAttemptToken(
                        attemptId = request.attemptContext.attemptId,
                        generation = generation,
                    )
                },
            ) { token -> runtime.analyze(input, token) }
            outcome.toContractResult(provenance)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PhotoInferenceResult(
                contractVersion = contractVersion,
                outcome = InferenceOutcome.Failure(
                    code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                    retryable = true,
                    userMessageKey = "inference.failure.runtime",
                    provenance = provenance,
                ),
            )
        }
    }

    /** Invalidates the current generation and asks the native runtime to stop. */
    suspend fun cancel(attemptId: AttemptId) {
        generations.cancel(attemptId)
    }

    private fun readAndValidate(request: PhotoInferenceRequest): NormalizedPhotoInput {
        if (!leases.isActive(request.photoRef)) {
            rejectInput("Photo content lease is not active for this attempt")
        }
        val input = photos.read(request.photoRef) ?: rejectInput("Photo content is unavailable for this attempt")
        if (input.ref != request.photoRef) {
            rejectInput("Photo content belongs to another attempt")
        }
        if (digest(input.bytes) != input.contentDigest) {
            rejectInput("Photo content digest does not match its lease")
        }
        return input
    }

    private fun rejectInput(message: String): Nothing = throw IosInferenceInputException(message)

    private fun unavailableProvenance(): InferenceProvenance = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = LOCAL_IOS_ENGINE_ID,
        modelVersion = UNAVAILABLE_MODEL_VERSION,
        calibrationVersion = UNAVAILABLE_CALIBRATION_VERSION,
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )
}

private fun IosRuntimeCapability.toEngineCapability(): EngineCapability = when (this) {
    is IosRuntimeCapability.Ready -> EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = LOCAL_IOS_ENGINE_ID,
        modelVersion = model.modelVersion,
    )

    is IosRuntimeCapability.Unavailable -> EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = state,
        reason = reason,
        userMessageKey = userMessageKey,
        engineId = LOCAL_IOS_ENGINE_ID,
        modelVersion = model?.modelVersion,
    )
}

private fun IosRuntimeOutcome.toContractResult(provenance: InferenceProvenance): PhotoInferenceResult =
    PhotoInferenceResult(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        outcome = when (this) {
            is IosRuntimeOutcome.Candidates -> InferenceOutcome.Candidates(
                candidates.map { candidate ->
                    InferenceCandidate(
                        candidateId = candidate.candidateId,
                        label = candidate.label,
                        rank = candidate.rank,
                        confidence = candidate.confidence,
                        unresolvedAssumptions = candidate.unresolvedAssumptions,
                        provenance = provenance,
                    )
                },
            )

            is IosRuntimeOutcome.Abstention -> InferenceOutcome.Abstention(
                reason = reason,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )

            is IosRuntimeOutcome.Failure -> InferenceOutcome.Failure(
                code = code,
                retryable = retryable,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )
        },
    )

private val CapabilityState.key: String
    get() = name.lowercase().replace('_', '-')

private val CapabilityState.isRetryable: Boolean
    get() = this == CapabilityState.DOWNLOADING || this == CapabilityState.TEMPORARILY_UNAVAILABLE
