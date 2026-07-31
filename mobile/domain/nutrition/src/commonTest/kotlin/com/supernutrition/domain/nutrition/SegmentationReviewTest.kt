package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentationReviewTest {
    private val proposed = listOf(
        DetectedItem("food:egg-boiled", TextSpan(0, 15)),
        DetectedItem("food:bread-rye", TextSpan(20, 40)),
    )

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

        val updated = review.remove("food:bread-rye")

        assertEquals(listOf("food:egg-boiled"), updated.items.map { it.canonicalItemId })
    }

    @Test
    fun `removing every item leaves a reviewable empty split rather than a confirmed meal`() {
        // Emptying the split is a legitimate way to say "none of this is right".
        // It must not read as a confirmed meal of zero items.
        val review = SegmentationReview.of(proposed).remove("food:egg-boiled").remove("food:bread-rye")

        assertTrue(review.items.isEmpty())
        assertTrue(!review.isConfirmable, "an empty split has nothing to confirm")
    }

    @Test
    fun `editing the split does not confirm anything`() {
        // CAP-5 and FM-1: the engine proposes, the user decides. No edit may
        // bind a canonical record, however confident the proposal was.
        val review = SegmentationReview.of(proposed).remove("food:bread-rye")

        assertTrue(review.confirmedItems.isEmpty(), "editing must never confirm an item")
        assertTrue(review.isConfirmable, "a non-empty split is confirmable")
    }

    @Test
    fun `confirming an item preserves the items already confirmed`() {
        val review = SegmentationReview.of(proposed)
            .confirm("food:egg-boiled")
            .confirm("food:bread-rye")

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
            .confirm("food:egg-boiled")
            .add(DetectedItem("food:rice-white-cooked", TextSpan(20, 40)))

        assertEquals(listOf("food:egg-boiled"), review.confirmedItems.map { it.canonicalItemId })
        assertEquals(3, review.items.size)
    }

    @Test
    fun `removing a confirmed item drops its confirmation too`() {
        // Otherwise a confirmation outlives the item it confirmed, and the meal
        // aggregates a food no longer in the split.
        val review = SegmentationReview.of(proposed)
            .confirm("food:bread-rye")
            .remove("food:bread-rye")

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
            .confirm("food:egg-boiled")
            .confirm("food:bread-rye")
            .merge("food:egg-boiled", "food:bread-rye", "food:rice-white-cooked")

        assertEquals(listOf("food:rice-white-cooked"), review.items.map { it.canonicalItemId })
        assertTrue(review.confirmedItems.isEmpty(), "a merge result must start unconfirmed")
    }

    @Test
    fun `confirming an item that is not in the split changes nothing`() {
        // The interface can emit a stale confirmation for an item the user just
        // removed. Ignoring it is safer than resurrecting a confirmation for a
        // food no longer in the meal.
        val review = SegmentationReview.of(proposed)
            .remove("food:bread-rye")
            .confirm("food:bread-rye")

        assertTrue(review.confirmedItems.isEmpty())
        assertEquals(listOf("food:egg-boiled"), review.items.map { it.canonicalItemId })
    }

    @Test
    fun `merging two items replaces both with one`() {
        val review = SegmentationReview.of(proposed)

        val merged = review.merge("food:egg-boiled", "food:bread-rye", "food:rice-white-cooked")

        assertEquals(listOf("food:rice-white-cooked"), merged.items.map { it.canonicalItemId })
        // The merged item spans everything the originals covered, so its
        // provenance still points at the words the user actually typed.
        assertEquals(TextSpan(0, 40), merged.items.single().sourceSpan)
    }
}
