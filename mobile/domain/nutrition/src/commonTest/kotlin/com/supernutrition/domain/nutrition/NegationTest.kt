package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "No nuts, allergic" must never log nuts.
 *
 * Found by stress-testing the matcher with realistic exclusion phrases: `no`,
 * `not` and `without` each combined with ten ordinary foods — nuts, eggs,
 * milk, rice, cheese, butter, onions, sugar, sauce, bread — and every single
 * combination matched a real record, most of them the very food being
 * excluded. `no nuts` reached a nuts record; `no cheese` reached a cheese
 * record; `no sauce` reached a sauce record.
 *
 * The mechanism is ordinary and therefore common: PREFIX carries no minimum
 * token length the way FUZZY does, and `no` is a two-character prefix of
 * `not` — which appears constantly in USDA qualifiers ("not sweetened", "no
 * salt added", "without added sugar"). `no nuts` needs nothing more exotic
 * than a coconut-meat record whose own name happens to end "...not
 * sweetened": `no`→`not` and `nuts`→`nuts` both satisfy PREFIX, and the whole
 * two-token query resolves.
 *
 * A negation query cannot be handled the way scaffolding or quantity words
 * are — by stripping the marker and resolving what is left — because the
 * marker changes what the food word MEANS. Stripping `no` from `no nuts` and
 * resolving `nuts` would be the exact failure this file exists to prevent,
 * only reached through the fix instead of around it. The only safe answer
 * when a negation word is present is to refuse outright, matching AGENTS.md's
 * standing rule against silently defaulting an uncertain medical or allergen
 * conclusion — "no nuts, allergic" is exactly that shape.
 */
class NegationTest {
    private val search = CatalogSearch(generatedCatalog)

    @Test
    fun `a negation word paired with a real food refuses instead of logging the food`() {
        // The four measured leaks, verbatim, plus the case that named this
        // defect: an allergy statement must not be read as an ingredient.
        for (query in listOf(
            "no nuts", "no nuts, allergic", "no cheese", "no sauce", "no milk", "no bread",
            "not nuts", "without eggs", "without cheese", "without butter",
        )) {
            assertEquals(emptyList(), search.search(query), "\"$query\" reached a record")
        }
    }

    @Test
    fun `every measured negation word refuses on its own`() {
        // Each of these was added to NEGATION_WORDS because it was measured
        // to cause a leak, or is an unambiguous synonym of one that was.
        for (word in listOf("no", "not", "without", "hold", "skip", "minus", "sans", "excluding")) {
            for (food in listOf("nuts", "cheese", "milk", "onions")) {
                val query = "$word $food"
                assertEquals(emptyList(), search.search(query), "\"$query\" reached a record")
            }
        }
    }

    @Test
    fun `the food word alone still resolves -- this is a pairing rule, not a ban`() {
        // The fix must not cost the ordinary, unqualified query. Refusing
        // "nuts" itself would be a regression the negation fix has no reason
        // to cause.
        for (food in listOf("nuts", "cheese", "milk", "onions", "eggs", "butter")) {
            assertTrue(search.search(food).isNotEmpty(), "\"$food\" alone reached no record")
        }
    }

    @Test
    fun `a real qualified product containing an excluded-sounding word still resolves`() {
        // `free` is deliberately absent from NEGATION_WORDS. "sugar-free" and
        // "gluten-free" name real USDA products people legitimately search
        // for; refusing them would trade a correct answer for a leak `free`
        // does not itself cause. This is the boundary the fix does not cross.
        assertTrue(search.search("sugar-free").isNotEmpty(), "\"sugar-free\" reached no record")
    }

    @Test
    fun `Spanish negation stays exactly as safe as before -- refusal by absence, not by this list`() {
        // sin/менее were measured against the same ten foods and produced no
        // leak in this catalog, so they were not added to NEGATION_WORDS.
        // These still refuse today through the pre-existing mechanism (no
        // record's form satisfies every token), not through this fix.
        for (query in listOf("sin cebolla", "sin queso", "без лука", "без сыра")) {
            assertEquals(emptyList(), search.search(query), "\"$query\" reached a record")
        }
    }
}
