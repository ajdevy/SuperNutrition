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
 */
class SegmentationReview private constructor(
    val items: List<DetectedItem>,
    private val confirmedIds: Set<String>,
) {
    /** Items the user has explicitly confirmed, in the order they appear in the split. */
    val confirmedItems: List<DetectedItem> get() = items.filter { it.canonicalItemId in confirmedIds }

    /**
     * False for an empty split. Emptying the split is a legitimate way to say
     * "none of this is right", and it must not read as a confirmed meal of zero
     * items.
     */
    val isConfirmable: Boolean get() = items.isNotEmpty()

    fun add(item: DetectedItem): SegmentationReview =
        SegmentationReview(items + item, confirmedIds)

    /**
     * Removing an item drops its confirmation with it. A confirmation that
     * outlived its item would let the meal aggregate a food no longer in the
     * split.
     */
    fun remove(canonicalItemId: String): SegmentationReview =
        SegmentationReview(
            items.filterNot { it.canonicalItemId == canonicalItemId },
            confirmedIds - canonicalItemId,
        )

    fun confirm(canonicalItemId: String): SegmentationReview =
        if (items.none { it.canonicalItemId == canonicalItemId }) {
            this
        } else {
            SegmentationReview(items, confirmedIds + canonicalItemId)
        }

    /**
     * Replaces two items with one. The replacement spans everything the
     * originals covered, so its provenance still points at words the user
     * actually typed rather than disowning part of the description.
     *
     * The replacement is unconfirmed even if both originals were confirmed: it
     * is a different food from either, and nobody has agreed to it yet.
     */
    fun merge(
        firstCanonicalItemId: String,
        secondCanonicalItemId: String,
        mergedCanonicalItemId: String,
    ): SegmentationReview {
        val first = items.firstOrNull { it.canonicalItemId == firstCanonicalItemId }
        val second = items.firstOrNull { it.canonicalItemId == secondCanonicalItemId }
        if (first == null || second == null) return this

        val merged = DetectedItem(
            canonicalItemId = mergedCanonicalItemId,
            sourceSpan = TextSpan(
                start = minOf(first.sourceSpan.start, second.sourceSpan.start),
                end = maxOf(first.sourceSpan.end, second.sourceSpan.end),
            ),
        )
        val remaining = items.filterNot {
            it.canonicalItemId == firstCanonicalItemId || it.canonicalItemId == secondCanonicalItemId
        }
        return SegmentationReview(
            remaining + merged,
            confirmedIds - firstCanonicalItemId - secondCanonicalItemId,
        )
    }

    companion object {
        fun of(items: List<DetectedItem>): SegmentationReview =
            SegmentationReview(items, emptySet())
    }
}
