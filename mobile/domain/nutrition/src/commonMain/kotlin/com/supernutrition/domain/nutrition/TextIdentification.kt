package com.supernutrition.domain.nutrition

/** Where in the user's description an item was found. */
data class TextSpan(val start: Int, val end: Int) {
    fun indexes(description: String): Boolean =
        start >= 0 && end > start && end <= description.length
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

    /** No usable engine result; the deterministic floor answered instead. */
    data class Floor(val canonicalItemIds: List<String>) : TextIdentificationOutcome
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
class TextIdentification(
    private val floor: CatalogSearch,
    private val engine: (String) -> TextEngineOutcome,
) {
    fun identify(description: String): TextIdentificationOutcome {
        val trimmed = description.trim()
        // Checked before the engine runs: invoking a model on an empty string
        // costs battery and time on exactly the devices that can least afford it.
        if (trimmed.isEmpty()) return TextIdentificationOutcome.Empty

        return when (val outcome = engine(trimmed)) {
            is TextEngineOutcome.Segmented -> acceptOrFallBack(outcome.items, trimmed)
            // An abstention is the engine being honest, not the journey failing.
            is TextEngineOutcome.Abstained -> fallBack(trimmed)
            is TextEngineOutcome.Unavailable -> fallBack(trimmed)
        }
    }

    /**
     * A span that does not index the description is worse than no span: the user
     * would be shown provenance claiming words that are not there. Such a result
     * is discarded whole rather than partially trusted.
     */
    private fun acceptOrFallBack(
        items: List<DetectedItem>,
        description: String,
    ): TextIdentificationOutcome {
        if (items.isEmpty()) return fallBack(description)
        val everySpanIndexes = items.all { it.sourceSpan.indexes(description) }
        return if (everySpanIndexes) {
            TextIdentificationOutcome.Items(items)
        } else {
            fallBack(description)
        }
    }

    private fun fallBack(description: String): TextIdentificationOutcome =
        TextIdentificationOutcome.Floor(
            floor.search(description).map { it.canonicalItemId },
        )
}
