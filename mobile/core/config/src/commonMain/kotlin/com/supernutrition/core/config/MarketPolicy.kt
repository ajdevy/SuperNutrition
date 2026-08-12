package com.supernutrition.core.config

/**
 * A market's processing position, as declared by `contracts/market-policy/v1`.
 *
 * The type that was missing. `MarketPolicyResolver` previously copied four
 * fields off a `ConfigurationSnapshot` and returned them — no policy lookup, no
 * approver, no expiry — and `ConfigurationSnapshot` requires its jurisdiction
 * and region non-blank, so the contract's default state was not representable at
 * all. A default that cannot be expressed is a default nothing can fail closed
 * to.
 *
 * Nothing here is inferred. A market absent from the registry, one declared
 * unresolved, and one whose legal review has expired are the same answer, which
 * is the rule the contract exists to hold: a posture must never arrive because a
 * market resembles a neighbour, shares a language, or sits in the same billing
 * region.
 */
data class MarketId(val value: String) {
    init {
        require(value.isNotBlank()) { "A market id must not be blank" }
    }
}

enum class ResidencyPosture { UNRESOLVED, IN_COUNTRY_ONLY, PERMITTED_WITH_REGION, PROHIBITED }

/** Declared per modality, because a description and a photo carry different content. */
enum class RemotePermission { FORBIDDEN, PERMITTED }

enum class TransferModality { PHOTO, TEXT }

/**
 * One market's declared position. Every field is required, including the two the
 * contract insists on and reviewers most often omit: who approved it, and when
 * that approval lapses.
 */
data class MarketPolicy(
    val marketId: MarketId,
    val jurisdiction: String,
    val residencyPosture: ResidencyPosture,
    val permittedProcessingRegions: List<String>,
    val photoRemotePermission: RemotePermission,
    val textRemotePermission: RemotePermission,
    val photoConsentCopyVersion: String?,
    val textConsentCopyVersion: String?,
    val acceptedInputLanguages: List<String>,
    val legalReviewApprover: String,
    val legalReviewExpiresOnEpochDay: Long,
) {
    init {
        require(jurisdiction.isNotBlank()) { "A declared market must name its jurisdiction" }
        require(acceptedInputLanguages.isNotEmpty()) { "A market must accept at least one input language" }
        if (residencyPosture != ResidencyPosture.UNRESOLVED) {
            require(legalReviewApprover.isNotBlank()) {
                "A market with a posture must name the human who approved it"
            }
            require(legalReviewExpiresOnEpochDay > 0) { "An approval that never expires is not a review" }
        }
        // A permission with no reviewed copy behind it authorises nothing, so it
        // is a contradiction rather than a permissive setting.
        if (photoRemotePermission == RemotePermission.PERMITTED) {
            require(!photoConsentCopyVersion.isNullOrBlank()) {
                "Photo transfer is permitted with no reviewed copy to authorise it"
            }
        }
        if (textRemotePermission == RemotePermission.PERMITTED) {
            require(!textConsentCopyVersion.isNullOrBlank()) {
                "Text transfer is permitted with no reviewed copy to authorise it"
            }
        }
        // One modality's copy cannot be the other's. The photo disclosure says a
        // smaller copy of the photo is sent, which is false for a description.
        if (!photoConsentCopyVersion.isNullOrBlank()) {
            require(photoConsentCopyVersion != textConsentCopyVersion) {
                "One consent copy cannot authorise both modalities"
            }
        }
    }
}

/** Why a market has no usable position. All three forbid transfer identically. */
enum class UnresolvedReason { ABSENT, DECLARED_UNRESOLVED, REVIEW_EXPIRED }

sealed interface MarketResolution {
    data class Resolved(val policy: MarketPolicy) : MarketResolution

    data class Unresolved(val reason: UnresolvedReason) : MarketResolution
}

/** Whether content of one modality may leave the device for this market. */
sealed interface TransferDecision {
    data class Permitted(val processingRegions: List<String>, val consentCopyVersion: String) : TransferDecision

    data class Refused(val reason: TransferRefusal) : TransferDecision
}

enum class TransferRefusal {
    MARKET_UNRESOLVED,

    /**
     * Distinct from MARKET_UNRESOLVED on purpose. "Unresolved" invites someone
     * to go and resolve it; prohibited is a decision that has already been
     * made, and collapsing the two would lose that.
     */
    MARKET_PROHIBITED,
    MODALITY_FORBIDDEN,
    NO_PERMITTED_REGION,
    CONSENT_COPY_MISMATCH,
}

/**
 * The declared markets, and the only way to ask about one.
 *
 * Empty until a named approver supplies a market, which is why
 * [MarketPolicyRegistry.unresolved] is the shipped value. The product works in
 * that state: the deterministic floor and on-device identification are available
 * under every posture, so an unresolved market is a working app rather than a
 * broken one.
 */
class MarketPolicyRegistry(policies: List<MarketPolicy>) {
    val policies: List<MarketPolicy> = policies.toList()

    init {
        val ids = policies.map { it.marketId }
        require(ids.toSet().size == ids.size) {
            "A market may hold one position; two declarations need one reconciled answer"
        }
    }

    /**
     * Fails closed, and treats absence, an unresolved declaration, and a lapsed
     * approval as the same answer. [todayEpochDay] is supplied rather than read,
     * because this module has no clock and an expiry check that invents its own
     * "now" is not auditable.
     */
    fun resolve(marketId: MarketId, todayEpochDay: Long): MarketResolution {
        val policy = policies.firstOrNull { it.marketId == marketId }
            ?: return MarketResolution.Unresolved(UnresolvedReason.ABSENT)
        if (policy.residencyPosture == ResidencyPosture.UNRESOLVED) {
            return MarketResolution.Unresolved(UnresolvedReason.DECLARED_UNRESOLVED)
        }
        if (policy.legalReviewExpiresOnEpochDay <= todayEpochDay) {
            return MarketResolution.Unresolved(UnresolvedReason.REVIEW_EXPIRED)
        }
        return MarketResolution.Resolved(policy)
    }

    /**
     * Whether a transfer may proceed, given the consent copy the surface actually
     * presented.
     *
     * [presentedConsentCopyVersion] is an argument rather than something read off
     * the policy, because the rule being enforced is that the copy shown to the
     * user matches the copy this modality is authorised by. Reading it from the
     * policy would compare the answer with itself and pass always — which is how
     * the photo disclosure could otherwise authorise a text transfer.
     */
    fun mayTransfer(
        marketId: MarketId,
        modality: TransferModality,
        presentedConsentCopyVersion: String,
        todayEpochDay: Long,
    ): TransferDecision {
        val policy = when (val resolution = resolve(marketId, todayEpochDay)) {
            is MarketResolution.Unresolved -> return TransferDecision.Refused(TransferRefusal.MARKET_UNRESOLVED)
            is MarketResolution.Resolved -> resolution.policy
        }
        // The posture is checked before the modality permission, because it
        // overrides it. resolve() rejects only UNRESOLVED and lapsed reviews, so
        // PROHIBITED came back Resolved and this function then asked only
        // whether the modality was permitted — meaning a market whose declared
        // legal position is "content may not leave" could authorise a photo
        // upload. A per-modality permission grants within a posture; it cannot
        // grant against one.
        if (policy.residencyPosture == ResidencyPosture.PROHIBITED) {
            return TransferDecision.Refused(TransferRefusal.MARKET_PROHIBITED)
        }
        val permission = when (modality) {
            TransferModality.PHOTO -> policy.photoRemotePermission
            TransferModality.TEXT -> policy.textRemotePermission
        }
        if (permission != RemotePermission.PERMITTED) {
            return TransferDecision.Refused(TransferRefusal.MODALITY_FORBIDDEN)
        }
        val authorising = when (modality) {
            TransferModality.PHOTO -> policy.photoConsentCopyVersion
            TransferModality.TEXT -> policy.textConsentCopyVersion
        }
        if (authorising != presentedConsentCopyVersion) {
            return TransferDecision.Refused(TransferRefusal.CONSENT_COPY_MISMATCH)
        }
        // Blank entries are not regions. An empty list already failed closed; a
        // list of blank strings did not, because it is non-empty — so a transfer
        // could be permitted to a region named "".
        val regions = policy.permittedProcessingRegions.filter { it.isNotBlank() }
        if (regions.isEmpty()) {
            return TransferDecision.Refused(TransferRefusal.NO_PERMITTED_REGION)
        }
        return TransferDecision.Permitted(regions, authorising)
    }

    /**
     * Accepted input languages, which do not depend on the posture in either
     * direction. Supporting a language is a product capability; operating in a
     * market is a legal position. A market with no declaration accepts none —
     * which is a statement about the matrix, not about whether the app works
     * there, since the floor answers under every posture.
     */
    fun acceptedInputLanguages(marketId: MarketId): List<String> =
        policies.firstOrNull { it.marketId == marketId }?.acceptedInputLanguages.orEmpty()

    companion object {
        /**
         * The shipped registry. Empty until `contracts/market-policy/v1` gains a
         * market with a named approver and an expiry date, which is a legal
         * decision and not a code change.
         */
        val unresolved: MarketPolicyRegistry = MarketPolicyRegistry(emptyList())
    }
}
