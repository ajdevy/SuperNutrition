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
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Physical Android adapter evidence using a deterministic injected runtime. */
class LocalAndroidInferenceDeviceTest {
    @Test
    fun readyAdapterReadsLeasedBytesLocallyWithoutNetworkAttempts() = runBlocking {
        val fixture = Fixture()
        val runtime = RecordingRuntime(
            AndroidRuntimeCapability.Ready(fixture.model),
            AndroidRuntimeOutcome.Failure(
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
            AndroidRuntimeCapability.Unavailable(
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
        val model = AndroidModelProvenance(
            modelVersion = "android-device-fixture-model",
            integrityIdentity = "b".repeat(64),
            compatibilityTier = "physical-device-fixture",
            calibrationVersion = "android-device-fixture-calibration",
            ownership = AndroidModelOwnership.APP_MANAGED,
            rollbackTargetVersion = "android-device-fixture-model-previous",
            verification = AndroidModelVerification.VERIFIED,
        )

        fun engine(runtime: AndroidLocalInferenceRuntime): LocalAndroidInferenceEngine = LocalAndroidInferenceEngine(
            photos = RecordingPhotoStore(input),
            leases = leases,
            runtime = runtime,
            digest = ::sha256,
        )
    }

    private class RecordingRuntime(
        private val currentCapability: AndroidRuntimeCapability,
        private val outcome: AndroidRuntimeOutcome = AndroidRuntimeOutcome.Failure(
            code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            retryable = true,
            userMessageKey = "inference.failure.temporary_unavailable",
        ),
    ) : AndroidLocalInferenceRuntime {
        var invocations = 0
        var networkAttempts = 0
        var seenBytes: ByteArray = byteArrayOf()

        override suspend fun capability(): AndroidRuntimeCapability = currentCapability

        override suspend fun analyze(
            input: NormalizedPhotoInput,
            token: AndroidInferenceAttemptToken,
        ): AndroidRuntimeOutcome {
            invocations += 1
            seenBytes = input.bytes
            return outcome
        }

        override fun release(token: AndroidInferenceAttemptToken) = Unit

        override fun cancel(token: AndroidInferenceAttemptToken) = Unit
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
