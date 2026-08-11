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

/**
 * How far ahead of this device a decision's timestamp may sit before it is
 * treated as not-yet-taken. Device clock drift, not a window a caller chooses.
 */
private const val CLOCK_SKEW_TOLERANCE_MILLIS = 60_000L

/**
 * Consent decided by the user, before any transfer. Never defaulted (AD-40).
 *
 * [attemptId], [payloadDigest] and [modality] are what BIND the decision to the
 * thing it consented to. Without them a decision is a bag of strings the engine
 * copies into an envelope: it could belong to a different attempt, or to
 * different bytes, and the transfer would still go out carrying a receipt that
 * looked complete.
 */
data class RemoteConsentDecision(
    val granted: Boolean,
    /** The attempt this decision was taken for, as the DOMAIN attempt id. */
    val attemptId: String,
    /** `sha256:...` over the exact scrubbed bytes shown to the user. */
    val payloadDigest: String,
    val modality: String,
    val consentCopyVersion: String?,
    val processingRegion: String?,
    val marketJurisdiction: String,
    val retentionVersion: String,
    val recordedAt: String,
    val expiresAt: String,
)

private data class Rfc3339Parts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
) {
    fun isInRange(): Boolean = month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..60

    /** Days from the civil epoch (Howard Hinnant), so no platform calendar is used. */
    fun daysFromCivil(): Long {
        val shiftedYear = if (month <= 2) year - 1 else year
        val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
        val yearOfEra = shiftedYear - era * 400
        val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }
}

private fun splitRfc3339(value: String): Rfc3339Parts? {
    val normalized = value.trim()
    if (normalized.length < 20 || !normalized.endsWith("Z")) return null
    val dateParts = normalized.substringBefore('T').split('-')
    val timeParts = normalized.substringAfter('T').removeSuffix("Z").substringBefore('.').split(':')
    if (dateParts.size != 3 || timeParts.size != 3) return null

    val numbers = (dateParts + timeParts).map { it.toIntOrNull() ?: return null }
    return Rfc3339Parts(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5])
}

/**
 * Parses the contract's `RFC 3339 UTC instant` to epoch milliseconds, or null.
 *
 * Deliberately strict and deliberately small: this module has no date library,
 * and the alternative to a strict parser here is trusting a client-supplied
 * timestamp we cannot read. Anything that is not exactly `YYYY-MM-DDThh:mm:ssZ`
 * (with optional fractional seconds) is unparseable, and unparseable fails
 * closed rather than being treated as "not expired".
 */
internal fun parseRfc3339Utc(value: String): Long? {
    val parts = splitRfc3339(value)?.takeIf { it.isInRange() } ?: return null
    val seconds =
        parts.daysFromCivil() * 86_400L + parts.hour * 3_600L + parts.minute * 60L + parts.second
    return seconds * 1_000L
}

/** The decision is about THIS request: this attempt, these bytes, this modality. */
private fun bindingRefusal(decision: RemoteConsentDecision, domainAttemptId: String, payloadDigest: String): String? =
    when {
        !decision.granted -> "inference.failure.consent_required"
        decision.attemptId != domainAttemptId -> "inference.failure.consent_attempt_mismatch"
        decision.payloadDigest != payloadDigest -> "inference.failure.consent_digest_mismatch"
        decision.modality != "photo" -> "inference.failure.consent_modality_mismatch"
        else -> null
    }

/**
 * A granted decision with no reviewed copy, no region or no market is INCOMPLETE,
 * not permissive. The gateway rejects each of these too, so sending anyway would
 * transfer the photo and only then be refused — the bytes having already left.
 */
private fun completenessRefusal(decision: RemoteConsentDecision, environment: GatewayEnvironment): String? = when {
    decision.consentCopyVersion.isNullOrBlank() -> "inference.failure.consent_copy_missing"

    decision.processingRegion.isNullOrBlank() -> "inference.failure.consent_region_missing"

    decision.marketJurisdiction.isBlank() -> "inference.failure.consent_market_missing"

    decision.retentionVersion.isBlank() -> "inference.failure.consent_retention_missing"

    environment.configurationVersion.isBlank() -> "inference.failure.consent_configuration_missing"

    decision.marketJurisdiction != environment.claimedMarketJurisdiction ->
        "inference.failure.consent_market_mismatch"

    else -> null
}

/** The window must be readable, ordered, already open, and not yet closed. */
private fun windowRefusal(decision: RemoteConsentDecision, nowEpochMillis: Long): String? {
    val recordedAt = parseRfc3339Utc(decision.recordedAt)
        ?: return "inference.failure.consent_timestamp_unreadable"
    val expiresAt = parseRfc3339Utc(decision.expiresAt)
        ?: return "inference.failure.consent_timestamp_unreadable"
    return when {
        expiresAt <= recordedAt -> "inference.failure.consent_window_invalid"

        nowEpochMillis >= expiresAt -> "inference.failure.consent_expired"

        // Recorded in the future: the decision has not been taken yet, and its
        // window has not opened however short it looks.
        recordedAt > nowEpochMillis + CLOCK_SKEW_TOLERANCE_MILLIS ->
            "inference.failure.consent_not_yet_valid"

        else -> null
    }
}

/** What the response must agree with to be an answer to this request. */
internal data class RequestBinding(
    val attemptId: String,
    val requestId: String,
    /**
     * The region the CONSENT RECEIPT promised, not merely one the response
     * supplies. The release makes resolvedProcessingRegion optional, so absence
     * is legal; a region that contradicts the one shown to the user before they
     * consented is not.
     */
    val consentedProcessingRegion: String?,
)

/**
 * The first way the response fails to be an answer to this request, or null.
 *
 * Market and processing region are included because they are the user-visible
 * privacy claim: the receipt said where the photo would be processed, and a
 * reply from somewhere else means the promise shown before consent was not the
 * promise kept.
 */
internal fun bindingMismatch(
    wire: GatewayResponseWire,
    expected: RequestBinding,
    environment: GatewayEnvironment,
): String? = when {
    wire.contractVersion != environment.contractVersion ->
        "inference.failure.response_contract_mismatch"

    wire.attemptId != expected.attemptId -> "inference.failure.response_attempt_mismatch"

    wire.requestId != expected.requestId -> "inference.failure.response_request_mismatch"

    wire.resolvedMarketJurisdiction != environment.claimedMarketJurisdiction ->
        "inference.failure.response_market_mismatch"

    // Absence is permitted by the release. A region that DISAGREES with the one
    // the user was shown before consenting is not: that is the privacy promise
    // being quietly broken after the fact.
    !wire.resolvedProcessingRegion.isNullOrBlank() &&
        !expected.consentedProcessingRegion.isNullOrBlank() &&
        wire.resolvedProcessingRegion != expected.consentedProcessingRegion ->
        "inference.failure.response_region_mismatch"

    else -> null
}

/**
 * Maps a released abstention reason code onto the domain enum.
 *
 * Unknown codes fall back to NO_FOOD_DETECTED, which is what every code used to
 * become. The difference is that the fallback is now explicit and reached only
 * by a code this client does not know, rather than by all of them.
 */
internal fun abstentionReasonFor(code: String?): AbstentionReason = when (code) {
    "identity-ambiguous" -> AbstentionReason.IDENTITY_AMBIGUOUS
    "no-food-detected" -> AbstentionReason.NO_FOOD_DETECTED
    "image-quality", "image-unusable" -> AbstentionReason.IMAGE_QUALITY
    "portion-ambiguous" -> AbstentionReason.PORTION_AMBIGUOUS
    "unsupported-content", "content-rejected-by-provider" -> AbstentionReason.UNSUPPORTED_CONTENT
    "ambiguous-scene" -> AbstentionReason.IDENTITY_AMBIGUOUS
    else -> AbstentionReason.NO_FOOD_DETECTED
}

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
class RemoteInferenceEngine
@Suppress("LongParameterList")
constructor(
    private val transport: GatewayTransport,
    private val environment: GatewayEnvironment,
    private val photoBytes: PhotoBytesSource,
    private val digest: (ByteArray) -> String,
    private val consent: (attemptId: String) -> RemoteConsentDecision,
    /**
     * Epoch milliseconds. Injected rather than read, for the same reason
     * MarketPolicy takes its day: an expiry check that invents its own "now" is
     * not auditable, and cannot be tested at the boundary where it matters.
     */
    private val nowEpochMillis: () -> Long,
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

    /**
     * The message key of the first thing wrong with this decision, or null.
     *
     * Ordered from "this decision is not about this request" outwards, so the
     * most specific mismatch is the one reported.
     */
    private fun consentRefusal(
        decision: RemoteConsentDecision,
        domainAttemptId: String,
        payloadDigest: String,
    ): String? = bindingRefusal(decision, domainAttemptId, payloadDigest)
        ?: completenessRefusal(decision, environment)
        ?: windowRefusal(decision, nowEpochMillis())

    /** The request envelope, built from values already validated above. */
    private fun requestEnvelope(attemptId: String, decision: RemoteConsentDecision, payloadDigest: String) =
        GatewayRequestWire(
            contractVersion = environment.contractVersion,
            attemptId = attemptId,
            effectId = "eff_${idFactory()}",
            modality = "photo",
            claimedMarketJurisdiction = environment.claimedMarketJurisdiction,
            consentReceipt = ConsentReceiptWire(
                version = "1",
                attemptId = attemptId,
                digest = payloadDigest,
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

    override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult {
        val domainAttemptId = request.attemptContext.attemptId.value
        val attemptId = contractAttemptId(domainAttemptId)
        val bytes = photoBytes.read(domainAttemptId)
            ?: return failure(InferenceFailureCode.INVALID_INPUT, false, "inference.failure.payload_missing")

        val decision = consent(domainAttemptId)
        // P1-4 — everything the receipt asserts is checked HERE, before the
        // envelope is built and long before transport is touched.
        //
        // Only `granted` used to be checked. Everything else — which attempt the
        // decision was for, which bytes it covered, whether it had expired,
        // whether a region and a reviewed copy actually existed — was copied
        // into the receipt unread. A receipt is a claim the gateway will hold us
        // to; assembling one out of unvalidated strings and sending the user's
        // photo behind it is the failure this whole path exists to prevent.
        //
        // Each refusal returns before `transport` is mentioned, which is what the
        // tests assert: not merely that the result is a failure, but that no call
        // was made.
        val refusal = consentRefusal(decision, domainAttemptId, digest(bytes))
        if (refusal != null) {
            return failure(InferenceFailureCode.INVALID_INPUT, false, refusal)
        }

        // Held rather than inlined: the response is checked against exactly the
        // values that were sent, and a freshly minted id at the call site would
        // make that check compare a value with itself.
        val requestId = "req_${idFactory()}"

        val envelope = requestEnvelope(attemptId, decision, digest(bytes))

        val response = try {
            transport.postInferencePhoto(
                GatewayCall(
                    contextJson = GatewayCodec.encodeRequest(envelope).toString(),
                    payload = bytes,
                    contentType = "image/jpeg",
                    requestId = requestId,
                    contractVersion = environment.contractVersion,
                    authorization = environment.authorization,
                ),
            )
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation // AD-6: cancellation is never swallowed.
        } catch (_: Throwable) {
            return failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, "inference.failure.network")
        }

        return if (response.status == 200) {
            mapSuccess(
                response.body,
                expected = RequestBinding(attemptId, requestId, decision.processingRegion),
            )
        } else {
            mapError(response.body)
        }
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

    private fun mapSuccess(body: String, expected: RequestBinding): PhotoInferenceResult {
        val wire = runCatching { GatewayCodec.decodeResponse(JSON.parseToJsonElement(body).jsonObject) }
            .getOrNull()
            ?: return failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, "inference.failure.malformed")

        // P1-8 — the response has to be an answer to THIS request. None of these
        // were checked: a reply carrying another attempt's id, another request's
        // id, or a contract version this client does not speak was decoded and
        // shown to the user as their own result. A correlation that is generated
        // and then never compared is not a correlation.
        // Non-retryable: a response that does not match this request will not
        // match it on a second attempt either.
        //
        // The code is TEMPORARY_UNAVAILABLE because InferenceFailureCode is part
        // of the frozen contracts/inference/v1 release and has no
        // `malformed-response` member. Adding one is a contract change and the
        // owner's call, so the distinction is carried by the message key and by
        // retryable=false rather than by inventing an enum value here. Flagged
        // in the PR report.
        bindingMismatch(wire, expected, environment)?.let {
            return failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, false, it)
        }

        return when (wire.outcome.outcomeType) {
            "candidates" -> {
                // P1-8 — decode-to-domain is wrapped. The domain types validate
                // in their init blocks (contiguous ranks, unique ids, score in
                // range), so a gateway sending an invalid rank threw an
                // IllegalArgumentException out of the engine instead of
                // returning a typed failure.
                val candidates = runCatching { wire.outcome.candidates.map { it.toDomain() } }
                    .getOrNull()
                    ?: return failure(
                        InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                        false,
                        "inference.failure.malformed_candidate",
                    )
                if (candidates.isEmpty()) {
                    abstention(AbstentionReason.NO_FOOD_DETECTED, "inference.abstention.no_food_detected")
                } else {
                    runCatching {
                        PhotoInferenceResult(
                            CONTRACT_V1,
                            // P1-9 — carried through instead of discarded.
                            InferenceOutcome.Candidates(candidates, wire.outcome.requiresConfirmation),
                        )
                    }.getOrNull() ?: failure(
                        InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                        false,
                        "inference.failure.malformed_candidate",
                    )
                }
            }

            // P1-9 — every reason code the release declares is mapped, with an
            // explicit fallback. Everything used to become NO_FOOD_DETECTED, so
            // an unusable image, an ambiguous scene and a provider refusal all
            // told the user "no food found" and offered the one recovery that
            // could not help.
            "abstention" -> abstention(
                abstentionReasonFor(wire.outcome.reasonCode),
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
