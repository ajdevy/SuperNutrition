package com.supernutrition.inference.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for `contracts/mobile-api/v1`.
 *
 * These mirror the released schemas exactly and are generated-by-hand bindings,
 * not a second authoring of the contract: the release under `contracts/` stays
 * the source of truth and is never written back to (AD-36). Field names match
 * `gateway-request.toml` / `gateway-response.toml` verbatim, because the
 * compatibility rule is ignore-unknown / reject-missing-required and a renamed
 * field is a silent rejection.
 */

@Serializable
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

@Serializable
internal data class InferenceContextWire(
    val catalogCountry: String,
    val mealType: String,
    val locale: String,
    val measurementSystem: String,
    val configurationVersion: String,
    val priorMealSummary: String? = null,
)

@Serializable
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

@Serializable
internal data class ConfidenceWire(val score: Double, val band: String)

@Serializable
internal data class AssumptionWire(val code: String, val userMessageKey: String)

@Serializable
internal data class ProvenanceWire(
    val route: String,
    val engineId: String,
    val modelVersion: String,
    val calibrationVersion: String,
    val contractVersion: String,
)

@Serializable
internal data class CandidateWire(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidence: ConfidenceWire,
    val unresolvedAssumptions: List<AssumptionWire> = emptyList(),
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
@Serializable
internal data class OutcomeWire(
    val outcomeType: String,
    val candidates: List<CandidateWire> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val reasonCode: String? = null,
    val userMessageKey: String? = null,
    val failureCode: String? = null,
    val recoverable: Boolean? = null,
    val retryAfterSeconds: Int? = null,
)

@Serializable
internal data class GatewayResponseWire(
    val contractVersion: String,
    val requestId: String,
    val attemptId: String,
    val resolvedMarketJurisdiction: String,
    val resolvedProcessingRegion: String? = null,
    val outcome: OutcomeWire,
)

@Serializable
internal data class GatewayErrorBodyWire(@SerialName("error") val error: GatewayErrorWire)

@Serializable
internal data class GatewayErrorWire(
    val gatewayCode: String,
    val userMessageKey: String,
    val recoverable: Boolean,
    val retryAfter: Int = 0,
    val correlationId: String? = null,
)
