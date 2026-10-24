package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MealCatalogValidationTest {
    @Test
    fun `new bindings require the current source and a matching exact product identity`() {
        val catalog = PresenterCatalogFixture()
        val selected = MealDraft(portion = "150").withCatalogProduct(catalog.product)
        assertError(MealDraftError.CATALOG_UNAVAILABLE, MealDraftValidator.validate(selected))
        assertError(
            MealDraftError.CATALOG_SELECTION_INVALID,
            MealDraftValidator.validate(selected.copy(name = "Other"), catalog),
        )
        assertError(
            MealDraftError.CATALOG_SELECTION_INVALID,
            MealDraftValidator.validate(selected.copy(ingredients = "Onion"), catalog),
        )
        catalog.lookupResult = null
        assertError(MealDraftError.CATALOG_SELECTION_INVALID, MealDraftValidator.validate(selected, catalog))
    }

    @Test
    fun `new stale or mismatched bindings are rejected and ordinary source failure is unavailable`() {
        val catalog = PresenterCatalogFixture()
        val selected = MealDraft(portion = "150").withCatalogProduct(catalog.product)
        catalog.snapshot = catalog.snapshot.copy(contentDigest = "b".repeat(64))
        assertError(MealDraftError.CATALOG_SELECTION_INVALID, MealDraftValidator.validate(selected, catalog))
        catalog.snapshot = catalog.product.snapshot
        catalog.lookupResult = catalog.product.copy(canonicalItemId = "food:other")
        assertError(MealDraftError.CATALOG_SELECTION_INVALID, MealDraftValidator.validate(selected, catalog))
        catalog.failure = IllegalStateException("private source failure")
        assertError(MealDraftError.CATALOG_UNAVAILABLE, MealDraftValidator.validate(selected, catalog))
    }

    @Test
    fun `historical bindings remain editable through stale missing and failed source lookups`() {
        val meal = presenterCatalogMeal()
        val draft = meal.toMealDraft().copy(note = "Note", portion = "200")
        val catalog = PresenterCatalogFixture()
        val cases = listOf<() -> Unit>(
            { catalog.snapshot = catalog.product.snapshot.copy(contentDigest = "b".repeat(64)) },
            { catalog.lookupResult = null },
            { catalog.lookupResult = catalog.product.copy(canonicalItemId = "food:other") },
            { catalog.lookupResult = catalog.product.copy(displayName = "Other product") },
            { catalog.failure = IllegalStateException("source closed") },
        )
        cases.forEach { configure ->
            catalog.snapshot = catalog.product.snapshot
            catalog.lookupResult = catalog.product
            catalog.failure = null
            configure()
            val validated = assertIs<MealDraftValidation.Valid>(MealDraftValidator.validate(draft, meal, catalog)).value
            assertEquals(meal.catalogBinding, validated.catalogBinding)
        }
        val known = assertIs<MealDraftValidation.Valid>(
            MealDraftValidator.validate(draft, meal, PresenterCatalogFixture()),
        ).value
        assertEquals(meal.catalogBinding, known.catalogBinding)
    }

    @Test
    fun `every bound selection needs positive finite grams including historical records`() {
        val meal = presenterCatalogMeal()
        listOf("", " ", "0", "-1", "NaN", "Infinity", "1e999", "1,2,3").forEach { grams ->
            val draft = meal.toMealDraft().copy(portion = grams)
            assertError(MealDraftError.PORTION_INVALID, MealDraftValidator.validate(draft, meal))
            assertError(MealDraftError.PORTION_INVALID, MealDraftValidator.validate(draft, PresenterCatalogFixture()))
        }
        val overflowing = meal.toMealDraft().copy(portion = Double.MAX_VALUE.toString())
        assertError(
            MealDraftError.PORTION_INVALID,
            MealDraftValidator.validate(overflowing, PresenterCatalogFixture()),
        )
        assertError(
            MealDraftError.PORTION_INVALID,
            MealDraftValidator.validate(overflowing, meal, PresenterCatalogFixture()),
        )
    }

    @Test
    fun `unbounded selected identifiers fail before a lookup or persistence`() {
        val product = presenterCatalogProduct()
        val oversized = listOf(
            product.copy(
                snapshot = product.snapshot.copy(identity = NutritionCatalogIdentity("x".repeat(257), "2018-04")),
            ),
            product.copy(
                snapshot = product.snapshot.copy(identity = NutritionCatalogIdentity("usda", "x".repeat(257))),
            ),
            product.copy(snapshot = product.snapshot.copy(catalogCountry = "x".repeat(33))),
            product.copy(canonicalItemId = "x".repeat(257)),
        )
        oversized.forEach { selected ->
            assertError(
                MealDraftError.CATALOG_SELECTION_INVALID,
                MealDraftValidator.validate(
                    MealDraft(portion = "150").withCatalogProduct(selected),
                    PresenterCatalogFixture(selected),
                ),
            )
        }
    }

    @Test
    fun `new binding cannot inherit trust from a different historical selection`() {
        val existing = presenterCatalogMeal()
        val other = presenterCatalogProduct("Other", "food:other")
        val selected = existing.toMealDraft().withCatalogProduct(other)
        assertError(MealDraftError.CATALOG_UNAVAILABLE, MealDraftValidator.validate(selected, existing))
        val existingUnbound = existing.copy(catalogBinding = null)
        assertError(
            MealDraftError.CATALOG_UNAVAILABLE,
            MealDraftValidator.validate(existing.toMealDraft(), existingUnbound),
        )
        val noPortion = existing.copy(portionGrams = null)
        assertError(MealDraftError.PORTION_INVALID, MealDraftValidator.validate(noPortion.toMealDraft(), noPortion))
    }

    @Test
    fun `source cancellation is rethrown for both new and historical selections`() {
        val catalog = PresenterCatalogFixture().also { it.failure = CancellationException("cancelled") }
        val existing = presenterCatalogMeal()
        assertFailsWith<CancellationException> { MealDraftValidator.validate(existing.toMealDraft(), catalog) }
        assertFailsWith<CancellationException> {
            MealDraftValidator.validate(existing.toMealDraft(), existing, catalog)
        }
    }

    @Test
    fun `typed bound drafts still reject malformed serving values before source lookup`() {
        val existing = presenterCatalogMeal()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach { grams ->
            val draft = ValidatedMealDraft(existing.name, grams, emptyList(), "", existing.catalogBinding)
            assertEquals(MealDraftError.PORTION_INVALID, mealCatalogBindingError(draft, existing, null))
        }
    }

    private fun assertError(expected: MealDraftError, result: MealDraftValidation) {
        assertEquals(setOf(expected), assertIs<MealDraftValidation.Invalid>(result).errors)
    }
}
