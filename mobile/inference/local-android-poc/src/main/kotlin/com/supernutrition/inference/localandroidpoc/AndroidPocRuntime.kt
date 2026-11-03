package com.supernutrition.inference.localandroidpoc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Capability
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.inference.localandroid.LocalAndroidInferenceEngine
import com.supernutrition.inference.localshared.LocalInferenceAttemptToken
import com.supernutrition.inference.localshared.LocalModelOwnership
import com.supernutrition.inference.localshared.LocalModelProvenance
import com.supernutrition.inference.localshared.LocalModelVerification
import com.supernutrition.inference.localshared.LocalPhotoInferenceRuntime
import com.supernutrition.inference.localshared.LocalRuntimeCandidate
import com.supernutrition.inference.localshared.LocalRuntimeCapability
import com.supernutrition.inference.localshared.LocalRuntimeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest

private const val DISH_ASSET = "swin-finetuned-food101-matmul-int8.onnx"
private const val LABEL_ASSET = "food101-labels.txt"
private const val DISH_MODEL_VERSION = "poc-food101-swin-int8-v1"
private const val CONTRACT_VERSION = INFERENCE_EVIDENCE_CONTRACT_V2
private const val DISH_SHA256 = "9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df"
private const val LABEL_SHA256 = "eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820"
const val ANDROID_POC_ARTIFACT_DIGEST =
    "390a541d90a124fc6ca6d601b400ed72708452675497d4ca760750685e4dd63e"

/** Owns the native PoC engine and keeps its Android-specific lifecycle explicit. */
class AndroidPocRuntimeEngines(
    context: Context,
    private val sessions: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
) : AutoCloseable {
    private val core = AndroidPocRuntimeCore(context)
    private val dishRuntime = AndroidPocDishRuntime(core)
    private val dishEngine = LocalAndroidInferenceEngine(sessions, leases, dishRuntime, ::sha256)
    private val componentEngine = AndroidPocComponentEngine()

    fun getDishEngine(): PhotoInferenceEngine = dishEngine

    fun getComponentEngine(): InferenceV2Engine = componentEngine

    override fun close() {
        core.close()
    }
}

private class AndroidPocRuntimeCore(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext ?: context
    private val engine = OptionalFoodIdentificationEngine.attach(appContext)
    private val assetsVerified = verifyAssets()

    suspend fun analyze(input: NormalizedPhotoInput, gridCellIndex: Int? = null): List<FoodDishCandidate> =
        withContext(Dispatchers.Default) {
            if (!engine.isAttached) throw IOException("Private local food-identification runtime is not attached")
            if (!assetsVerified) throw IOException("PoC model assets are missing or failed integrity verification")
            val bitmap = BitmapFactory.decodeByteArray(input.bytes, 0, input.bytes.size)
                ?: throw IOException("Normalized photo bytes could not be decoded")
            try {
                engine.analyze(bitmap, gridCellIndex ?: -1)
            } finally {
                bitmap.recycle()
            }
    }

    fun isReady(): Boolean = engine.isAttached && assetsVerified

    fun unavailableMessageKey(): String = if (engine.isAttached) {
        "inference.capability.poc-assets-missing"
    } else {
        "inference.capability.local-model-not-attached"
    }

    private fun verifyAssets(): Boolean = listOf(
        DISH_ASSET to DISH_SHA256,
        LABEL_ASSET to LABEL_SHA256,
    ).all { (name, expected) -> assetSha256(name) == expected } &&
        sha256(listOf(DISH_SHA256, LABEL_SHA256).joinToString(":").encodeToByteArray()) ==
            ANDROID_POC_ARTIFACT_DIGEST

    private fun assetSha256(name: String): String = runCatching {
        appContext.assets.open(name).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
            digest.digest().toHex()
        }
    }.getOrDefault("")

    override fun close() {
        engine.close()
    }
}

private data class FoodDishCandidate(val label: String, val confidence: Float)

/** The AAR is loaded only in local builds; default builds report it as unattached. */
private class OptionalFoodIdentificationEngine private constructor(private val engine: Any?) : AutoCloseable {
    val isAttached: Boolean get() = engine != null

    fun analyze(bitmap: Bitmap, gridCellIndex: Int): List<FoodDishCandidate> {
        val attachedEngine = engine ?: throw IOException("Private local food-identification runtime is not attached")
        val analysis = invokePublic(
            attachedEngine,
            "analyze",
            arrayOf(Bitmap::class.java, Int::class.javaPrimitiveType!!),
            bitmap,
            gridCellIndex,
        ) ?: throw IOException("Food-identification runtime returned no analysis")
        return try {
            val candidates = invokePublic(analysis, "getDishCandidates", emptyArray()) as? List<*>
                ?: throw IOException("Food-identification runtime returned invalid dish candidates")
            candidates.map { candidate ->
                val value = candidate ?: throw IOException("Food-identification runtime returned an empty candidate")
                val label = invokePublic(value, "getLabel", emptyArray()) as? String
                    ?: throw IOException("Food-identification runtime returned a candidate without a label")
                val confidence = invokePublic(value, "getConfidence", emptyArray()) as? Number
                    ?: throw IOException("Food-identification runtime returned a candidate without confidence")
                FoodDishCandidate(label, confidence.toFloat())
            }
        } finally {
            (analysis as? AutoCloseable)?.close()
        }
    }

    override fun close() {
        (engine as? AutoCloseable)?.close()
    }

    companion object {
        fun attach(context: Context): OptionalFoodIdentificationEngine {
            val engine = try {
                val type = Class.forName("com.supernutrition.foodidentification.runtime.PocInferenceEngine")
                type.getConstructor(Context::class.java, Boolean::class.javaPrimitiveType)
                    .newInstance(context, false)
            } catch (_: ClassNotFoundException) {
                null
            }
            return OptionalFoodIdentificationEngine(engine)
        }

        private fun invokePublic(
            target: Any,
            methodName: String,
            parameterTypes: Array<Class<*>>,
            vararg arguments: Any,
        ): Any? = try {
            target.javaClass.getMethod(methodName, *parameterTypes).invoke(target, *arguments)
        } catch (error: InvocationTargetException) {
            throw error.targetException ?: error
        }
    }
}

private class AndroidPocDishRuntime(private val core: AndroidPocRuntimeCore) : LocalPhotoInferenceRuntime {
    private val model = LocalModelProvenance(
        modelVersion = DISH_MODEL_VERSION,
        integrityIdentity = DISH_SHA256,
        compatibilityTier = "research-poc",
        calibrationVersion = "softmax-proxy-v1",
        ownership = LocalModelOwnership.APP_MANAGED,
        verification = LocalModelVerification.VERIFIED,
    )

    override suspend fun capability(): LocalRuntimeCapability = if (core.isReady()) {
        LocalRuntimeCapability.Ready(model)
    } else {
        LocalRuntimeCapability.Unavailable(
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = core.unavailableMessageKey(),
        )
    }

    override suspend fun analyze(
        input: NormalizedPhotoInput,
        token: LocalInferenceAttemptToken,
    ): LocalRuntimeOutcome {
        val analysis = core.analyze(input, token.gridCellIndex)
        val candidates = analysis.take(5).mapIndexed { index, candidate ->
            LocalRuntimeCandidate(
                candidateId = "food101:${candidate.label}",
                label = candidate.label,
                rank = index + 1,
                confidence = com.supernutrition.domain.inference.Confidence(
                    score = candidate.confidence.toDouble(),
                    band = ConfidenceBand.fromScore(candidate.confidence.toDouble()),
                ),
                unresolvedAssumptions = listOf(
                    InferenceAssumption(AssumptionCode.PORTION_UNKNOWN, "inference.assumption.portion_unknown"),
                    InferenceAssumption(
                        AssumptionCode.HIDDEN_INGREDIENTS_UNKNOWN,
                        "inference.assumption.hidden_ingredients_unknown",
                    ),
                ),
            )
        }
        return if (candidates.isEmpty()) {
            LocalRuntimeOutcome.Abstention(
                reason = com.supernutrition.domain.inference.AbstentionReason.NO_FOOD_DETECTED,
                userMessageKey = "inference.abstention.no-food-detected",
            )
        } else {
            LocalRuntimeOutcome.Candidates(candidates)
        }
    }

    override fun release(token: LocalInferenceAttemptToken) = Unit
}

private class AndroidPocComponentEngine : InferenceV2Engine() {
    private val provenance = InferenceV2Provenance(
        route = InferenceRoute.LOCAL,
        engineId = "local-android-component-unavailable",
        modelVersion = "no-cleared-component-model",
        taxonomyVersion = "unavailable",
        calibrationVersion = "unavailable",
        contractVersion = CONTRACT_VERSION,
    )

    override val contractVersion: String = CONTRACT_VERSION

    override suspend fun capability(): InferenceV2Capability = InferenceV2Capability(
        contractVersion = CONTRACT_VERSION,
        state = CapabilityState.MODEL_REQUIRED,
        reason = CapabilityReason.MODEL_MISSING,
        userMessageKey = "inference.capability.model_required",
        engineId = provenance.engineId,
        modelVersion = provenance.modelVersion,
        taxonomyVersion = provenance.taxonomyVersion,
        calibrationVersion = provenance.calibrationVersion,
    )

    override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result = InferenceV2Result(
        contractVersion = CONTRACT_VERSION,
        inputModality = request.input.modality,
        outcome = InferenceV2Outcome.Failure(
            code = V2InferenceFailureCode.MODEL_UNAVAILABLE,
            retryable = false,
            userMessageKey = "inference.failure.model_unavailable",
            provenance = provenance,
        ),
    )
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
