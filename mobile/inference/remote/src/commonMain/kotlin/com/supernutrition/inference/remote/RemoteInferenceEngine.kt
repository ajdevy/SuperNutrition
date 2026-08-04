package com.supernutrition.inference.remote

import com.supernutrition.domain.inference.AbstentionReason
import com.supernutrition.domain.inference.AssumptionCode
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.EngineCapability
import com.supernutrition.domain.inference.InferenceAssumption
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Supplies the scrubbed, normalized bytes for an attempt's PhotoRef. */
fun interface PhotoBytesSource {
    fun read(attemptId: String): ByteArray?
}

/** Consent decided by the user, before any transfer. Never defaulted (AD-40). */
data class RemoteConsentDecision(
    val granted: Boolean,
    val consentCopyVersion: String?,
    val processingRegion: String?,
    val marketJurisdiction: String,
    val retentionVersion: String,
    val recordedAt: String,
    val expiresAt: String,
)

/**
 * E4.2 — the first-party gateway Engine.
 *
 * Implements the frozen v1 Engine contract and speaks only the SuperNutrition
 * envelope, never a provider schema (AD-13). Three properties are deliberate:
 *
 *  - The consent digest is computed over the bytes that are ACTUALLY sent. The
 *    gateway recomputes it and rejects a mismatch, so computing it over
 *    anything else fails closed rather than silently transferring (AD-8).
 *  - A gateway failure becomes a typed `InferenceOutcome.Failure`, never an
 *    exception carrying an upstream message.
 *  - No nutrition field is read from the response. The contract forbids the
 *    gateway from sending any, and the Engine contract has nowhere to put one.
 */
class RemoteInferenceEngine(
    private val transport: GatewayTransport,
    private val environment: GatewayEnvironment,
    private val photoBytes: PhotoBytesSource,
    private val digest: (ByteArray) -> String,
    private val consent: (attemptId: String) -> RemoteConsentDecision,
    /**
     * Yields an opaque token of 16..64 alphanumerics. The engine adds the `eff_`
     * and `req_` prefixes itself, because the contract's ids are
     * `^(eff|req)_[A-Za-z0-9]{16,64}$` — underscores are illegal AFTER the
     * prefix, so a factory that returned a prefixed id could not be reused for
     * the other field without producing `req_eff_...` and failing validation.
     */
    private val idFactory: () -> String,
) : PhotoInferenceEngine {

    override val contractVersion: String = CONTRACT_V1

    override suspend fun capability(): EngineCapability = EngineCapability(
        contractVersion = CONTRACT_V1,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.remote_ready",
        engineId = ENGINE_ID,
        // A ready capability must name a model version. For a remote engine the
        // per-attempt model is chosen server-side, so the honest capability-time
        // identity is the gateway binding; the concrete model that ran appears
        // in each result's provenance (AD-9).
        modelVersion = environment.contractVersion,
    )

    override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
        val domainAttemptId = request.attemptContext.attemptId.value
        val attemptId = contractAttemptId(domainAttemptId)
        val bytes = photoBytes.read(domainAttemptId)
            ?: return failure(InferenceFailureCode.INVALID_INPUT, false, "inference.failure.payload_missing")

        val decision = consent(domainAttemptId)
        // A declined or unbound decision must not reach the network at all.
        if (!decision.granted) {
            return failure(InferenceFailureCode.INVALID_INPUT, false, "inference.failure.consent_required")
        }

        val envelope = GatewayRequestWire(
            contractVersion = environment.contractVersion,
            attemptId = attemptId,
            effectId = "eff_${idFactory()}",
            modality = "photo",
            claimedMarketJurisdiction = environment.claimedMarketJurisdiction,
            consentReceipt = ConsentReceiptWire(
                version = "1",
                attemptId = attemptId,
                digest = digest(bytes),
                modality = "photo",
                purpose = "food-identification",
                decision = "granted",
                marketJurisdiction = decision.marketJurisdiction,
                processingRegion = decision.processingRegion,
                consentCopyVersion = decision.consentCopyVersion,
                retentionVersion = decision.retentionVersion,
                retentionDays = 0,
                configurationVersion = environment.configurationVersion,
                recordedAt = decision.recordedAt,
                expiresAt = decision.expiresAt,
            ),
            inferenceContext = InferenceContextWire(
                catalogCountry = environment.catalogCountry,
                mealType = "unspecified",
                locale = environment.locale,
                measurementSystem = environment.measurementSystem,
                configurationVersion = environment.configurationVersion,
                priorMealSummary = null,
            ),
        )

        val response = try {
            transport.postInferencePhoto(
                GatewayCall(
                    contextJson = GatewayCodec.encodeRequest(envelope).toString(),
                    payload = bytes,
                    contentType = "image/jpeg",
                    requestId = "req_${idFactory()}",
                    contractVersion = environment.contractVersion,
                    authorization = environment.authorization,
                ),
            )
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation // AD-6: cancellation is never swallowed.
        } catch (_: Throwable) {
            return failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, "inference.failure.network")
        }

        return if (response.status == 200) mapSuccess(response.body) else mapError(response.body)
    }

    /**
     * Maps a domain attempt id onto the contract's opaque form.
     *
     * The mobile domain lets an attempt id be any non-blank string — the Android
     * host uses "android-photo-attempt" — while the gateway requires
     * `^att_[A-Za-z0-9]{16,64}$` and rejects anything else with
     * `validation-error`. Passing the domain value through therefore fails every
     * real request, which is what it did on the first device run.
     *
     * The mapping is a digest rather than a random id because this value is the
     * gateway's per-attempt anchor: the same attempt has to produce the same id
     * on a retry, and the raw domain value must not travel.
     */
    private fun contractAttemptId(raw: String): String = if (ATTEMPT_PATTERN.matches(raw)) {
        raw
    } else {
        "att_" + digest(raw.encodeToByteArray()).substringAfter("sha256:").take(32)
    }

    private fun mapSuccess(body: String): PhotoInferenceResult {
        val wire = runCatching { GatewayCodec.decodeResponse(JSON.parseToJsonElement(body).jsonObject) }
            .getOrNull()
            ?: return failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, "inference.failure.malformed")

        return when (wire.outcome.outcomeType) {
            "candidates" -> {
                val candidates = wire.outcome.candidates.map { it.toDomain() }
                if (candidates.isEmpty()) {
                    abstention(AbstentionReason.NO_FOOD_DETECTED, "inference.abstention.no_food_detected")
                } else {
                    PhotoInferenceResult(CONTRACT_V1, InferenceOutcome.Candidates(candidates))
                }
            }

            "abstention" -> abstention(
                AbstentionReason.NO_FOOD_DETECTED,
                wire.outcome.userMessageKey ?: "inference.abstention.no_food_detected",
            )

            "failure" -> failure(
                failureCodeFor(wire.outcome.failureCode),
                wire.outcome.recoverable ?: false,
                wire.outcome.userMessageKey ?: "inference.failure.unknown",
            )

            // Unknown discriminant: recoverable failure, never a crash.
            else -> failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, "inference.failure.unknown")
        }
    }

    private fun mapError(body: String): PhotoInferenceResult {
        val wire = runCatching { GatewayCodec.decodeErrorBody(JSON.parseToJsonElement(body).jsonObject) }
            .getOrNull()?.error
            ?: return failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, "inference.failure.unknown")
        return failure(gatewayCodeToFailure(wire.gatewayCode), wire.recoverable, wire.userMessageKey)
    }

    private fun abstention(reason: AbstentionReason, key: String) = PhotoInferenceResult(
        CONTRACT_V1,
        InferenceOutcome.Abstention(reason, key, provenance()),
    )

    private fun failure(code: InferenceFailureCode, retryable: Boolean, key: String) = PhotoInferenceResult(
        CONTRACT_V1,
        InferenceOutcome.Failure(code, retryable, key, provenance()),
    )

    private fun provenance() = InferenceProvenance(
        route = InferenceRoute.REMOTE,
        engineId = ENGINE_ID,
        modelVersion = "gateway",
        calibrationVersion = "gateway",
        contractVersion = CONTRACT_V1,
    )

    private fun CandidateWire.toDomain() = InferenceCandidate(
        candidateId = candidateId,
        label = label,
        rank = rank,
        // The band is recomputed from the score rather than trusted: the domain
        // type requires band == fromScore(score), so a disagreeing gateway would
        // otherwise throw inside a data class init.
        confidence = Confidence(confidence.score, ConfidenceBand.fromScore(confidence.score)),
        // An assumption code the client does not recognise is dropped, not
        // mapped to a wrong one: the domain enum is closed, and a v2 gateway
        // adding a code must not crash a v1 client.
        unresolvedAssumptions = unresolvedAssumptions.mapNotNull { wire ->
            assumptionCodeFor(wire.code)?.let { InferenceAssumption(it, wire.userMessageKey) }
        },
        provenance = InferenceProvenance(
            route = InferenceRoute.REMOTE,
            engineId = provenance.engineId,
            modelVersion = provenance.modelVersion,
            calibrationVersion = provenance.calibrationVersion,
            contractVersion = CONTRACT_V1,
        ),
    )

    internal companion object {
        const val ENGINE_ID: String = "gateway-remote"
        const val CONTRACT_V1: String = "photo-inference-engine/v1"

        val ATTEMPT_PATTERN: Regex = Regex("^att_[A-Za-z0-9]{16,64}$")

        val JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Maps the B-5 error taxonomy onto the closed v1 failure set. */
        fun gatewayCodeToFailure(code: String): InferenceFailureCode = when (code) {
            "validation-error", "invalid-modality", "malformed-consent",
            "consent-expired", "consent-required", "unsupported-media-type",
            "content-too-large", "unsupported-version",
            -> InferenceFailureCode.INVALID_INPUT

            "rate-limited", "budget-exceeded" -> InferenceFailureCode.RESOURCE_LIMIT

            "kill-switch", "market-not-permitted", "test-mode-forbidden",
            "insufficient-scope", "authentication-failed", "session-expired",
            "token-replayed",
            -> InferenceFailureCode.MODEL_UNAVAILABLE

            else -> InferenceFailureCode.TEMPORARY_UNAVAILABLE
        }

        /** Wire assumption code -> closed domain enum; null when unrecognised. */
        fun assumptionCodeFor(code: String): AssumptionCode? = when (code) {
            "portion-unknown" -> AssumptionCode.PORTION_UNKNOWN
            "preparation-unknown" -> AssumptionCode.PREPARATION_UNKNOWN
            "hidden-ingredients-unknown", "ingredient-hidden" -> AssumptionCode.HIDDEN_INGREDIENTS_UNKNOWN
            "mixed-dish" -> AssumptionCode.MIXED_DISH
            else -> null
        }

        fun failureCodeFor(code: String?): InferenceFailureCode = when (code) {
            "content-rejected" -> InferenceFailureCode.INVALID_INPUT
            "budget-exceeded", "rate-limited" -> InferenceFailureCode.RESOURCE_LIMIT
            "kill-switch" -> InferenceFailureCode.MODEL_UNAVAILABLE
            else -> InferenceFailureCode.TEMPORARY_UNAVAILABLE
        }
    }
}
