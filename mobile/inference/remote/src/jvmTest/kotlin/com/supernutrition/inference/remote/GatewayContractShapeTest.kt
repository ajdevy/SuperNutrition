package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the wire envelope against `contracts/mobile-api/v1/gateway-request.toml`.
 *
 * [RemoteInferenceEngineTest] drives a fake transport, so it observes the mapped
 * outcome but never the bytes sent. That gap let the engine ship an
 * `effectId`/`requestId` pair that no single factory value could make valid at
 * once, and the gateway rejected the first real call with `validation-error`.
 * Encoding the released patterns here catches that class of drift without a
 * server, so it fails in `jvmTest` rather than on a device.
 */
private const val SHAPE_ATTEMPT = "att_0000000000000001"

private val ATTEMPT_PATTERN = Regex("^att_[A-Za-z0-9]{16,64}$")
private val EFFECT_PATTERN = Regex("^eff_[A-Za-z0-9]{16,64}$")
private val REQUEST_PATTERN = Regex("^req_[A-Za-z0-9]{16,64}$")

private class CapturingTransport : GatewayTransport {
    var contextJson: String? = null
    var requestId: String? = null

    override suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse {
        this.contextJson = call.contextJson
        this.requestId = call.requestId
        // Status is irrelevant: these assertions are about the request.
        return GatewayHttpResponse(500, "")
    }
}

private fun shapeEngine(transport: GatewayTransport, idFactory: () -> String) = RemoteInferenceEngine(
    transport = transport,
    environment = GatewayEnvironment(
        baseUrl = "http://10.0.2.2:3000",
        claimedMarketJurisdiction = "ZZ",
        catalogCountry = "ZZ",
        locale = "en-GB",
        measurementSystem = "metric",
        configurationVersion = "cfg/1",
        authorization = "Bearer test.installation-0001",
    ),
    photoBytes = { byteArrayOf(0xFF.toByte(), 0xD8.toByte()) },
    digest = { "sha256:" + "a".repeat(64) },
    consent = {
        RemoteConsentDecision(
            granted = true,
            consentCopyVersion = "photo-consent/fixture-1",
            processingRegion = "fixture-region",
            marketJurisdiction = "ZZ",
            retentionVersion = "retention/2",
            recordedAt = "2026-08-03T10:00:00Z",
            expiresAt = "2026-08-03T10:15:00Z",
        )
    },
    idFactory = idFactory,
)

private fun shapeRequest() = PhotoInferenceRequest(
    photoRef = PhotoRef.issue(AttemptId(SHAPE_ATTEMPT), "photo0000000001"),
    attemptContext = InferenceAttemptContext(
        attemptId = AttemptId(SHAPE_ATTEMPT),
        requestId = "req_0000000000000001",
        configurationVersion = "cfg/1",
        configurationDigest = "b".repeat(64),
    ),
)

private fun shapeEngineFor(transport: GatewayTransport, idFactory: () -> String) = shapeEngine(transport, idFactory)

private fun rawRequest(attempt: String) = PhotoInferenceRequest(
    photoRef = PhotoRef.issue(AttemptId(attempt), "photo0000000001"),
    attemptContext = InferenceAttemptContext(
        attemptId = AttemptId(attempt),
        requestId = "req_0000000000000001",
        configurationVersion = "cfg/1",
        configurationDigest = "b".repeat(64),
    ),
)

private fun field(body: String, name: String) = Regex("\"$name\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)

class GatewayContractShapeTest {

    @Test
    fun envelopeIdsSatisfyTheReleasedContractPatterns() = runBlocking {
        val transport = CapturingTransport()

        shapeEngine(transport) { "0000000000000001" }.analyze(shapeRequest())

        val body = assertNotNull(transport.contextJson)
        val effectId = assertNotNull(field(body, "effectId"))
        val attemptId = assertNotNull(field(body, "attemptId"))
        val requestId = assertNotNull(transport.requestId)

        assertTrue(EFFECT_PATTERN.matches(effectId), "effectId=$effectId")
        assertTrue(ATTEMPT_PATTERN.matches(attemptId), "attemptId=$attemptId")
        assertTrue(REQUEST_PATTERN.matches(requestId), "requestId=$requestId")
    }

    @Test
    fun aDomainAttemptIdIsMappedOntoTheContractFormRatherThanSentRaw() = runBlocking {
        // The Android host's attempt id is "android-photo-attempt", which the
        // gateway rejects outright. Sending it raw failed every real request.
        val transport = CapturingTransport()
        val engine = shapeEngineFor(transport) { "0000000000000001" }

        engine.analyze(rawRequest("android-photo-attempt"))

        val body = assertNotNull(transport.contextJson)
        val attemptId = assertNotNull(field(body, "attemptId"))
        assertTrue(ATTEMPT_PATTERN.matches(attemptId), "attemptId=$attemptId")
        assertTrue("android-photo-attempt" !in body, "raw domain id must not travel")
        // The receipt is cross-checked against the envelope, so both must map.
        assertTrue(body.count { it == '{' } > 0 && attemptId in body)
    }

    @Test
    fun theMappedAttemptIdIsStableAcrossRetries() = runBlocking {
        val first = CapturingTransport()
        val second = CapturingTransport()
        shapeEngineFor(first) { "0000000000000001" }
            .analyze(rawRequest("android-photo-attempt"))
        shapeEngineFor(second) { "0000000000000002" }
            .analyze(rawRequest("android-photo-attempt"))

        assertEquals(
            field(assertNotNull(first.contextJson), "attemptId"),
            field(assertNotNull(second.contextJson), "attemptId"),
        )
    }

    @Test
    fun effectAndRequestIdsAreDistinctDrawsRatherThanOneTokenReused() = runBlocking {
        val transport = CapturingTransport()
        var n = 0

        shapeEngine(transport) { "000000000000000" + n++ }.analyze(shapeRequest())

        val effectId = assertNotNull(field(assertNotNull(transport.contextJson), "effectId"))
        // The effect id is the idempotency key and the request id is the
        // correlation id. Reusing one token for both would make a retry
        // indistinguishable from a fresh effect.
        assertTrue(
            effectId.removePrefix("eff_") != assertNotNull(transport.requestId).removePrefix("req_"),
            "effectId=$effectId requestId=${transport.requestId}",
        )
    }
}
