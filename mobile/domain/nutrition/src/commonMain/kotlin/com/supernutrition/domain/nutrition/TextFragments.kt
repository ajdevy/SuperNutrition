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
     * A span shrunk to the words it names, dropping the separators that joined
     * it to its neighbours. Null when it names no words at all.
     *
     * Owned here because this is where separator knowledge lives. An earlier
     * version of `SegmentationReview.split` shared only the conjunction list and
     * kept its own whitespace trimming, which meant it agreed with [separatorAt]
     * about "and" and disagreed about ",". The result was a half whose
     * provenance read "chicken," — a span this segmenter would never produce.
     * Sharing one list and re-implementing the rest is the drift AGENTS.md warns
     * about, one level down.
     *
     * The loop runs to a fixed point because separators combine: "chicken , and
     * rice" joins with a comma *and* a conjunction, and trimming either one
     * exposes the other.
     */
    internal fun trimSeparators(description: String, start: Int, end: Int): TextSpan? {
        var from = start
        var to = end
        var previousFrom = -1
        var previousTo = -1
        while (from < to && (from != previousFrom || to != previousTo)) {
            previousFrom = from
            previousTo = to
            from = skipSeparatorsForward(description, from, to)
            to = skipSeparatorsBackward(description, from, to)
            from = skipLeadingConjunction(description, from, to)
            to = skipTrailingConjunction(description, from, to)
        }
        return if (from < to) TextSpan(from, to) else null
    }

    /**
     * Splits a fragment on the weak separators, for a caller that has already
     * failed to resolve it whole. Returns the fragment unchanged when it holds
     * none, so a caller can treat "no split available" and "split changed
     * nothing" identically.
     *
     * Spans stay absolute so each part still indexes the original description —
     * a part whose span pointed into the fragment would show the user
     * provenance from the wrong offset.
     */
    internal fun splitOnWeakSeparators(fragment: TextFragment): List<TextFragment> {
        val text = fragment.text
        val base = fragment.span.start
        val parts = mutableListOf<TextFragment>()
        var start = 0
        var index = 0

        fun emit(endExclusive: Int) {
            val trimmed = trimSeparators(text, start, endExclusive) ?: return
            parts += TextFragment(
                text = text.substring(trimmed.start, trimmed.end),
                span = TextSpan(base + trimmed.start, base + trimmed.end),
            )
        }

        while (index < text.length) {
            val width = weakSeparatorAt(text, index)
            if (width > 0) {
                emit(index)
                index += width
                start = index
            } else {
                index++
            }
        }
        emit(text.length)
        return if (parts.isEmpty()) listOf(fragment) else parts
    }

    private fun weakSeparatorAt(text: String, index: Int): Int {
        if (!text[index].isWhitespace()) return 0
        val wordStart = skipWhitespace(text, index)
        var wordEnd = wordStart
        while (wordEnd < text.length && !text[wordEnd].isWhitespace()) wordEnd++
        if (text.substring(wordStart, wordEnd).lowercase() in WEAK_SEPARATORS) {
            return skipWhitespace(text, wordEnd) - index
        }
        return 0
    }

    /**
     * Whether this position separates two foods rather than sitting inside one.
     *
     * The rule a surface must respect when it offers a split: a cut through the
     * middle of a word hands each half a fragment of it.
     */
    internal fun separatesWords(description: String, at: Int): Boolean =
        (at > 0 && isSeparatorCharacter(description[at - 1])) ||
            (at < description.length && isSeparatorCharacter(description[at]))
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
    "gram", "grams", "g", "mg", "kg", "ml", "l", "oz", "lb",
    "tbsp", "tsp", "tablespoon", "tablespoons", "teaspoon", "teaspoons",
    // Russian. Numerals appear in several cases because a query is not a
    // sentence and the user types whichever form they were thinking in.
    "один", "одна", "два", "две", "три", "четыре", "пять", "шесть", "семь",
    "восемь", "девять", "десять", "пол", "половина", "пара",
    "грамм", "грамма", "граммов", "г", "мг", "кг", "мл", "л",
    "штука", "штуки", "шт", "ломтик", "ломтика", "ломтики",
    "кусок", "куска", "куски", "порция", "порции", "стакан", "стакана",
    "ложка", "ложки", "ст", "ч",
    // Spanish.
    "un", "una", "unos", "unas", "dos", "tres", "cuatro", "cinco", "seis",
    "siete", "ocho", "nueve", "diez", "medio", "media", "mitad",
    "gramo", "gramos", "kilo", "kilos", "litro", "litros", "mililitro", "mililitros",
    "rebanada", "rebanadas", "rodaja", "rodajas", "trozo", "trozos",
    "porcion", "porciones", "taza", "tazas", "plato", "platos", "vaso", "vasos",
    "cucharada", "cucharadas", "cucharadita", "cucharaditas",
    "de", "del",
)

/**
 * Whether a query says nothing about food.
 *
 * A string made only of separators and counting words names no food, and the
 * catalog must not answer it. Measured before this existed: `and` reached one
 * record, `с` another, `y` another, and `200 gramos` another — the PREFIX tier
 * lets a one-character token claim any record whose own token starts with it,
 * and no length bound applied there the way one already applies to fuzzy
 * matching.
 *
 * A bare conjunction resolving to a food is the FM-1 shape exactly: confident,
 * plausible and wrong. It matters more than an ordinary miss because the caller
 * cannot tell it apart from a real answer — segmentation leaves such tokens
 * behind routinely, so the wrong food would arrive attached to a real meal.
 *
 * Checked semantically rather than by a minimum length, because the problem is
 * not that these strings are short. "и" and "with" differ in length by three
 * characters and fail for the same reason.
 */
internal fun namesNoFood(query: String): Boolean {
    val tokens = tokenizeNormalized(normalizeForSearch(query))
    return tokens.isEmpty() || tokens.all { isQuantityToken(it) || isSeparatorWord(it) }
}

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
    // "200g", "2tbsp": a number glued to its unit is still one quantity. It
    // consults QUANTITY_WORDS rather than a units list of its own — the two
    // vocabularies drifted apart immediately when they were separate, so
    // "500mg rice" resolved while "500 mg rice" did not, and "2 tbsp rice"
    // resolved while "2tbsp rice" did not. Whether a space was typed is not a
    // distinction the catalog should notice.
    val digits = token.takeWhile(Char::isDigit)
    return digits.isNotEmpty() && token.drop(digits.length) in QUANTITY_WORDS
}

private fun skipSeparatorsForward(description: String, from: Int, limit: Int): Int {
    var at = from
    while (at < limit && isSeparatorCharacter(description[at])) at++
    return at
}

private fun skipSeparatorsBackward(description: String, floor: Int, from: Int): Int {
    var at = from
    while (at > floor && isSeparatorCharacter(description[at - 1])) at--
    return at
}

private fun skipLeadingConjunction(description: String, from: Int, limit: Int): Int {
    var wordEnd = from
    while (wordEnd < limit && !isSeparatorCharacter(description[wordEnd])) wordEnd++
    return if (wordEnd > from && isConjunction(description.substring(from, wordEnd))) wordEnd else from
}

private fun skipTrailingConjunction(description: String, floor: Int, to: Int): Int {
    var wordStart = to
    while (wordStart > floor && !isSeparatorCharacter(description[wordStart - 1])) wordStart--
    return if (wordStart < to && isConjunction(description.substring(wordStart, to))) wordStart else to
}

/** Whitespace and the comma [TextFragments] already treats as a separator on its own. */
private fun isSeparatorCharacter(character: Char): Boolean = character.isWhitespace() || character == ','

private fun isConjunction(word: String): Boolean = word.lowercase() in CONJUNCTIONS

/**
 * Any word this file treats as joining two foods, strong or weak. Kept as one
 * predicate so a word added to either set is automatically a word the catalog
 * refuses to answer on its own.
 */
private fun isSeparatorWord(word: String): Boolean =
    word.lowercase().let { it in CONJUNCTIONS || it in WEAK_SEPARATORS }

/**
 * Deliberately small and English-only. A separator list is language-specific
 * data and belongs with the per-market language matrix rather than here.
 */
private val CONJUNCTIONS = setOf("and", "plus", "&")

/**
 * Words that join two foods AND appear inside compound food names.
 *
 * `with` sits here rather than with the strong conjunctions because
 * "macaroni with cheese" is one food and "eggs with toast" is two, and
 * nothing in the text distinguishes them — only the catalog does. Spanish
 * `con` and Russian `с`/`со` are the same word doing the same double duty,
 * and they are listed now so the three languages behave alike instead of
 * English being split and the others escaping by omission.
 *
 * A fragment is only split on these after failing to resolve whole.
 */
private val WEAK_SEPARATORS = setOf(
    "with",
    "con",
    "с",
    "со",
    // `и` and `y` were briefly strong, on the reasoning that a true coordinator
    // cannot appear inside a food name the way `con` does. The corpus falsified
    // that against real AGROVOC records: `ягоды лесные и садовые` and `свежие
    // фрукты и ягоды` are single records whose names contain `и`, and promoting
    // it split both. The claim had been marked as recalled rather than sourced,
    // and it was wrong — which is the argument for resolving whole first and
    // splitting only on failure, rather than for a better list.
    "и",
    "y",
    "e",
)
