package com.supernutrition.domain.nutrition

/** Where in the user's description an item was found. */
data class TextSpan(val start: Int, val end: Int) {
    fun indexes(description: String): Boolean = start >= 0 && end > start && end <= description.length
}

/** One food the engine believes the description named, and the words it came from. */
data class DetectedItem(val canonicalItemId: String, val sourceSpan: TextSpan)

/** FR-6 capability states, as reported by a text engine. */
enum class TextEngineState { UNAVAILABLE, MODEL_REQUIRED, DOWNLOADING, READY, TEMPORARILY_UNAVAILABLE, UNSUPPORTED }

/** What a text engine returns. It never returns nutrition, only identity. */
sealed interface TextEngineOutcome {
    data class Unavailable(val state: TextEngineState) : TextEngineOutcome
    data class Abstained(val reason: String) : TextEngineOutcome
    data class Segmented(val items: List<DetectedItem>) : TextEngineOutcome
}

/** What the user's typed description resolved to. */
sealed interface TextIdentificationOutcome {
    /** Nothing worth identifying was typed. */
    data object Empty : TextIdentificationOutcome

    /** The engine proposed a split the user can review and correct. */
    data class Items(val items: List<DetectedItem>) : TextIdentificationOutcome

    /**
     * No usable engine result; the deterministic floor answered instead.
     *
     * [unresolvedFragments] carries the pieces of the description the floor could
     * not match. FM-6 is a failure about what a result omits rather than what it
     * shows, so reporting only the matches would be that failure exactly: "two
     * eggs and toast" resolving to one item is indistinguishable from a
     * one-item meal unless the dropped fragment travels with the result. Naming
     * the leftovers is what lets the caller show them and lets the user add them
     * back (CAP-7) instead of silently reaching a grounded result for half a meal.
     */
    data class Floor(val canonicalItemIds: List<String>, val unresolvedFragments: List<String> = emptyList()) :
        TextIdentificationOutcome
}

/**
 * Typed meal identification, with the deterministic catalog search floor
 * underneath it.
 *
 * The engine is a parameter rather than a dependency because it is genuinely
 * optional. UJ-4 places the offline fallback on a low-end device, so the journey
 * that most needs typed identification is the one least able to run a model. An
 * engine that is unavailable, downloading, unsupported, or simply unsure must
 * never end the journey — the floor answers instead, and the user reaches a
 * grounded result either way.
 *
 * Nothing here produces a nutrition number. Both paths resolve identity only;
 * numbers come later from a confirmed catalog record.
 */
class TextIdentification(private val floor: CatalogSearch, private val engine: (String) -> TextEngineOutcome) {
    fun identify(description: String): TextIdentificationOutcome {
        // Checked before the engine runs: invoking a model on an empty string
        // costs battery and time on exactly the devices that can least afford it.
        if (description.isBlank()) return TextIdentificationOutcome.Empty

        // The description is passed through untrimmed so that every span indexes
        // the string the caller holds. Trimming first shifted every offset by
        // the leading whitespace — which a paste or a mobile keyboard supplies
        // routinely — and because the spans were then validated against the
        // trimmed copy too, the guard below could not see that they were wrong.
        // TextFragments trims each fragment's own edges, so nothing is lost.
        return when (val outcome = engine(description)) {
            is TextEngineOutcome.Segmented -> acceptOrFallBack(outcome.items, description)

            // An abstention is the engine being honest, not the journey failing.
            is TextEngineOutcome.Abstained -> fallBack(description)

            is TextEngineOutcome.Unavailable -> fallBack(description)
        }
    }

    /**
     * A span that does not index the description is worse than no span: the user
     * would be shown provenance claiming words that are not there. Such a result
     * is discarded whole rather than partially trusted.
     */
    private fun acceptOrFallBack(items: List<DetectedItem>, description: String): TextIdentificationOutcome {
        if (items.isEmpty()) return fallBack(description)
        val everySpanIndexes = items.all { it.sourceSpan.indexes(description) }
        return if (everySpanIndexes) {
            TextIdentificationOutcome.Items(items)
        } else {
            fallBack(description)
        }
    }

    /**
     * The floor resolves one record per query, so handing it a whole meal was a
     * category error: every tier requires all query tokens to be satisfied by a
     * single record, and no record contains "and". A multi-item description
     * matched nothing and the journey ended in silence — the failure this class
     * exists to prevent.
     *
     * The description is therefore segmented the same way the engine segments
     * it, and each fragment resolved on its own.
     *
     * Unlike the engine, partial resolution is kept rather than abstained on.
     * The engine proposes a split and must be all-or-nothing, but the floor is
     * the last resort: returning the foods it did recognise beats returning
     * nothing. What FM-6 forbids is not partial grounding, it is *invisible*
     * partial grounding, so anything that failed to resolve is reported
     * alongside what did rather than dropped.
     */
    private fun fallBack(description: String): TextIdentificationOutcome {
        val resolved = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        for (fragment in TextFragments.of(description)) {
            val hit = floor.search(fragment.text).firstOrNull()
            if (hit == null) unresolved += fragment.text else resolved += hit.canonicalItemId
        }
        return TextIdentificationOutcome.Floor(resolved.distinct(), unresolved)
    }
}
