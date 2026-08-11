package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real [HttpGatewayTransport] against a running gateway.
 *
 * This is the only test that proves the client and the backend agree; every
 * other test asserts against the client's own idea of the contract, which is
 * exactly how the `effectId`/`requestId` defect survived. It exercises the same
 * class the APK ships, because `HttpGatewayTransport` lives in the shared
 * `jvmAndroidMain` source set rather than in `androidMain`.
 *
 * Opt-in: set `SUPERNUTRITION_GATEWAY_BASE_URL` (e.g. `http://127.0.0.1:3000`).
 * Without it the test returns rather than fails, so an ordinary `jvmTest` run on
 * a machine with no server stays green and no one is tempted to delete it.
 */
private const val HEX = "0123456789abcdef"

class LiveGatewayIntegrationTest {

    private val baseUrl: String? = System.getenv("SUPERNUTRITION_GATEWAY_BASE_URL")

    // ff d8 ff is the JPEG magic the gateway checks; the stub provider does not
    // decode the image, so a minimal well-formed prefix is a truthful payload.
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)

    // Hex is built from a literal alphabet rather than String.format: the consent
    // digest must be byte-identical to the gateway's, and format() would render
    // digits per the default locale.
    private fun sha256(bytes: ByteArray): String = buildString {
        append("sha256:")
        for (byte in MessageDigest.getInstance("SHA-256").digest(bytes)) {
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }

    private fun engine(granted: Boolean = true): RemoteInferenceEngine {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return RemoteInferenceEngine(
            transport = HttpGatewayTransport(baseUrl!!),
            environment = GatewayEnvironment(
                baseUrl = baseUrl,
                claimedMarketJurisdiction = "ZZ",
                catalogCountry = "ZZ",
                locale = "en-GB",
                measurementSystem = "metric",
                configurationVersion = "cfg/1",
                authorization = "Bearer test.installation-0001",
            ),
            photoBytes = { jpegBytes },
            digest = ::sha256,
            consent = {
                RemoteConsentDecision(
                    attemptId = it,
                    payloadDigest = sha256(jpegBytes),
                    modality = "photo",
                    granted = granted,
                    consentCopyVersion = "photo-consent/fixture-1",
                    processingRegion = "fixture-region",
                    marketJurisdiction = "ZZ",
                    retentionVersion = "retention/2",
                    recordedAt = now.toString().replace(".000Z", "Z"),
                    expiresAt = now.plus(10, ChronoUnit.MINUTES).toString().replace(".000Z", "Z"),
                )
            },
            idFactory = { (1..20).joinToString("") { ('a'..'z').random().toString() } },
            nowEpochMillis = { now.toEpochMilli() },
        )
    }

    private fun request(attempt: String) = PhotoInferenceRequest(
        photoRef = PhotoRef.issue(AttemptId(attempt), "photo0000000001"),
        attemptContext = InferenceAttemptContext(
            attemptId = AttemptId(attempt),
            requestId = "req_0000000000000001",
            configurationVersion = "cfg/1",
            configurationDigest = "b".repeat(64),
        ),
    )

    private fun attemptId() = "att_" + (1..20).joinToString("") { ('a'..'z').random().toString() }

    @Test
    fun realGatewayReturnsMappedCandidates() {
        if (baseUrl == null) return
        val result = runBlocking { engine().analyze(request(attemptId())) }

        val outcome = result.outcome
        assertTrue(outcome is InferenceOutcome.Candidates, "expected candidates, got $outcome")
        assertTrue(outcome.candidates.isNotEmpty())

        val top = outcome.candidates.first()
        assertEquals(1, top.rank)
        assertTrue(top.label.isNotBlank())
        // Provenance must name the engine that actually ran, server-side.
        assertTrue(top.provenance.engineId.isNotBlank())
        assertEquals("photo-inference-engine/v1", top.provenance.contractVersion)
    }

    @Test
    fun deniedConsentNeverReachesTheNetwork() {
        if (baseUrl == null) return
        // Points at a closed port: if consent were not enforced client-side this
        // would surface as a connection failure instead of an input failure.
        val closed = RemoteInferenceEngine(
            transport = HttpGatewayTransport("http://127.0.0.1:1"),
            environment = GatewayEnvironment(
                baseUrl = "http://127.0.0.1:1",
                claimedMarketJurisdiction = "ZZ",
                catalogCountry = "ZZ",
                locale = "en-GB",
                measurementSystem = "metric",
                configurationVersion = "cfg/1",
                authorization = "Bearer test.installation-0001",
            ),
            photoBytes = { jpegBytes },
            digest = ::sha256,
            consent = {
                RemoteConsentDecision(
                    attemptId = it,
                    payloadDigest = sha256(jpegBytes),
                    modality = "photo",
                    granted = false,
                    consentCopyVersion = null,
                    processingRegion = null,
                    marketJurisdiction = "ZZ",
                    retentionVersion = "retention/2",
                    recordedAt = "2026-08-03T10:00:00Z",
                    expiresAt = "2026-08-03T10:15:00Z",
                )
            },
            idFactory = { "0000000000000001" },
            nowEpochMillis = { 1_785_751_500_000L },
        )

        val outcome = runBlocking { closed.analyze(request(attemptId())) }.outcome
        assertTrue(outcome is InferenceOutcome.Failure)
        assertEquals("inference.failure.consent_required", outcome.userMessageKey)
    }
}
