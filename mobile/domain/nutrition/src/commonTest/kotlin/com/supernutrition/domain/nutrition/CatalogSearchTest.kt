package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSearchTest {
    @Test
    fun `exact name match ignores case`() {
        val search = CatalogSearch(
            listOf(CatalogRecord(canonicalItemId = "food:rye-bread", displayName = "Bread, rye")),
        )

        val results = search.search("bread, rye")

        assertEquals(listOf("food:rye-bread"), results.map { it.canonicalItemId })
    }

    @Test
    fun `exact match ignores diacritics so unaccented entry finds an accented record`() {
        val search = CatalogSearch(
            listOf(CatalogRecord(canonicalItemId = "food:platano", displayName = "Plátano")),
        )

        val results = search.search("platano")

        assertEquals(listOf("food:platano"), results.map { it.canonicalItemId })
    }

    @Test
    fun `prefix match finds a record from leading fragments in any order`() {
        val search = CatalogSearch(
            listOf(CatalogRecord(canonicalItemId = "food:rye-bread", displayName = "Bread, rye")),
        )

        val results = search.search("rye bre")

        assertEquals(listOf("food:rye-bread"), results.map { it.canonicalItemId })
    }

    @Test
    fun `a complete token match wins outright over a prefix-only match`() {
        val search = CatalogSearch(
            listOf(
                CatalogRecord(canonicalItemId = "food:rye-crispbread", displayName = "Ryebread crisp"),
                CatalogRecord(canonicalItemId = "food:rye-bread", displayName = "Bread, rye"),
            ),
        )

        val results = search.search("rye")

        // "rye" is a complete token of "Bread, rye" but only a prefix of
        // "Ryebread". The stronger tier takes the result set alone, so the
        // prefix-only record does not dilute it.
        assertEquals(listOf("food:rye-bread"), results.map { it.canonicalItemId })
    }

    @Test
    fun `a query accounting for more of a record outranks a more qualified record`() {
        val search = CatalogSearch(
            listOf(
                CatalogRecord(
                    canonicalItemId = "food:bread-rye-reduced-sodium",
                    displayName = "Bread, rye, reduced sodium",
                ),
                CatalogRecord(canonicalItemId = "food:rye-bread", displayName = "Bread, rye"),
            ),
        )

        val results = search.search("rye bread")

        // Both match TOKEN_SUBSET, so the tier cannot separate them. The query
        // accounts for all of "Bread, rye" but half of the sodium variant, so
        // the unqualified record ranks first. Canonical id would order these the
        // other way round, which is why it is only the final tiebreak.
        assertEquals(
            listOf("food:rye-bread", "food:bread-rye-reduced-sodium"),
            results.map { it.canonicalItemId },
        )
    }

    @Test
    fun `records matching in the same tier are ordered by canonical id, not catalog order`() {
        val search = CatalogSearch(
            listOf(
                CatalogRecord(canonicalItemId = "food:zucchini-raw", displayName = "Zucchini raw"),
                CatalogRecord(canonicalItemId = "food:apple-raw", displayName = "Apple raw"),
            ),
        )

        val results = search.search("raw")

        // Both match TOKEN_SUBSET equally. Catalog order must not decide the
        // result order, or the same query returns different orderings on two
        // devices whose packages were built in a different sequence.
        assertEquals(
            listOf("food:apple-raw", "food:zucchini-raw"),
            results.map { it.canonicalItemId },
        )
    }

    @Test
    fun `a single-character typo still finds the record`() {
        val search = CatalogSearch(
            listOf(CatalogRecord(canonicalItemId = "food:rye-bread", displayName = "Bread, rye")),
        )

        val results = search.search("bred")

        assertEquals(listOf("food:rye-bread"), results.map { it.canonicalItemId })
    }

    @Test
    fun `an alternate name finds the record it names`() {
        val search = CatalogSearch(
            listOf(
                CatalogRecord(
                    canonicalItemId = "food:eggplant",
                    displayName = "Eggplant",
                    alternateNames = listOf("aubergine", "баклажан"),
                ),
            ),
        )

        // aubergine and eggplant are different words for one food, not
        // spellings or inflections of each other, so nothing but data connects
        // them. Without this, the catalog serves only the wording its author
        // happened to choose.
        assertEquals(listOf("food:eggplant"), search.search("aubergine").map { it.canonicalItemId })
    }

    @Test
    fun `an unrelated query returns nothing rather than a nearest guess`() {
        val search = CatalogSearch(
            listOf(
                CatalogRecord(canonicalItemId = "food:rye-bread", displayName = "Bread, rye"),
                CatalogRecord(canonicalItemId = "food:platano", displayName = "Plátano"),
            ),
        )

        assertEquals(emptyList(), search.search("motorcycle").map { it.canonicalItemId })
    }
}
