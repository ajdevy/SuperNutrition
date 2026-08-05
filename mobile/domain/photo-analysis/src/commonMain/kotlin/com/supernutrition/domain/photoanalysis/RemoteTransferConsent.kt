package com.supernutrition.domain.photoanalysis

import com.supernutrition.core.config.MarketId
import com.supernutrition.core.config.MarketPolicyRegistry
import com.supernutrition.core.config.TransferDecision
import com.supernutrition.core.config.TransferModality
import com.supernutrition.core.config.TransferRefusal
import com.supernutrition.domain.inference.isSha256Digest

/**
 * Consent for a remote transfer, as `contracts/mobile-api/v1/consent-receipt.toml`
 * defines it.
 *
 * Slice A synthesised a granted receipt so the gateway path could be exercised.
 * That is the exact shape AD-40 forbids in production — an upload the user was
 * never asked about — so the synthesis is replaced here by a decision the user
 * actually makes, on a surface that shows what is being disclosed before asking.
 *
 * Three rules the contract states and this file enforces rather than assumes:
 *
 *  - The decision is explicit. There is no default and no pre-selection, so this
 *    module offers no way to construct a granted receipt without a decision
 *    value; [TransferConsentDecision] has no default and no ordering that
 *    implies one.
 *  - A declined receipt is recorded too. Proving the user said no is as
 *    important as proving they said yes, so declining produces a receipt rather
 *    than an absence.
 *  - The receipt binds to the digest of the SCRUBBED NORMALIZED bytes. It is the
 *    bytes that will actually be transferred, not the source file, so consent
 *    cannot survive the payload changing underneath it.
 */
enum class TransferConsentDecision { GRANTED, DECLINED }

/**
 * What the surface must show before it may ask.
 *
 * The market jurisdiction and processing region are here because the contract
 * requires the user to see them at decision time (MP-7). They are resolved by
 * the policy layer and only displayed: users consent to the transfer that
 * results, they never pick provider routing.
 *
 * Locale and measurement system travel with the disclosure so they are visible
 * and correctable rather than silently defaulted (AD-40).
 */
data class RemoteTransferDisclosure(
    val marketJurisdiction: String,
    val processingRegion: String,
    val consentCopyVersion: String,
    val retentionVersion: String,
    val retentionDays: Int,
    val configurationVersion: String,
    val locale: String,
    val measurementSystem: String,
) {
    init {
        require(MARKET_PATTERN.matches(marketJurisdiction)) {
            "A disclosed market jurisdiction must be a two-letter market id"
        }
        require(processingRegion.isNotBlank()) { "A disclosed processing region must not be blank" }
        require(consentCopyVersion.isNotBlank()) { "A disclosure must name the copy version it showed" }
        require(retentionVersion.isNotBlank()) { "A disclosure must name the retention version it showed" }
        // The contract constrains this to exactly 0 in v1: AD-8 forbids durable
        // raw-photo retention, so a nonzero value is a contract change rather
        // than a configuration edit.
        require(retentionDays == 0) { "v1 discloses zero provider-side retention" }
        require(configurationVersion.isNotBlank()) { "A disclosure must name its configuration version" }
        require(locale.isNotBlank()) { "A disclosure must show the locale it will send" }
        require(measurementSystem.isNotBlank()) { "A disclosure must show the measurement system it will send" }
    }
}

/**
 * The receipt, shaped by the contract field for field.
 *
 * `processingRegion` and `consentCopyVersion` are nullable because a declined
 * receipt may carry neither — the user refused before any region was resolved.
 * A GRANTED receipt with either missing fails construction: without them there
 * is no record of what was disclosed, which is the one thing the receipt exists
 * to prove.
 */
data class TransferConsentReceipt(
    val version: String,
    val attemptId: String,
    val digest: String,
    val modality: String,
    val purpose: String,
    val decision: TransferConsentDecision,
    val marketJurisdiction: String,
    val processingRegion: String?,
    val consentCopyVersion: String?,
    val retentionVersion: String,
    val retentionDays: Int,
    val configurationVersion: String,
    val recordedAt: String,
    val expiresAt: String,
) {
    init {
        require(version == RECEIPT_VERSION) { "Consent receipt version must be $RECEIPT_VERSION" }
        require(ATTEMPT_PATTERN.matches(attemptId)) { "Consent receipt attempt id must match the contract pattern" }
        require(digest.startsWith(DIGEST_PREFIX) && isSha256Digest(digest.removePrefix(DIGEST_PREFIX))) {
            "Consent receipt digest must be sha256: followed by a lowercase SHA-256"
        }
        require(modality == PHOTO_MODALITY) { "This release records photo consent only" }
        require(purpose == FOOD_IDENTIFICATION) { "Consent purpose is a closed set" }
        require(MARKET_PATTERN.matches(marketJurisdiction)) {
            "Consent receipt market jurisdiction must be a two-letter market id"
        }
        require(retentionVersion.isNotBlank()) { "Consent receipt must name its retention version" }
        require(retentionDays == 0) { "v1 records zero provider-side retention" }
        require(configurationVersion.isNotBlank()) { "Consent receipt must name its configuration version" }
        require(recordedAt.isNotBlank()) { "Consent receipt must record when the user decided" }
        require(expiresAt.isNotBlank()) { "Consent is never open-ended" }
        if (decision == TransferConsentDecision.GRANTED) {
            require(!processingRegion.isNullOrBlank()) {
                "A granted receipt must record the processing region the user was shown"
            }
            require(!consentCopyVersion.isNullOrBlank()) {
                "A granted receipt must record the copy version the user was shown"
            }
        }
    }

    companion object {
        const val RECEIPT_VERSION: String = "1"
        const val PHOTO_MODALITY: String = "photo"
        const val FOOD_IDENTIFICATION: String = "food-identification"
        const val DIGEST_PREFIX: String = "sha256:"
    }
}

/** Why a transfer cannot even be offered. All of these forbid asking. */
enum class RemoteTransferUnavailable {
    /** No declared market, an unresolved declaration, or a lapsed legal review. */
    MARKET_UNRESOLVED,

    /** The market declares photo transfer forbidden. */
    MODALITY_FORBIDDEN,

    /** Declared permitted with no permitted processing region to send to. */
    NO_PERMITTED_REGION,

    /** The copy this surface can show is not the copy the market authorises. */
    CONSENT_COPY_MISMATCH,
}

/**
 * Whether the surface may ask at all.
 *
 * Refused is not a failure state for the product: the deterministic floor and
 * on-device identification answer under every posture, so an unresolved market
 * is a working app with no remote step rather than a broken one. The surface
 * still says so rather than silently omitting the option.
 */
sealed interface RemoteTransferGate {
    data class Askable(val disclosure: RemoteTransferDisclosure) : RemoteTransferGate

    data class Unavailable(val reason: RemoteTransferUnavailable) : RemoteTransferGate
}

/**
 * The snapshot-sourced context a disclosure is rendered from.
 *
 * Grouped rather than passed loose because these three always travel together
 * and always come from the same place — the immutable configuration snapshot.
 * Splitting them invites a caller to source one of them somewhere else, which is
 * how a locale gets silently defaulted.
 */
data class RemoteTransferContext(val configurationVersion: String, val locale: String, val measurementSystem: String)

/**
 * Turns the declared market policy into a gate, given the copy this build can
 * actually show.
 *
 * [presentedConsentCopyVersion] is passed to the registry rather than read from
 * it, because the rule is that the copy the user saw matches the copy the market
 * authorises. Reading it from the policy would compare the answer with itself.
 */
class RemoteTransferConsentPolicy(private val registry: MarketPolicyRegistry, private val retentionVersion: String) {
    fun gate(
        marketId: String,
        presentedConsentCopyVersion: String,
        context: RemoteTransferContext,
        todayEpochDay: Long,
    ): RemoteTransferGate {
        if (!MARKET_PATTERN.matches(marketId)) {
            return RemoteTransferGate.Unavailable(RemoteTransferUnavailable.MARKET_UNRESOLVED)
        }
        val decision = registry.mayTransfer(
            marketId = MarketId(marketId),
            modality = TransferModality.PHOTO,
            presentedConsentCopyVersion = presentedConsentCopyVersion,
            todayEpochDay = todayEpochDay,
        )
        return when (decision) {
            is TransferDecision.Refused -> RemoteTransferGate.Unavailable(decision.reason.toUnavailable())

            is TransferDecision.Permitted -> RemoteTransferGate.Askable(
                RemoteTransferDisclosure(
                    marketJurisdiction = marketId,
                    // The first permitted region. The user is shown where the
                    // transfer will be processed; they do not choose it.
                    processingRegion = decision.processingRegions.first(),
                    consentCopyVersion = decision.consentCopyVersion,
                    retentionVersion = retentionVersion,
                    retentionDays = 0,
                    configurationVersion = context.configurationVersion,
                    locale = context.locale,
                    measurementSystem = context.measurementSystem,
                ),
            )
        }
    }
}

private fun TransferRefusal.toUnavailable(): RemoteTransferUnavailable = when (this) {
    TransferRefusal.MARKET_UNRESOLVED -> RemoteTransferUnavailable.MARKET_UNRESOLVED
    TransferRefusal.MODALITY_FORBIDDEN -> RemoteTransferUnavailable.MODALITY_FORBIDDEN
    TransferRefusal.NO_PERMITTED_REGION -> RemoteTransferUnavailable.NO_PERMITTED_REGION
    TransferRefusal.CONSENT_COPY_MISMATCH -> RemoteTransferUnavailable.CONSENT_COPY_MISMATCH
}

/**
 * Where a decision lives: one attempt, in memory, and nowhere else.
 *
 * `contracts/interaction-policy/v1` declares `remote_consent = "attempt-scoped"`,
 * so recording a second attempt's decision discards the first and nothing is
 * written to disk. A decision that outlived its attempt would authorise a
 * transfer of bytes the user never saw.
 */
class AttemptScopedConsentStore {
    private var key: String? = null
    private var current: TransferConsentReceipt? = null

    /**
     * [attemptKey] is the attempt id the app uses, which is not the receipt's
     * `attemptId`: the contract requires `^att_[A-Za-z0-9]{16,64}$` and the
     * Android host's own attempt id is not in that shape, so the engine maps it
     * before sending. Keying the store on the app's id and letting the receipt
     * carry the contract's keeps the two from being silently conflated — which
     * would make every lookup miss and every transfer refuse for the wrong
     * reason.
     */
    fun record(attemptKey: String, receipt: TransferConsentReceipt) {
        key = attemptKey
        current = receipt
    }

    /** Whether this attempt has been asked at all, regardless of the answer. */
    fun decidedFor(attemptKey: String): Boolean = key == attemptKey && current != null

    /**
     * The receipt for this attempt and these bytes, or null.
     *
     * The digest is part of the lookup rather than checked afterwards: a caller
     * that forgets to compare would otherwise transfer a payload the receipt
     * does not cover, which is precisely the binding the contract requires.
     */
    fun receiptFor(attemptKey: String, digest: String): TransferConsentReceipt? =
        current?.takeIf { key == attemptKey && it.digest == digest }

    /** True only for a granted, digest-bound receipt. Everything else refuses. */
    fun isGrantedFor(attemptKey: String, digest: String): Boolean =
        receiptFor(attemptKey, digest)?.decision == TransferConsentDecision.GRANTED

    fun clear() {
        key = null
        current = null
    }
}

private val MARKET_PATTERN = Regex("^[A-Z]{2}$")
private val ATTEMPT_PATTERN = Regex("^att_[A-Za-z0-9]{16,64}$")
