package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentationReviewTest {
    private val egg = DetectedItem("food:egg-boiled", TextSpan(0, 15))
    private val bread = DetectedItem("food:bread-rye", TextSpan(20, 40))
    private val proposed = listOf(egg, bread)

    @Test
    fun `an item the engine missed can be added`() {
        val review = SegmentationReview.of(proposed)

        val updated = review.add(DetectedItem("food:coriander-leaf", TextSpan(0, 15)))

        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye", "food:coriander-leaf"),
            updated.items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `an item the engine invented can be removed`() {
        val review = SegmentationReview.of(proposed)

        val updated = review.remove(bread)

        assertEquals(listOf("food:egg-boiled"), updated.items.map { it.canonicalItemId })
    }

    @Test
    fun `removing every item leaves a reviewable empty split rather than a confirmed meal`() {
        // Emptying the split is a legitimate way to say "none of this is right".
        // It must not read as a confirmed meal of zero items.
        val review = SegmentationReview.of(proposed).remove(egg).remove(bread)

        assertTrue(review.items.isEmpty())
        assertTrue(!review.isConfirmable, "an empty split has nothing to confirm")
    }

    @Test
    fun `editing the split does not confirm anything`() {
        // CAP-5 and FM-1: the engine proposes, the user decides. No edit may
        // bind a canonical record, however confident the proposal was.
        val review = SegmentationReview.of(proposed).remove(bread)

        assertTrue(review.confirmedItems.isEmpty(), "editing must never confirm an item")
        assertTrue(review.isConfirmable, "a non-empty split is confirmable")
    }

    @Test
    fun `confirming an item preserves the items already confirmed`() {
        val review = SegmentationReview.of(proposed)
            .confirm(egg)
            .confirm(bread)

        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye"),
            review.confirmedItems.map { it.canonicalItemId },
        )
    }

    @Test
    fun `adding an item after confirming another keeps the confirmation`() {
        // CAP-7: correcting the split must not restart the session or discard
        // work already done.
        val review = SegmentationReview.of(proposed)
            .confirm(egg)
            .add(DetectedItem("food:rice-white-cooked", TextSpan(20, 40)))

        assertEquals(listOf("food:egg-boiled"), review.confirmedItems.map { it.canonicalItemId })
        assertEquals(3, review.items.size)
    }

    @Test
    fun `removing a confirmed item drops its confirmation too`() {
        // Otherwise a confirmation outlives the item it confirmed, and the meal
        // aggregates a food no longer in the split.
        val review = SegmentationReview.of(proposed)
            .confirm(bread)
            .remove(bread)

        assertTrue(review.confirmedItems.isEmpty())
        assertEquals(listOf("food:egg-boiled"), review.items.map { it.canonicalItemId })
    }

    @Test
    fun `a merged item is unconfirmed even when both originals were confirmed`() {
        // Merging egg and toast into egg-on-toast produces a different food from
        // either. A user who agreed to two separate items has never agreed to
        // the combination, so carrying the confirmations forward would bind a
        // canonical record nobody approved.
        val review = SegmentationReview.of(proposed)
            .confirm(egg)
            .confirm(bread)
            .merge(egg, bread, "food:rice-white-cooked")

        assertEquals(listOf("food:rice-white-cooked"), review.items.map { it.canonicalItemId })
        assertTrue(review.confirmedItems.isEmpty(), "a merge result must start unconfirmed")
    }

    @Test
    fun `confirming an item that is not in the split changes nothing`() {
        // The interface can emit a stale confirmation for an item the user just
        // removed. Ignoring it is safer than resurrecting a confirmation for a
        // food no longer in the meal.
        val review = SegmentationReview.of(proposed)
            .remove(bread)
            .confirm(bread)

        assertTrue(review.confirmedItems.isEmpty())
        assertEquals(listOf("food:egg-boiled"), review.items.map { it.canonicalItemId })
    }

    @Test
    fun `merging two items replaces both with one`() {
        val review = SegmentationReview.of(proposed)

        val merged = review.merge(egg, bread, "food:rice-white-cooked")

        assertEquals(listOf("food:rice-white-cooked"), merged.items.map { it.canonicalItemId })
        // The merged item spans everything the originals covered, so its
        // provenance still points at the words the user actually typed.
        assertEquals(TextSpan(0, 40), merged.items.single().sourceSpan)
    }

    @Test
    fun `a food named twice is two items the user edits independently`() {
        // Verified by execution against the real engine: "rye toast and rye
        // bread" segments into two items that both resolve to food:bread-rye.
        // Keying edits on the record id made them indistinguishable — removing
        // one removed both, and confirming one confirmed both, which is exactly
        // the CAP-7 promise that removing an invented item must not discard an
        // already-confirmed one.
        val first = DetectedItem("food:bread-rye", TextSpan(0, 9))
        val second = DetectedItem("food:bread-rye", TextSpan(14, 23))
        val review = SegmentationReview.of(listOf(first, second))

        assertEquals(listOf(first), review.remove(second).items)
        assertEquals(listOf(first), review.confirm(first).confirmedItems)
    }

    @Test
    fun `confirming one instance of a repeated food leaves the other unconfirmed`() {
        val first = DetectedItem("food:bread-rye", TextSpan(0, 9))
        val second = DetectedItem("food:bread-rye", TextSpan(14, 23))

        val review = SegmentationReview.of(listOf(first, second)).confirm(second)

        assertEquals(listOf(second), review.confirmedItems)
        assertEquals(2, review.items.size, "confirming must not drop the other instance")
    }
}
