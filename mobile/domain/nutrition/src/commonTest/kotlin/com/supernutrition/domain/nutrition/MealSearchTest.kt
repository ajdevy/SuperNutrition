package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MealSearchTest {
    private val search = MealSearch(
        foods = CatalogSearch(SeedCatalog.records),
        dishes = DishExpansion(SeedDishes.records),
        localePreference = DishLocalePreference(SeedDishes.marketAttribution),
    )

    @Test
    fun `a food name reaches the food and a dish name reaches the dish`() {
        // The case a fixed precedence rule gets wrong in one direction or the
        // other. Match strength distinguishes them with no rule to learn.
        assertIs<MealSearchHit.Food>(search.search("boiled egg").first())
        assertIs<MealSearchHit.Dish>(search.search("chicken and rice").first())
    }

    @Test
    fun `a dish hit carries what a tap would add`() {
        // "Chicken curry, 4 ingredients" is the difference between choosing a
        // composition and discovering one after the fact.
        val hit = assertIs<MealSearchHit.Dish>(search.search("black bean bowl").first())

        assertEquals("Black bean bowl", hit.displayName)
        assertEquals(4, hit.ingredients.size)
    }

    @Test
    fun `foods and dishes share one list rather than one being appended to the other`() {
        // "bean" matches a food and two dishes. If either kind were simply
        // concatenated after the other, this ordering would be by kind rather
        // than by how well each matched.
        val kinds = search.search("bean").map { if (it is MealSearchHit.Dish) "dish" else "food" }

        assertTrue(kinds.contains("dish"), "expected dishes in the result")
        assertTrue(kinds.contains("food"), "expected foods in the result")
        assertTrue(kinds.distinct().size > 1, "one list, not two concatenated blocks")
    }

    @Test
    fun `a stronger tier outranks a weaker one regardless of kind or market`() {
        // Tier is what the merge promises to order by. Coverage inside a tier is
        // left to the catalog and to locale, so asserting full strength order
        // here would pin behaviour the design deliberately does not fix.
        for (locale in listOf(DishLocale.Unspecified, DishLocale(market = "US"), DishLocale(market = "RU"))) {
            val hits = search.search("bean", locale)
            val strongestFirst = hits.zipWithNext().all { (a, b) ->
                a.strength >= b.strength || a.strength.sameTier(
                    b.strength,
                )
            }
            assertTrue(strongestFirst, "a weaker tier must never precede a stronger one: $hits")
        }
    }

    @Test
    fun `locale still decides between two equally matched dishes`() {
        // Locale reorders dishes before the merge, and the merge sort is stable,
        // so a market preference survives into the combined list.
        val us = search.search("bean", DishLocale(market = "US")).filterIsInstance<MealSearchHit.Dish>()
        val ru = search.search("bean", DishLocale(market = "RU")).filterIsInstance<MealSearchHit.Dish>()

        assertEquals("dish:black-bean-bowl", us.first().canonicalDishId)
        assertEquals("dish:bean-and-egg-bowl", ru.first().canonicalDishId)
        assertEquals(us.map { it.canonicalDishId }.toSet(), ru.map { it.canonicalDishId }.toSet())
    }

    @Test
    fun `a dish and an ingredient it contains are both offered`() {
        // "bean" reaches the bowl and the beans. They are different things —
        // one is a meal, one is an item — and someone who ate only beans is as
        // real as someone who ate the bowl. Suppressing either guesses which,
        // and the row label already tells them apart, so there is nothing to
        // disambiguate by hiding.
        val hits = search.search("bean")
        val dish = hits.filterIsInstance<MealSearchHit.Dish>()
            .firstOrNull { it.canonicalDishId == "dish:black-bean-bowl" }
        val food = hits.filterIsInstance<MealSearchHit.Food>()
            .firstOrNull { it.canonicalItemId == "food:bean-black" }

        assertTrue(dish != null, "expected the bowl")
        assertTrue(food != null, "expected the beans")
        assertTrue(
            dish.ingredients.any { it.canonicalItemId == "food:bean-black" },
            "and the food offered separately is genuinely one of the dish's ingredients",
        )
    }

    @Test
    fun `an unmatched preparation still travels on a food hit`() {
        val hit = assertIs<MealSearchHit.Food>(search.search("grilled chicken breast").first())

        assertEquals(listOf("grilled"), hit.unmatchedPreparation)
    }
}

class DishPortionTest {
    @Test
    fun `the default is one serving and no decisions`() {
        val portion = DishPortion()

        assertTrue(portion.isDefault, "the default must cost the user nothing")
        assertEquals(1.0, portion.scaleFor("food:chicken-breast-raw"))
    }

    @Test
    fun `one serving figure scales every ingredient`() {
        val portion = DishPortion(dishServings = 2.0)

        assertEquals(2.0, portion.scaleFor("food:chicken-breast-raw"))
        assertEquals(2.0, portion.scaleFor("food:rice-white-cooked"))
        assertTrue(!portion.isDefault)
    }

    @Test
    fun `an adjusted ingredient replaces the dish serving rather than compounding it`() {
        // "Double the chicken" must mean double, not double-of-whatever-the-dish
        // -scale-happens-to-be, or the same words mean different amounts
        // depending on an unrelated earlier choice.
        val portion = DishPortion(dishServings = 3.0).adjust("food:chicken-breast-raw", 2.0)

        assertEquals(2.0, portion.scaleFor("food:chicken-breast-raw"))
        assertEquals(3.0, portion.scaleFor("food:rice-white-cooked"))
    }

    @Test
    fun `an adjustment can be undone back to the dish serving`() {
        val portion = DishPortion(dishServings = 2.0)
            .adjust("food:chicken-breast-raw", 5.0)
            .clearAdjustment("food:chicken-breast-raw")

        assertEquals(2.0, portion.scaleFor("food:chicken-breast-raw"))
    }

    @Test
    fun `a portion cannot be zero or negative`() {
        // Removing an ingredient is an edit to the split, not a scale of zero:
        // an item scaled to nothing would still be listed as eaten.
        kotlin.test.assertFailsWith<IllegalArgumentException> { DishPortion(dishServings = 0.0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { DishPortion(dishServings = -1.0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            DishPortion(ingredientScales = mapOf("food:x" to 0.0))
        }
    }
}
