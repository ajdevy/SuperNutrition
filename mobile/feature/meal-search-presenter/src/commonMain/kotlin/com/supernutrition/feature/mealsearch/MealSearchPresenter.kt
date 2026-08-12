package com.supernutrition.feature.mealsearch

import com.supernutrition.domain.nutrition.DishLocale
import com.supernutrition.domain.nutrition.MealSearch
import com.supernutrition.domain.nutrition.MealSearchHit

/**
 * One row of the search list, already resolved to what a surface draws.
 *
 * The presenter decides what a row says so that the same decision is not made
 * twice in Compose and again in SwiftUI. Both hosts render the same strings.
 */
sealed interface MealSearchRow {
    val id: String
    val title: String

    /**
     * [note] carries a preparation the catalog could not honour — "grilled"
     * against a record that only exists raw. It is on the row rather than
     * hidden behind a tap because a user choosing between results needs it
     * while choosing, not afterwards.
     */
    data class Food(override val id: String, override val title: String, val note: String?) : MealSearchRow

    /**
     * [ingredientCount] is what makes a dish row honest. Tapping a food adds
     * one item and tapping a dish adds several, and the row says which before
     * the tap rather than the list explaining itself afterwards.
     */
    data class Dish(override val id: String, override val title: String, val ingredientCount: Int) : MealSearchRow
}

/**
 * What the search surface shows.
 *
 * [showsNoResults] is deliberately not `rows.isEmpty()`. An untouched search box
 * and a query that found nothing are different states, and collapsing them
 * greets a user who has typed nothing with "no results".
 */
data class MealSearchUiState(
    val query: String = "",
    val rows: List<MealSearchRow> = emptyList(),
    val showsNoResults: Boolean = false,
)

/**
 * Turns a typed query into rows.
 *
 * Deliberately synchronous and stateless: the search underneath is an in-memory
 * deterministic lookup with no I/O, so a coroutine here would add ceremony and
 * a scope to leak without making anything faster. When a licensed package
 * arrives with real storage, that is the change that earns suspension.
 */
class MealSearchPresenter(private val search: MealSearch) {
    fun onQueryChanged(query: String, locale: DishLocale = DishLocale.Unspecified): MealSearchUiState {
        if (query.isBlank()) return MealSearchUiState(query = query)

        val rows = search.search(query, locale).map { hit ->
            when (hit) {
                is MealSearchHit.Food -> MealSearchRow.Food(
                    id = hit.canonicalItemId,
                    title = hit.displayName,
                    // One conditional rather than a chain of safe calls. Two
                    // `?.` links on the same nullable produce a second null
                    // branch that nothing can reach independently — an
                    // artifact of the chaining rather than a case, and not
                    // something to write a test for.
                    note = if (hit.unmatchedPreparation.isEmpty()) {
                        null
                    } else {
                        "not matched: " + hit.unmatchedPreparation.joinToString(", ")
                    },
                )

                is MealSearchHit.Dish -> MealSearchRow.Dish(
                    id = hit.canonicalDishId,
                    title = hit.displayName,
                    ingredientCount = hit.ingredients.size,
                )
            }
        }
        return MealSearchUiState(query = query, rows = rows, showsNoResults = rows.isEmpty())
    }
}
