package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualIngredientParserTest {
    private val catalog = CatalogSearch(SeedCatalog.records)
    private val parser = ManualIngredientParser(
        mealSearch = MealSearch(
            foods = catalog,
            dishes = DishExpansion(SeedDishes.records),
            localePreference = DishLocalePreference(SeedDishes.marketAttribution),
        ),
        displayNameFor = catalog::displayNameFor,
    )

    @Test
    fun `blank input has no ingredients or unresolved fragments`() {
        assertEquals(ManualIngredientParseResult(emptyList(), emptyList()), parser.parse("  "))
    }

    @Test
    fun `whole dish is expanded into catalog ingredients`() {
        val result = parser.parse("chicken and rice")

        assertEquals(
            listOf("food:chicken-breast-raw", "food:rice-white-cooked"),
            result.ingredients.map { it.canonicalItemId },
        )
        assertEquals(listOf("Chicken and rice", "Chicken and rice"), result.ingredients.map { it.sourceDishName })
        assertTrue(result.unresolvedFragments.isEmpty())
    }

    @Test
    fun `comma separated ingredients are added without dish expansion`() {
        val result = parser.parse("boiled egg, rice")

        assertEquals(listOf("food:egg-boiled", "food:rice-white-cooked"), result.ingredients.map { it.canonicalItemId })
        assertEquals(listOf(null, null), result.ingredients.map { it.sourceDishName })
    }

    @Test
    fun `unresolved fragments are returned`() {
        val result = parser.parse("rice, food from another planet")

        assertEquals(listOf("food:rice-white-cooked"), result.ingredients.map { it.canonicalItemId })
        assertEquals(listOf("food from another planet"), result.unresolvedFragments)
    }

    @Test
    fun `production parser is wired to the generated identity catalogs`() {
        val result = productionManualIngredientParser().parse("milk")

        assertTrue(result.ingredients.isNotEmpty())
        assertTrue(result.ingredients.all { it.displayName.isNotBlank() })
        assertTrue(result.unresolvedFragments.isEmpty())
    }

    @Test
    fun `a dish inside a separated list is expanded alongside a direct food`() {
        val result = parser.parse("black bean bowl, boiled egg")

        assertEquals(
            listOf(
                "food:bean-black",
                "food:rice-white-cooked",
                "food:avocado-raw",
                "food:coriander-leaf",
                "food:egg-boiled",
            ),
            result.ingredients.map { it.canonicalItemId },
        )
    }

    @Test
    fun `a single unresolved phrase is reported rather than discarded`() {
        val result = parser.parse("food from another planet")

        assertTrue(result.ingredients.isEmpty())
        assertEquals(listOf("food from another planet"), result.unresolvedFragments)
    }

    @Test
    fun `dish ingredients without catalog display names are omitted`() {
        val parserWithoutNames = ManualIngredientParser(
            mealSearch = MealSearch(
                foods = catalog,
                dishes = DishExpansion(SeedDishes.records),
                localePreference = DishLocalePreference(SeedDishes.marketAttribution),
            ),
            displayNameFor = { null },
        )

        val result = parserWithoutNames.parse("chicken and rice")

        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.unresolvedFragments.isEmpty())
    }
}
