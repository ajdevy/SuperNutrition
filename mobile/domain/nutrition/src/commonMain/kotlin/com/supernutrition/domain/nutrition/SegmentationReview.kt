package com.supernutrition.domain.nutrition

/**
 * A proposed split of a typed description into food items, under user review.
 *
 * The engine proposes; the user decides. Nothing here confirms an item as a
 * side effect of editing, because a confident proposal binding a canonical
 * record without an explicit decision is how a plausible-but-wrong food reaches
 * a Grounded Nutrition Result with intact provenance.
 *
 * Every operation returns a new review rather than mutating this one, so a
 * correction cannot partially apply and leave the session in a state no user
 * asked for.
 *
 * Edits identify an item by the item, never by its canonical record id. A
 * description can name the same food twice — "rye toast and rye bread" segments
 * into two items that both resolve to one record — and keying on the id made
 * those two indistinguishable: removing one removed both, and confirming one
 * confirmed both. The span is what tells them apart, and it is also the
 * provenance the user is shown, so it is the right identity for an edit.
 *
 * The review holds the description as well as the items, because CAP-7 requires
 * each item to show the words it came from and a span alone cannot produce them.
 * Holding it here rather than in a presenter also lets [split] land its halves on
 * real words instead of on offsets that happen to be whitespace.
 */
class SegmentationReview private constructor(
    val description: String,
    val items: List<DetectedItem>,
    private val confirmed: Set<DetectedItem>,
) {
    /** Items the user has explicitly confirmed, in the order they appear in the split. */
    val confirmedItems: List<DetectedItem> get() = items.filter { it in confirmed }

    /**
     * False for an empty split. Emptying the split is a legitimate way to say
     * "none of this is right", and it must not read as a confirmed meal of zero
     * items.
     */
    val isConfirmable: Boolean get() = items.isNotEmpty()

    /**
     * The words this item came from, exactly as the user typed them.
     *
     * This is the provenance CAP-7 shows beside each item. It is a function of
     * the item's span, so it is only meaningful for an item in this review —
     * asking for a foreign item's words would return a substring of a
     * description that item never came from.
     */
    fun wordsFor(item: DetectedItem): String {
        require(item in items) { "Cannot show the words for an item that is not in this split" }
        return description.substring(item.sourceSpan.start, item.sourceSpan.end)
    }

    /**
     * Adds an item the engine missed.
     *
     * The item must point at words in this description. An item the user adds
     * by searching, having never typed it at all, has no words to point at and
     * is not representable yet: [DetectedItem.sourceSpan] is not nullable. That
     * is the thing to change when the add-by-search path exists, rather than
     * inventing a span that claims provenance the item does not have.
     */
    fun add(item: DetectedItem): SegmentationReview {
        require(item.sourceSpan.indexes(description)) {
            "An added item must point at words in the description it is being added to"
        }
        return SegmentationReview(description, items + item, confirmed)
    }

    /**
     * Removing an item drops its confirmation with it. A confirmation that
     * outlived its item would let the meal aggregate a food no longer in the
     * split.
     */
    fun remove(item: DetectedItem): SegmentationReview =
        SegmentationReview(description, items.filterNot { it == item }, confirmed - item)

    fun confirm(item: DetectedItem): SegmentationReview =
        if (item !in items) this else SegmentationReview(description, items, confirmed + item)

    /**
     * Replaces two items with one. The replacement spans everything the
     * originals covered, so its provenance still points at words the user
     * actually typed.
     *
     * The replacement is unconfirmed even if both originals were confirmed: it
     * is a different food from either, and nobody has agreed to it yet.
     *
     * The two must have nothing between them. A [DetectedItem] carries one span,
     * so merging items that straddle a third produces a span covering that
     * third item's words too — merging the eggs and the olive oil in "two boiled
     * eggs and a slice of rye toast with olive oil" would claim the toast, while
     * the toast is still in the split claiming the same words. Until a non-
     * contiguous item is representable, a surface should offer merge only for
     * neighbours, so straddling is a defect rather than an unusual choice.
     */
    fun merge(first: DetectedItem, second: DetectedItem, mergedCanonicalItemId: String): SegmentationReview {
        if (first !in items || second !in items) return this

        val hull = TextSpan(
            start = minOf(first.sourceSpan.start, second.sourceSpan.start),
            end = maxOf(first.sourceSpan.end, second.sourceSpan.end),
        )
        val remaining = items.filterNot { it == first || it == second }
        val straddled = remaining.filter { it.sourceSpan.start < hull.end && it.sourceSpan.end > hull.start }
        require(straddled.isEmpty()) {
            "Merging these two would claim the words of ${straddled.size} item(s) still in the split"
        }

        val merged = DetectedItem(canonicalItemId = mergedCanonicalItemId, sourceSpan = hull)
        return SegmentationReview(description, remaining + merged, confirmed - first - second)
    }

    /**
     * Replaces one item with two, cutting at [at] in the description.
     *
     * The other half of [merge], and the case that actually happens: the engine
     * reads "chicken and rice" as one dish, and the two foods it collapsed have
     * different numbers. Without this the only repair is removing the item and
     * retyping, which is the dead end CAP-7 exists to prevent.
     *
     * Both halves are unconfirmed even if the original was. Neither is the food
     * the user agreed to — that food was the combination — so carrying the
     * confirmation across would bind a record nobody approved, which is the same
     * failure [merge] avoids from the other direction.
     *
     * Each half is trimmed by [TextFragments.trimSeparators], the segmenter's own
     * notion of what joins two foods, so the halves read as the segmenter would
     * have produced them. The replacements take the original's position rather
     * than moving to the end: a split has exactly one obviously right position,
     * which a merge does not.
     */
    fun split(
        item: DetectedItem,
        at: Int,
        firstCanonicalItemId: String,
        secondCanonicalItemId: String,
    ): SegmentationReview {
        // Membership is checked the way the other edits check it — a stale
        // reference means the item was removed while the user was deciding, and
        // doing nothing is the right answer. The offset is different: the caller
        // controls it, so a bad one is a defect rather than a race, and silently
        // returning an unsplit review would look exactly like a split that
        // worked.
        if (item !in items) return this

        val span = item.sourceSpan
        require(at > span.start && at < span.end) {
            "A split must cut inside the item's own words, not at or beyond their edge"
        }
        // A cut through the middle of a word would hand each half a fragment of
        // it -- "chicken a" and "nd rice" -- which is provenance no segmenter
        // would ever produce. A surface offering this picks a gap between words,
        // so a cut anywhere else is a defect rather than an unusual choice.
        // "Gap" is the segmenter's notion, not whitespace: "chicken,rice" has no
        // space in it and is still two foods.
        require(TextFragments.separatesWords(description, at)) {
            "A split must cut between words, not through one"
        }
        val first = TextFragments.trimSeparators(description, span.start, at)
        val second = TextFragments.trimSeparators(description, at, span.end)
        require(first != null && second != null) {
            "A split must leave words on both sides; this cut leaves one side blank"
        }

        val replacements = listOf(
            DetectedItem(firstCanonicalItemId, first),
            DetectedItem(secondCanonicalItemId, second),
        )
        val updated = items.flatMap { if (it == item) replacements else listOf(it) }
        return SegmentationReview(description, updated, confirmed - item)
    }

    companion object {
        fun of(description: String, items: List<DetectedItem>): SegmentationReview {
            require(items.all { it.sourceSpan.indexes(description) }) {
                "Every item must point at words in the description it was segmented from"
            }
            return SegmentationReview(description, items, emptySet())
        }
    }
}
