package com.supernutrition.inference.remote

/**
 * The transport seam for the first-party gateway (E4.2).
 *
 * The engine depends on this, not on Ktor, for two reasons:
 *
 *  - contract behaviour (consent binding, envelope shape, error mapping) is then
 *    testable without an HTTP stack or a network, in `commonTest`;
 *  - the Ktor engine is an Android/Apple concern and does not belong in
 *    `commonMain`, which must stay platform-pure (AD-4).
 *
 * Only a platform adapter implements this. Nothing Ktor-shaped escapes it.
 */
interface GatewayTransport {
    /**
     * Performs the multipart POST to `/gw/v1/inference/photo`.
     *
     * @param contextJson the serialized `context` part — the envelope.
     * @param payload the scrubbed, normalized photo bytes.
     * @return the raw status and body; interpreting them is the engine's job so
     *         that mapping stays in one place and stays testable.
     */
    suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse
}

/**
 * One outbound gateway call.
 *
 * Not a `data class`: it holds the photo bytes, and generated `equals`/`hashCode`
 * over a `ByteArray` compare identity rather than content, which is a trap for
 * anyone who later reaches for them.
 */
class GatewayCall(
    val contextJson: String,
    val payload: ByteArray,
    val contentType: String,
    val requestId: String,
    val contractVersion: String,
    val authorization: String,
)

/** A transport-level result. Deliberately dumb: no interpretation happens here. */
data class GatewayHttpResponse(val status: Int, val body: String)

/**
 * Everything the engine needs that is not the photo itself.
 *
 * The market jurisdiction is a HINT. The gateway resolves the authoritative
 * value and echoes it back; a client cannot widen its own market by asserting a
 * more permissive one (AD-41 MP-7).
 */
data class GatewayEnvironment(
    val baseUrl: String,
    val contractVersion: String = "mobile-api/v1",
    val claimedMarketJurisdiction: String,
    val catalogCountry: String,
    val locale: String,
    val measurementSystem: String,
    val configurationVersion: String,
    val authorization: String,
)
