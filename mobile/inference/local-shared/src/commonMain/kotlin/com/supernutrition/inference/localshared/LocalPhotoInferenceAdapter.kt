package com.supernutrition.inference.localshared

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

private const val UNAVAILABLE_MODEL_VERSION = "model-unavailable"
private const val UNAVAILABLE_CALIBRATION_VERSION = "calibration-unavailable"

enum class LocalModelOwnership {
    APP_MANAGED,
    PLATFORM_MANAGED,
}

enum class LocalModelVerification {
    VERIFIED,
    UNVERIFIED,
}

data class LocalModelProvenance(
    val modelVersion: String,
    val integrityIdentity: String,
    val compatibilityTier: String,
    val calibrationVersion: String,
    val ownership: LocalModelOwnership,
    val rollbackTargetVersion: String? = null,
    val verification: LocalModelVerification = LocalModelVerification.UNVERIFIED,
) {
    init {
        require(verification == LocalModelVerification.VERIFIED) {
            "Local model provenance must carry verified runtime evidence"
        }
        require(modelVersion.isNotBlank()) { "Local model version must not be blank" }
        require(integrityIdentity.isNotBlank()) { "Local model integrity identity must not be blank" }
        require(compatibilityTier.isNotBlank()) { "Local model compatibility tier must not be blank" }
        require(calibrationVersion.isNotBlank()) { "Local model calibration version must not be blank" }
        when (ownership) {
            LocalModelOwnership.APP_MANAGED -> {
                requireSha256Digest(
                    integrityIdentity,
                    "App-managed local model integrity identity must be a lowercase SHA-256 digest",
                )
                rollbackTargetVersion?.let {
                    require(it.isNotBlank()) { "Local model rollback target must not be blank" }
                }
            }

            LocalModelOwnership.PLATFORM_MANAGED -> require(rollbackTargetVersion == null) {
                "Platform-managed local models cannot claim app rollback"
            }
        }
    }

    fun toInferenceProvenance(engineId: String): InferenceProvenance = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = engineId,
        modelVersion = modelVersion,
        calibrationVersion = calibrationVersion,
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )
}

sealed interface LocalRuntimeCapability {
    val model: LocalModelProvenance?

    data class Ready(override val model: LocalModelProvenance) : LocalRuntimeCapability

    data class Unavailable(
        val state: CapabilityState,
        val reason: CapabilityReason,
        val userMessageKey: String = "inference.capability.${state.key}",
        override val model: LocalModelProvenance? = null,
    ) : LocalRuntimeCapability {
        init {
            require(state != CapabilityState.READY) { "Unavailable local runtime capability cannot be ready" }
            require(reason != CapabilityReason.NONE) { "Unavailable local runtime capability must have a reason" }
            require(userMessageKey.isNotBlank()) { "Local capability user message key must not be blank" }
        }
    }
}

sealed interface LocalRuntimeOutcome {
    data class Candidates(val candidates: List<LocalRuntimeCandidate>) : LocalRuntimeOutcome

    data class Abstention(val reason: AbstentionReason, val userMessageKey: String) : LocalRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "Local abstention message key must not be blank" }
        }
    }

    data class Failure(val code: InferenceFailureCode, val retryable: Boolean, val userMessageKey: String) :
        LocalRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "Local failure message key must not be blank" }
        }
    }
}

data class LocalRuntimeCandidate(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: Confidence,
    val unresolvedAssumptions: List<InferenceAssumption>,
) {
    init {
        require(candidateId.isNotBlank()) { "Local candidate ID must not be blank" }
        require(label.isNotBlank()) { "Local candidate label must not be blank" }
        require(rank > 0) { "Local candidate rank must be positive" }
    }
}

data class LocalInferenceAttemptToken(val attemptId: AttemptId, val generation: Long, val gridCellIndex: Int? = null) {
    init {
        require(gridCellIndex == null || gridCellIndex in 0..8) {
            "Grid cell index must be null or between 0 and 8"
        }
    }
}

interface LocalPhotoInferenceRuntime {
    suspend fun capability(): LocalRuntimeCapability

    suspend fun analyze(input: NormalizedPhotoInput, token: LocalInferenceAttemptToken): LocalRuntimeOutcome

    /** Must be safe to call more than once for one token. */
    fun release(token: LocalInferenceAttemptToken)

    /** Requests best-effort cancellation of an in-flight native operation. */
    fun cancel(token: LocalInferenceAttemptToken) = Unit
}

class LocalInferenceInputException(message: String) : IllegalArgumentException(message)

/** Shared v1 local adapter. Platform wrappers choose only their engine identifier. */
class LocalPhotoInferenceAdapter(
    private val photos: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
    private val runtime: LocalPhotoInferenceRuntime,
    private val digest: (ByteArray) -> String,
    private val engineId: String,
) : PhotoInferenceEngine {
    private val generations = InferenceGenerationController<LocalInferenceAttemptToken>(
        cancelToken = runtime::cancel,
        releaseToken = runtime::release,
    )

    override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1

    override suspend fun capability(): EngineCapability = runtime.capability().toEngineCapability(engineId)

    override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
        val runtimeCapability = runtime.capability()
        val provenance = runtimeCapability.model?.toInferenceProvenance(engineId) ?: unavailableProvenance(engineId)
        if (runtimeCapability is LocalRuntimeCapability.Unavailable) {
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
        } catch (_: LocalInferenceInputException) {
            return invalidInputFailure(provenance)
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
            runtimeFailure(provenance)
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

    private fun rejectInput(message: String): Nothing = throw LocalInferenceInputException(message)
}

private fun invalidInputFailure(provenance: InferenceProvenance): PhotoInferenceResult = PhotoInferenceResult(
    contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    outcome = InferenceOutcome.Failure(
        code = InferenceFailureCode.INVALID_INPUT,
        retryable = false,
        userMessageKey = "inference.failure.invalid_input",
        provenance = provenance,
    ),
)

private fun runtimeFailure(provenance: InferenceProvenance): PhotoInferenceResult = PhotoInferenceResult(
    contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    outcome = InferenceOutcome.Failure(
        code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
        retryable = true,
        userMessageKey = "inference.failure.runtime",
        provenance = provenance,
    ),
)

private fun unavailableProvenance(engineId: String): InferenceProvenance = InferenceProvenance(
    route = InferenceRoute.LOCAL,
    engineId = engineId,
    modelVersion = UNAVAILABLE_MODEL_VERSION,
    calibrationVersion = UNAVAILABLE_CALIBRATION_VERSION,
    contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
)

private fun LocalRuntimeCapability.toEngineCapability(engineId: String): EngineCapability = when (this) {
    is LocalRuntimeCapability.Ready -> EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = engineId,
        modelVersion = model.modelVersion,
    )

    is LocalRuntimeCapability.Unavailable -> EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = state,
        reason = reason,
        userMessageKey = userMessageKey,
        engineId = engineId,
        modelVersion = model?.modelVersion,
    )
}

private fun LocalRuntimeOutcome.toContractResult(provenance: InferenceProvenance): PhotoInferenceResult =
    PhotoInferenceResult(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        outcome = when (this) {
            is LocalRuntimeOutcome.Candidates -> InferenceOutcome.Candidates(
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

            is LocalRuntimeOutcome.Abstention -> InferenceOutcome.Abstention(
                reason = reason,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )

            is LocalRuntimeOutcome.Failure -> InferenceOutcome.Failure(
                code = code,
                retryable = retryable,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )
        },
    )

private val CapabilityState.key: String
    get() = name.lowercase().replace('_', '-')

internal val CapabilityState.isRetryable: Boolean
    get() = this == CapabilityState.DOWNLOADING || this == CapabilityState.TEMPORARILY_UNAVAILABLE
