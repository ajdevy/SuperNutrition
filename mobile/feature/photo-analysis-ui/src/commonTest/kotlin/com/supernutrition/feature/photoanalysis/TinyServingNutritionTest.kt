package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogNutrientProfile
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.GroundedNutritionResult
import com.supernutrition.domain.nutrition.NutritionServing
import com.supernutrition.domain.nutrition.RewardFoodAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TinyServingNutritionTest {
    @Test
    fun `known zero nutrients stay known per100 grams for a tiny positive serving`() {
        val fixture = profileConfirmedFood("zero", "tiny-meal", "Known zero food")
        val zeros = CatalogNutrientProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val grounded = GroundedNutritionResult(
            catalogSnapshot = fixture.grounded.catalogSnapshot,
            record = fixture.grounded.record.copy(nutrientsPer100Grams = zeros),
            serving = NutritionServing(1e-308, "g", 1e-308),
            nutrients = zeros,
        )
        val food = ConfirmedConsumedFood.fromGroundedResult(
            entryId = "tiny-zero",
            localDay = 10,
            mealId = "tiny-meal",
            grounded = grounded,
            attributes = RewardFoodAttributes(hasProtein = false),
        )
        val nutrients = assertNotNull(confirmedMealHistory(listOf(food)).single().nutrition(true))
        assertEquals(0.0, nutrients.energyKcal)
        assertEquals(0.0, nutrients.proteinGrams)
        assertEquals(0.0, nutrients.carbohydrateGrams)
        assertEquals(0.0, nutrients.fatGrams)
        assertEquals(0.0, nutrients.fiberGrams)
        assertEquals(0.0, nutrients.sodiumMilligrams)
    }

    @Test
    fun `overflowing legacy mass hides per100 and portion mass but keeps known zero portions`() {
        val fixture = profileConfirmedFood("huge-zero", "huge-meal", "Known zero food")
        val zeros = CatalogNutrientProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val grounded = GroundedNutritionResult(
            catalogSnapshot = fixture.grounded.catalogSnapshot,
            record = fixture.grounded.record.copy(nutrientsPer100Grams = zeros),
            serving = NutritionServing(Double.MAX_VALUE, "g", Double.MAX_VALUE),
            nutrients = zeros,
        )
        val foods = listOf("first", "second").map { id ->
            ConfirmedConsumedFood.fromGroundedResult(
                entryId = id,
                localDay = 10,
                mealId = "huge-meal",
                grounded = grounded,
                attributes = RewardFoodAttributes(hasProtein = false),
            )
        }
        val meal = confirmedMealHistory(foods).single()
        assertNull(meal.portionGrams())
        assertNull(meal.nutrition(true))
        assertEquals(0.0, meal.nutrition(false)?.energyKcal)
        assertEquals(0.0, meal.nutrition(false)?.proteinGrams)
    }
}
