package com.supernutrition.domain.nutrition

/**
 * The locale signals that may influence which dishes rank first.
 *
 * Three axes kept independent because AD-12 already separates country from
 * language, and collapsing them here would undo that. A user in Germany, typing
 * Russian, looking for a Turkish dish is the ordinary case rather than the edge
 * case, and any design that reduces these to one gets that user wrong.
 *
 * [market] is the activated country package, not a claim about where the user is
 * from. Nothing here is nationality: a device locale is not a nationality, an IP
 * address is not a home, and neither is a fact about a person (DFM-5). This type
 * carries a setting the user can see and change, and stores nothing else.
 */
data class DishLocale(val market: String? = null, val inputLanguage: String? = null) {
    companion object {
        /** No signal at all — the honest default before a user has chosen one. */
        val Unspecified = DishLocale()
    }
}

/**
 * Which markets a dish is carried by, as data rather than as a conditional.
 *
 * Empty means "not attributed to any market", which ranks neutrally rather than
 * last: an unattributed dish is missing data, not a foreign one.
 */
data class DishMarkets(val canonicalDishId: String, val markets: Set<String>)

/**
 * Reorders dish proposals by locale without removing any of them.
 *
 * Ranking, never filtering. A dish outside the active market moves down and
 * stays reachable, because someone travelling eats what is in front of them and
 * someone who emigrated did not stop eating what they grew up on. Hiding those
 * results would make the failure invisible instead of merely inconvenient
 * (DFM-3).
 *
 * The reordering is stable: proposals that rank equally keep the order the
 * deterministic catalog search gave them, so locale breaks ties rather than
 * replacing the ranking underneath it.
 */
class DishLocalePreference(attributions: List<DishMarkets>) {
    private val marketsByDish = attributions.associate { it.canonicalDishId to it.markets }

    /**
     * [proposals] reordered for [locale], carrying every input through.
     *
     * With no market set this is the identity function, which is the point: an
     * unset signal must not quietly become a signal.
     */
    fun prefer(proposals: List<DishProposal>, locale: DishLocale): List<DishProposal> {
        val market = locale.market ?: return proposals
        return proposals.sortedByDescending { proposal ->
            val markets = marketsByDish[proposal.canonicalDishId].orEmpty()
            when {
                market in markets -> CARRIED_BY_MARKET
                markets.isEmpty() -> UNATTRIBUTED
                else -> CARRIED_ELSEWHERE
            }
        }
    }
}

// Ordering weights only. An unattributed dish sits between a local one and one
// attributed elsewhere, because missing attribution is missing data rather than
// evidence that the dish is foreign.
private const val CARRIED_BY_MARKET = 2
private const val UNATTRIBUTED = 1
private const val CARRIED_ELSEWHERE = 0
