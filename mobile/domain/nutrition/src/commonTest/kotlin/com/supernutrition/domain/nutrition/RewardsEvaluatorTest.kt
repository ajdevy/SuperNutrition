package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardsEvaluatorTest {
    @Test
    fun `empty day keeps every card ready without earning anything`() {
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(emptyList(), asOfDay = 10))
        val firstCalendarDay = RewardsEvaluator.evaluate(RewardsInput(emptyList(), asOfDay = 0))

        assertEquals(7, snapshot.cards.size)
        assertTrue(snapshot.cards.all { it.progress.current == 0 })
        assertTrue(snapshot.cards.all { !it.isEarned })
        assertEquals(2, snapshot.status(RewardId.PROTEIN_ALL_DAY).progress.target)
        assertEquals(7, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.target)
        assertEquals(0, firstCalendarDay.asOfDay)
    }

    @Test
    fun `protein requires at least two meals and protein in every recorded meal`() {
        val twoMeals = listOf(
            food(day = 10, meal = "breakfast", id = "egg", attributes = RewardFoodAttributes(hasProtein = true)),
            food(day = 10, meal = "lunch", id = "beans", attributes = RewardFoodAttributes(hasProtein = true)),
        )
        assertEquals("egg", twoMeals.first().displayName)
        assertTrue(
            RewardsEvaluator.evaluate(RewardsInput(twoMeals, 10))
                .status(RewardId.PROTEIN_ALL_DAY).isEarned,
        )

        val missingProtein = twoMeals + food(
            day = 10,
            meal = "dinner",
            id = "salad",
            attributes = RewardFoodAttributes(hasProtein = false),
        )
        val status = RewardsEvaluator.evaluate(RewardsInput(missingProtein, 10))
            .status(RewardId.PROTEIN_ALL_DAY)
        assertEquals(2, status.progress.current)
        assertEquals(3, status.progress.target)
        assertFalse(status.isEarned)
    }

    @Test
    fun `daily fat fiber and color cards use their distinct catalog groups`() {
        val entries = listOf(
            food(
                day = 10,
                meal = "breakfast",
                id = "oats",
                attributes = RewardFoodAttributes(
                    hasProtein = false,
                    fiberGroup = RewardFiberGroup.WHOLE_GRAINS,
                    color = RewardFoodColor.YELLOW,
                ),
            ),
            food(
                day = 10,
                meal = "lunch",
                id = "lentils",
                attributes = RewardFoodAttributes(
                    hasProtein = true,
                    fatGroup = RewardFatGroup.OILS,
                    fiberGroup = RewardFiberGroup.LEGUMES,
                    color = RewardFoodColor.RED,
                ),
            ),
            food(
                day = 10,
                meal = "lunch",
                id = "greens",
                attributes = RewardFoodAttributes(
                    hasProtein = false,
                    color = RewardFoodColor.GREEN,
                ),
            ),
        )
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(entries, 10))

        assertTrue(snapshot.status(RewardId.FATS_PRESENT).isEarned)
        assertTrue(snapshot.status(RewardId.FIBER_PRESENT).isEarned)
        assertTrue(snapshot.status(RewardId.COLORS_OF_DAY).isEarned)
        assertEquals(2, snapshot.status(RewardId.FIBER_PRESENT).progress.current)
        assertEquals(3, snapshot.status(RewardId.COLORS_OF_DAY).progress.current)
    }

    @Test
    fun `weekly products are distinct and herbs are capped at five`() {
        val ordinary = (1..25).map { index ->
            food(day = 10, meal = "meal-$index", id = "ordinary-$index")
        }
        val herbs = (1..6).map { index ->
            food(
                day = 10,
                meal = "herb-meal-$index",
                id = "herb-$index",
                attributes = RewardFoodAttributes(hasProtein = false, isHerbOrSpice = true),
            )
        }
        val duplicate = food(day = 10, meal = "another-meal", id = "ordinary-1")
        val status = RewardsEvaluator.evaluate(RewardsInput(ordinary + herbs + duplicate, 10))
            .status(RewardId.THIRTY_PRODUCTS)

        assertEquals(30, status.progress.current)
        assertTrue(status.isEarned)
    }

    @Test
    fun `fermented card counts different days and seven day card counts calendar coverage`() {
        val entries = (4..10).map { day ->
            food(
                day = day,
                meal = "breakfast-$day",
                id = "food-$day",
                attributes = RewardFoodAttributes(hasProtein = true, isFermented = day in 8..10),
            )
        } + food(
            day = 10,
            meal = "lunch",
            id = "fermented-duplicate-day",
            attributes = RewardFoodAttributes(hasProtein = false, isFermented = true),
        )
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(entries, 10))

        assertEquals(3, snapshot.status(RewardId.FERMENTED_THREE_DAYS).progress.current)
        assertTrue(snapshot.status(RewardId.FERMENTED_THREE_DAYS).isEarned)
        assertEquals(7, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertTrue(snapshot.status(RewardId.SEVEN_DAYS_RECORDED).isEarned)
    }

    @Test
    fun `entries outside the inclusive seven day window are ignored`() {
        val entries = listOf(
            food(day = 3, meal = "old", id = "old"),
            food(day = 4, meal = "inside", id = "inside"),
            food(day = 11, meal = "future", id = "future"),
        )
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(entries, asOfDay = 10))

        assertEquals(1, snapshot.status(RewardId.THIRTY_PRODUCTS).progress.current)
        assertEquals(1, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
    }

    @Test
    fun `reward models reject invalid reference values and duplicate cards`() {
        assertFails { RewardsInput(emptyList(), asOfDay = -1) }
        assertFails { RewardProgress(current = -1, target = 1) }
        assertFails { RewardProgress(current = 0, target = 0) }
        assertFails {
            ConfirmedConsumedFood.fromGroundedResult(
                entryId = "entry",
                localDay = -1,
                mealId = "meal",
                grounded = grounded("food"),
                attributes = RewardFoodAttributes(hasProtein = true),
            )
        }
        val status = RewardStatus(RewardId.FATS_PRESENT, RewardProgress(current = 1, target = 1))
        assertFails { RewardsSnapshot(asOfDay = 10, cards = listOf(status, status)) }
    }

    private fun RewardsSnapshot.status(id: RewardId): RewardStatus = cards.first { it.id == id }

    private fun food(
        day: Int,
        meal: String,
        id: String,
        attributes: RewardFoodAttributes = RewardFoodAttributes(hasProtein = true),
    ): ConfirmedConsumedFood = ConfirmedConsumedFood.fromGroundedResult(
        entryId = "$day-$meal-$id",
        localDay = day,
        mealId = meal,
        grounded = grounded(id),
        attributes = attributes,
    )

    private fun grounded(id: String): GroundedNutritionResult {
        val digest = "a".repeat(64)
        val record = NutritionCatalogRecord(
            canonicalItemId = id,
            displayName = id,
            itemType = NutritionCatalogItemType.INGREDIENT,
            catalogContentDigest = digest,
            nutrientsPer100Grams = CatalogNutrientProfile(
                energyKcal = 100.0,
                proteinGrams = 5.0,
                carbohydrateGrams = 10.0,
                fatGrams = 2.0,
                fiberGrams = 2.0,
                sodiumMilligrams = 20.0,
            ),
            defaultServing = null,
        )
        return GroundedNutritionResult(
            catalogSnapshot = NutritionCatalogSnapshot(
                identity = NutritionCatalogIdentity("test", "1"),
                catalogCountry = "US",
                contentDigest = digest,
            ),
            record = record,
            serving = NutritionServing(amount = 100.0, unit = "g", grams = 100.0),
            nutrients = record.nutrientsPer100Grams,
        )
    }
}
