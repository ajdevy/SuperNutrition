package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The generated USDA catalog, exercised against the real matcher.
 *
 * `SeedCatalog` stays what it is — the deliberately fake eight-record set the
 * mechanism was built against, which G-2 replaces. This is a different thing:
 * real USDA SR Legacy names, public domain, joined to AGROVOC and Wikidata
 * terms so a Russian or Spanish word reaches the same record an English one
 * does.
 *
 * It carries no nutrition numbers, and that is not an omission. `CatalogRecord`
 * has no field for one, so this file cannot assert a calorie even by accident —
 * which is what keeps importing 3,519 real foods on the right side of G-2.
 */
class GeneratedCatalogTest {
    private val search = CatalogSearch(generatedCatalog)

    @Test
    fun `the catalog is present and its ids are unique`() {
        assertTrue(generatedCatalog.size > 3_000, "generated catalog holds ${generatedCatalog.size} records")
        val ids = generatedCatalog.map { it.canonicalItemId }
        assertEquals(ids.size, ids.toSet().size, "duplicate canonical ids would make an edit ambiguous")
    }

    @Test
    fun `no generated record smuggles a nutrition number into its name`() {
        // The G-2 line. Identity data may ship; numbers may not, and the type
        // has no field for one — so the only way a number could arrive is inside
        // a display name, which is worth checking rather than assuming.
        //
        // A numeric claim, not the word. My first version matched "calorie"
        // anywhere and flagged "Yogurt, ... sweetened with low-calorie
        // sweetener" — which describes the food rather than asserting a number,
        // and is exactly the kind of name a real catalog carries.
        val energyClaim = Regex("""\d\s*(kcal|cal\b|calories)""", RegexOption.IGNORE_CASE)
        val suspicious = generatedCatalog
            .filter { energyClaim.containsMatchIn(it.displayName) }
            .map { it.displayName }

        assertEquals(emptyList(), suspicious)
    }

    @Test
    fun `everyday English foods are reachable`() {
        // The point of importing 7,793 records and keeping 3,519: the selection
        // is worthless if it dropped the foods people actually type. These were
        // the probe that chose the size cap.
        for (query in listOf("butter", "broccoli", "chicken breast", "whole milk", "ground beef")) {
            assertTrue(search.search(query).isNotEmpty(), "\"$query\" reached no record")
        }
    }

    @Test
    fun `Russian and Spanish words reach a record`() {
        // What the AGROVOC and Wikidata join is for. Before it, the catalog was
        // English-only and a Russian speaker had nothing to type.
        for (query in listOf("курица", "рис", "молоко", "arroz", "pollo", "leche")) {
            assertTrue(search.search(query).isNotEmpty(), "\"$query\" reached no record")
        }
    }

    @Test
    fun `the pepper join stays refused`() {
        // A real wrong join, caught while building this: USDA `Peppers` is
        // Capsicum and AGROVOC's `pepper` concept is Piper nigrum, the spice.
        // The exact join succeeded, so `pimienta` and `перец черный` would have
        // answered with bell peppers — a confident wrong food, not a miss.
        for (query in listOf("pimienta", "перец черный")) {
            val hits = search.search(query).map { it.displayName }
            assertTrue(
                hits.none { it.startsWith("Peppers,") || it.startsWith("Pepper, banana") },
                "\"$query\" reached $hits",
            )
        }
    }

    @Test
    fun `a separator or counting word still names no food here`() {
        // The FM-1 guard, re-checked against a catalog three orders of magnitude
        // larger than the seed set. More records mean more chances for a bare
        // token to find something.
        for (query in listOf("and", "с", "y", "con", "и", "200", "grams")) {
            assertEquals(emptyList(), search.search(query), "\"$query\" reached a record")
        }
    }
}
