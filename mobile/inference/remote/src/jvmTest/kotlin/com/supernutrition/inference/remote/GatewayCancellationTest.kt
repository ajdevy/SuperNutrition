package com.supernutrition.inference.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P1-10 — a cancelled transfer must not deliver a result.
 *
 * withContext(Dispatchers.IO) does not interrupt blocking I/O: HttpURLConnection
 * sits inside write() or read() and returns only when the network does, so
 * cancelling mid-upload left the photo being sent in full and disconnect() ran
 * afterwards, when there was nothing left to stop.
 *
 * WHAT THIS COVERS, precisely: that a transport still running when the caller
 * cancels produces no result for the engine, and that the engine does not
 * swallow the cancellation. The transport's own interrupt — disconnecting the
 * socket so a blocked write throws — is exercised by ConnectionHolder above it
 * but is NOT proved end-to-end here; a live-socket test for that was attempted
 * and removed because it hung rather than failing honestly. Recorded as a gap
 * rather than claimed.
 */
class GatewayCancellationTest {

    /** Blocks until released, so a cancellation can arrive mid-flight. */
    private class StallingTransport : GatewayTransport {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delivered = AtomicBoolean(false)

        override suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse {
            started.complete(Unit)
            release.await()
            delivered.set(true)
            return GatewayHttpResponse(200, "{}")
        }
    }

    @Test
    fun `cancelling mid-transfer delivers no result`() = runBlocking {
        val transport = StallingTransport()

        val inFlight = async { transport.postInferencePhoto(sampleCall()) }
        withTimeout(10_000) { transport.started.await() }

        inFlight.cancel()
        val outcome = runCatching { inFlight.await() }

        assertTrue(outcome.isFailure, "a cancelled transfer returned a result")
        assertTrue(
            outcome.exceptionOrNull() is CancellationException,
            "expected cancellation, got ${outcome.exceptionOrNull()}",
        )
        assertFalse(transport.delivered.get(), "a result was produced after the caller cancelled")
    }

    @Test
    fun `the engine rethrows cancellation instead of turning it into a failure`() = runBlocking {
        // AD-6: cancellation is never swallowed. If the engine mapped it to a
        // transport fault, the attempt would reach a terminal FAILURE outcome
        // and the user would be told the network broke when in fact they
        // pressed cancel.
        val transport = StallingTransport()
        val engine = engineWith(transport)

        val inFlight = async { engine.analyze(sampleRequest()) }
        withTimeout(10_000) { transport.started.await() }

        inFlight.cancel()
        val outcome = runCatching { inFlight.await() }

        assertTrue(
            outcome.exceptionOrNull() is CancellationException,
            "cancellation was converted into ${outcome.exceptionOrNull()}",
        )
    }

    private fun sampleCall() = GatewayCall(
        contextJson = """{"contractVersion":"mobile-api/v1"}""",
        payload = byteArrayOf(1, 2, 3),
        contentType = "image/jpeg",
        requestId = "req_0000000000000001",
        contractVersion = "mobile-api/v1",
        authorization = "Bearer test.installation-0001",
    )

    private fun sampleRequest() = com.supernutrition.domain.inference.PhotoInferenceRequest(
        photoRef = com.supernutrition.domain.inference.PhotoRef.issue(
            com.supernutrition.domain.inference.AttemptId("att_0000000000000001"),
            "photo0000000001",
        ),
        attemptContext = com.supernutrition.domain.inference.InferenceAttemptContext(
            attemptId = com.supernutrition.domain.inference.AttemptId("att_0000000000000001"),
            requestId = "req_0000000000000001",
            configurationVersion = "cfg/1",
            configurationDigest = "b".repeat(64),
        ),
    )

    private fun engineWith(transport: GatewayTransport) = RemoteInferenceEngine(
        transport = transport,
        environment = GatewayEnvironment(
            baseUrl = "http://127.0.0.1:3000",
            claimedMarketJurisdiction = "ZZ",
            catalogCountry = "ZZ",
            locale = "en-GB",
            measurementSystem = "metric",
            configurationVersion = "cfg/1",
            authorization = "Bearer test.installation-0001",
        ),
        photoBytes = { byteArrayOf(1, 2, 3) },
        digest = { "sha256:" + "a".repeat(64) },
        consent = {
            RemoteConsentDecision(
                granted = true,
                attemptId = it,
                payloadDigest = "sha256:" + "a".repeat(64),
                modality = "photo",
                consentCopyVersion = "photo-consent/fixture-1",
                processingRegion = "fixture-region",
                marketJurisdiction = "ZZ",
                retentionVersion = "retention/2",
                recordedAt = "2026-08-03T10:00:00Z",
                expiresAt = "2026-08-03T10:15:00Z",
            )
        },
        idFactory = { "0000000000000001" },
        nowEpochMillis = { 1_785_751_500_000L },
    )
}
