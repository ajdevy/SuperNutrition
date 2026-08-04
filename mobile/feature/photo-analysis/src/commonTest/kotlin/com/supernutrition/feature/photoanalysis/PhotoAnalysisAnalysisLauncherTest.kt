package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoFormat
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RecoveryReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("InjectDispatcher")
class PhotoAnalysisAnalysisLauncherTest {
    @Test
    fun `launcher runs coordinator and exposes unavailable runtime as offline recovery`() = runBlocking {
        val feature = preparedFeature("launcher-unavailable")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val launcher = launcher(feature, scope, unavailableEngine())

        launcher.start()

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.OFFLINE), feature.state)
        scope.cancel()
    }

    @Test
    fun `launcher cancellation closes an in-flight analysis`() = runBlocking {
        val feature = preparedFeature("launcher-cancel")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        val launcher = launcher(
            feature = feature,
            scope = scope,
            componentEngine = blockingEngine(gate),
        )

        launcher.start()
        assertIs<PhotoAnalysisState.Analyzing>(feature.state)

        launcher.cancel()
        yield()

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED), feature.state)
        scope.cancel()
    }

    private fun launcher(
        feature: PhotoAnalysisFeature,
        scope: CoroutineScope,
        componentEngine: InferenceV2Engine,
    ): CoordinatedPhotoAnalysisAnalysisLauncher = CoordinatedPhotoAnalysisAnalysisLauncher(
        feature = feature,
        dishEngine = EmptyDishEngine,
        componentEngine = componentEngine,
        scope = scope,
        configuration = PhotoAnalysisAnalysisConfiguration(
            requestPrefix = "test-photo-analysis",
            configurationVersion = "test-configuration-v1",
            configurationDigest = "b".repeat(64),
        ),
    )

    private fun unavailableEngine() = object : InferenceV2Engine() {
        override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2

        override suspend fun capability() = error("capability is not used by the coordinator")

        override suspend fun analyzeUnvalidated(request: InferenceV2Request) = InferenceV2Result(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            inputModality = InferenceInputModality.PHOTO,
            outcome = InferenceV2Outcome.Failure(
                code = V2InferenceFailureCode.MODEL_UNAVAILABLE,
                retryable = false,
                userMessageKey = "inference.failure.model_unavailable",
                provenance = InferenceV2Provenance(
                    route = InferenceRoute.LOCAL,
                    engineId = "test-unavailable",
                    modelVersion = "model-unavailable",
                    taxonomyVersion = "taxonomy-unavailable",
                    calibrationVersion = "calibration-unavailable",
                    contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                ),
            ),
        )
    }

    private fun blockingEngine(gate: CompletableDeferred<Unit>) = object : InferenceV2Engine() {
        override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2

        override suspend fun capability() = error("capability is not used by the coordinator")

        override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result {
            gate.await()
            return InferenceV2Result(
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                inputModality = InferenceInputModality.PHOTO,
                outcome = InferenceV2Outcome.Failure(
                    code = V2InferenceFailureCode.MODEL_UNAVAILABLE,
                    retryable = false,
                    userMessageKey = "inference.failure.model_unavailable",
                    provenance = InferenceV2Provenance(
                        route = InferenceRoute.LOCAL,
                        engineId = "test-blocking",
                        modelVersion = "model-unavailable",
                        taxonomyVersion = "taxonomy-unavailable",
                        calibrationVersion = "calibration-unavailable",
                        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                    ),
                ),
            )
        }
    }

    private fun preparedFeature(attempt: String): PhotoAnalysisFeature {
        val feature = PhotoAnalysisFeature(AttemptId(attempt), PhotoLeaseStore(), PhotoSessionStore())
        val ref = feature.issuePhotoRef("photo-$attempt")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        feature.dispatch(
            PhotoAnalysisEvent.PreparationSucceeded(
                NormalizedPhotoInput(
                    ref = ref,
                    bytes = byteArrayOf(1),
                    width = 1,
                    height = 1,
                    orientation = ImageOrientation.UP,
                    colorSpace = "sRGB",
                    metadataKeys = emptySet(),
                    contentDigest = "a".repeat(64),
                ),
            ),
        )
        return feature
    }

    private object EmptyDishEngine : PhotoInferenceEngine {
        override val contractVersion: String = PHOTO_INFERENCE_CONTRACT_V1

        override suspend fun capability() = error("capability is not used by the coordinator")

        override suspend fun analyze(request: PhotoInferenceRequest) = PhotoInferenceResult(
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
            outcome = InferenceOutcome.Failure(
                code = com.supernutrition.domain.inference.InferenceFailureCode.MODEL_UNAVAILABLE,
                retryable = false,
                userMessageKey = "inference.failure.model_unavailable",
                provenance = InferenceProvenance(
                    route = InferenceRoute.LOCAL,
                    engineId = "test-dish",
                    modelVersion = "model-unavailable",
                    calibrationVersion = "calibration-unavailable",
                    contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                ),
            ),
        )
    }
}
