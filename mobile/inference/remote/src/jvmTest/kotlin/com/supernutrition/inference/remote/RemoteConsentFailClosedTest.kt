package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * P1-4 — consent is checked before anything leaves the device.
 *
 * The engine used to check `decision.granted` and nothing else. Every other
 * field of the receipt — which attempt the decision covered, which bytes, when
 * it was taken, whether it had expired, whether a reviewed consent copy and a
 * permitted region actually existed — was copied into the envelope unread and
 * sent with the user's photo behind it.
 *
 * What these assert is deliberately stronger than "the result is a failure": the
 * transport records every call it receives, and each case asserts the count is
 * ZERO. A failure returned after the bytes have left is not fail-closed.
 */
class RemoteConsentFailClosedTest {

    private class RecordingTransport : GatewayTransport {
        val calls = mutableListOf<GatewayCall>()
        override suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse {
            calls += call
            return GatewayHttpResponse(200, """{"contractVersion":"mobile-api/v1"}""")
        }
    }

    private val bytes = byteArrayOf(1, 2, 3, 4)
    private val digestValue = "sha256:" + "a".repeat(64)
    private val attempt = "attempt-1"

    /** 2026-08-03T10:05:00Z, inside the granted decision's window. */
    private val now = 1_785_751_500_000L

    private val granted = RemoteConsentDecision(
        granted = true,
        attemptId = attempt,
        payloadDigest = digestValue,
        modality = "photo",
        consentCopyVersion = "photo-consent/fixture-1",
        processingRegion = "fixture-region",
        marketJurisdiction = "ZZ",
        retentionVersion = "retention/2",
        recordedAt = "2026-08-03T10:00:00Z",
        expiresAt = "2026-08-03T10:15:00Z",
    )

    private fun engineFor(
        transport: RecordingTransport,
        decision: RemoteConsentDecision,
        nowMillis: Long = now,
    ) = RemoteInferenceEngine(
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
        photoBytes = { bytes },
        digest = { digestValue },
        consent = { decision },
        idFactory = { "0000000000000001" },
        nowEpochMillis = { nowMillis },
    )

    private fun request() = PhotoInferenceRequest(
        photoRef = PhotoRef.issue(AttemptId(attempt), "photo0000000001"),
        attemptContext = InferenceAttemptContext(
            attemptId = AttemptId(attempt),
            requestId = "req_0000000000000001",
            configurationVersion = "cfg/1",
            configurationDigest = "b".repeat(64),
        ),
    )

    private fun assertRefusedWithoutTransport(
        decision: RemoteConsentDecision,
        nowMillis: Long = now,
    ) = runBlocking {
        val transport = RecordingTransport()
        val result = engineFor(transport, decision, nowMillis).analyze(request())

        val outcome = result.outcome
        assertIs<InferenceOutcome.Failure>(outcome)
        assertEquals(InferenceFailureCode.INVALID_INPUT, outcome.code)
        assertTrue(
            transport.calls.isEmpty(),
            "the photo was sent anyway: ${transport.calls.size} transport call(s)",
        )
    }

    @Test
    fun `a declined decision sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(granted = false))

    @Test
    fun `a decision taken for another attempt sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(attemptId = "attempt-2"))

    @Test
    fun `a decision covering other bytes sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(payloadDigest = "sha256:" + "b".repeat(64)))

    @Test
    fun `a decision for another modality sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(modality = "text"))

    @Test
    fun `an expired decision sends nothing`() =
        // 10:20:00Z, five minutes past the window's end.
        assertRefusedWithoutTransport(granted, nowMillis = 1_785_752_400_000L)

    @Test
    fun `a decision at the exact instant of expiry sends nothing`() =
        // The boundary is exclusive: "expires at 10:15" means it is not valid AT
        // 10:15, and an off-by-one here is a transfer nobody consented to.
        assertRefusedWithoutTransport(granted, nowMillis = 1_785_752_100_000L)

    @Test
    fun `a decision recorded in the future sends nothing`() =
        // Every relative rule is satisfied — the window is fifteen minutes and
        // has not expired — but it has not been taken yet.
        assertRefusedWithoutTransport(
            granted.copy(recordedAt = "2027-08-03T10:00:00Z", expiresAt = "2027-08-03T10:15:00Z"),
        )

    @Test
    fun `an unreadable timestamp sends nothing`() =
        // Unparseable must fail closed. Treating it as "not expired" would make
        // a malformed receipt the most permissive one there is.
        assertRefusedWithoutTransport(granted.copy(expiresAt = "not-a-timestamp"))

    @Test
    fun `an inverted window sends nothing`() =
        assertRefusedWithoutTransport(
            granted.copy(recordedAt = "2026-08-03T10:15:00Z", expiresAt = "2026-08-03T10:00:00Z"),
        )

    @Test
    fun `a granted decision with no reviewed copy sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(consentCopyVersion = null))

    @Test
    fun `a granted decision with a blank reviewed copy sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(consentCopyVersion = "   "))

    @Test
    fun `a granted decision with no processing region sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(processingRegion = null))

    @Test
    fun `a granted decision with a blank processing region sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(processingRegion = ""))

    @Test
    fun `a granted decision with no market sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(marketJurisdiction = ""))

    @Test
    fun `a decision for a different market than the environment sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(marketJurisdiction = "QQ"))

    @Test
    fun `a granted decision with no retention version sends nothing`() =
        assertRefusedWithoutTransport(granted.copy(retentionVersion = ""))

    @Test
    fun `a complete, current, bound decision does transfer`() = runBlocking {
        // The positive control. A fail-closed path that refuses everything is
        // not fail-closed, it is broken.
        val transport = RecordingTransport()
        engineFor(transport, granted).analyze(request())

        assertEquals(1, transport.calls.size, "a valid decision should have transferred exactly once")
    }
}
