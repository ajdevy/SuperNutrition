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

/**
 * One resolved search hit.
 *
 * [unmatchedPreparation] names preparation words the query used that this record
 * does not carry — "grilled" against a record that only exists raw. The hit is
 * still returned, because the food is right and refusing to answer helps nobody,
 * but the word is never dropped in silence: preparation moves nutrition (raw and
 * cooked chicken differ per 100g through water loss), so a result that quietly
 * ignored it would hand back numbers for a food the user did not eat.
 */
data class CatalogSearchResult(
    val canonicalItemId: String,
    val unmatchedPreparation: List<String> = emptyList(),
    val strength: MatchStrength = MatchStrength.WEAKEST,
)

/**
 * How well a query matched, comparable across different record sets.
 *
 * Exposed because foods and dishes are searched separately and shown in one
 * list: without a shared scale the merge would need a precedence rule — dishes
 * always first, or foods always first — and both are wrong. "chicken" should
 * reach the food and "chicken curry" the dish, which falls out of match
 * strength and does not fall out of any fixed ordering.
 *
 * Tier dominates coverage deliberately. An exact match on a short name beats a
 * partial match on a long one, which is the same first-tier-wins rule the
 * catalog already applies within one record set.
 */
class MatchStrength internal constructor(internal val tier: MatchTier, internal val coverage: Int) :
    Comparable<MatchStrength> {
    // Deliberately not a data class: that would generate a public copy() and
    // componentN() exposing MatchTier, which is an implementation detail of how
    // matching works. Callers need to compare strengths, not read them apart.
    override fun compareTo(other: MatchStrength): Int =
        // Tiers are declared strongest-first, so a lower ordinal is a better match.
        compareValuesBy(other, this, { it.tier.ordinal }, { -it.coverage })

    override fun equals(other: Any?): Boolean =
        other is MatchStrength && tier == other.tier && coverage == other.coverage

    override fun hashCode(): Int = 31 * tier.hashCode() + coverage

    override fun toString(): String = "MatchStrength($tier, $coverage)"

    /** True when both matched at the same tier, whatever their coverage. */
    fun sameTier(other: MatchStrength): Boolean = tier == other.tier

    companion object {
        /** Weaker than any real match; the default before a search has run. */
        val WEAKEST: MatchStrength = MatchStrength(MatchTier.FUZZY, 0)
    }
}

/**
 * Deterministic catalog search over an in-memory record set.
 *
 * Ranking carries no personalization, history, or learned component, so the
 * same query against the same records always yields the same order.
 */
class CatalogSearch(records: List<CatalogRecord>) {
    private val indexed = records.map(::IndexedRecord)

    /**
     * Three attempts, weakest last, so a stronger reading always wins outright.
     *
     * Note the difference between the two relaxations. Quantity words are
     * *removed*: "two" tells the catalog nothing and the amount is asked for
     * again at serving confirmation. Preparation words are *kept and reported*:
     * "grilled" is part of what the food is, so dropping it silently would be a
     * wrong answer rather than a cleaned query.
     */
    fun search(query: String): List<CatalogSearchResult> {
        val direct = searchExact(query)
        if (direct.isNotEmpty()) return direct

        // People type "two boiled eggs", not "egg, boiled". Every tier requires
        // all query tokens to be satisfied by one record, so a single counting
        // word makes the fragment unresolvable. Retry without them, second so
        // that a food whose real name contains such a word still wins outright.
        val stripped = stripQuantityWords(query)
        val withoutQuantity = if (stripped == query) emptyList() else searchExact(stripped)
        if (withoutQuantity.isNotEmpty()) return withoutQuantity

        return searchRelaxingPreparation(stripped)
    }

    /**
     * Last resort: match on the food alone and report the preparation that did
     * not land. Only reached when the query failed as written, so a catalog that
     * carries the preparation — as a licensed package will — never gets here.
     */
    private fun searchRelaxingPreparation(query: String): List<CatalogSearchResult> {
        val tokens = tokenizeNormalized(normalizeForSearch(query))
        val preparation = tokens.filter { it in PREPARATION_WORDS }
        val food = tokens.filterNot { it in PREPARATION_WORDS }
        if (preparation.isEmpty() || food.isEmpty()) return emptyList()

        return searchExact(food.joinToString(" ")).map { hit ->
            val record = indexed.first { it.canonicalItemId == hit.canonicalItemId }
            hit.copy(unmatchedPreparation = preparation.filterNot { record.mentions(it) })
        }
    }

    private fun searchExact(query: String): List<CatalogSearchResult> {
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
                        compareByDescending<IndexedRecord> { it.coverageOf(tier, normalizedQuery, queryTokens) }
                            .thenBy { it.canonicalItemId },
                    )
                    .map {
                        CatalogSearchResult(
                            canonicalItemId = it.canonicalItemId,
                            strength = MatchStrength(tier, it.coverageOf(tier, normalizedQuery, queryTokens)),
                        )
                    }
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

    /** Whether any searchable form of this record uses [token]. */
    fun mentions(token: String): Boolean = formTokens.any { form -> form.any { it == token } }

    val normalizedName: String get() = searchableForms.first()
    val tokens: List<String> get() = formTokens.first()

    /**
     * How much of this record the query accounted for, as a permille integer so
     * ordering never depends on floating-point comparison. A record the query
     * describes completely outranks a more qualified variant of the same food.
     *
     * Scored only on forms that actually satisfied [tier]. Taking the best over
     * every form scored a record on a name that never matched: a record whose
     * synonyms include the bare word `rice` was credited with covering it in
     * full even when the query won the tier through a long display name, so a
     * qualified variant outranked the staple — the inverse of the ranking rule
     * this exists to implement.
     */
    fun coverageOf(tier: MatchTier, query: String, queryTokens: List<String>): Int {
        var best = 0
        forEachForm { formName, formTokens ->
            if (formTokens.isNotEmpty() && tier.matchesForm(query, queryTokens, formName, formTokens)) {
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
    entry.anyForm { formName, formTokens -> matchesForm(query, tokens, formName, formTokens) }

/**
 * Whether one specific form satisfies this tier. Separate from [matches] because
 * ranking needs to know *which* form matched, and collapsing that to a boolean is
 * what let a record be scored on a name the query never reached.
 */
internal fun MatchTier.matchesForm(
    query: String,
    tokens: List<String>,
    formName: String,
    formTokens: List<String>,
): Boolean = when (this) {
    MatchTier.EXACT -> formName == query
    MatchTier.TOKEN_SUBSET -> tokens.all { it in formTokens }
    MatchTier.PREFIX -> tokens.all { token -> formTokens.any { it.startsWith(token) } }
    MatchTier.FUZZY -> tokens.all { token -> formTokens.any { isNearMatch(token, it) } }
}

/**
 * Bounded near-match. The allowance scales with token length because a fixed
 * allowance is meaningless on short tokens: at three characters, distance one
 * reaches `ray`, `rice`, `rum` and `dry` from `rye`, which is not a filter at
 * all. Short tokens therefore get no fuzzy allowance.
 *
 * The allowance is keyed on the *shorter* of the two tokens, not on the query
 * alone. Keying it on the query let a four-character query reach a
 * three-character record at distance one, which is the same unbounded behaviour
 * measured from the other end: `ribs` reached `ris`, the Latin-script entry for
 * рис, and proposed white rice to someone typing ribs.
 *
 * Plurals are matched as morphology rather than as edit distance. They are not
 * near-misses — they are the same word — and the language matrix puts English
 * at "none beyond plurals", so folding them here is what lets the fuzzy tier be
 * tightened without taking the commonest English case with it.
 */
internal fun isNearMatch(queryToken: String, recordToken: String): Boolean {
    if (queryToken == recordToken) return true
    if (isPluralPair(queryToken, recordToken)) return true
    val allowance = when {
        minOf(queryToken.length, recordToken.length) < FUZZY_MIN_TOKEN_LENGTH -> return false
        minOf(queryToken.length, recordToken.length) < FUZZY_LONG_TOKEN_LENGTH -> 1
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

/**
 * English plural folding, applied symmetrically so it does not matter which
 * side carries the plural: a query may be plural against a singular record name
 * or the reverse.
 */
internal fun isPluralPair(left: String, right: String): Boolean = isPluralOf(left, right) || isPluralOf(right, left)

private fun isPluralOf(plural: String, singular: String): Boolean =
    singular.isNotEmpty() && (plural == singular + "s" || plural == singular + "es")

/**
 * Words naming how a food was prepared.
 *
 * Unlike [QUANTITY_WORDS] these are never stripped. Preparation discriminates
 * between real records — "Egg, boiled" and a raw egg are different foods with
 * different numbers — so a query naming one is asking for something specific.
 * They earn a relaxation rather than a deletion: match the food, report the
 * preparation that the catalog could not honour, and let the user decide.
 *
 * English-only, on the same terms as the other search vocabularies: Russian
 * ships in E7.3, and all of them belong in the per-market language matrix as
 * data rather than as constants here.
 */
internal val PREPARATION_WORDS: Set<String> = setOf(
    "raw", "fresh", "cooked", "boiled", "poached", "steamed",
    "grilled", "griddled", "roasted", "baked", "fried", "seared", "sauteed",
    "toasted", "smoked", "cured", "pickled", "dried", "frozen", "canned",
)

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
