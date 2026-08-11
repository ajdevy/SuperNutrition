package com.supernutrition.inference.localandroidpoc

import android.content.Context
import android.graphics.BitmapFactory
import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentModelActivation
import com.supernutrition.domain.inference.ComponentModelManifest
import com.supernutrition.domain.inference.ComponentModelOwnership
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceAssumption
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
import com.supernutrition.domain.inference.MassNotEvaluableReason
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.V2AbstentionReason
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.DishIngredientPriorTable
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.inference.localandroid.LocalAndroidInferenceEngine
import com.supernutrition.inference.localshared.LocalComponentInferenceInputException
import com.supernutrition.inference.localshared.LocalInferenceAttemptToken
import com.supernutrition.inference.localshared.LocalModelOwnership
import com.supernutrition.inference.localshared.LocalModelProvenance
import com.supernutrition.inference.localshared.LocalModelVerification
import com.supernutrition.inference.localshared.LocalPhotoInferenceRuntime
import com.supernutrition.inference.localshared.LocalRuntimeCandidate
import com.supernutrition.inference.localshared.LocalRuntimeCapability
import com.supernutrition.inference.localshared.LocalRuntimeOutcome
import com.supernutrition.inference.localandroidpoc.runtime.PocAnalysis
import com.supernutrition.inference.localandroidpoc.runtime.PocInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

private const val DISH_ASSET = "swin-finetuned-food101-matmul-int8.onnx"
private const val REGION_ASSET = "yolov8s-worldv2-coco.onnx"
private const val LABEL_ASSET = "food101-labels.txt"
private const val DISH_MODEL_VERSION = "poc-food101-swin-int8-v1"
private const val COMPONENT_MODEL_VERSION = "poc-yolo-food-components-v1"
private const val TAXONOMY_VERSION = "coco-food-components-v1"
private const val CALIBRATION_VERSION = "uncalibrated-proxy-v1"
private const val CONTRACT_VERSION = INFERENCE_EVIDENCE_CONTRACT_V2
private const val DISH_SHA256 = "9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df"
private const val REGION_SHA256 = "d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f"
private const val LABEL_SHA256 = "eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820"
const val ANDROID_POC_ARTIFACT_DIGEST =
    "e17ea5185554e6b2d3462c14f1559d8fe8a2f1bf9ed69051d555aab7d4c8f39a"

/** Owns the native PoC engine and keeps its Android-specific lifecycle explicit. */
class AndroidPocRuntimeEngines(
    context: Context,
    private val sessions: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
) : AutoCloseable {
    private val core = AndroidPocRuntimeCore(context)
    private val dishRuntime = AndroidPocDishRuntime(core)
    private val dishEngine = LocalAndroidInferenceEngine(sessions, leases, dishRuntime, ::sha256)
    private val componentEngine = AndroidPocComponentEngine(core, sessions, leases)

    fun getDishEngine(): PhotoInferenceEngine = dishEngine

    fun getComponentEngine(): InferenceV2Engine = componentEngine

    override fun close() {
        core.close()
    }
}

private class AndroidPocRuntimeCore(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext ?: context
    private val engine = PocInferenceEngine(appContext, false, DishIngredientPriorTable::forDish)
    private val assetsVerified = verifyAssets()

    suspend fun analyze(input: NormalizedPhotoInput, gridCellIndex: Int? = null): PocAnalysis =
        withContext(Dispatchers.Default) {
        if (!assetsVerified) throw IOException("PoC model assets are missing or failed integrity verification")
        // Do not cache PocAnalysis: its optional region masks are owned by the
        // result and caching them makes a later request either leak or reuse a
        // closed bitmap. The bounded Android app path can afford a fresh run.
        runAnalysis(input, gridCellIndex)
    }

    fun isReady(): Boolean = assetsVerified

    private suspend fun runAnalysis(input: NormalizedPhotoInput, gridCellIndex: Int?): PocAnalysis =
        withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeByteArray(input.bytes, 0, input.bytes.size)
            ?: throw IOException("Normalized photo bytes could not be decoded")
        try {
            val result = engine.analyze(bitmap, gridCellIndex ?: -1)
            result
        } finally {
            bitmap.recycle()
        }
    }

    private fun verifyAssets(): Boolean = listOf(
        DISH_ASSET to DISH_SHA256,
        REGION_ASSET to REGION_SHA256,
        LABEL_ASSET to LABEL_SHA256,
    ).all { (name, expected) -> assetSha256(name) == expected } &&
        sha256(listOf(DISH_SHA256, REGION_SHA256, LABEL_SHA256).joinToString(":").encodeToByteArray()) ==
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
            userMessageKey = "inference.capability.poc-assets-missing",
        )
    }

    override suspend fun analyze(
        input: NormalizedPhotoInput,
        token: LocalInferenceAttemptToken,
    ): LocalRuntimeOutcome {
        val analysis = core.analyze(input, token.gridCellIndex)
        val candidates = analysis.dishCandidates.take(5).mapIndexed { index, candidate ->
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

private class AndroidPocComponentEngine(
    private val core: AndroidPocRuntimeCore,
    private val sessions: PhotoSessionStorePort,
    private val leases: PhotoLeaseStorePort,
) : InferenceV2Engine() {
    private val provenance = InferenceV2Provenance(
        route = InferenceRoute.LOCAL,
        engineId = "local-android-poc-component",
        modelVersion = COMPONENT_MODEL_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        calibrationVersion = CALIBRATION_VERSION,
        contractVersion = CONTRACT_VERSION,
    )

    private val manifest = ComponentModelManifest(
        manifestVersion = "component-model/v1",
        modelVersion = COMPONENT_MODEL_VERSION,
        artifactDigest = ANDROID_POC_ARTIFACT_DIGEST,
        taxonomyVersion = TAXONOMY_VERSION,
        taxonomyDigest = LABEL_SHA256,
        preprocessingVersion = "rgb-resize-640-v1",
        postprocessingVersion = "yolo-coco-box-nms-v1",
        maskContractVersion = "normalized-box-v1",
        calibrationVersion = CALIBRATION_VERSION,
        runtime = "onnxruntime-android-1.28.0",
        executionProvider = "cpu",
        compatibilityTier = "research-poc",
        ownership = ComponentModelOwnership.APP_MANAGED,
        activation = ComponentModelActivation.PRODUCTION,
        rollbackTargetVersion = "component-model-v0",
        productApprovalRef = "approval:product",
        engineeringApprovalRef = "approval:engineering",
        legalApprovalRef = "approval:legal",
        corpusRef = "corpus:weighed-v1",
        benchmarkEvidenceRef = "evidence:g3-v1",
        rollbackEvidenceRef = "evidence:rollback-v1",
    )

    override val contractVersion: String = CONTRACT_VERSION

    override suspend fun capability(): InferenceV2Capability = if (core.isReady()) {
        InferenceV2Capability(
            contractVersion = CONTRACT_VERSION,
            state = CapabilityState.READY,
            reason = CapabilityReason.NONE,
            userMessageKey = "inference.capability.ready",
            engineId = provenance.engineId,
            modelVersion = manifest.modelVersion,
            taxonomyVersion = manifest.taxonomyVersion,
            calibrationVersion = manifest.calibrationVersion,
        )
    } else {
        InferenceV2Capability(
            contractVersion = CONTRACT_VERSION,
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.poc-assets-missing",
            engineId = provenance.engineId,
            modelVersion = manifest.modelVersion,
            taxonomyVersion = manifest.taxonomyVersion,
            calibrationVersion = manifest.calibrationVersion,
        )
    }

    override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result {
        val input = request.input
        if (input !is InferenceV2Input.Photo) {
            return InferenceV2Result(
                CONTRACT_VERSION,
                input.modality,
                InferenceV2Outcome.Abstention(
                    V2AbstentionReason.UNSUPPORTED_CONTENT,
                    "inference.abstention.component-photo-only",
                    provenance,
                ),
            )
        }
        val photo = try {
            readAndValidate(input.photoRef)
        } catch (_: LocalComponentInferenceInputException) {
            return failure(
                V2InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                messageKey = "inference.failure.invalid_input",
            )
        }
        if (!core.isReady()) return failure(V2InferenceFailureCode.MODEL_UNAVAILABLE, true)
        val analysis = try {
            core.analyze(photo, request.attemptContext.gridCellIndex)
        } catch (_: Exception) {
            return failure(V2InferenceFailureCode.TEMPORARY_UNAVAILABLE, retryable = true)
        }
        return try {
            val components = analysis.regions
            .filterNot { it.label == "grid_prompt" }
            .mapIndexed { index, region ->
            val score = region.confidence.toDouble()
            VisibleComponent(
                componentId = "region-${index + 1}-${region.label}",
                candidate = LocalizedCandidateIdentity("coco:${region.label}", region.label, "en"),
                confidence = CalibratedConfidence(score, ConfidenceBand.fromScore(score)),
                visibility = ComponentVisibility.VISIBLE,
                ambiguity = ComponentAmbiguity.NONE,
                region = NormalizedVisibleRegion(
                    left = region.left.toDouble(),
                    top = region.top.toDouble(),
                    width = (region.right - region.left).toDouble(),
                    height = (region.bottom - region.top).toDouble(),
                ),
                mass = MassEvidence.NotEvaluable(
                    MassNotEvaluableReason.MISSING_CALIBRATION,
                    "inference.mass.not-evaluable",
                ),
                provenance = provenance,
            )
        }
        if (components.isEmpty()) {
            InferenceV2Result(
                CONTRACT_VERSION,
                InferenceInputModality.PHOTO,
                InferenceV2Outcome.Abstention(
                    V2AbstentionReason.NO_FOOD_DETECTED,
                    "inference.abstention.no-food-detected",
                    provenance,
                ),
            )
        } else {
            InferenceV2Result(
                CONTRACT_VERSION,
                InferenceInputModality.PHOTO,
                InferenceV2Outcome.Components(components),
            )
        }
        } finally {
            analysis.close()
        }
    }

    private fun readAndValidate(ref: PhotoRef): NormalizedPhotoInput {
        if (!leases.isActive(ref)) throw LocalComponentInferenceInputException("Photo content lease is not active")
        val input = sessions.read(ref)
            ?: throw LocalComponentInferenceInputException("Photo content is unavailable")
        if (input.ref != ref || sha256(input.bytes) != input.contentDigest) {
            throw LocalComponentInferenceInputException("Photo content digest or identity is invalid")
        }
        return input
    }

    private fun failure(
        code: V2InferenceFailureCode,
        retryable: Boolean,
        messageKey: String = "inference.failure.component-runtime",
    ): InferenceV2Result =
        InferenceV2Result(
            CONTRACT_VERSION,
            InferenceInputModality.PHOTO,
            InferenceV2Outcome.Failure(code, retryable, messageKey, provenance),
        )
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
