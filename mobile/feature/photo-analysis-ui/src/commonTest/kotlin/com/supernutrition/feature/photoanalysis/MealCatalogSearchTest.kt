package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MealCatalogSearchTest {
    @Test
    fun `availability ordinary failure no results and cancellation remain distinct`() {
        val catalog = UiCatalogFixture()
        assertSame(MealCatalogSearchResult.Loading, searchMealCatalog(MealCatalogState.Loading, "cheese"))
        assertSame(MealCatalogSearchResult.Unavailable, searchMealCatalog(MealCatalogState.Unavailable, "cheese"))
        assertSame(MealCatalogSearchResult.Prompt, searchMealCatalog(MealCatalogState.Ready(catalog), " "))
        catalog.results = emptyList()
        val empty = assertIs<MealCatalogSearchResult.Matches>(searchMealCatalog(MealCatalogState.Ready(catalog), "x"))
        assertEquals(emptyList(), empty.entries)
        catalog.searchFailure = IllegalStateException("database closed")
        assertSame(MealCatalogSearchResult.Unavailable, searchMealCatalog(MealCatalogState.Ready(catalog), "x"))
        val cancelled = CancellationException("source disposed")
        catalog.searchFailure = cancelled
        val thrown = assertFailsWith<CancellationException> {
            searchMealCatalog(MealCatalogState.Ready(catalog), "x")
        }
        assertSame(cancelled, thrown)
    }

    @Test
    fun `query is bounded and paging can expose all matches before a new search resets it`() {
        val search = MealCatalogSearchState()
        search.updateQuery("q".repeat(300))
        assertEquals(CATALOG_QUERY_LIMIT, search.query.length)
        search.showMore(45)
        assertEquals(40, search.visibleCount)
        search.showMore(45)
        assertEquals(45, search.visibleCount)
        search.updateQuery("cheese")
        assertEquals(CATALOG_PAGE_SIZE, search.visibleCount)
        search.retry()
        assertEquals(1, search.attempt)
        val result = searchMealCatalog(MealCatalogState.Ready(UiCatalogFixture()), search.query)
        assertEquals(
            listOf(CatalogProductSearchResult(uiCatalogProduct(), listOf("fried"))),
            assertIs<MealCatalogSearchResult.Matches>(result).entries,
        )
    }
}
