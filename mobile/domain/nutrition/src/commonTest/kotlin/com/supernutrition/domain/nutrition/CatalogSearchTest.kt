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
}
