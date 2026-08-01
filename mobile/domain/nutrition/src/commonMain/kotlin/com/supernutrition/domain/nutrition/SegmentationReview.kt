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
 */
class SegmentationReview private constructor(val items: List<DetectedItem>, private val confirmed: Set<DetectedItem>) {
    /** Items the user has explicitly confirmed, in the order they appear in the split. */
    val confirmedItems: List<DetectedItem> get() = items.filter { it in confirmed }

    /**
     * False for an empty split. Emptying the split is a legitimate way to say
     * "none of this is right", and it must not read as a confirmed meal of zero
     * items.
     */
    val isConfirmable: Boolean get() = items.isNotEmpty()

    fun add(item: DetectedItem): SegmentationReview = SegmentationReview(items + item, confirmed)

    /**
     * Removing an item drops its confirmation with it. A confirmation that
     * outlived its item would let the meal aggregate a food no longer in the
     * split.
     */
    fun remove(item: DetectedItem): SegmentationReview =
        SegmentationReview(items.filterNot { it == item }, confirmed - item)

    fun confirm(item: DetectedItem): SegmentationReview =
        if (item !in items) this else SegmentationReview(items, confirmed + item)

    /**
     * Replaces two items with one. The replacement spans everything the
     * originals covered, so its provenance still points at words the user
     * actually typed rather than disowning part of the description.
     *
     * The replacement is unconfirmed even if both originals were confirmed: it
     * is a different food from either, and nobody has agreed to it yet.
     */
    fun merge(first: DetectedItem, second: DetectedItem, mergedCanonicalItemId: String): SegmentationReview {
        if (first !in items || second !in items) return this

        val merged = DetectedItem(
            canonicalItemId = mergedCanonicalItemId,
            sourceSpan = TextSpan(
                start = minOf(first.sourceSpan.start, second.sourceSpan.start),
                end = maxOf(first.sourceSpan.end, second.sourceSpan.end),
            ),
        )
        val remaining = items.filterNot { it == first || it == second }
        return SegmentationReview(remaining + merged, confirmed - first - second)
    }

    companion object {
        fun of(items: List<DetectedItem>): SegmentationReview = SegmentationReview(items, emptySet())
    }
}
