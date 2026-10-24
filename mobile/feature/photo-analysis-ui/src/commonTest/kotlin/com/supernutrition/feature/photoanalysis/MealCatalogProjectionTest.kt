package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.PartialNutrientProfile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MealCatalogProjectionTest {
    @Test
    fun `catalog portions per100 and legacy sugar remain independently truthful`() {
        val catalog = UiCatalogFixture()
        val meal = mergedMealHistory(emptyList(), listOf(uiCatalogMeal()), catalog).single()
        val portion = assertNotNull(meal.nutrition(false))
        assertEquals("162.5 kcal", nutrientValue(portion.energyKcal, "kcal"))
        assertEquals("12.26 g", nutrientValue(portion.proteinGrams))
        assertEquals("12.18 g", nutrientValue(portion.fatGrams))
        assertEquals("904 mg", nutrientValue(portion.sodiumMilligrams, "mg"))
        assertEquals("0.28 g", nutrientValue(portion.sugarGrams))
        assertNull(portion.fiberGrams)
        assertEquals(325.0, meal.nutrition(true)?.energyKcal)
        assertEquals(catalog.provenance, meal.nutritionProjection?.provenance)
        val legacy = confirmedMealHistory(listOf(profileConfirmedFood("a", "legacy", "Old food"))).single()
        assertEquals(100.0, legacy.nutrition(false)?.energyKcal)
        assertNull(legacy.nutrition(false)?.sugarGrams)
    }

    @Test
    fun `missing stale renamed and failed sources abstain while cancellation propagates`() {
        val saved = uiCatalogMeal()
        val catalog = UiCatalogFixture()
        assertNull(mergedMealHistory(emptyList(), listOf(saved)).single().nutrition(false))
        val renamed = mergedMealHistory(emptyList(), listOf(saved.copy(name = "Edited")), catalog).single()
        assertNull(renamed.nutrition(false))
        catalog.snapshot = catalog.snapshot.copy(contentDigest = "b".repeat(64))
        assertNull(mergedMealHistory(emptyList(), listOf(saved), catalog).single().nutrition(false))
        catalog.snapshot = catalog.product.snapshot
        catalog.lookupResult = null
        assertNull(mergedMealHistory(emptyList(), listOf(saved), catalog).single().nutrition(false))
        catalog.lookupFailure = IllegalStateException("source closed")
        assertNull(mergedMealHistory(emptyList(), listOf(saved), catalog).single().nutrition(false))
        catalog.lookupFailure = CancellationException("disposed")
        assertFailsWith<CancellationException> { mergedMealHistory(emptyList(), listOf(saved), catalog) }
    }

    @Test
    fun `daily totals keep null contributors and exclude other days`() {
        val source = UiCatalogFixture()
        val known = uiCatalogMeal()
        val entries = mergedMealHistory(emptyList(), listOf(known, known.copy(id = "yesterday", localDay = 9)), source)
        assertEquals(162.5, todayMealNutrients(entries, 10).energyKcal)
        assertNull(todayMealNutrients(entries, 10).fiberGrams)
        assertNull(todayMealNutrients(entries, 8).proteinGrams)
        val mixed = mergedMealHistory(emptyList(), listOf(known, diaryMealFixture("unknown")), source)
        assertNull(todayMealNutrients(mixed, 10).energyKcal)
        assertNull(todayMealNutrients(mixed, 10).proteinGrams)
    }

    @Test
    fun `overflow stays unknown without losing known zeros and load failure hides all totals`() {
        val source = UiCatalogFixture(
            uiCatalogProduct().copy(
                nutrientsPer100Grams = PartialNutrientProfile(Double.MAX_VALUE, 0.0, 0.0, 0.0, 0.0, null, 0.0),
            ),
        )
        val saved = uiCatalogMeal(source.product).copy(portionGrams = 100.0)
        val entries = mergedMealHistory(emptyList(), listOf(saved, saved.copy(id = "second")), source)
        val totals = todayMealNutrients(entries, 10)
        assertNull(totals.energyKcal)
        assertEquals(0.0, totals.proteinGrams)
        assertEquals("0 g", liveHomeScreenModel("Today", 2, todayNutrients = totals).protein.value)
        val unavailable = liveHomeScreenModel("Today", 2, storageLoadFailed = true, todayNutrients = totals)
        assertEquals("—", unavailable.dailySummaryValue)
        assertEquals("—", unavailable.protein.value)
        assertEquals("—", unavailable.fiber.value)
        assertEquals("—", unavailable.fats.value)
        assertNull(unavailable.protein.progressFraction)
    }
}
