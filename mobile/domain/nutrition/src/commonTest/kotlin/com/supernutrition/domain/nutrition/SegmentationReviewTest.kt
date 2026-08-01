package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SegmentationReviewTest {
    // The spans below index this exactly: "two boiled eggs" is 0..15 and
    // "a slice of rye toast" is 20..40. The trailing "with olive oil" is what
    // the engine missed, so an added item has real words to point at.
    private val description = "two boiled eggs and a slice of rye toast with olive oil"
    private val egg = DetectedItem("food:egg-boiled", TextSpan(0, 15))
    private val bread = DetectedItem("food:bread-rye", TextSpan(20, 40))
    private val proposed = listOf(egg, bread)

    // The duplicate-food case, whose spans index this one: 0..9 and 14..23.
    private val repeated = "rye toast and rye bread"

    @Test
    fun `an item the engine missed can be added`() {
        val review = SegmentationReview.of(description, proposed)

        val updated = review.add(DetectedItem("food:olive-oil", TextSpan(46, 55)))

        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye", "food:olive-oil"),
            updated.items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `an item the engine invented can be removed`() {
        val review = SegmentationReview.of(description, proposed)

        val updated = review.remove(bread)

        assertEquals(listOf("food:egg-boiled"), updated.items.map { it.canonicalItemId })
    }

    @Test
    fun `removing every item leaves a reviewable empty split rather than a confirmed meal`() {
        // Emptying the split is a legitimate way to say "none of this is right".
        // It must not read as a confirmed meal of zero items.
        val review = SegmentationReview.of(description, proposed).remove(egg).remove(bread)

        assertTrue(review.items.isEmpty())
        assertTrue(!review.isConfirmable, "an empty split has nothing to confirm")
    }

    @Test
    fun `editing the split does not confirm anything`() {
        // CAP-5 and FM-1: the engine proposes, the user decides. No edit may
        // bind a canonical record, however confident the proposal was.
        val review = SegmentationReview.of(description, proposed).remove(bread)

        assertTrue(review.confirmedItems.isEmpty(), "editing must never confirm an item")
        assertTrue(review.isConfirmable, "a non-empty split is confirmable")
    }

    @Test
    fun `confirming an item preserves the items already confirmed`() {
        val review = SegmentationReview.of(description, proposed)
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
        val review = SegmentationReview.of(description, proposed)
            .confirm(egg)
            .add(DetectedItem("food:rice-white-cooked", TextSpan(20, 40)))

        assertEquals(listOf("food:egg-boiled"), review.confirmedItems.map { it.canonicalItemId })
        assertEquals(3, review.items.size)
    }

    @Test
    fun `removing a confirmed item drops its confirmation too`() {
        // Otherwise a confirmation outlives the item it confirmed, and the meal
        // aggregates a food no longer in the split.
        val review = SegmentationReview.of(description, proposed)
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
        val review = SegmentationReview.of(description, proposed)
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
        val review = SegmentationReview.of(description, proposed)
            .remove(bread)
            .confirm(bread)

        assertTrue(review.confirmedItems.isEmpty())
        assertEquals(listOf("food:egg-boiled"), review.items.map { it.canonicalItemId })
    }

    @Test
    fun `merging two items replaces both with one`() {
        val review = SegmentationReview.of(description, proposed)

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
        val review = SegmentationReview.of(repeated, listOf(first, second))

        assertEquals(listOf(first), review.remove(second).items)
        assertEquals(listOf(first), review.confirm(first).confirmedItems)
    }

    @Test
    fun `confirming one instance of a repeated food leaves the other unconfirmed`() {
        val first = DetectedItem("food:bread-rye", TextSpan(0, 9))
        val second = DetectedItem("food:bread-rye", TextSpan(14, 23))

        val review = SegmentationReview.of(repeated, listOf(first, second)).confirm(second)

        assertEquals(listOf(second), review.confirmedItems)
        assertEquals(2, review.items.size, "confirming must not drop the other instance")
    }

    @Test
    fun `an item the engine collapsed can be split into the two foods it hid`() {
        // The case split exists for. "chicken and rice" read as one dish is two
        // foods with different numbers, and without split the only repair is
        // removing the item and retyping -- the dead end CAP-7 prevents.
        val collapsed = "chicken and rice"
        val one = DetectedItem("food:chicken-and-rice", TextSpan(0, 16))
        val review = SegmentationReview.of(collapsed, listOf(one))

        val updated = review.split(one, at = 7, "food:chicken-breast-raw", "food:rice-white-cooked")

        assertEquals(
            listOf("food:chicken-breast-raw", "food:rice-white-cooked"),
            updated.items.map { it.canonicalItemId },
        )
        assertEquals(listOf("chicken", "rice"), updated.items.map(updated::wordsFor))
    }

    @Test
    fun `each half of a split lands on words, not on the whitespace between them`() {
        // Cutting anywhere inside " and " must still produce readable halves. A
        // span whose edges are whitespace is what the v2 contract rejects for
        // exactly this reason: it looks authoritative and points at nothing.
        val collapsed = "chicken and rice"
        val one = DetectedItem("food:chicken-and-rice", TextSpan(0, 16))
        val review = SegmentationReview.of(collapsed, listOf(one))

        // Every gap around "and" -- before it, after it, and both sides of the
        // spaces -- must give the same two foods. The conjunction lands on one
        // side or the other depending on the cut, and belongs to neither.
        for (cut in listOf(7, 8, 11, 12)) {
            val updated = review.split(one, at = cut, "food:chicken-breast-raw", "food:rice-white-cooked")

            assertEquals(listOf("chicken", "rice"), updated.items.map(updated::wordsFor), "cut at $cut")
        }
    }

    @Test
    fun `both halves of a split are unconfirmed even when the original was`() {
        // Neither half is the food the user agreed to -- that food was the
        // combination -- so carrying the confirmation across would bind a record
        // nobody approved.
        val collapsed = "chicken and rice"
        val one = DetectedItem("food:chicken-and-rice", TextSpan(0, 16))
        val review = SegmentationReview.of(collapsed, listOf(one)).confirm(one)

        val updated = review.split(one, at = 7, "food:chicken-breast-raw", "food:rice-white-cooked")

        assertEquals(emptyList(), updated.confirmedItems)
        assertEquals(2, updated.items.size)
    }

    @Test
    fun `a split leaves the halves where the original was, not at the end`() {
        // Unlike a merge, which combines two items that may be far apart and has
        // no obviously right position, a split has exactly one.
        val review = SegmentationReview.of(description, proposed)

        val updated = review.split(egg, at = 3, "food:egg-boiled", "food:egg-boiled")

        assertEquals(listOf(TextSpan(0, 3), TextSpan(4, 15), TextSpan(20, 40)), updated.items.map { it.sourceSpan })
    }

    @Test
    fun `a cut at or outside the item's own words is a defect, not a silent no-op`() {
        // The caller controls the offset, so a bad one is a bug rather than a
        // race. Returning the review unchanged would look exactly like a split
        // that worked.
        val review = SegmentationReview.of(description, proposed)

        assertFailsWith<IllegalArgumentException> { review.split(egg, 0, "a", "b") }
        assertFailsWith<IllegalArgumentException> { review.split(egg, 15, "a", "b") }
        assertFailsWith<IllegalArgumentException> { review.split(egg, 30, "a", "b") }
        // Through the middle of "boiled", which would leave "two bo" and "iled eggs".
        assertFailsWith<IllegalArgumentException> { review.split(egg, 6, "a", "b") }
    }

    @Test
    fun `splitting an item already removed changes nothing`() {
        // A stale reference means the item went away while the user was
        // deciding, which is a race rather than a defect -- the same reading
        // confirm and merge already take.
        val review = SegmentationReview.of(description, proposed).remove(egg)

        assertEquals(listOf(bread), review.split(egg, at = 7, "a", "b").items)
    }

    @Test
    fun `an item shows the words it came from`() {
        // CAP-7's visible provenance, and the reason the review holds the
        // description at all.
        val review = SegmentationReview.of(description, proposed)

        assertEquals("two boiled eggs", review.wordsFor(egg))
        assertEquals("a slice of rye toast", review.wordsFor(bread))
    }

    @Test
    fun `an item pointing outside the description cannot enter the split`() {
        // A span that does not index the description makes wordsFor a lie, and
        // the provenance shown beside the item would be someone else's words.
        assertFailsWith<IllegalArgumentException> {
            SegmentationReview.of(description, listOf(DetectedItem("food:egg-boiled", TextSpan(0, 900))))
        }
        assertFailsWith<IllegalArgumentException> {
            SegmentationReview.of(description, proposed).add(DetectedItem("food:olive-oil", TextSpan(50, 900)))
        }
    }

    @Test
    fun `a comma joins two foods just as a conjunction does`() {
        // The segmenter treats "," as a separator on its own. Sharing only the
        // conjunction list left split agreeing about "and" and disagreeing about
        // ",", so a half's provenance read "chicken," -- a span the segmenter
        // would never have produced.
        val collapsed = "chicken, rice"
        val one = DetectedItem("food:chicken-and-rice", TextSpan(0, 13))
        val review = SegmentationReview.of(collapsed, listOf(one))

        for (cut in listOf(7, 8, 9)) {
            val updated = review.split(one, at = cut, "food:chicken-breast-raw", "food:rice-white-cooked")

            assertEquals(listOf("chicken", "rice"), updated.items.map(updated::wordsFor), "cut at $cut")
        }
    }

    @Test
    fun `a comma with no space around it is still a place to split`() {
        // "chicken,rice" contains no whitespace at all. A whitespace-only
        // boundary rule made it unsplittable, which is a dead end for a
        // description the segmenter itself would have split.
        val collapsed = "chicken,rice"
        val one = DetectedItem("food:chicken-and-rice", TextSpan(0, 12))
        val review = SegmentationReview.of(collapsed, listOf(one))

        val updated = review.split(one, at = 7, "food:chicken-breast-raw", "food:rice-white-cooked")

        assertEquals(listOf("chicken", "rice"), updated.items.map(updated::wordsFor))
    }

    @Test
    fun `separators that combine are all trimmed, not just the first`() {
        // "chicken , and rice" joins with a comma and a conjunction, and
        // trimming either exposes the other.
        val collapsed = "chicken , and rice"
        val one = DetectedItem("food:chicken-and-rice", TextSpan(0, 18))
        val review = SegmentationReview.of(collapsed, listOf(one))

        val updated = review.split(one, at = 8, "food:chicken-breast-raw", "food:rice-white-cooked")

        assertEquals(listOf("chicken", "rice"), updated.items.map(updated::wordsFor))
    }

    @Test
    fun `merging across an item the user kept would claim its words, so it is refused`() {
        // A DetectedItem carries one span, so a merge of two items straddling a
        // third produces a span covering the third's words -- while the third is
        // still in the split claiming the same words. Two items cannot both have
        // come from "a slice of rye toast".
        val oil = DetectedItem("food:olive-oil", TextSpan(46, 55))
        val review = SegmentationReview.of(description, proposed).add(oil)

        val failure = assertFailsWith<IllegalArgumentException> {
            review.merge(egg, oil, "food:eggs-fried-in-olive-oil")
        }

        assertTrue("still in the split" in failure.message.orEmpty())
    }

    @Test
    fun `merging across an item is allowed once that item is gone`() {
        // The rule is about words another item still claims, not about distance.
        val oil = DetectedItem("food:olive-oil", TextSpan(46, 55))
        val review = SegmentationReview.of(description, proposed).add(oil).remove(bread)

        val merged = review.merge(egg, oil, "food:eggs-fried-in-olive-oil")

        assertEquals(listOf("food:eggs-fried-in-olive-oil"), merged.items.map { it.canonicalItemId })
        assertEquals(TextSpan(0, 55), merged.items.single().sourceSpan)
    }
}
