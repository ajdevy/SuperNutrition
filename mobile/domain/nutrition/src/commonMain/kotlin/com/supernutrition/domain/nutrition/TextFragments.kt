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
     * The word separators carry their own surrounding spaces, which is what
     * keeps them from matching inside a word: " and " cannot occur within
     * "sandwich". An earlier version added a boundary check on top and rejected
     * every real separator, because the character before the leading space is
     * the last letter of the preceding word.
     */
    private fun separatorAt(description: String, index: Int): Int {
        if (description[index] == ',') return 1
        return SEPARATOR_WORDS
            .firstOrNull { description.startsWith(it, index, ignoreCase = true) }
            ?.length
            ?: 0
    }

    /**
     * Deliberately small and English-only. A separator list is language-specific
     * data and belongs with the per-market language matrix rather than here.
     */
    private val SEPARATOR_WORDS = listOf(" and ", " with ", " plus ", " & ")
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
    while (from < to && tokens[from] in QUANTITY_WORDS) from++
    while (to > from && tokens[to - 1] in QUANTITY_WORDS) to--
    if (from == 0 && to == tokens.size) return query
    if (from >= to) return query
    return tokens.subList(from, to).joinToString(" ")
}
