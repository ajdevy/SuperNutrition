package com.supernutrition.inference.localandroid

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.inference.localshared.LocalInferenceAttemptToken
import com.supernutrition.inference.localshared.LocalModelOwnership
import com.supernutrition.inference.localshared.LocalModelProvenance
import com.supernutrition.inference.localshared.LocalModelVerification
import com.supernutrition.inference.localshared.LocalPhotoInferenceRuntime
import com.supernutrition.inference.localshared.LocalRuntimeCapability
import com.supernutrition.inference.localshared.LocalRuntimeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Physical Android adapter evidence using a deterministic injected runtime. */
class LocalAndroidInferenceDeviceTest {
    @Test
    fun readyAdapterReadsLeasedBytesLocallyWithoutNetworkAttempts() = runBlocking {
        val fixture = Fixture()
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Ready(fixture.model),
            LocalRuntimeOutcome.Failure(
                code = InferenceFailureCode.RESOURCE_LIMIT,
                retryable = false,
                userMessageKey = "inference.failure.resource_limit",
            ),
        )
        val result = fixture.engine(runtime).analyze(fixture.request)

        val failure = assertIs<InferenceOutcome.Failure>(result.outcome)
        assertEquals(InferenceRoute.LOCAL, failure.provenance.route)
        assertEquals(1, runtime.invocations)
        assertEquals(0, runtime.networkAttempts)
        assertContentEquals(fixture.bytes, runtime.seenBytes)
    }

    @Test
    fun unavailableAdapterDoesNotReadPhotoContent() = runBlocking {
        val fixture = Fixture()
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Unavailable(
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.MODEL_MISSING,
            ),
        )
        val store = RecordingPhotoStore(fixture.input)
        val result = LocalAndroidInferenceEngine(
            photos = store,
            leases = fixture.leases,
            runtime = runtime,
            digest = ::sha256,
        ).analyze(fixture.request)

        val failure = assertIs<InferenceOutcome.Failure>(result.outcome)
        assertEquals(InferenceFailureCode.MODEL_UNAVAILABLE, failure.code)
        assertEquals(0, store.reads)
        assertEquals(0, runtime.invocations)
        assertEquals(0, runtime.networkAttempts)
    }

    @Test
    fun resourceBudgetCapabilityFailsClosedWithoutReadingPhotoContent() = runBlocking {
        val fixture = Fixture()
        val runtime = RecordingRuntime(
            LocalRuntimeCapability.Unavailable(
                state = CapabilityState.TEMPORARILY_UNAVAILABLE,
                reason = CapabilityReason.RESOURCE_BUDGET,
            ),
        )
        val store = RecordingPhotoStore(fixture.input)
        val result = LocalAndroidInferenceEngine(
            photos = store,
            leases = fixture.leases,
            runtime = runtime,
            digest = ::sha256,
        ).analyze(fixture.request)

        val failure = assertIs<InferenceOutcome.Failure>(result.outcome)
        assertEquals(InferenceFailureCode.MODEL_UNAVAILABLE, failure.code)
        assertEquals(0, store.reads)
        assertEquals(0, runtime.invocations)
        assertEquals(0, runtime.networkAttempts)
    }

    @Test
    fun cancelledAttemptReleasesRuntimeAndRejectsLateResult() = runBlocking {
        val fixture = Fixture()
        val runtime = BlockingRuntime(fixture.model)
        val engine = fixture.engine(runtime)
        val pending = async { engine.analyze(fixture.request) }

        runtime.started.await()
        engine.cancel(fixture.attempt)
        runtime.release.complete(Unit)

        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(2, runtime.cancelCalls)
        assertEquals(1, runtime.releaseCalls)
    }

    private class Fixture {
        val attempt = AttemptId("android-device-attempt")
        val bytes = byteArrayOf(10, 20, 30, 40)
        val photo: PhotoRef = PhotoRef.issue(attempt, "android-device-photo")
        val leases = PhotoLeaseStore().also { it.issue(attempt, photo.opaqueId) }
        val input = NormalizedPhotoInput(
            ref = photo,
            bytes = bytes,
            width = 10,
            height = 10,
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
            contentDigest = sha256(bytes),
        )
        val request = PhotoInferenceRequest(
            photoRef = photo,
            attemptContext = InferenceAttemptContext(
                attemptId = attempt,
                requestId = "android-device-request",
                configurationVersion = "android-device-config",
                configurationDigest = "a".repeat(64),
            ),
        )
        val model = LocalModelProvenance(
            modelVersion = "android-device-fixture-model",
            integrityIdentity = "b".repeat(64),
            compatibilityTier = "physical-device-fixture",
            calibrationVersion = "android-device-fixture-calibration",
            ownership = LocalModelOwnership.APP_MANAGED,
            rollbackTargetVersion = "android-device-fixture-model-previous",
            verification = LocalModelVerification.VERIFIED,
        )

        fun engine(runtime: LocalPhotoInferenceRuntime): LocalAndroidInferenceEngine = LocalAndroidInferenceEngine(
            photos = RecordingPhotoStore(input),
            leases = leases,
            runtime = runtime,
            digest = ::sha256,
        )
    }

    private class RecordingRuntime(
        private val currentCapability: LocalRuntimeCapability,
        private val outcome: LocalRuntimeOutcome = LocalRuntimeOutcome.Failure(
            code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            retryable = true,
            userMessageKey = "inference.failure.temporary_unavailable",
        ),
    ) : LocalPhotoInferenceRuntime {
        var invocations = 0
        var networkAttempts = 0
        var seenBytes: ByteArray = byteArrayOf()

        override suspend fun capability(): LocalRuntimeCapability = currentCapability

        override suspend fun analyze(
            input: NormalizedPhotoInput,
            token: LocalInferenceAttemptToken,
        ): LocalRuntimeOutcome {
            invocations += 1
            seenBytes = input.bytes
            return outcome
        }

        override fun release(token: LocalInferenceAttemptToken) = Unit

        override fun cancel(token: LocalInferenceAttemptToken) = Unit
    }

    private class BlockingRuntime(private val model: LocalModelProvenance) : LocalPhotoInferenceRuntime {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cancelCalls = 0
        var releaseCalls = 0

        override suspend fun capability(): LocalRuntimeCapability = LocalRuntimeCapability.Ready(model)

        override suspend fun analyze(
            input: NormalizedPhotoInput,
            token: LocalInferenceAttemptToken,
        ): LocalRuntimeOutcome {
            started.complete(Unit)
            release.await()
            return LocalRuntimeOutcome.Failure(
                code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                retryable = true,
                userMessageKey = "inference.failure.temporary_unavailable",
            )
        }

        override fun release(token: LocalInferenceAttemptToken) {
            releaseCalls += 1
        }

        override fun cancel(token: LocalInferenceAttemptToken) {
            cancelCalls += 1
        }
    }

    private class RecordingPhotoStore(private val input: NormalizedPhotoInput) : PhotoSessionStorePort {
        var reads = 0

        override fun put(input: NormalizedPhotoInput) = Unit

        override fun read(ref: PhotoRef): NormalizedPhotoInput? {
            reads += 1
            return input.takeIf { it.ref == ref }
        }

        override fun remove(ref: PhotoRef): Boolean = false

        override fun clearAttempt(attemptId: AttemptId): Int = 0
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
