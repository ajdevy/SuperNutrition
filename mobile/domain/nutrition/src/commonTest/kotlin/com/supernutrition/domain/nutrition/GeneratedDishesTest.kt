package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The generated FNDDS dish set, exercised through the real expander.
 *
 * A dish is a name that means a composition rather than a record, so what this
 * data buys is the step from "chicken curry" to the several foods it contains.
 * The weights that would turn that into nutrition are deliberately not here.
 *
 * The two properties worth protecting are opposites, and both are checked below.
 * Every ingredient must carry no weight, because a gram figure without an
 * approved package is fabricated nutrition wearing a valid provenance chain —
 * signed, versioned, arithmetically sound, and describing a meal nobody ate.
 * And every ingredient must name a record that actually exists, because an id
 * that resolves to nothing is a dish that silently expands to fewer foods than
 * it claims — FM-6 arriving through the data instead of through the matcher.
 */
class GeneratedDishesTest {
    private val expansion = DishExpansion(generatedDishes)

    @Test
    fun `the dish set is present and its ids are unique`() {
        assertTrue(generatedDishes.size > 400, "generated dishes hold ${generatedDishes.size} records")
        val ids = generatedDishes.map { it.canonicalDishId }
        assertEquals(ids.size, ids.toSet().size, "a duplicate dish id would make an expansion ambiguous")
    }

    @Test
    fun `no generated ingredient carries a weight`() {
        // The G-2 line, on the generated set. DishExpansionTest asserts the same
        // property for the hand-written seed dishes; this is the one that would
        // actually catch a generator quietly learning to emit the source's
        // gram column, which it has and which stays in the JSON.
        val weighed = generatedDishes
            .flatMap { dish -> dish.ingredients.map { dish.canonicalDishId to it } }
            .filter { it.second.grams != null }

        assertEquals(emptyList(), weighed.take(5))
    }

    @Test
    fun `every ingredient names a record the catalog actually holds`() {
        // A dangling id is invisible: the dish resolves, the name looks right,
        // and it expands to fewer foods than it names. Checked here rather than
        // trusted from the generator, because the two files are produced by
        // different scripts from different USDA releases and only agree by
        // construction.
        val known = generatedCatalog.mapTo(mutableSetOf()) { it.canonicalItemId }
        val dangling = generatedDishes
            .flatMap { dish -> dish.ingredients.map { dish.canonicalDishId to it.canonicalItemId } }
            .filterNot { it.second in known }

        assertEquals(emptyList(), dangling.take(10))
    }

    @Test
    fun `a dish that expands to one food is not a dish`() {
        // The selection rule, stated as a property. A single-ingredient "dish"
        // is just that food under another name, and it would offer the user a
        // review step with nothing to review.
        val thin = generatedDishes.filter { it.ingredients.size < 2 }.map { it.canonicalDishId }

        assertEquals(emptyList(), thin.take(10))
    }

    @Test
    fun `dish names reach their compositions`() {
        // The point of the data. Names taken from the file itself rather than
        // invented, so this fails if the selection changes out from under it.
        for (name in listOf("Alfredo sauce", "Ambrosia", "Almond butter")) {
            val proposals = expansion.expand(name)
            assertTrue(proposals.isNotEmpty(), "\"$name\" reached no dish")
            assertTrue(
                proposals.first().ingredients.size >= 2,
                "\"$name\" expanded to ${proposals.first().ingredients.size} ingredients",
            )
        }
    }

    @Test
    fun `no dish name smuggles a nutrition number`() {
        // Same guard as the catalog's, for the same reason: DishRecord has no
        // field for a number, so a display name is the only way one could
        // arrive. A numeric claim, not the bare word — "low-calorie" describes
        // a food rather than asserting a figure.
        val energyClaim = Regex("""\d\s*(kcal|cal\b|calories)""", RegexOption.IGNORE_CASE)
        val suspicious = generatedDishes
            .filter { energyClaim.containsMatchIn(it.displayName) }
            .map { it.displayName }

        assertEquals(emptyList(), suspicious)
    }

    @Test
    fun `a counting or joining word expands to no dish`() {
        // FM-1 over the dish set. Dishes are searched with the same matcher as
        // foods, so the same guard has to hold — and dish names are longer and
        // more numerous, which gives a bare token more to land on.
        for (query in listOf("and", "with", "с", "и", "200", "grams")) {
            assertEquals(emptyList(), expansion.expand(query), "\"$query\" reached a dish")
        }
    }
}
