package com.supernutrition.inference.localandroid

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.EngineCapability
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceFailureCode
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

const val LOCAL_ANDROID_ENGINE_ID = "local-android"
private const val UNAVAILABLE_MODEL_VERSION = "model-unavailable"
private const val UNAVAILABLE_CALIBRATION_VERSION = "calibration-unavailable"

enum class AndroidModelOwnership {
    APP_MANAGED,
    PLATFORM_MANAGED,
}

enum class AndroidModelVerification {
    VERIFIED,
    UNVERIFIED,
}

data class AndroidModelProvenance(
    val modelVersion: String,
    val integrityIdentity: String,
    val compatibilityTier: String,
    val calibrationVersion: String,
    val ownership: AndroidModelOwnership,
    val rollbackTargetVersion: String? = null,
    val verification: AndroidModelVerification = AndroidModelVerification.UNVERIFIED,
) {
    init {
        require(verification == AndroidModelVerification.VERIFIED) {
            "Android model provenance must carry verified runtime evidence"
        }
        require(modelVersion.isNotBlank()) { "Android model version must not be blank" }
        require(integrityIdentity.isNotBlank()) { "Android model integrity identity must not be blank" }
        require(compatibilityTier.isNotBlank()) { "Android model compatibility tier must not be blank" }
        require(calibrationVersion.isNotBlank()) { "Android model calibration version must not be blank" }
        when (ownership) {
            AndroidModelOwnership.APP_MANAGED -> {
                requireSha256Digest(
                    integrityIdentity,
                    "App-managed Android model integrity identity must be a lowercase SHA-256 digest",
                )
                rollbackTargetVersion?.let {
                    require(it.isNotBlank()) { "Android model rollback target must not be blank" }
                }
            }

            AndroidModelOwnership.PLATFORM_MANAGED -> {
                require(rollbackTargetVersion == null) {
                    "Platform-managed Android models cannot claim app rollback"
                }
            }
        }
    }

    fun toInferenceProvenance(): InferenceProvenance = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = LOCAL_ANDROID_ENGINE_ID,
        modelVersion = modelVersion,
        calibrationVersion = calibrationVersion,
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )
}

sealed interface AndroidRuntimeCapability {
    val model: AndroidModelProvenance?

    data class Ready(override val model: AndroidModelProvenance) : AndroidRuntimeCapability

    data class Unavailable(
        val state: CapabilityState,
        val reason: CapabilityReason,
        val userMessageKey: String = "inference.capability.${state.key}",
        override val model: AndroidModelProvenance? = null,
    ) : AndroidRuntimeCapability {
        init {
            require(state != CapabilityState.READY) {
                "Unavailable Android runtime capability cannot be ready"
            }
            require(reason != CapabilityReason.NONE) {
                "Unavailable Android runtime capability must have a reason"
            }
            require(userMessageKey.isNotBlank()) {
                "Android capability user message key must not be blank"
            }
        }
    }
}

sealed interface AndroidRuntimeOutcome {
    data class Candidates(val candidates: List<AndroidRuntimeCandidate>) : AndroidRuntimeOutcome

    data class Abstention(val reason: AbstentionReason, val userMessageKey: String) : AndroidRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "Android abstention message key must not be blank" }
        }
    }

    data class Failure(val code: InferenceFailureCode, val retryable: Boolean, val userMessageKey: String) :
        AndroidRuntimeOutcome {
        init {
            require(userMessageKey.isNotBlank()) { "Android failure message key must not be blank" }
        }
    }
}

data class AndroidRuntimeCandidate(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: com.supernutrition.domain.inference.Confidence,
    val unresolvedAssumptions: List<com.supernutrition.domain.inference.InferenceAssumption>,
) {
    init {
        require(candidateId.isNotBlank()) { "Android candidate ID must not be blank" }
        require(label.isNotBlank()) { "Android candidate label must not be blank" }
        require(rank > 0) { "Android candidate rank must be positive" }
    }
}

data class AndroidInferenceAttemptToken(
    val attemptId: AttemptId,
    val generation: Long,
    val gridCellIndex: Int? = null,
) {
    init {
        require(gridCellIndex == null || gridCellIndex in 0..8) {
            "Grid cell index must be null or between 0 and 8"
        }
    }
}

interface AndroidLocalInferenceRuntime {
    suspend fun capability(): AndroidRuntimeCapability

    suspend fun analyze(input: NormalizedPhotoInput, token: AndroidInferenceAttemptToken): AndroidRuntimeOutcome

    /** Must be safe to call more than once for one token. */
    fun release(token: AndroidInferenceAttemptToken)

    /** Requests best-effort cancellation of an in-flight native operation. */
    fun cancel(token: AndroidInferenceAttemptToken) = Unit
}

class AndroidInferenceInputException(message: String) : IllegalArgumentException(message)

/** Contract-v1 Android adapter; routing and consent remain outside this module. */
class LocalAndroidInferenceEngine(
    private val photos: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
    private val runtime: AndroidLocalInferenceRuntime,
    private val digest: (ByteArray) -> String,
) : PhotoInferenceEngine {
    private var nextGeneration = 0L
    private val stateMutex = Mutex()
    private val activeGenerations = mutableMapOf<AttemptId, Long>()
    private val activeTokens = mutableMapOf<AttemptId, AndroidInferenceAttemptToken>()

    override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1

    override suspend fun capability(): EngineCapability = runtime.capability().toEngineCapability()

    override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
        val runtimeCapability = runtime.capability()
        val provenance = runtimeCapability.model?.toInferenceProvenance() ?: unavailableProvenance()
        if (runtimeCapability is AndroidRuntimeCapability.Unavailable) {
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

        val input = readAndValidate(request)
        val (token, previousToken) = stateMutex.withLock {
            val token = AndroidInferenceAttemptToken(
                attemptId = request.attemptContext.attemptId,
                generation = ++nextGeneration,
                gridCellIndex = request.attemptContext.gridCellIndex,
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
                throw CancellationException("Android inference result was superseded")
            }
            outcome.toContractResult(provenance)
        } catch (cancellation: CancellationException) {
            runtime.cancel(token)
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
        } finally {
            stateMutex.withLock {
                if (activeGenerations[token.attemptId] == token.generation) {
                    activeGenerations.remove(token.attemptId)
                }
                if (activeTokens[token.attemptId] == token) {
                    activeTokens.remove(token.attemptId)
                }
            }
            runtime.release(token)
        }
    }

    /** Invalidates the current generation and asks the native runtime to stop. */
    suspend fun cancel(attemptId: AttemptId) {
        val token = stateMutex.withLock {
            activeGenerations.remove(attemptId)
            activeTokens.remove(attemptId)
        }
        token?.let(runtime::cancel)
    }

    private fun readAndValidate(request: PhotoInferenceRequest): NormalizedPhotoInput {
        if (!leases.isActive(request.photoRef)) {
            rejectInput("Photo content lease is not active for this attempt")
        }
        val input = photos.read(request.photoRef)
            ?: rejectInput("Photo content is unavailable for this attempt")
        if (input.ref != request.photoRef) {
            rejectInput("Photo content belongs to another attempt")
        }
        if (digest(input.bytes) != input.contentDigest) {
            rejectInput("Photo content digest does not match its lease")
        }
        return input
    }

    private fun rejectInput(message: String): Nothing = throw AndroidInferenceInputException(message)

    private fun unavailableProvenance(): InferenceProvenance = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = LOCAL_ANDROID_ENGINE_ID,
        modelVersion = UNAVAILABLE_MODEL_VERSION,
        calibrationVersion = UNAVAILABLE_CALIBRATION_VERSION,
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )
}

private fun AndroidRuntimeCapability.toEngineCapability(): EngineCapability = when (this) {
    is AndroidRuntimeCapability.Ready -> EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = LOCAL_ANDROID_ENGINE_ID,
        modelVersion = model.modelVersion,
    )

    is AndroidRuntimeCapability.Unavailable -> EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = state,
        reason = reason,
        userMessageKey = userMessageKey,
        engineId = LOCAL_ANDROID_ENGINE_ID,
        modelVersion = model?.modelVersion,
    )
}

private fun AndroidRuntimeOutcome.toContractResult(provenance: InferenceProvenance): PhotoInferenceResult =
    PhotoInferenceResult(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        outcome = when (this) {
            is AndroidRuntimeOutcome.Candidates -> InferenceOutcome.Candidates(
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

            is AndroidRuntimeOutcome.Abstention -> InferenceOutcome.Abstention(
                reason = reason,
                userMessageKey = userMessageKey,
                provenance = provenance,
            )

            is AndroidRuntimeOutcome.Failure -> InferenceOutcome.Failure(
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
