package com.supernutrition.domain.nutrition

/**
 * A text engine that needs no model.
 *
 * It splits a description on ordinary separators and resolves each fragment
 * through the catalog. That makes it worse than a language model at
 * understanding language and better at being available: it runs on every
 * supported device, downloads nothing, negotiates no resource budget, and does
 * not wait on the G-3 device matrix.
 *
 * This is the same layering the rest of the design uses — a floor that always
 * answers, with cleverness above it. When an on-device model is approved it
 * becomes a swap behind the same port, not a prerequisite for the story.
 */
class DeterministicTextEngine(private val catalog: CatalogSearch) {
    fun identify(description: String): TextEngineOutcome {
        val fragments = splitIntoFragments(description)
        if (fragments.isEmpty()) return TextEngineOutcome.Abstained("no-recognizable-fragment")

        val items = mutableListOf<DetectedItem>()
        for (fragment in fragments) {
            val resolved = catalog.search(fragment.text).firstOrNull()
                // Partial segmentation is the FM-6 failure: returning the
                // fragments that resolved would silently drop the rest of the
                // meal while looking authoritative. Abstain for the whole
                // description instead, and let the floor answer.
                ?: return TextEngineOutcome.Abstained("fragment-unresolved")
            items += DetectedItem(resolved.canonicalItemId, fragment.span)
        }
        return TextEngineOutcome.Segmented(items)
    }

    private data class Fragment(val text: String, val span: TextSpan)

    /**
     * Splits on separators a person actually types between foods, then trims
     * each fragment back to the text that resolved. The span must cover real
     * words rather than surrounding whitespace, because it is shown to the user
     * as the provenance of an item.
     */
    private fun splitIntoFragments(description: String): List<Fragment> {
        val fragments = mutableListOf<Fragment>()
        var start = 0
        var index = 0

        fun emit(endExclusive: Int) {
            var from = start
            var to = endExclusive
            while (from < to && description[from].isWhitespace()) from++
            while (to > from && description[to - 1].isWhitespace()) to--
            if (from < to) fragments += Fragment(description.substring(from, to), TextSpan(from, to))
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
     * "sandwich". No further boundary check is needed, and an earlier version
     * that added one rejected every real separator, because the character
     * before the leading space is the last letter of the preceding word.
     */
    private fun separatorAt(description: String, index: Int): Int {
        if (description[index] == ',') return 1
        return SEPARATOR_WORDS
            .firstOrNull { description.startsWith(it, index, ignoreCase = true) }
            ?.length
            ?: 0
    }

    private companion object {
        /**
         * Deliberately small and English-only for now. A separator list is
         * language-specific data, so extending it belongs with the per-market
         * language matrix rather than here.
         */
        val SEPARATOR_WORDS = listOf(" and ", " with ", " plus ", " & ")
    }
}
