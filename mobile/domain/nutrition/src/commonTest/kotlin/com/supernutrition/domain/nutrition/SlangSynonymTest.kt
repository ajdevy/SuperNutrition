package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Curated slang synonyms from the 20-round, 875-scenario multilingual slang
 * sweep (see
 * `_bmad-output/implementation-artifacts/slang-stress-test-known-limitations.md`),
 * added one at a time as reviewed, dated `MANUAL_TERMS` entries in
 * `scripts/generate-food-synonyms.py`, following the established масло/
 * yogurt/chicken precedent: each is a one-word literal addition to a concept
 * the query already needs every token of, so it can only ever help a query
 * that already typed this exact word and cannot redirect any other.
 *
 * Two candidates from the same batch are deliberately absent here: `PB`
 * (peanut butter) and `za` (pizza) have no correct target this mechanism can
 * reach -- see the comment above the five entries in
 * `scripts/generate-food-synonyms.py`'s `MANUAL_TERMS` for why. A third,
 * `avo` (avocado), needed no addition at all: it already resolves via the
 * PREFIX tier against "avocado"/"avocados", tested below for the same reason
 * the others are -- so a future change to that record's alternate names
 * cannot silently break it without a test noticing.
 */
class SlangSynonymTest {
    private val search = CatalogSearch(generatedCatalog)

    private fun top(query: String): String? = search.search(query).firstOrNull()?.displayName

    @Test
    fun `curated slang terms answer the food they were added for`() {
        assertTrue(top("shroom")?.startsWith("Mushrooms") == true, "\"shroom\" answered ${top("shroom")}")
        assertTrue(top("taters")?.startsWith("Potatoes") == true, "\"taters\" answered ${top("taters")}")
        assertTrue(top("nanner")?.startsWith("Bananas") == true, "\"nanner\" answered ${top("nanner")}")
        assertTrue(top("cheez")?.startsWith("Cheese") == true, "\"cheez\" answered ${top("cheez")}")
        assertTrue(top("chkn")?.startsWith("Chicken") == true, "\"chkn\" answered ${top("chkn")}")
    }

    @Test
    fun `avocado slang already resolves without any data change`() {
        assertTrue(top("avo")?.startsWith("Avocados") == true, "\"avo\" answered ${top("avo")}")
    }

    @Test
    fun `the new slang terms cannot steal a match from the food they sit beside`() {
        // Every tier requires ALL of a query's tokens to be satisfied by ONE
        // form, and none of these five one-word additions shares a token
        // with a query for an unrelated food, so a query that already
        // resolved correctly before this batch must keep resolving to the
        // same food after it.
        assertTrue(top("chicken")?.startsWith("Chicken") == true, "\"chicken\" answered ${top("chicken")}")
        assertTrue(top("cheese")?.startsWith("Cheese") == true, "\"cheese\" answered ${top("cheese")}")
    }
}
