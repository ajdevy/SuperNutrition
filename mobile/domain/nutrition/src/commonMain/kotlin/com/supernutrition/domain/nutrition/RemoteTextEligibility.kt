package com.supernutrition.domain.nutrition

/**
 * The closed set of reasons remote analysis may be offered, from AD-6.
 *
 * Closed is the point: configuration cannot invent a fourth reason, so a
 * confident local result can never be turned into an upload prompt by policy.
 */
enum class RemoteEligibilityReason { LOCAL_UNAVAILABLE, LOCAL_ABSTENTION, LOW_CONFIDENCE }

/**
 * What a market permits for typed descriptions, resolved from market policy
 * rather than from the food catalog.
 *
 * A null consent copy or processing region is not a default to fill in. It means
 * the market has not been resolved, and an unresolved market gets the local path
 * rather than the nearest provider region.
 */
data class MarketTextPolicy(
    val remoteTextPermitted: Boolean,
    val textConsentCopyVersion: String?,
    val processingRegion: String?,
)

/** Whether the user may be offered remote identification for a typed description. */
sealed interface RemoteTextDecision {
    /** The local path answered; there is nothing to escalate. */
    data object NotOffered : RemoteTextDecision

    /** The market cannot support a lawful, honest offer. */
    data object NotPermitted : RemoteTextDecision

    /**
     * The offer may be made. It carries the text-specific disclosure version and
     * the resolved region, because both must be shown before any bytes move.
     */
    data class Offer(
        val reason: RemoteEligibilityReason,
        val consentCopyVersion: String,
        val processingRegion: String,
    ) : RemoteTextDecision
}

/**
 * Decides whether remote text identification may be offered.
 *
 * This is a pure decision from the local outcome and market policy. It never
 * sends anything: transport is the gateway's concern, and keeping the decision
 * separate is what lets the rules be tested without a network.
 */
object RemoteTextEligibility {
    fun decide(localOutcome: TextEngineOutcome, market: MarketTextPolicy): RemoteTextDecision {
        val reason = when (localOutcome) {
            is TextEngineOutcome.Unavailable -> RemoteEligibilityReason.LOCAL_UNAVAILABLE

            is TextEngineOutcome.Abstained -> RemoteEligibilityReason.LOCAL_ABSTENTION

            // A local result that succeeded is a terminal answer. Escalating it
            // would widen AD-6's closed trigger set and turn every meal into an
            // upload prompt.
            is TextEngineOutcome.Segmented -> return RemoteTextDecision.NotOffered
        }

        if (!market.remoteTextPermitted) return RemoteTextDecision.NotPermitted

        // The photo disclosure states that a smaller copy of the photo is sent,
        // which is false for a description. A market without its own text copy
        // cannot borrow the photo one, so no honest offer exists.
        val consentCopyVersion = market.textConsentCopyVersion ?: return RemoteTextDecision.NotPermitted

        // The resolved jurisdiction is shown before transfer. An offer that
        // cannot name where the text goes is not an informed choice.
        val processingRegion = market.processingRegion ?: return RemoteTextDecision.NotPermitted

        return RemoteTextDecision.Offer(reason, consentCopyVersion, processingRegion)
    }
}
