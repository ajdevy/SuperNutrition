package com.supernutrition.domain.nutrition

/** One canonical catalog record a query can resolve to. */
data class CatalogRecord(val canonicalItemId: String, val displayName: String)

/** One resolved search hit. */
data class CatalogSearchResult(val canonicalItemId: String)

/**
 * Deterministic catalog search over an in-memory record set.
 *
 * Ranking carries no personalization, history, or learned component, so the
 * same query against the same records always yields the same order.
 */
class CatalogSearch(records: List<CatalogRecord>) {
    private val indexed = records.map(::IndexedRecord)

    fun search(query: String): List<CatalogSearchResult> {
        val normalizedQuery = normalizeForSearch(query)
        val queryTokens = tokenizeNormalized(normalizedQuery)
        if (queryTokens.isEmpty()) return emptyList()

        for (tier in MatchTier.entries) {
            val hits = indexed.filter { tier.matches(normalizedQuery, queryTokens, it) }
            if (hits.isNotEmpty()) return hits.map { CatalogSearchResult(it.canonicalItemId) }
        }
        return emptyList()
    }
}

/**
 * Match tiers, most specific first. The first tier with any hit takes the
 * result set outright, so a weaker tier never dilutes a confident match.
 *
 * Ordering matters: a complete token is also a prefix of itself, so
 * TOKEN_SUBSET must precede PREFIX or it can never match anything of its own.
 */
internal enum class MatchTier { EXACT, TOKEN_SUBSET, PREFIX }

/** A record with its normalized form and tokens computed once, not per tier. */
internal class IndexedRecord(record: CatalogRecord) {
    val canonicalItemId: String = record.canonicalItemId
    val normalizedName: String = normalizeForSearch(record.displayName)
    val tokens: List<String> = tokenizeNormalized(normalizedName)
}

internal fun MatchTier.matches(query: String, tokens: List<String>, entry: IndexedRecord): Boolean = when (this) {
    MatchTier.EXACT -> entry.normalizedName == query
    MatchTier.TOKEN_SUBSET -> tokens.all { it in entry.tokens }
    MatchTier.PREFIX -> tokens.all { token -> entry.tokens.any { it.startsWith(token) } }
}

/**
 * Case- and diacritic-folded form used identically for index entries and
 * queries, so the two can never drift. Kotlin common has no Unicode normalizer,
 * so the fold is an explicit table rather than a platform call.
 */
internal fun normalizeForSearch(value: String): String {
    val expanded = value.lowercase().replace("ß", "ss").replace("æ", "ae").replace("œ", "oe")
    return buildString(expanded.length) {
        for (character in expanded) {
            val index = ACCENTED_CHARACTERS.indexOf(character)
            append(if (index >= 0) PLAIN_CHARACTERS[index] else character)
        }
    }
}

/**
 * Splits already-normalized text on anything that is not a letter or digit.
 * Deliberately avoids `Regex` so tokenization cannot differ between the Kotlin
 * platforms, which the byte-identical ordering requirement depends on.
 */
internal fun tokenizeNormalized(normalized: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    for (character in normalized) {
        if (character.isLetterOrDigit()) {
            current.append(character)
        } else if (current.isNotEmpty()) {
            tokens += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

private const val ACCENTED_CHARACTERS = "áàâäãåāéèêëēíìîïīóòôöõøōúùûüūñńçćšžýÿđł"
private const val PLAIN_CHARACTERS = "aaaaaaaeeeeeiiiiiooooooouuuuunnccszyydl"
