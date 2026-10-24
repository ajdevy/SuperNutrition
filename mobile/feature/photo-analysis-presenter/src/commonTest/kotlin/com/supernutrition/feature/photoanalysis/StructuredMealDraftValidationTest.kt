package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class StructuredMealDraftValidationTest {
    @Test
    fun `structured labels retain commas and list boundaries while editable fields normalize`() {
        val draft = draft().copy(
            name = "  Carrots and tofu  ",
            ingredients = listOf("carrots, cooked", "豆腐"),
            note = "  Original note\nsecond line  ",
        )
        val result = valid(draft)
        assertEquals("Carrots and tofu", result.name)
        assertEquals(125.5, result.portionGrams)
        assertEquals(draft.ingredients, result.ingredients)
        assertEquals("Original note\nsecond line", result.note)
        assertNull(result.catalogBinding)
    }

    @Test
    fun `structured ingredient content budget excludes formatting and never splits comma labels`() {
        val labels = List(MealDraftLimits.INGREDIENTS_MAX_COUNT) {
            "i".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH - 3) + ",x"
        }
        val result = valid(draft().copy(ingredients = labels))
        assertEquals(labels, result.ingredients)
        assertEquals(MealDraftLimits.INGREDIENTS_MAX_COUNT, result.ingredients.size)
    }

    @Test
    fun `structured blank oversized and excessive ingredients are rejected intact`() {
        val cases = listOf(
            listOf(" ") to MealDraftError.INGREDIENT_REQUIRED,
            listOf("x".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH) + ",") to MealDraftError.INGREDIENT_TOO_LONG,
            List(MealDraftLimits.INGREDIENTS_MAX_COUNT + 1) { "pea" } to MealDraftError.TOO_MANY_INGREDIENTS,
            List(MealDraftLimits.INGREDIENTS_MAX_COUNT) {
                "x".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH)
            } to MealDraftError.INGREDIENTS_TOO_LONG,
        )
        cases.forEach { (ingredients, error) ->
            assertError(error, draft().copy(ingredients = ingredients))
        }
    }

    @Test
    fun `structured input still validates name note and optional portion`() {
        val invalid = assertIs<MealDraftValidation.Invalid>(
            MealDraftValidator.validate(
                draft().copy(name = " ", note = "n".repeat(MealDraftLimits.NOTE_MAX_LENGTH + 1)),
                null,
                null,
            ),
        )
        assertEquals(setOf(MealDraftError.NAME_REQUIRED, MealDraftError.NOTE_TOO_LONG), invalid.errors)
        assertError(MealDraftError.NAME_TOO_LONG, draft().copy(name = "n".repeat(MealDraftLimits.NAME_MAX_LENGTH + 1)))
        val optional = valid(draft().copy(portionGrams = null))
        assertNull(optional.portionGrams)
        assertEquals(emptyList(), optional.ingredients)
    }

    @Test
    fun `new structured binding is retained only with a current exact product`() {
        val catalog = PresenterCatalogFixture()
        val selected = boundDraft()
        val result = valid(selected, catalog = catalog)
        assertEquals(selected, result)
        assertError(MealDraftError.CATALOG_UNAVAILABLE, selected)
        assertError(MealDraftError.CATALOG_SELECTION_INVALID, selected.copy(name = "Other"), catalog = catalog)
        assertError(
            MealDraftError.CATALOG_SELECTION_INVALID,
            selected.copy(ingredients = listOf("Onion")),
            catalog = catalog,
        )
        catalog.snapshot = catalog.snapshot.copy(contentDigest = "b".repeat(64))
        assertError(MealDraftError.CATALOG_SELECTION_INVALID, selected, catalog = catalog)
    }

    @Test
    fun `unchanged historical structured binding survives unavailable stale and missing source`() {
        val existing = presenterCatalogMeal()
        val edited = boundDraft().copy(portionGrams = 200.5, note = "Changed note")
        assertEquals(edited, valid(edited, existing))
        val catalog = PresenterCatalogFixture()
        catalog.snapshot = catalog.snapshot.copy(contentDigest = "b".repeat(64))
        assertEquals(edited, valid(edited, existing, catalog))
        catalog.snapshot = catalog.product.snapshot
        catalog.lookupResult = null
        assertEquals(edited, valid(edited, existing, catalog))
        catalog.failure = IllegalStateException("source closed")
        assertEquals(edited, valid(edited, existing, catalog))
    }

    @Test
    fun `structured historical name or ingredient edits clear the old binding`() {
        val existing = presenterCatalogMeal()
        val renamed = valid(boundDraft().copy(name = "Other"), existing)
        assertEquals("Other", renamed.name)
        assertNull(renamed.catalogBinding)
        val ingredients = listOf("carrots, cooked", "豆腐")
        val changedIngredients = valid(boundDraft().copy(ingredients = ingredients), existing)
        assertEquals(ingredients, changedIngredients.ingredients)
        assertNull(changedIngredients.catalogBinding)
    }

    @Test
    fun `a different structured selection cannot borrow historical trust`() {
        val existing = presenterCatalogMeal()
        val other = presenterCatalogMeal(presenterCatalogProduct("Other", "food:other"))
        val selection = boundDraft().copy(name = other.name, catalogBinding = other.catalogBinding)
        assertError(MealDraftError.CATALOG_UNAVAILABLE, selection, existing)
        assertError(MealDraftError.CATALOG_UNAVAILABLE, boundDraft(), existing.copy(catalogBinding = null))
    }

    @Test
    fun `structured portions reject invalid numbers and bound records also require a portion`() {
        val existing = presenterCatalogMeal()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -1.0).forEach { grams ->
            assertError(MealDraftError.PORTION_INVALID, draft().copy(portionGrams = grams))
            assertError(MealDraftError.PORTION_INVALID, boundDraft().copy(portionGrams = grams), existing)
        }
        assertError(MealDraftError.PORTION_INVALID, boundDraft().copy(portionGrams = null))
        assertError(MealDraftError.PORTION_INVALID, boundDraft().copy(portionGrams = null), existing)
        assertError(
            MealDraftError.PORTION_INVALID,
            boundDraft().copy(portionGrams = Double.MAX_VALUE),
            catalog = PresenterCatalogFixture(),
        )
        assertEquals(Double.MAX_VALUE, valid(draft().copy(portionGrams = Double.MAX_VALUE)).portionGrams)
    }

    @Test
    fun `structured source cancellation propagates for new and historical bindings`() {
        val catalog = PresenterCatalogFixture().also { it.failure = CancellationException("cancelled") }
        assertFailsWith<CancellationException> { MealDraftValidator.validate(boundDraft(), null, catalog) }
        assertFailsWith<CancellationException> {
            MealDraftValidator.validate(boundDraft(), presenterCatalogMeal(), catalog)
        }
    }

    private fun draft() = ValidatedMealDraft("Soup", 125.5, emptyList(), "")

    private fun boundDraft(): ValidatedMealDraft {
        val existing = presenterCatalogMeal()
        return ValidatedMealDraft(
            existing.name,
            existing.portionGrams,
            existing.ingredients,
            "",
            existing.catalogBinding,
        )
    }

    private fun valid(
        draft: ValidatedMealDraft,
        existing: SavedMeal? = null,
        catalog: ApprovedNutritionCatalog? = null,
    ): ValidatedMealDraft =
        assertIs<MealDraftValidation.Valid>(MealDraftValidator.validate(draft, existing, catalog)).value

    private fun assertError(
        expected: MealDraftError,
        draft: ValidatedMealDraft,
        existing: SavedMeal? = null,
        catalog: ApprovedNutritionCatalog? = null,
    ) {
        assertEquals(
            setOf(expected),
            assertIs<MealDraftValidation.Invalid>(MealDraftValidator.validate(draft, existing, catalog)).errors,
        )
    }
}
