package com.supernutrition.inference.localandroid

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.inference.localshared.LocalInferenceAttemptToken
import com.supernutrition.inference.localshared.LocalModelOwnership
import com.supernutrition.inference.localshared.LocalModelProvenance
import com.supernutrition.inference.localshared.LocalModelVerification
import com.supernutrition.inference.localshared.LocalPhotoInferenceRuntime
import com.supernutrition.inference.localshared.LocalRuntimeCandidate
import com.supernutrition.inference.localshared.LocalRuntimeCapability
import com.supernutrition.inference.localshared.LocalRuntimeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalAndroidInferenceEngineTest {
    private val attempt = AttemptId("android-attempt")
    private val photo = PhotoRef.issue(attempt, "photo-1")
    private val request = PhotoInferenceRequest(
        photoRef = photo,
        attemptContext = com.supernutrition.domain.inference.InferenceAttemptContext(
            attemptId = attempt,
            requestId = "request-1",
            configurationVersion = "config-1",
            configurationDigest = "a".repeat(64),
        ),
    )
    private val input = NormalizedPhotoInput(
        ref = photo,
        bytes = byteArrayOf(1, 2, 3),
        width = 10,
        height = 10,
        orientation = ImageOrientation.UP,
        colorSpace = "sRGB",
        metadataKeys = emptySet(),
        contentDigest = "a".repeat(64),
    )
    private val model = LocalModelProvenance(
        modelVersion = "android-model-1",
        integrityIdentity = "b".repeat(64),
        compatibilityTier = "tier-test",
        calibrationVersion = "calibration-1",
        ownership = LocalModelOwnership.APP_MANAGED,
        rollbackTargetVersion = "android-model-0",
        verification = LocalModelVerification.VERIFIED,
    )

    @Test
    fun `ready capability and candidate result expose local provenance`() = runTest {
        val runtime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Ready(model),
            outcome = LocalRuntimeOutcome.Candidates(
                listOf(
                    LocalRuntimeCandidate(
                        candidateId = "food:banana",
                        label = "Banana",
                        rank = 1,
                        confidence = Confidence(0.93, ConfidenceBand.HIGH),
                        unresolvedAssumptions = listOf(
                            InferenceAssumption(
                                code = com.supernutrition.domain.inference.AssumptionCode.PORTION_UNKNOWN,
                                userMessageKey = "inference.assumption.portion_unknown",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val engine = engine(runtime)

        val capability = engine.capability()
        val result = engine.analyze(request)

        assertEquals(CapabilityState.READY, capability.state)
        assertEquals("android-model-1", capability.modelVersion)
        assertEquals(PHOTO_INFERENCE_CONTRACT_V1, engine.contractVersion)
        val candidates = assertIs<InferenceOutcome.Candidates>(result.outcome).candidates
        assertEquals(InferenceRoute.LOCAL, candidates.single().provenance.route)
        assertEquals("android-model-1", candidates.single().provenance.modelVersion)
        assertEquals(0, runtime.networkAttempts)
        assertEquals(input.bytes.toList(), runtime.seenBytes)
    }

    @Test
    fun `unavailable capability does not read content or attempt a network`() = runTest {
        val runtime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Unavailable(
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.MODEL_MISSING,
            ),
        )
        val store = RecordingPhotoStore(input)
        val engine = LocalAndroidInferenceEngine(store, leaseStore(), runtime) { "a".repeat(64) }

        val capability = engine.capability()

        assertEquals(CapabilityState.MODEL_REQUIRED, capability.state)
        assertEquals(0, store.reads)
        assertEquals(0, runtime.networkAttempts)
    }

    @Test
    fun `capability maps every unavailable state and retry policy`() = runTest {
        val cases = listOf(
            CapabilityState.UNAVAILABLE to CapabilityReason.DEVICE_UNSUPPORTED,
            CapabilityState.MODEL_REQUIRED to CapabilityReason.MODEL_MISSING,
            CapabilityState.DOWNLOADING to CapabilityReason.MODEL_DOWNLOADING,
            CapabilityState.TEMPORARILY_UNAVAILABLE to CapabilityReason.TEMPORARY_UNAVAILABLE,
            CapabilityState.TEMPORARILY_UNAVAILABLE to CapabilityReason.RESOURCE_BUDGET,
            CapabilityState.UNSUPPORTED to CapabilityReason.DEVICE_UNSUPPORTED,
        )

        cases.forEach { (state, reason) ->
            val runtime = RecordingRuntime(
                currentCapability = LocalRuntimeCapability.Unavailable(state, reason),
            )
            val capability = engine(runtime).capability()

            assertEquals(state, capability.state)
            assertEquals(reason, capability.reason)
            assertEquals(LOCAL_ANDROID_ENGINE_ID, capability.engineId)
        }
    }

    @Test
    fun `unavailable analysis is fail closed with local provenance`() = runTest {
        val runtime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Unavailable(
                state = CapabilityState.TEMPORARILY_UNAVAILABLE,
                reason = CapabilityReason.TEMPORARY_UNAVAILABLE,
                model = model,
            ),
        )
        val store = RecordingPhotoStore(input)
        val result = LocalAndroidInferenceEngine(store, leaseStore(), runtime) { "a".repeat(64) }.analyze(request)

        val failure = assertIs<InferenceOutcome.Failure>(result.outcome)
        assertEquals(InferenceFailureCode.MODEL_UNAVAILABLE, failure.code)
        assertTrue(failure.retryable)
        assertEquals(model.toInferenceProvenance(LOCAL_ANDROID_ENGINE_ID), failure.provenance)
        assertEquals(0, store.reads)
        assertEquals(0, runtime.invocations)

        val noModelRuntime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Unavailable(
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.MODEL_MISSING,
            ),
        )
        val noModelFailure = assertIs<InferenceOutcome.Failure>(
            LocalAndroidInferenceEngine(RecordingPhotoStore(input), leaseStore(), noModelRuntime) { "a".repeat(64) }
                .analyze(request)
                .outcome,
        )
        assertEquals("model-unavailable", noModelFailure.provenance.modelVersion)
        assertFalse(noModelFailure.retryable)
    }

    @Test
    fun `runtime abstention and failure preserve provenance`() = runTest {
        val abstentionRuntime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Ready(model),
            outcome = LocalRuntimeOutcome.Abstention(
                reason = AbstentionReason.IMAGE_QUALITY,
                userMessageKey = "inference.abstention.image_quality",
            ),
        )
        val abstention = engine(abstentionRuntime).analyze(request)
        val abstentionOutcome = assertIs<InferenceOutcome.Abstention>(abstention.outcome)
        assertEquals(AbstentionReason.IMAGE_QUALITY, abstentionOutcome.reason)
        assertEquals(model.toInferenceProvenance(LOCAL_ANDROID_ENGINE_ID), abstentionOutcome.provenance)

        val failureRuntime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Ready(model),
            outcome = LocalRuntimeOutcome.Failure(
                code = InferenceFailureCode.RESOURCE_LIMIT,
                retryable = false,
                userMessageKey = "inference.failure.resource_limit",
            ),
        )
        val failure = assertIs<InferenceOutcome.Failure>(engine(failureRuntime).analyze(request).outcome)
        assertEquals(InferenceFailureCode.RESOURCE_LIMIT, failure.code)
        assertFalse(failure.retryable)
        assertEquals(model.toInferenceProvenance(LOCAL_ANDROID_ENGINE_ID), failure.provenance)
    }

    @Test
    fun `digest mismatch rejects input before runtime invocation`() = runTest {
        val runtime = RecordingRuntime(currentCapability = LocalRuntimeCapability.Ready(model))
        val engine = LocalAndroidInferenceEngine(RecordingPhotoStore(input), leaseStore(), runtime) { "c".repeat(64) }

        assertEquals(
            InferenceFailureCode.INVALID_INPUT,
            assertIs<InferenceOutcome.Failure>(engine.analyze(request).outcome).code,
        )
        assertEquals(0, runtime.invocations)
    }

    @Test
    fun `revoked lease is rejected before the session store is read`() = runTest {
        val store = RecordingPhotoStore(input)
        val runtime = RecordingRuntime(LocalRuntimeCapability.Ready(model))
        val revoked = LocalAndroidInferenceEngine(
            store,
            leaseStore(PhotoRef.issue(attempt, "revoked")),
            runtime,
        ) { "a".repeat(64) }

        assertEquals(
            InferenceFailureCode.INVALID_INPUT,
            assertIs<InferenceOutcome.Failure>(revoked.analyze(request).outcome).code,
        )
        assertEquals(0, store.reads)
        assertEquals(0, runtime.invocations)
    }

    @Test
    fun `malformed runtime candidates become typed fail-closed output`() = runTest {
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            LocalRuntimeOutcome.Candidates(emptyList()),
        )

        val failure = assertIs<InferenceOutcome.Failure>(engine(runtime).analyze(request).outcome)
        assertEquals(InferenceFailureCode.TEMPORARY_UNAVAILABLE, failure.code)
        assertTrue(failure.retryable)
    }

    @Test
    fun `external cancellation invalidates a blocking runtime generation`() = runTest {
        val runtime = SupersedingRuntime(model)
        val engine = engine(runtime)

        coroutineScope {
            val pending = async { engine.analyze(request) }
            runtime.firstInvocation.await()
            engine.cancel(attempt)
            runtime.releaseFirst.complete(Unit)
            assertFailsWith<CancellationException> { pending.await() }
        }
        assertEquals(2, runtime.cancelCount)
    }

    @Test
    fun `runtime exception is sanitized into a typed failure`() = runTest {
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            thrown = IllegalStateException("raw-photo-payload"),
        )

        val failure = assertIs<InferenceOutcome.Failure>(engine(runtime).analyze(request).outcome)
        assertEquals(InferenceFailureCode.TEMPORARY_UNAVAILABLE, failure.code)
        assertEquals("inference.failure.runtime", failure.userMessageKey)
    }

    @Test
    fun `runtime diagnostics expose none of the forbidden payload surface`() = runTest {
        val forbidden = listOf(
            "raw-photo-bytes",
            "/tmp/photo.jpg",
            "Banana",
            "prompt text",
            "model response",
            "secret-token",
            "Authorization: Bearer",
        )
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            thrown = IllegalStateException(forbidden.joinToString("|")),
        )

        val failure = assertIs<InferenceOutcome.Failure>(engine(runtime).analyze(request).outcome)
        val diagnostic = failure.toString()

        forbidden.forEach { value -> assertFalse(diagnostic.contains(value)) }
        assertEquals("inference.failure.runtime", failure.userMessageKey)
    }

    @Test
    fun `input lease validation rejects missing foreign empty and unnormalized content`() = runTest {
        val runtime = RecordingRuntime(currentCapability = LocalRuntimeCapability.Ready(model))
        val cases = listOf(
            null,
            input.copy(ref = PhotoRef.issue(attempt, "other-photo")),
            input.copy(ref = PhotoRef.issue(AttemptId("other-attempt"), "photo-1")),
        )

        cases.forEach { value ->
            val store = RecordingPhotoStore(value)
            assertEquals(
                InferenceFailureCode.INVALID_INPUT,
                assertIs<InferenceOutcome.Failure>(
                    LocalAndroidInferenceEngine(store, leaseStore(), runtime) { "a".repeat(64) }
                        .analyze(request).outcome,
                ).code,
            )
        }
        assertEquals(
            InferenceFailureCode.INVALID_INPUT,
            assertIs<InferenceOutcome.Failure>(
                LocalAndroidInferenceEngine(
                    RecordingPhotoStore(
                        input.copy(ref = PhotoRef.issue(attempt, "foreign-returned")),
                        matchRef = false,
                    ),
                    leaseStore(),
                    runtime,
                ) { "a".repeat(64) }.analyze(request).outcome,
            ).code,
        )
        assertEquals(0, runtime.invocations)
    }

    @Test
    fun `runtime cancellation is rethrown and releases resources`() = runTest {
        val runtime = RecordingRuntime(
            currentCapability = LocalRuntimeCapability.Ready(model),
            cancellation = CancellationException("test cancellation"),
        )
        val engine = engine(runtime)

        assertFailsWith<CancellationException> { engine.analyze(request) }
        assertEquals(1, runtime.releaseCount)
    }

    @Test
    fun `a later attempt supersedes a late runtime result`() = runTest {
        val runtime = SupersedingRuntime(model)
        val engine = LocalAndroidInferenceEngine(RecordingPhotoStore(input), leaseStore(), runtime) { "a".repeat(64) }

        coroutineScope {
            val first = async { engine.analyze(request) }
            runtime.firstInvocation.await()
            engine.analyze(request)
            runtime.releaseFirst.complete(Unit)

            assertFailsWith<CancellationException> { first.await() }
            assertEquals(2, runtime.releaseCount)
        }
    }

    @Test
    fun `model provenance rejects invalid metadata`() {
        assertFailsWith<IllegalArgumentException> {
            LocalModelProvenance(
                modelVersion = "model",
                integrityIdentity = "not-a-digest",
                compatibilityTier = "tier",
                calibrationVersion = "calibration",
                ownership = LocalModelOwnership.APP_MANAGED,
                verification = LocalModelVerification.VERIFIED,
            )
        }
        listOf(
            { validModel(modelVersion = " ") },
            { validModel(integrityIdentity = " ") },
            { validModel(compatibilityTier = " ") },
            { validModel(calibrationVersion = " ") },
        ).forEach { factory -> assertFailsWith<IllegalArgumentException> { factory() } }
    }

    @Test
    fun `model provenance enforces rollback ownership`() {
        assertEquals(null, validModel(modelVersion = "app-model-without-rollback").rollbackTargetVersion)
        assertEquals(
            LocalModelOwnership.PLATFORM_MANAGED,
            validModel(
                modelVersion = "platform-model",
                integrityIdentity = "aicore/runtime-1",
                ownership = LocalModelOwnership.PLATFORM_MANAGED,
            ).ownership,
        )
        assertFailsWith<IllegalArgumentException> {
            validModel(
                rollbackTargetVersion = " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validModel(
                modelVersion = "platform-model",
                integrityIdentity = "aicore/runtime-1",
                ownership = LocalModelOwnership.PLATFORM_MANAGED,
                rollbackTargetVersion = "previous",
            )
        }
    }

    @Test
    fun `runtime value objects reject invalid messages and candidates`() {
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCapability.Unavailable(
                state = CapabilityState.READY,
                reason = CapabilityReason.MODEL_MISSING,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCapability.Unavailable(
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.NONE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCapability.Unavailable(
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.MODEL_MISSING,
                userMessageKey = " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeOutcome.Abstention(AbstentionReason.IMAGE_QUALITY, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeOutcome.Failure(InferenceFailureCode.INVALID_INPUT, true, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCandidate(
                candidateId = " ",
                label = "label",
                rank = 1,
                confidence = Confidence(0.5, ConfidenceBand.MEDIUM),
                unresolvedAssumptions = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCandidate(
                candidateId = "candidate",
                label = " ",
                rank = 1,
                confidence = Confidence(0.5, ConfidenceBand.MEDIUM),
                unresolvedAssumptions = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCandidate(
                candidateId = "candidate",
                label = "label",
                rank = 0,
                confidence = Confidence(0.5, ConfidenceBand.MEDIUM),
                unresolvedAssumptions = emptyList(),
            )
        }
    }

    private fun engine(runtime: LocalPhotoInferenceRuntime): LocalAndroidInferenceEngine =
        LocalAndroidInferenceEngine(RecordingPhotoStore(input), leaseStore(), runtime) { "a".repeat(64) }

    @Suppress("LongParameterList")
    private fun validModel(
        modelVersion: String = "model",
        integrityIdentity: String = "a".repeat(64),
        compatibilityTier: String = "tier",
        calibrationVersion: String = "calibration",
        ownership: LocalModelOwnership = LocalModelOwnership.APP_MANAGED,
        rollbackTargetVersion: String? = null,
    ) = LocalModelProvenance(
        modelVersion = modelVersion,
        integrityIdentity = integrityIdentity,
        compatibilityTier = compatibilityTier,
        calibrationVersion = calibrationVersion,
        ownership = ownership,
        rollbackTargetVersion = rollbackTargetVersion,
        verification = LocalModelVerification.VERIFIED,
    )

    private fun leaseStore(active: PhotoRef = photo): PhotoLeaseStorePort = object : PhotoLeaseStorePort {
        override fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef = PhotoRef.issue(attemptId, opaqueId)
        override fun isActive(ref: PhotoRef): Boolean = ref == active
        override fun revoke(ref: PhotoRef): Boolean = false
        override fun revokeAttempt(attemptId: AttemptId): Int = 0
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private class RecordingPhotoStore(value: NormalizedPhotoInput?, matchRef: Boolean = true) :
        PhotoSessionStorePort {
        private val storedValue = value
        private val matchesRef = matchRef

        var reads: Int = 0
            private set

        override fun put(input: NormalizedPhotoInput) = Unit

        override fun read(ref: PhotoRef): NormalizedPhotoInput? {
            reads += 1
            return storedValue?.takeIf { !matchesRef || ref == it.ref }
        }

        override fun remove(ref: PhotoRef): Boolean = false

        override fun clearAttempt(attemptId: AttemptId): Int = 0
    }

    private class RecordingRuntime(
        private var currentCapability: LocalRuntimeCapability,
        private val outcome: LocalRuntimeOutcome = LocalRuntimeOutcome.Failure(
            code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            retryable = true,
            userMessageKey = "inference.failure.temporary_unavailable",
        ),
        private val cancellation: CancellationException? = null,
        private val thrown: Exception? = null,
    ) : LocalPhotoInferenceRuntime {
        var invocations: Int = 0
            private set
        var networkAttempts: Int = 0
            private set
        var seenBytes: List<Byte> = emptyList()
            private set
        var releaseCount: Int = 0
            private set

        override suspend fun capability(): LocalRuntimeCapability = currentCapability

        override suspend fun analyze(
            input: NormalizedPhotoInput,
            token: LocalInferenceAttemptToken,
        ): LocalRuntimeOutcome {
            invocations += 1
            seenBytes = input.bytes.toList()
            cancellation?.let { throw it }
            thrown?.let { throw it }
            return outcome
        }

        override fun release(token: LocalInferenceAttemptToken) {
            releaseCount += 1
        }

        override fun cancel(token: LocalInferenceAttemptToken) = Unit
    }

    private class SupersedingRuntime(private val model: LocalModelProvenance) : LocalPhotoInferenceRuntime {
        val firstInvocation = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var releaseCount: Int = 0
            private set
        var cancelCount: Int = 0
            private set
        private var invocations = 0

        override suspend fun capability(): LocalRuntimeCapability = LocalRuntimeCapability.Ready(model)

        override suspend fun analyze(
            input: NormalizedPhotoInput,
            token: LocalInferenceAttemptToken,
        ): LocalRuntimeOutcome {
            invocations += 1
            if (invocations == 1) {
                firstInvocation.complete(Unit)
                releaseFirst.await()
            }
            return LocalRuntimeOutcome.Failure(
                code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                retryable = true,
                userMessageKey = "inference.failure.temporary_unavailable",
            )
        }

        override fun release(token: LocalInferenceAttemptToken) {
            releaseCount += 1
        }

        override fun cancel(token: LocalInferenceAttemptToken) {
            cancelCount += 1
        }
    }
}
