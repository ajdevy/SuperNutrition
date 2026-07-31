package com.supernutrition.domain.nutrition

/**
 * One canonical catalog record a query can resolve to.
 *
 * [alternateNames] carries regional variants, alternate spellings, and
 * cross-language names for the same food. They are matched but never displayed:
 * someone typing `aubergine` and someone typing `баклажан` reach one record, and
 * both then read the catalog's own name for it rather than a translation the
 * catalog cannot vouch for.
 */
data class CatalogRecord(
    val canonicalItemId: String,
    val displayName: String,
    val alternateNames: List<String> = emptyList(),
)

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
            if (hits.isNotEmpty()) {
                // Order from the data, never from catalog construction order:
                // two packages built from the same records in a different
                // sequence must answer the same query identically. Coverage
                // carries the meaning; canonical id only breaks remaining ties.
                return hits
                    .sortedWith(
                        compareByDescending<IndexedRecord> { it.coverageOf(queryTokens) }
                            .thenBy { it.canonicalItemId },
                    )
                    .map { CatalogSearchResult(it.canonicalItemId) }
            }
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
internal enum class MatchTier { EXACT, TOKEN_SUBSET, PREFIX, FUZZY }

/**
 * A record with its searchable forms computed once, not per tier.
 *
 * Every name a record answers to is indexed as a separate searchable form, so a
 * regional variant matches at the same tier its display name would. Flattening
 * synonyms into one token bag instead would let a query match a word from the
 * display name and a word from a synonym simultaneously, which is a match no
 * human wrote.
 */
internal class IndexedRecord(record: CatalogRecord) {
    val canonicalItemId: String = record.canonicalItemId
    private val searchableForms: List<String> =
        (listOf(record.displayName) + record.alternateNames).map(::normalizeForSearch)
    private val formTokens: List<List<String>> = searchableForms.map(::tokenizeNormalized)

    /** True when any single searchable form satisfies [predicate]. */
    fun anyForm(predicate: (normalized: String, tokens: List<String>) -> Boolean): Boolean =
        searchableForms.indices.any { predicate(searchableForms[it], formTokens[it]) }

    val normalizedName: String get() = searchableForms.first()
    val tokens: List<String> get() = formTokens.first()

    /**
     * How much of this record the query accounted for, as a permille integer so
     * ordering never depends on floating-point comparison. A record the query
     * describes completely outranks a more qualified variant of the same food.
     *
     * Measured against the best-covered searchable form, not the display name.
     * A query that matched a regional variant must be scored on the name it
     * actually matched; scoring it against the display name would rank a
     * perfect synonym match by how little of an unrelated word it covered.
     */
    fun coverageOf(queryTokens: List<String>): Int {
        var best = 0
        forEachForm { _, formTokens ->
            if (formTokens.isNotEmpty()) {
                val accounted = formTokens.count { recordToken ->
                    queryTokens.any { recordToken.startsWith(it) || isNearMatch(it, recordToken) }
                }
                val coverage = accounted * COVERAGE_SCALE / formTokens.size
                if (coverage > best) best = coverage
            }
        }
        return best
    }

    private inline fun forEachForm(action: (String, List<String>) -> Unit) {
        for (index in searchableForms.indices) action(searchableForms[index], formTokens[index])
    }
}

private const val COVERAGE_SCALE = 1000

internal fun MatchTier.matches(query: String, tokens: List<String>, entry: IndexedRecord): Boolean =
    entry.anyForm { formName, formTokens ->
        when (this) {
            MatchTier.EXACT -> formName == query
            MatchTier.TOKEN_SUBSET -> tokens.all { it in formTokens }
            MatchTier.PREFIX -> tokens.all { token -> formTokens.any { it.startsWith(token) } }
            MatchTier.FUZZY -> tokens.all { token -> formTokens.any { isNearMatch(token, it) } }
        }
    }

/**
 * Bounded near-match. The allowance scales with token length because a fixed
 * allowance is meaningless on short tokens: at three characters, distance one
 * reaches `ray`, `rice`, `rum` and `dry` from `rye`, which is not a filter at
 * all. Short tokens therefore get no fuzzy allowance.
 */
internal fun isNearMatch(queryToken: String, recordToken: String): Boolean {
    val allowance = when {
        queryToken.length < FUZZY_MIN_TOKEN_LENGTH -> return queryToken == recordToken
        queryToken.length < FUZZY_LONG_TOKEN_LENGTH -> 1
        else -> 2
    }
    if (recordToken.length - queryToken.length > allowance) return false
    if (queryToken.length - recordToken.length > allowance) return false
    return editDistance(queryToken, recordToken) <= allowance
}

/**
 * Levenshtein distance over a single rolling row. Iterates only indexed
 * sequences, never a hash-ordered collection, so the result is identical on
 * every Kotlin target.
 */
internal fun editDistance(left: String, right: String): Int {
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    for (i in 1..left.length) {
        current[0] = i
        for (j in 1..right.length) {
            val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
            val deletion = previous[j] + 1
            val insertion = current[j - 1] + 1
            current[j] = minOf(substitution, deletion, insertion)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}

private const val FUZZY_MIN_TOKEN_LENGTH = 4
private const val FUZZY_LONG_TOKEN_LENGTH = 8

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
