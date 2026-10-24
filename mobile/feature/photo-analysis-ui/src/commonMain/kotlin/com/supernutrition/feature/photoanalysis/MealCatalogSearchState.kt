package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import kotlin.coroutines.cancellation.CancellationException

internal const val CATALOG_QUERY_LIMIT = 256
internal const val CATALOG_PAGE_SIZE = 20

/** Search text and paging are transient and never participate in a saved-state registry. */
internal class MealCatalogSearchState {
    var query by mutableStateOf("")
        private set
    var visibleCount by mutableIntStateOf(CATALOG_PAGE_SIZE)
        private set
    var attempt by mutableIntStateOf(0)
        private set

    fun updateQuery(value: String) {
        query = value.take(CATALOG_QUERY_LIMIT)
        visibleCount = CATALOG_PAGE_SIZE
    }

    fun showMore(total: Int) {
        visibleCount = minOf(visibleCount + CATALOG_PAGE_SIZE, total)
    }

    fun retry() {
        attempt++
    }
}

internal sealed interface MealCatalogSearchResult {
    data object Loading : MealCatalogSearchResult
    data object Unavailable : MealCatalogSearchResult
    data object Prompt : MealCatalogSearchResult
    data class Matches(val entries: List<CatalogProductSearchResult>) : MealCatalogSearchResult
}

internal fun searchMealCatalog(state: MealCatalogState, query: String): MealCatalogSearchResult = when (state) {
    MealCatalogState.Loading -> MealCatalogSearchResult.Loading
    MealCatalogState.Unavailable -> MealCatalogSearchResult.Unavailable
    is MealCatalogState.Ready -> searchReadyCatalog(state, query)
}

private fun searchReadyCatalog(state: MealCatalogState.Ready, query: String): MealCatalogSearchResult = try {
    if (query.isBlank()) {
        MealCatalogSearchResult.Prompt
    } else {
        MealCatalogSearchResult.Matches(state.catalog.search(query))
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    MealCatalogSearchResult.Unavailable
}
