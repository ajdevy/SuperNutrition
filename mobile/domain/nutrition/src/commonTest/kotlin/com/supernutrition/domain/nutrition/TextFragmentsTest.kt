package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct unit coverage of [TextFragments]' internal helpers, for shapes the
 * higher-level catalog and segmentation tests never happen to construct:
 * trailing whitespace with no trailing separator, a cut position at either
 * edge of the description, a query built entirely of stripped edge words, and
 * a bare numeral with no unit attached.
 */
class TextFragmentsTest {
    @Test
    fun `a fragment's trailing whitespace is trimmed even with no separator after it`() {
        // Every other caller's trailing whitespace is already consumed by the
        // separator that follows it (the conjunction match itself swallows its
        // own surrounding spaces). Plain trailing whitespace with nothing after
        // it -- the end of the whole description -- is the one case nothing
        // else exercises.
        val fragments = TextFragments.of("rye bread   ")

        assertEquals(listOf(TextFragment("rye bread", TextSpan(0, 9))), fragments)
    }

    @Test
    fun `separatesWords is true at position zero when a separator sits right after it`() {
        assertTrue(TextFragments.separatesWords(",rice", 0))
    }

    @Test
    fun `separatesWords is false at position zero with no separator on either side`() {
        assertTrue(!TextFragments.separatesWords("rice", 0))
    }

    @Test
    fun `separatesWords is true at the description's own end when a separator precedes it`() {
        assertTrue(TextFragments.separatesWords("rice,", 5))
    }

    @Test
    fun `separatesWords is false at the description's own end with no separator before it`() {
        assertTrue(!TextFragments.separatesWords("rice", 4))
    }

    @Test
    fun `trimSeparators returns null for a span made entirely of separator characters`() {
        // Forward skip alone consumes the whole thing, leaving nothing for
        // backward skip to do -- distinct from the "one side is only a
        // conjunction" case covered via SegmentationReview.split.
        assertEquals(null, TextFragments.trimSeparators(" , ", 0, 3))
    }

    @Test
    fun `stripNonFoodEdgeWords strips quantity words from both edges at once`() {
        assertEquals("boiled eggs", stripNonFoodEdgeWords("two boiled eggs of a"))
    }

    @Test
    fun `stripNonFoodEdgeWords leaves a query untouched when it is nothing but edge words`() {
        // Every token is strippable, so the strip loops consume the whole
        // list and `from` never lands below `to` -- the guard this exercises
        // returns the original query rather than corrupting it to a blank
        // string built from zero tokens.
        assertEquals("two of a", stripNonFoodEdgeWords("two of a"))
    }

    @Test
    fun `stripNonFoodEdgeWords leaves a query with nothing strippable at either edge alone`() {
        assertEquals("boiled eggs", stripNonFoodEdgeWords("boiled eggs"))
    }

    @Test
    fun `a bare numeral with no attached unit is recognised as a quantity on its own`() {
        // Distinct from "200g": here the whole token is digits, satisfied
        // without ever falling through to the digit-prefix-plus-unit path.
        assertEquals("boiled eggs", stripNonFoodEdgeWords("2 boiled eggs"))
    }
}
