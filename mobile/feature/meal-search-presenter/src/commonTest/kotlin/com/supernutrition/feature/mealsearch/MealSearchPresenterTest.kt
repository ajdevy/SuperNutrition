package com.supernutrition.feature.mealsearch

import com.supernutrition.domain.nutrition.CatalogSearch
import com.supernutrition.domain.nutrition.DishExpansion
import com.supernutrition.domain.nutrition.DishLocale
import com.supernutrition.domain.nutrition.DishLocalePreference
import com.supernutrition.domain.nutrition.MealSearch
import com.supernutrition.domain.nutrition.SeedCatalog
import com.supernutrition.domain.nutrition.SeedDishes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealSearchPresenterTest {
    private val presenter = MealSearchPresenter(
        MealSearch(
            foods = CatalogSearch(SeedCatalog.records),
            dishes = DishExpansion(SeedDishes.records),
            localePreference = DishLocalePreference(SeedDishes.marketAttribution),
        ),
    )

    @Test
    fun `an untouched search box is not a failed search`() {
        // showsNoResults is not rows.isEmpty(). Collapsing them greets someone
        // who has typed nothing with "no results".
        for (blank in listOf("", "   ", "\n")) {
            val state = presenter.onQueryChanged(blank)

            assertEquals(emptyList(), state.rows)
            assertTrue(!state.showsNoResults, "a blank box has not searched for anything yet")
        }
    }

    @Test
    fun `a query that finds nothing does say so`() {
        val state = presenter.onQueryChanged("wildebeest")

        assertEquals(emptyList(), state.rows)
        assertTrue(state.showsNoResults)
    }

    @Test
    fun `a food row shows the catalog's own name`() {
        val row = assertIs<MealSearchRow.Food>(presenter.onQueryChanged("boiled egg").rows.first())

        assertEquals("food:egg-boiled", row.id)
        assertEquals("Egg, boiled", row.title)
        assertNull(row.note, "nothing went unmatched, so there is nothing to report")
    }

    @Test
    fun `a dish row says how many items a tap will add`() {
        val row = assertIs<MealSearchRow.Dish>(presenter.onQueryChanged("black bean bowl").rows.first())

        assertEquals("dish:black-bean-bowl", row.id)
        assertEquals("Black bean bowl", row.title)
        assertEquals(4, row.ingredientCount)
    }

    @Test
    fun `an unhonoured preparation is on the row, not behind a tap`() {
        // Someone choosing between results needs to know the catalog only has
        // this food raw while they are choosing, not after committing to it.
        val row = assertIs<MealSearchRow.Food>(presenter.onQueryChanged("grilled chicken breast").rows.first())

        assertEquals("not matched: grilled", row.note)
    }

    @Test
    fun `the query is echoed back so the field and the results cannot disagree`() {
        assertEquals("boiled egg", presenter.onQueryChanged("boiled egg").query)
        assertEquals("   ", presenter.onQueryChanged("   ").query)
    }

    @Test
    fun `market preference reaches the rows`() {
        val us = presenter.onQueryChanged("bean", DishLocale(market = "US")).rows
        val ru = presenter.onQueryChanged("bean", DishLocale(market = "RU")).rows

        assertEquals(us.map { it.id }.toSet(), ru.map { it.id }.toSet(), "ranking must not drop a row")
        assertTrue(us.map { it.id } != ru.map { it.id }, "and the market must change the order")
    }
}
