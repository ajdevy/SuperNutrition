package com.supernutrition.domain.nutrition

/** One food-sized piece of a description, and the words it came from. */
data class TextFragment(val text: String, val span: TextSpan)

/**
 * Splits a typed description into food-sized fragments.
 *
 * Shared by the deterministic engine and by the floor fallback. They previously
 * disagreed: the engine segmented and the fallback did not, so an unavailable
 * engine turned a two-item meal into no result at all. One implementation means
 * they cannot drift again.
 */
object TextFragments {
    fun of(description: String): List<TextFragment> {
        val fragments = mutableListOf<TextFragment>()
        var start = 0
        var index = 0

        fun emit(endExclusive: Int) {
            var from = start
            var to = endExclusive
            while (from < to && description[from].isWhitespace()) from++
            while (to > from && description[to - 1].isWhitespace()) to--
            if (from < to) {
                fragments += TextFragment(description.substring(from, to), TextSpan(from, to))
            }
        }

        while (index < description.length) {
            val separator = separatorAt(description, index)
            if (separator > 0) {
                emit(index)
                index += separator
                start = index
            } else {
                index++
            }
        }
        emit(description.length)
        return fragments
    }

    /**
     * Length of the separator starting at [index], or 0 if none starts there.
     *
     * Conjunctions are matched as whole words between runs of whitespace rather
     * than as literals carrying their own spaces. The literal form only matched
     * a single space on each side, so "boiled egg\nand rye bread" separated
     * nowhere and "and" leaked into the query as a token no record satisfies,
     * taking the whole description down with it. Matching the word between
     * whitespace runs keeps the property the literals were there for — " and "
     * cannot occur inside "sandwich" — without being fooled by which whitespace
     * character the user typed.
     */
    private fun separatorAt(description: String, index: Int): Int {
        if (description[index] == ',') return 1
        if (!description[index].isWhitespace()) return 0

        val wordStart = skipWhitespace(description, index)
        var wordEnd = wordStart
        while (wordEnd < description.length && !description[wordEnd].isWhitespace()) wordEnd++
        if (description.substring(wordStart, wordEnd).lowercase() in CONJUNCTIONS) {
            return skipWhitespace(description, wordEnd) - index
        }

        // A line break separates on its own: a list typed one item per line is
        // still a list, and nothing else in the description says so.
        return if (description.substring(index, wordStart).any { it == '\n' }) wordStart - index else 0
    }

    private fun skipWhitespace(description: String, from: Int): Int {
        var at = from
        while (at < description.length && description[at].isWhitespace()) at++
        return at
    }

    /**
     * Deliberately small and English-only. A separator list is language-specific
     * data and belongs with the per-market language matrix rather than here.
     */
    private val CONJUNCTIONS = setOf("and", "with", "plus", "&")
}

/**
 * Words that quantify a food rather than name it.
 *
 * People type "two boiled eggs and a slice of rye toast", not "egg, boiled".
 * Every catalog tier requires all query tokens to be satisfied by one record, so
 * a single counting word makes the whole fragment unresolvable — which is why
 * the spec's own success signal returned nothing.
 *
 * Stripping these is a search concern, not a catalog one: the amounts they
 * express belong to serving confirmation, which asks for them explicitly.
 *
 * English-only, like [TextFragments]' separators, and for the same reason: the
 * beta is English-first and Russian ships in E7.3. Note what that story needs
 * here, because a Russian quantity list alone would not deliver it — the
 * language matrix declares Russian as full lemmatization across cases, so
 * "два вареных яйца" misses on the inflected *вареных* even after *два* is
 * stripped. Both lists are ultimately matrix data versioned with the catalog
 * package rather than constants in search code, which is also why neither is
 * worth extending one word at a time.
 */
internal val QUANTITY_WORDS: Set<String> = setOf(
    "a", "an", "the", "some", "of",
    "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "half", "quarter",
    "slice", "slices", "piece", "pieces", "serving", "servings", "portion", "portions",
    "cup", "cups", "bowl", "bowls", "plate", "plates", "glass", "glasses",
    "gram", "grams", "g", "kg", "ml", "l", "oz", "lb",
    "tbsp", "tsp", "tablespoon", "tablespoons", "teaspoon", "teaspoons",
)

/**
 * Drops leading and trailing quantity words, keeping the food name between them.
 *
 * Only the edges are trimmed. A quantity word in the middle of a name is part of
 * that name — "half and half" is a food, not half of a food — and removing
 * interior words would corrupt records rather than clean queries.
 */
internal fun stripQuantityWords(query: String): String {
    val tokens = tokenizeNormalized(normalizeForSearch(query))
    var from = 0
    var to = tokens.size
    while (from < to && isQuantityToken(tokens[from])) from++
    while (to > from && isQuantityToken(tokens[to - 1])) to--
    if (from == 0 && to == tokens.size) return query
    if (from >= to) return query
    return tokens.subList(from, to).joinToString(" ")
}

/**
 * Whether a token quantifies rather than names.
 *
 * Digits are handled as a token class rather than as list entries, which is the
 * difference between a rule and an endless vocabulary. Without this, "2 boiled
 * eggs" resolved to nothing while "two boiled eggs" resolved fine — a
 * distinction no user would predict, and one keystroke from the spec's own
 * success signal. A leading digit also halted the strip loop, so every quantity
 * word behind it survived too.
 */
private fun isQuantityToken(token: String): Boolean {
    if (token in QUANTITY_WORDS) return true
    if (token.isNotEmpty() && token.all(Char::isDigit)) return true
    // "200g", "500ml": a number glued to its unit is still one quantity.
    val digits = token.takeWhile(Char::isDigit)
    return digits.isNotEmpty() && token.drop(digits.length) in QUANTITY_UNITS
}

private val QUANTITY_UNITS: Set<String> = setOf("g", "kg", "mg", "ml", "l", "oz", "lb")
