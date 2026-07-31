package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seed catalog is fake data standing in for a licensed nutrition package,
 * which Gate G-2 has not yet approved. G-2 blocks grounding beyond a fake
 * catalog, not development against one, so this exists to give the search floor
 * something real to search before the licensed package arrives.
 *
 * These tests pin the properties the real package must also satisfy, so swapping
 * it in is a data change rather than a redesign.
 */
class SeedCatalogTest {
    @Test
    fun `every record has a unique namespace-qualified canonical id`() {
        val ids = SeedCatalog.records.map { it.canonicalItemId }

        assertEquals(ids.size, ids.toSet().size, "canonical ids must be unique")
        assertTrue(ids.all { it.startsWith("food:") }, "canonical ids must be namespace-qualified")
    }

    @Test
    fun `no record is reachable only by its display name`() {
        // A record with no alternate names is one that only answers to the exact
        // words its catalog author chose. That is the failure UJ-3 exists to
        // catch, so the seed data must not model it as normal.
        val withoutAlternates = SeedCatalog.records.filter { it.alternateNames.isEmpty() }

        assertEquals(
            emptyList(),
            withoutAlternates.map { it.canonicalItemId },
            "every seed record should carry at least one alternate name",
        )
    }

    @Test
    fun `spanish regional variants of one food reach one canonical id`() {
        val search = CatalogSearch(SeedCatalog.records)

        val palta = search.search("palta").map { it.canonicalItemId }
        val aguacate = search.search("aguacate").map { it.canonicalItemId }

        assertEquals(palta, aguacate)
        assertEquals(listOf("food:avocado-raw"), palta)
    }

    @Test
    fun `russian inflected and latin-script entry reach one canonical id`() {
        val search = CatalogSearch(SeedCatalog.records)

        // Nominative, an oblique case, and Latin-script entry of a Cyrillic
        // record. All three are how a real user types the same food.
        val nominative = search.search("курица").map { it.canonicalItemId }
        val genitive = search.search("курицы").map { it.canonicalItemId }
        val transliterated = search.search("kuritsa").map { it.canonicalItemId }

        assertEquals(listOf("food:chicken-breast-raw"), nominative)
        assertEquals(nominative, genitive)
        assertEquals(nominative, transliterated)
    }

    @Test
    fun `english alternate names reach one canonical id`() {
        val search = CatalogSearch(SeedCatalog.records)

        assertEquals(
            search.search("aubergine").map { it.canonicalItemId },
            search.search("eggplant").map { it.canonicalItemId },
        )
    }

    @Test
    fun `a multi-item meal resolves each item independently`() {
        val search = CatalogSearch(SeedCatalog.records)

        // The success signal from the spec: Noah types this offline with no
        // engine, and reaches a grounded result one item at a time.
        val eggs = search.search("boiled egg").map { it.canonicalItemId }
        val toast = search.search("rye bread").map { it.canonicalItemId }

        assertEquals(listOf("food:egg-boiled"), eggs)
        assertEquals(listOf("food:bread-rye"), toast)
    }
}
