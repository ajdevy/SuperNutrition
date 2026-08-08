package com.supernutrition.inference.localandroid

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentModelActivation
import com.supernutrition.domain.inference.ComponentModelManifest
import com.supernutrition.domain.inference.ComponentModelOwnership
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceInputModality
import com.supernutrition.domain.inference.InferenceV2Input
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.PhotoRef
import com.supernutrition.domain.inference.V2InferenceFailureCode
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoFormat
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalAndroidComponentInferenceTest {
    private val attempt = AttemptId("android-component-attempt")
    private val photo = PhotoRef.issue(attempt, "photo-component-1")
    private val input = NormalizedPhotoInput(
        ref = photo,
        bytes = byteArrayOf(1, 2, 3),
        width = 10,
        height = 10,
        format = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet()),
        contentDigest = "a".repeat(64),
    )
    private val request = InferenceV2Request(
        input = InferenceV2Input.Photo(photo),
        attemptContext = InferenceAttemptContext(
            attemptId = attempt,
            requestId = "request-component-1",
            configurationVersion = "config-1",
            configurationDigest = "b".repeat(64),
        ),
    )

    @Test
    fun `ready runtime maps component evidence and provenance`() = runTest {
        val runtime = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(manifest()),
            AndroidComponentRuntimeOutcome.Components(listOf(component())),
        )
        val engine = engine(runtime)

        val capability = engine.capability()
        val result = engine.analyze(request)

        assertEquals(CapabilityState.READY, capability.state)
        assertEquals("component-model-1", capability.modelVersion)
        val components = assertIs<InferenceV2Outcome.Components>(result.outcome).components
        assertEquals("component:rice", components.single().componentId)
        assertEquals("component-model-1", components.single().provenance.modelVersion)
        assertEquals(InferenceInputModality.PHOTO, result.inputModality)
        assertEquals(input.bytes.toList(), runtime.seenBytes)
    }

    @Test
    fun `unavailable capability and text input fail closed before photo read`() = runTest {
        val unavailable = RecordingRuntime(
            AndroidComponentRuntimeCapability.Unavailable(
                CapabilityState.MODEL_REQUIRED,
                CapabilityReason.MODEL_MISSING,
            ),
        )
        val store = RecordingPhotoStore(input)
        val failed = LocalAndroidComponentInferenceEngine(store, leaseStore(), unavailable) { "a".repeat(64) }
            .analyze(request)
        val failure = assertIs<InferenceV2Outcome.Failure>(failed.outcome)
        assertEquals(V2InferenceFailureCode.MODEL_UNAVAILABLE, failure.code)
        assertEquals(0, store.reads)

        val ready = RecordingRuntime(AndroidComponentRuntimeCapability.Ready(manifest()))
        val textRequest = InferenceV2Request(
            input = InferenceV2Input.Text("rice", "en"),
            attemptContext = request.attemptContext,
        )
        val textResult = engine(ready).analyze(textRequest)
        assertIs<InferenceV2Outcome.Abstention>(textResult.outcome)
        assertEquals(InferenceInputModality.TEXT, textResult.inputModality)
        assertEquals(0, ready.invocations)
    }

    @Test
    fun `non-production ready manifest fails closed before photo read`() = runTest {
        val runtime = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(
                manifest(ComponentModelActivation.RESEARCH_ONLY),
            ),
            AndroidComponentRuntimeOutcome.Components(listOf(component())),
        )
        val store = RecordingPhotoStore(input)
        val engine = LocalAndroidComponentInferenceEngine(store, leaseStore(), runtime) { "a".repeat(64) }

        assertEquals(CapabilityState.MODEL_REQUIRED, engine.capability().state)
        val failure = assertIs<InferenceV2Outcome.Failure>(engine.analyze(request).outcome)
        assertEquals(V2InferenceFailureCode.MODEL_UNAVAILABLE, failure.code)
        assertEquals(0, store.reads)
        assertEquals(0, runtime.invocations)
    }

    @Test
    fun `capability mapping preserves staged manifest and retryable state`() = runTest {
        val runtime = RecordingRuntime(
            AndroidComponentRuntimeCapability.Unavailable(
                state = CapabilityState.TEMPORARILY_UNAVAILABLE,
                reason = CapabilityReason.TEMPORARY_UNAVAILABLE,
                userMessageKey = "inference.capability.retry",
                manifest = manifest(),
            ),
        )
        val capability = engine(runtime).capability()

        assertEquals(CapabilityState.TEMPORARILY_UNAVAILABLE, capability.state)
        assertEquals("component-model-1", capability.modelVersion)
        assertEquals("ingredient-taxonomy-1", capability.taxonomyVersion)
        assertEquals("mass-calibration-1", capability.calibrationVersion)
        assertTrue(
            assertIs<InferenceV2Outcome.Failure>(engine(runtime).analyze(request).outcome).retryable,
        )
        val downloading = RecordingRuntime(
            AndroidComponentRuntimeCapability.Unavailable(
                CapabilityState.DOWNLOADING,
                CapabilityReason.MODEL_DOWNLOADING,
            ),
        )
        assertTrue(assertIs<InferenceV2Outcome.Failure>(engine(downloading).analyze(request).outcome).retryable)

        assertFailsWith<IllegalArgumentException> {
            AndroidComponentRuntimeCapability.Unavailable(CapabilityState.READY, CapabilityReason.MODEL_MISSING)
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidComponentRuntimeCapability.Unavailable(CapabilityState.MODEL_REQUIRED, CapabilityReason.NONE)
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidComponentRuntimeCapability.Unavailable(
                CapabilityState.MODEL_REQUIRED,
                CapabilityReason.MODEL_MISSING,
                userMessageKey = "",
            )
        }
        val missingManifest = RecordingRuntime(
            AndroidComponentRuntimeCapability.Unavailable(
                CapabilityState.MODEL_REQUIRED,
                CapabilityReason.MODEL_MISSING,
            ),
        )
        val unavailableCapability = engine(missingManifest).capability()
        assertEquals("model-unavailable", unavailableCapability.modelVersion)
    }

    @Test
    fun `runtime outcome kinds map and invalid messages fail closed`() = runTest {
        val abstention = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(manifest()),
            AndroidComponentRuntimeOutcome.Abstention(
                com.supernutrition.domain.inference.V2AbstentionReason.IMAGE_QUALITY,
                "inference.abstention.image_quality",
            ),
        )
        assertIs<InferenceV2Outcome.Abstention>(engine(abstention).analyze(request).outcome)

        val failure = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(manifest()),
            AndroidComponentRuntimeOutcome.Failure(
                V2InferenceFailureCode.RESOURCE_LIMIT,
                retryable = true,
                userMessageKey = "inference.failure.resource_limit",
            ),
        )
        assertEquals(
            V2InferenceFailureCode.RESOURCE_LIMIT,
            assertIs<InferenceV2Outcome.Failure>(engine(failure).analyze(request).outcome).code,
        )

        val validComponent = component()
        assertFailsWith<IllegalArgumentException> {
            AndroidComponentRuntimeComponent(
                componentId = "",
                candidate = validComponent.candidate,
                confidence = validComponent.confidence,
                visibility = validComponent.visibility,
                ambiguity = validComponent.ambiguity,
                region = validComponent.region,
                mass = validComponent.mass,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidComponentRuntimeOutcome.Abstention(
                com.supernutrition.domain.inference.V2AbstentionReason.IMAGE_QUALITY,
                "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidComponentRuntimeOutcome.Failure(
                V2InferenceFailureCode.RESOURCE_LIMIT,
                retryable = true,
                userMessageKey = "",
            )
        }
    }

    @Test
    fun `digest mismatch and duplicate component output fail closed`() = runTest {
        val digestRuntime = RecordingRuntime(AndroidComponentRuntimeCapability.Ready(manifest()))
        assertFailsWith<AndroidComponentInferenceInputException> {
            LocalAndroidComponentInferenceEngine(
                RecordingPhotoStore(input),
                leaseStore(),
                digestRuntime,
            ) { "c".repeat(64) }
                .analyze(request)
        }
        assertEquals(0, digestRuntime.invocations)

        val duplicateRuntime = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(manifest()),
            AndroidComponentRuntimeOutcome.Components(listOf(component(), component())),
        )
        val failure = assertIs<InferenceV2Outcome.Failure>(engine(duplicateRuntime).analyze(request).outcome)
        assertEquals(V2InferenceFailureCode.TEMPORARY_UNAVAILABLE, failure.code)
        assertEquals(1, duplicateRuntime.releaseCount)
    }

    @Test
    fun `photo lease validation rejects inactive missing and mismatched content`() = runTest {
        val runtime = RecordingRuntime(AndroidComponentRuntimeCapability.Ready(manifest()))
        assertFailsWith<AndroidComponentInferenceInputException> {
            LocalAndroidComponentInferenceEngine(RecordingPhotoStore(input), leaseStore(active = false), runtime) {
                "a".repeat(64)
            }.analyze(request)
        }
        assertFailsWith<AndroidComponentInferenceInputException> {
            LocalAndroidComponentInferenceEngine(
                RecordingPhotoStore(null),
                leaseStore(),
                runtime,
            ) { "a".repeat(64) }.analyze(request)
        }
        val otherPhoto = PhotoRef.issue(attempt, "other-photo")
        val wrongInput = input.copy(ref = otherPhoto)
        assertFailsWith<AndroidComponentInferenceInputException> {
            LocalAndroidComponentInferenceEngine(
                RecordingPhotoStore(wrongInput),
                leaseStore(),
                runtime,
            ) { "a".repeat(64) }.analyze(request)
        }
        assertEquals(0, runtime.invocations)
    }

    @Test
    fun `cancellation is rethrown and native resources are released`() = runTest {
        val runtime = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(manifest()),
            throwsCancellation = true,
        )
        assertFailsWith<CancellationException> { engine(runtime).analyze(request) }
        assertEquals(1, runtime.cancelCount)
        assertEquals(1, runtime.releaseCount)
    }

    @Test
    fun `explicit cancel suppresses stale result and cancels previous generation`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val runtime = RecordingRuntime(
            AndroidComponentRuntimeCapability.Ready(manifest()),
            gate = finish,
            startedSignals = listOf(firstStarted, secondStarted),
        )
        val engine = engine(runtime)
        val first = launch { engine.analyze(request) }
        firstStarted.await()
        val second = launch { engine.analyze(request) }
        secondStarted.await()
        engine.cancel(attempt)
        finish.complete(Unit)

        first.join()
        assertTrue(first.isCancelled)
        second.join()
        assertTrue(second.isCancelled)
        assertTrue(runtime.cancelCount >= 2)
        assertEquals(2, runtime.releaseCount)
        engine.cancel(AttemptId("missing-attempt"))
    }

    private fun engine(runtime: AndroidLocalComponentInferenceRuntime) =
        LocalAndroidComponentInferenceEngine(RecordingPhotoStore(input), leaseStore(), runtime) { "a".repeat(64) }

    private fun manifest(activation: ComponentModelActivation = ComponentModelActivation.PRODUCTION) =
        ComponentModelManifest(
            manifestVersion = "component-model/v1",
            modelVersion = "component-model-1",
            artifactDigest = "c".repeat(64),
            taxonomyVersion = "ingredient-taxonomy-1",
            taxonomyDigest = "d".repeat(64),
            preprocessingVersion = "preprocess-1",
            postprocessingVersion = "postprocess-1",
            maskContractVersion = "mask-region-v1",
            calibrationVersion = "mass-calibration-1",
            runtime = "onnx-runtime",
            executionProvider = "cpu",
            compatibilityTier = "android-test",
            ownership = ComponentModelOwnership.APP_MANAGED,
            activation = activation,
            rollbackTargetVersion = "component-model-0",
            productApprovalRef = if (activation == ComponentModelActivation.PRODUCTION) {
                "approval:product"
            } else {
                null
            },
            engineeringApprovalRef = if (activation == ComponentModelActivation.PRODUCTION) {
                "approval:engineering"
            } else {
                null
            },
            legalApprovalRef = if (activation == ComponentModelActivation.PRODUCTION) "approval:legal" else null,
            corpusRef = if (activation == ComponentModelActivation.PRODUCTION) "corpus:weighed-v1" else null,
            benchmarkEvidenceRef = if (activation == ComponentModelActivation.PRODUCTION) {
                "evidence:g3-v1"
            } else {
                null
            },
            rollbackEvidenceRef = if (activation == ComponentModelActivation.PRODUCTION) {
                "evidence:rollback-v1"
            } else {
                null
            },
        )

    private fun component() = AndroidComponentRuntimeComponent(
        componentId = "component:rice",
        candidate = LocalizedCandidateIdentity("ingredient:rice", "Rice", "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        region = NormalizedVisibleRegion(0.1, 0.1, 0.4, 0.4),
        mass = MassEvidence.Estimated(
            range = com.supernutrition.domain.inference.GramRange(100.0, 140.0),
            calibrationAssumptions = listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
    )

    private fun leaseStore(active: Boolean = true) = object : PhotoLeaseStorePort {
        override fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef = PhotoRef.issue(attemptId, opaqueId)
        override fun isActive(ref: PhotoRef): Boolean = active && ref == photo
        override fun revoke(ref: PhotoRef): Boolean = false
        override fun revokeAttempt(attemptId: AttemptId): Int = 0
    }

    private fun runTest(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        kotlinx.coroutines.runBlocking { block(this) }

    private class RecordingPhotoStore(private val stored: NormalizedPhotoInput?) : PhotoSessionStorePort {
        var reads = 0
            private set

        override fun put(input: NormalizedPhotoInput) = Unit

        override fun read(ref: PhotoRef): NormalizedPhotoInput? {
            reads += 1
            return stored
        }

        override fun remove(ref: PhotoRef): Boolean = false
        override fun clearAttempt(attemptId: AttemptId): Int = 0
    }

    private class RecordingRuntime(
        private val currentCapability: AndroidComponentRuntimeCapability,
        private val currentOutcome: AndroidComponentRuntimeOutcome = AndroidComponentRuntimeOutcome.Abstention(
            com.supernutrition.domain.inference.V2AbstentionReason.NO_FOOD_DETECTED,
            "inference.abstention.no_food_detected",
        ),
        private val throwsCancellation: Boolean = false,
        private val gate: CompletableDeferred<Unit>? = null,
        private val startedSignals: List<CompletableDeferred<Unit>> = emptyList(),
    ) : AndroidLocalComponentInferenceRuntime {
        var invocations = 0
            private set
        var releaseCount = 0
            private set
        var cancelCount = 0
            private set
        var seenBytes: List<Byte> = emptyList()
            private set

        override suspend fun capability(): AndroidComponentRuntimeCapability = currentCapability

        override suspend fun analyze(
            input: NormalizedPhotoInput,
            token: AndroidComponentInferenceAttemptToken,
        ): AndroidComponentRuntimeOutcome {
            invocations += 1
            seenBytes = input.bytes.toList()
            startedSignals.getOrNull(invocations - 1)?.complete(Unit)
            gate?.await()
            if (throwsCancellation) throw CancellationException("test cancellation")
            return currentOutcome
        }

        override fun release(token: AndroidComponentInferenceAttemptToken) {
            releaseCount += 1
        }

        override fun cancel(token: AndroidComponentInferenceAttemptToken) {
            cancelCount += 1
        }
    }
}
