package com.supernutrition.inference.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Wire types for `contracts/mobile-api/v1`, encoded and decoded by hand in
 * [GatewayCodec].
 *
 * These mirror the released schemas exactly and are bindings, not a second
 * authoring of the contract: the release under `contracts/` stays the source of
 * truth and is never written back to (AD-36). Field names match
 * `gateway-request.toml` / `gateway-response.toml` verbatim, because the
 * compatibility rule is ignore-unknown / reject-missing-required and a renamed
 * field is a silent rejection.
 *
 * WHY THE CODEC IS HAND-WRITTEN. `@Serializable` emits one synthetic branch per
 * optional field in a generated bitmask constructor, reachable only by
 * enumerating field-presence combinations. Those dominated this module's branch
 * denominator — 71 of 78 missed branches were generated constructor code — so it
 * could not meet the 85% branch floor without lowering the floor or excluding
 * generated code, and AD-33 permits neither. Reading fields explicitly means
 * every branch measured here is a branch someone wrote, and the compatibility
 * rules become visible instead of implied.
 */

internal data class ConsentReceiptWire(
    val version: String,
    val attemptId: String,
    val digest: String,
    val modality: String,
    val purpose: String,
    val decision: String,
    val marketJurisdiction: String,
    val processingRegion: String?,
    val consentCopyVersion: String?,
    val retentionVersion: String,
    val retentionDays: Int,
    val configurationVersion: String,
    val recordedAt: String,
    val expiresAt: String,
)

internal data class InferenceContextWire(
    val catalogCountry: String,
    val mealType: String,
    val locale: String,
    val measurementSystem: String,
    val configurationVersion: String,
    val priorMealSummary: String?,
)

internal data class GatewayRequestWire(
    val contractVersion: String,
    val attemptId: String,
    val effectId: String,
    val modality: String,
    val claimedMarketJurisdiction: String?,
    val consentReceipt: ConsentReceiptWire,
    val inferenceContext: InferenceContextWire,
)

// --- response -------------------------------------------------------------

internal data class ConfidenceWire(val score: Double, val band: String)

internal data class AssumptionWire(val code: String, val userMessageKey: String)

internal data class ProvenanceWire(
    val route: String,
    val engineId: String,
    val modelVersion: String,
    val calibrationVersion: String,
    val contractVersion: String,
)

/**
 * No default parameter values anywhere in these types: [GatewayCodec] supplies
 * every field, and a defaulted parameter costs a branch in Kotlin's synthetic
 * `$default` constructor that no test can honestly reach.
 */
internal data class CandidateWire(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: ConfidenceWire,
    val unresolvedAssumptions: List<AssumptionWire>,
    val provenance: ProvenanceWire,
)

/**
 * The sealed outcome union, discriminated by `outcomeType`.
 *
 * Modelled as one nullable-bearing type rather than a Kotlin sealed hierarchy
 * because the contract's rule for an unrecognised discriminant is "map to
 * unknown and treat as a recoverable failure" — a sealed decoder would throw
 * instead, turning a forward-compatible response into a crash.
 */
internal data class OutcomeWire(
    val outcomeType: String,
    val candidates: List<CandidateWire>,
    val requiresConfirmation: Boolean,
    val reasonCode: String?,
    val userMessageKey: String?,
    val failureCode: String?,
    val recoverable: Boolean?,
    val retryAfterSeconds: Int?,
)

internal data class GatewayResponseWire(
    val contractVersion: String,
    val requestId: String,
    val attemptId: String,
    val resolvedMarketJurisdiction: String,
    val resolvedProcessingRegion: String?,
    val outcome: OutcomeWire,
)

internal data class GatewayErrorBodyWire(val error: GatewayErrorWire)

internal data class GatewayErrorWire(
    val gatewayCode: String,
    val userMessageKey: String,
    val recoverable: Boolean,
    val retryAfter: Int,
    val correlationId: String?,
)

// --- codec ----------------------------------------------------------------

/** Required string. Absence throws, which is reject-missing-required. */
private fun JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

/** Optional string. Absent or JSON null both yield null. */
private fun JsonObject.strOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

/**
 * The only place the envelope meets JSON.
 *
 * Unknown fields are ignored because nothing reads them; a missing optional
 * field takes its default here rather than in a generated constructor.
 */
internal object GatewayCodec {

    fun encodeRequest(request: GatewayRequestWire): JsonObject = buildJsonObject {
        put("contractVersion", request.contractVersion)
        put("attemptId", request.attemptId)
        put("effectId", request.effectId)
        put("modality", request.modality)
        request.claimedMarketJurisdiction?.let { put("claimedMarketJurisdiction", it) }
        put("consentReceipt", encodeConsent(request.consentReceipt))
        put("inferenceContext", encodeContext(request.inferenceContext))
    }

    private fun encodeConsent(receipt: ConsentReceiptWire): JsonObject = buildJsonObject {
        put("version", receipt.version)
        put("attemptId", receipt.attemptId)
        put("digest", receipt.digest)
        put("modality", receipt.modality)
        put("purpose", receipt.purpose)
        put("decision", receipt.decision)
        put("marketJurisdiction", receipt.marketJurisdiction)
        receipt.processingRegion?.let { put("processingRegion", it) }
        receipt.consentCopyVersion?.let { put("consentCopyVersion", it) }
        put("retentionVersion", receipt.retentionVersion)
        put("retentionDays", receipt.retentionDays)
        put("configurationVersion", receipt.configurationVersion)
        put("recordedAt", receipt.recordedAt)
        put("expiresAt", receipt.expiresAt)
    }

    private fun encodeContext(context: InferenceContextWire): JsonObject = buildJsonObject {
        put("catalogCountry", context.catalogCountry)
        put("mealType", context.mealType)
        put("locale", context.locale)
        put("measurementSystem", context.measurementSystem)
        put("configurationVersion", context.configurationVersion)
        context.priorMealSummary?.let { put("priorMealSummary", it) }
    }

    fun decodeResponse(root: JsonObject): GatewayResponseWire = GatewayResponseWire(
        contractVersion = root.str("contractVersion"),
        requestId = root.str("requestId"),
        attemptId = root.str("attemptId"),
        resolvedMarketJurisdiction = root.str("resolvedMarketJurisdiction"),
        resolvedProcessingRegion = root.strOrNull("resolvedProcessingRegion"),
        outcome = decodeOutcome(root.obj("outcome")),
    )

    fun decodeOutcome(node: JsonObject): OutcomeWire = OutcomeWire(
        outcomeType = node.str("outcomeType"),
        candidates = (node["candidates"] as? JsonArray)?.map { decodeCandidate(it.jsonObject) }.orEmpty(),
        requiresConfirmation = (node["requiresConfirmation"] as? JsonPrimitive)?.booleanOrNull ?: false,
        reasonCode = node.strOrNull("reasonCode"),
        userMessageKey = node.strOrNull("userMessageKey"),
        failureCode = node.strOrNull("failureCode"),
        recoverable = (node["recoverable"] as? JsonPrimitive)?.booleanOrNull,
        retryAfterSeconds = (node["retryAfterSeconds"] as? JsonPrimitive)?.intOrNull,
    )

    fun decodeCandidate(node: JsonObject): CandidateWire = CandidateWire(
        candidateId = node.str("candidateId"),
        label = node.str("label"),
        rank = node.getValue("rank").jsonPrimitive.int,
        confidence = node.obj("confidence").let {
            ConfidenceWire(it.getValue("score").jsonPrimitive.double, it.str("band"))
        },
        unresolvedAssumptions = (node["unresolvedAssumptions"] as? JsonArray)
            ?.map { AssumptionWire(it.jsonObject.str("code"), it.jsonObject.str("userMessageKey")) }
            .orEmpty(),
        provenance = node.obj("provenance").let {
            ProvenanceWire(
                route = it.str("route"),
                engineId = it.str("engineId"),
                modelVersion = it.str("modelVersion"),
                calibrationVersion = it.str("calibrationVersion"),
                contractVersion = it.str("contractVersion"),
            )
        },
    )

    fun decodeErrorBody(root: JsonObject): GatewayErrorBodyWire {
        val error = root.obj("error")
        return GatewayErrorBodyWire(
            GatewayErrorWire(
                gatewayCode = error.str("gatewayCode"),
                userMessageKey = error.str("userMessageKey"),
                recoverable = error.getValue("recoverable").jsonPrimitive.boolean,
                retryAfter = (error["retryAfter"] as? JsonPrimitive)?.intOrNull ?: 0,
                correlationId = error.strOrNull("correlationId"),
            ),
        )
    }
}
