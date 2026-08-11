package com.supernutrition.inference.localios

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceRoute
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

class LocalIosInferenceEngineTest {
    private val attempt = AttemptId("ios-attempt")
    private val photo = PhotoRef.issue(attempt, "photo-1")
    private val request = PhotoInferenceRequest(
        photoRef = photo,
        attemptContext = InferenceAttemptContext(
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
        modelVersion = "ios-model-1",
        integrityIdentity = "b".repeat(64),
        compatibilityTier = "tier-test",
        calibrationVersion = "calibration-1",
        ownership = LocalModelOwnership.APP_MANAGED,
        rollbackTargetVersion = "ios-model-0",
        verification = LocalModelVerification.VERIFIED,
    )

    @Test
    fun `ready candidate exposes local contract provenance`() = runTest {
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            LocalRuntimeOutcome.Candidates(
                listOf(
                    LocalRuntimeCandidate(
                        candidateId = "food:apple",
                        label = "Apple",
                        rank = 1,
                        confidence = Confidence(0.91, ConfidenceBand.HIGH),
                        unresolvedAssumptions = listOf(
                            InferenceAssumption(AssumptionCode.PORTION_UNKNOWN, "inference.assumption.portion_unknown"),
                        ),
                    ),
                ),
            ),
        )
        val engine = engine(runtime)

        val capability = engine.capability()
        val result = engine.analyze(request)
        val candidate = assertIs<InferenceOutcome.Candidates>(result.outcome).candidates.single()

        assertEquals(CapabilityState.READY, capability.state)
        assertEquals(LOCAL_IOS_ENGINE_ID, capability.engineId)
        assertEquals(InferenceRoute.LOCAL, candidate.provenance.route)
        assertEquals("ios-model-1", candidate.provenance.modelVersion)
        assertEquals(input.bytes.toList(), runtime.seenBytes)
        assertEquals(0, runtime.networkAttempts)
    }

    @Test
    fun `all unavailable capability states fail closed without reading content`() = runTest {
        val cases = listOf(
            CapabilityState.UNAVAILABLE to CapabilityReason.DEVICE_UNSUPPORTED,
            CapabilityState.MODEL_REQUIRED to CapabilityReason.MODEL_MISSING,
            CapabilityState.DOWNLOADING to CapabilityReason.MODEL_DOWNLOADING,
            CapabilityState.TEMPORARILY_UNAVAILABLE to CapabilityReason.RESOURCE_BUDGET,
            CapabilityState.UNSUPPORTED to CapabilityReason.DEVICE_UNSUPPORTED,
        )

        cases.forEach { (state, reason) ->
            val runtime = RecordingRuntime(LocalRuntimeCapability.Unavailable(state, reason))
            val store = RecordingPhotoStore(input)
            val engine = LocalIosInferenceEngine(store, leaseStore(), runtime) { "a".repeat(64) }

            assertEquals(state, engine.capability().state)
            val failure = assertIs<InferenceOutcome.Failure>(engine.analyze(request).outcome)
            assertEquals(InferenceFailureCode.MODEL_UNAVAILABLE, failure.code)
            assertEquals(0, store.reads)
            assertEquals(0, runtime.invocations)
        }
    }

    @Test
    fun `abstention and failure preserve iOS provenance`() = runTest {
        val abstentionRuntime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            LocalRuntimeOutcome.Abstention(AbstentionReason.IMAGE_QUALITY, "inference.abstention.image_quality"),
        )
        val abstention = assertIs<InferenceOutcome.Abstention>(engine(abstentionRuntime).analyze(request).outcome)
        assertEquals(AbstentionReason.IMAGE_QUALITY, abstention.reason)
        assertEquals(model.toInferenceProvenance(LOCAL_IOS_ENGINE_ID), abstention.provenance)

        val failureRuntime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            LocalRuntimeOutcome.Failure(InferenceFailureCode.RESOURCE_LIMIT, false, "inference.failure.resource_limit"),
        )
        val failure = assertIs<InferenceOutcome.Failure>(engine(failureRuntime).analyze(request).outcome)
        assertEquals(InferenceFailureCode.RESOURCE_LIMIT, failure.code)
        assertFalse(failure.retryable)
        assertEquals(model.toInferenceProvenance(LOCAL_IOS_ENGINE_ID), failure.provenance)
    }

    @Test
    fun `missing foreign and mismatched digest input never reaches runtime`() = runTest {
        val runtime = RecordingRuntime(LocalRuntimeCapability.Ready(model))
        listOf(
            null,
            input.copy(ref = PhotoRef.issue(attempt, "other-photo")),
        ).forEach { value ->
            assertEquals(
                InferenceFailureCode.INVALID_INPUT,
                assertIs<InferenceOutcome.Failure>(
                    LocalAndroidLikeEngine(value, runtime).analyze(request).outcome,
                ).code,
            )
        }
        assertEquals(
            InferenceFailureCode.INVALID_INPUT,
            assertIs<InferenceOutcome.Failure>(
                LocalIosInferenceEngine(RecordingPhotoStore(input), leaseStore(), runtime) { "c".repeat(64) }
                    .analyze(request).outcome,
            ).code,
        )
        assertEquals(
            InferenceFailureCode.INVALID_INPUT,
            assertIs<InferenceOutcome.Failure>(
                LocalIosInferenceEngine(
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
    fun `revoked lease is rejected before the session store is read`() = runTest {
        val store = RecordingPhotoStore(input)
        val runtime = RecordingRuntime(LocalRuntimeCapability.Ready(model))
        val revoked = LocalIosInferenceEngine(
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
    fun `external cancellation invalidates a blocking runtime generation`() = runTest {
        val runtime = BlockingRuntime(model)
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
            "Apple",
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
    fun `cancellation releases runtime and late result is suppressed`() = runTest {
        val cancellationRuntime = RecordingRuntime(
            LocalRuntimeCapability.Ready(model),
            cancellation = CancellationException("test cancellation"),
        )
        assertFailsWith<CancellationException> { engine(cancellationRuntime).analyze(request) }
        assertEquals(1, cancellationRuntime.releaseCount)

        val runtime = BlockingRuntime(model)
        val engine = engine(runtime)
        coroutineScope {
            val first = async { engine.analyze(request) }
            runtime.firstInvocation.await()
            engine.analyze(request)
            runtime.releaseFirst.complete(Unit)
            assertFailsWith<CancellationException> { first.await() }
        }
        assertEquals(2, runtime.releaseCount)
    }

    @Test
    fun `model provenance rejects malformed metadata`() {
        listOf(
            { validModel(integrityIdentity = "not-a-digest") },
            { validModel(modelVersion = "") },
            { validModel(compatibilityTier = "") },
            { validModel(calibrationVersion = "") },
            { validModel(integrityIdentity = "") },
        ).forEach { factory -> assertFailsWith<IllegalArgumentException> { factory() } }
    }

    @Test
    fun `model provenance enforces ownership and rollback`() {
        assertFailsWith<IllegalArgumentException> {
            validModel(
                modelVersion = "platform",
                integrityIdentity = "aicore/runtime-1",
                ownership = LocalModelOwnership.PLATFORM_MANAGED,
                rollbackTargetVersion = "previous",
            )
        }
        assertEquals(
            LocalModelOwnership.PLATFORM_MANAGED,
            validModel(
                modelVersion = "platform",
                integrityIdentity = "aicore/runtime-1",
                ownership = LocalModelOwnership.PLATFORM_MANAGED,
            ).ownership,
        )
        assertEquals(null, validModel().rollbackTargetVersion)
    }

    @Test
    fun `runtime value objects reject malformed state`() {
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCapability.Unavailable(
                CapabilityState.READY,
                CapabilityReason.MODEL_MISSING,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeOutcome.Abstention(AbstentionReason.NO_FOOD_DETECTED, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCandidate(
                " ",
                "label",
                1,
                Confidence(0.5, ConfidenceBand.MEDIUM),
                emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCandidate(
                "candidate",
                " ",
                1,
                Confidence(0.5, ConfidenceBand.MEDIUM),
                emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCandidate(
                "candidate",
                "label",
                0,
                Confidence(0.5, ConfidenceBand.MEDIUM),
                emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCapability.Unavailable(
                CapabilityState.MODEL_REQUIRED,
                CapabilityReason.MODEL_MISSING,
                " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeCapability.Unavailable(
                CapabilityState.TEMPORARILY_UNAVAILABLE,
                CapabilityReason.NONE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LocalRuntimeOutcome.Failure(InferenceFailureCode.RESOURCE_LIMIT, false, " ")
        }
    }

    private fun engine(runtime: LocalPhotoInferenceRuntime): LocalIosInferenceEngine =
        LocalIosInferenceEngine(RecordingPhotoStore(input), leaseStore(), runtime) { "a".repeat(64) }

    @Suppress("LongParameterList")
    private fun validModel(
        modelVersion: String = "app",
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

        var reads = 0
            private set

        override fun put(input: NormalizedPhotoInput) = Unit

        override fun read(ref: PhotoRef): NormalizedPhotoInput? {
            reads += 1
            return storedValue?.takeIf { !matchesRef || it.ref == ref }
        }

        override fun remove(ref: PhotoRef): Boolean = false
        override fun clearAttempt(attemptId: AttemptId): Int = 0
    }

    private class RecordingRuntime(
        private val currentCapability: LocalRuntimeCapability,
        private val outcome: LocalRuntimeOutcome = LocalRuntimeOutcome.Failure(
            InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            true,
            "inference.failure.temporary_unavailable",
        ),
        private val cancellation: CancellationException? = null,
        private val thrown: Exception? = null,
    ) : LocalPhotoInferenceRuntime {
        var invocations = 0
            private set
        var networkAttempts = 0
            private set
        var seenBytes: List<Byte> = emptyList()
            private set
        var releaseCount = 0
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

    private class BlockingRuntime(private val model: LocalModelProvenance) : LocalPhotoInferenceRuntime {
        val firstInvocation = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var releaseCount = 0
            private set
        var cancelCount = 0
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
                InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                true,
                "inference.failure.temporary_unavailable",
            )
        }

        override fun release(token: LocalInferenceAttemptToken) {
            releaseCount += 1
        }

        override fun cancel(token: LocalInferenceAttemptToken) {
            cancelCount += 1
        }
    }

    private inner class LocalAndroidLikeEngine(value: NormalizedPhotoInput?, runtime: LocalPhotoInferenceRuntime) {
        private val delegate = LocalIosInferenceEngine(
            RecordingPhotoStore(value),
            leaseStore(),
            runtime,
        ) { "a".repeat(64) }

        suspend fun analyze(request: PhotoInferenceRequest) = delegate.analyze(request)
    }
}
