package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.CatalogMealNutritionUnknownReason
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class MealCatalogPresentationTest {
    @Test
    fun `catalog state is independent from draft content and publishes only explicit ready source`() {
        val catalog = PresenterCatalogFixture()
        assertNull(MealCatalogState.Loading.catalogOrNull)
        assertNull(MealCatalogState.Unavailable.catalogOrNull)
        val ready = MealCatalogState.Ready(catalog)
        assertSame(catalog, ready.catalogOrNull)
        assertEquals(MealCatalogState.Ready(catalog), ready)
    }

    @Test
    fun `one saved identity projection supplies scaled values without copying numbers into the meal`() {
        val meal = presenterCatalogMeal()
        val result = assertIs<CatalogMealNutritionResolution.Grounded>(
            meal.resolveCatalogNutrition(PresenterCatalogFixture()),
        )
        assertEquals(300.0, result.result.nutrients.energyKcal)
        assertEquals(7.5, result.result.nutrients.proteinGrams)
        assertEquals(0.0, result.result.nutrients.fiberGrams)
        assertNull(result.result.nutrients.sugarGrams)
    }

    @Test
    fun `source unavailable stale and missing bindings never expose numeric values`() {
        val meal = presenterCatalogMeal()
        assertUnknown(CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE, meal.resolveCatalogNutrition(null))
        assertUnknown(
            CatalogMealNutritionUnknownReason.MISSING_BINDING,
            meal.copy(catalogBinding = null).resolveCatalogNutrition(PresenterCatalogFixture()),
        )
        val stale = PresenterCatalogFixture().also { it.snapshot = it.snapshot.copy(contentDigest = "b".repeat(64)) }
        assertUnknown(CatalogMealNutritionUnknownReason.STALE_BINDING, meal.resolveCatalogNutrition(stale))
    }

    @Test
    fun `wrong display identity or recipe ingredients cannot borrow a product numeric profile`() {
        val catalog = PresenterCatalogFixture()
        val meal = presenterCatalogMeal()
        assertUnknown(
            CatalogMealNutritionUnknownReason.RECORD_MISMATCH,
            meal.copy(name = "Other").resolveCatalogNutrition(catalog),
        )
        assertUnknown(
            CatalogMealNutritionUnknownReason.RECORD_MISMATCH,
            meal.copy(ingredients = listOf("Onion")).resolveCatalogNutrition(catalog),
        )
        catalog.failure = IllegalStateException("closed")
        assertUnknown(CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE, meal.resolveCatalogNutrition(catalog))
        catalog.failure = CancellationException()
        assertFailsWith<CancellationException> { meal.resolveCatalogNutrition(catalog) }
    }

    private fun assertUnknown(reason: CatalogMealNutritionUnknownReason, resolution: CatalogMealNutritionResolution) {
        assertEquals(reason, assertIs<CatalogMealNutritionResolution.Unknown>(resolution).reason)
    }
}
