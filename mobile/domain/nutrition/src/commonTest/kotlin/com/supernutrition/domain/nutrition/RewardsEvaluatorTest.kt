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
    fun `explicit identity only meal counts as recording without inventing nutrient facts`() {
        val snapshot = RewardsEvaluator.evaluate(
            RewardsInput(emptyList(), 10, recordedMeals = listOf(RecordedMealEvidence("soup", 10))),
        )
        val habit = snapshot.status(RewardId.SEVEN_DAYS_RECORDED)
        val nutrition = snapshot.cards.filter { it.id != RewardId.SEVEN_DAYS_RECORDED }
        assertEquals(1, habit.progress.current)
        assertFalse(habit.isEarned)
        assertTrue(nutrition.all { it.progress.current == 0 })
        assertTrue(nutrition.none { it.isEarned })
    }

    @Test
    fun `seven explicit identity only days earn only the recording habit`() {
        val recorded = (3..9).map { day -> RecordedMealEvidence("meal-$day", day) }
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(emptyList(), 10, recordedMeals = recorded))
        assertEquals(7, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertTrue(snapshot.status(RewardId.SEVEN_DAYS_RECORDED).isEarned)
        assertTrue(snapshot.cards.filter { it.id != RewardId.SEVEN_DAYS_RECORDED }.none { it.isEarned })
        assertEquals(0, snapshot.status(RewardId.THIRTY_PRODUCTS).progress.current)
    }

    @Test
    fun `grounded and explicit recording days form one deduplicated consecutive run`() {
        val grounded = listOf(4, 5, 7, 9, 10).map { day -> food(day, "grounded-$day", "food-$day") }
        val recorded = listOf(6, 8, 10, 11).map { day -> RecordedMealEvidence("meal-$day", day) } +
            RecordedMealEvidence("another-meal-10", 10)
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(grounded, 10, recordedMeals = recorded))
        assertEquals(7, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(5, snapshot.status(RewardId.THIRTY_PRODUCTS).progress.current)
    }

    @Test
    fun `unknown third recorded meal blocks protein in every meal award`() {
        val grounded = listOf(food(10, "breakfast", "egg"), food(10, "lunch", "beans"))
        val recorded = listOf("breakfast", "lunch", "dinner").map { RecordedMealEvidence(it, 10) }
        val status = RewardsEvaluator.evaluate(RewardsInput(grounded, 10, recordedMeals = recorded))
            .status(RewardId.PROTEIN_ALL_DAY)
        assertEquals(2, status.progress.current)
        assertEquals(3, status.progress.target)
        assertFalse(status.isEarned)
    }

    @Test
    fun `overlapping and duplicate recorded meal IDs do not increase protein target`() {
        val grounded = listOf(food(10, "breakfast", "egg"), food(10, "lunch", "beans"))
        val recorded = listOf(
            RecordedMealEvidence("breakfast", 10),
            RecordedMealEvidence("lunch", 10),
            RecordedMealEvidence("breakfast", 10),
            RecordedMealEvidence("unknown-yesterday", 9),
            RecordedMealEvidence("unknown-tomorrow", 11),
        )
        val status = RewardsEvaluator.evaluate(RewardsInput(grounded, 10, recordedMeals = recorded))
            .status(RewardId.PROTEIN_ALL_DAY)
        assertEquals(2, status.progress.current)
        assertEquals(2, status.progress.target)
        assertTrue(status.isEarned)
    }

    @Test
    fun `identity only run respects gaps future dates and the first calendar day`() {
        val scattered = listOf(4, 6, 8, 10, 11).map { day -> RecordedMealEvidence("meal-$day", day) }
        val current = RewardsEvaluator.evaluate(RewardsInput(emptyList(), 10, recordedMeals = scattered))
        val missed = RewardsEvaluator.evaluate(
            RewardsInput(emptyList(), 10, recordedMeals = listOf(RecordedMealEvidence("old", 8))),
        )
        val future = RewardsEvaluator.evaluate(RewardsInput(emptyList(), 0, recordedMeals = scattered))
        val firstDay = RewardsEvaluator.evaluate(
            RewardsInput(emptyList(), 0, recordedMeals = listOf(RecordedMealEvidence("first", 0))),
        )
        assertEquals(1, current.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(0, missed.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(0, future.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(1, firstDay.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
    }

    @Test
    fun `recorded meal evidence rejects blank IDs and negative dates`() {
        assertFails { RecordedMealEvidence("", 10) }
        assertFails { RecordedMealEvidence(" ", 10) }
        assertFails { RecordedMealEvidence("meal", -1) }
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
    fun `orange and yellow occupy one color bucket and cannot earn a third color`() {
        val colors = listOf(RewardFoodColor.ORANGE, RewardFoodColor.YELLOW, RewardFoodColor.RED, RewardFoodColor.GREEN)
        val entries = colors.mapIndexed { index, color ->
            food(
                day = 10,
                meal = "color-meal",
                id = "color-$index",
                attributes = RewardFoodAttributes(hasProtein = false, color = color),
            )
        }
        val warmColors = RewardsEvaluator.evaluate(RewardsInput(entries.take(2), 10)).status(RewardId.COLORS_OF_DAY)
        val twoGroups = RewardsEvaluator.evaluate(RewardsInput(entries.take(3), 10)).status(RewardId.COLORS_OF_DAY)
        val threeGroups = RewardsEvaluator.evaluate(RewardsInput(entries, 10)).status(RewardId.COLORS_OF_DAY)
        assertEquals(1, warmColors.progress.current)
        assertEquals(2, twoGroups.progress.current)
        assertFalse(twoGroups.isEarned)
        assertEquals(3, threeGroups.progress.current)
        assertTrue(threeGroups.isEarned)
    }

    @Test
    fun `blue purple remains a distinct fourth group alongside combined orange yellow`() {
        val entries = RewardFoodColor.entries.map { color ->
            food(
                day = 10,
                meal = "color-meal",
                id = "color-$color",
                attributes = RewardFoodAttributes(hasProtein = false, color = color),
            )
        }
        val allGroups = RewardsEvaluator.evaluate(RewardsInput(entries, 10)).status(RewardId.COLORS_OF_DAY)
        val bluePurple = RewardsEvaluator.evaluate(RewardsInput(entries.takeLast(1), 10))
            .status(RewardId.COLORS_OF_DAY)
        assertEquals(4, allGroups.progress.current)
        assertEquals(3, allGroups.progress.target)
        assertTrue(allGroups.isEarned)
        assertEquals(1, bluePurple.progress.current)
        assertFalse(bluePurple.isEarned)
    }

    @Test
    fun `separated recorded dates do not add to the current consecutive run`() {
        val entries = listOf(4, 6, 8, 10).map { day -> food(day, "meal-$day", "food-$day") }
        val status = RewardsEvaluator.evaluate(RewardsInput(entries, 10)).status(RewardId.SEVEN_DAYS_RECORDED)
        assertEquals(1, status.progress.current)
        assertFalse(status.isEarned)
    }

    @Test
    fun `today can continue a seven day run ending yesterday`() {
        val entries = (3..9).map { day -> food(day, "meal-$day", "food-$day") }
        val snapshot = RewardsEvaluator.evaluate(RewardsInput(entries, 10))
        assertEquals(7, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertTrue(snapshot.status(RewardId.SEVEN_DAYS_RECORDED).isEarned)
        assertEquals(6, snapshot.status(RewardId.THIRTY_PRODUCTS).progress.current)
    }

    @Test
    fun `missing yesterday resets the run while future entries remain excluded`() {
        val entries = listOf(6, 7, 8, 11).map { day -> food(day, "meal-$day", "food-$day") }
        val status = RewardsEvaluator.evaluate(RewardsInput(entries, 10)).status(RewardId.SEVEN_DAYS_RECORDED)
        assertEquals(0, status.progress.current)
        assertFalse(status.isEarned)
    }

    @Test
    fun `empty today preserves only the consecutive suffix ending yesterday`() {
        val entries = listOf(4, 6, 8, 9, 11).map { day -> food(day, "meal-$day", "food-$day") }
        val status = RewardsEvaluator.evaluate(RewardsInput(entries, 10)).status(RewardId.SEVEN_DAYS_RECORDED)
        assertEquals(2, status.progress.current)
        assertFalse(status.isEarned)
    }

    @Test
    fun `future dates cannot begin a run or cross the first day boundary`() {
        val entries = listOf(1, 2, Int.MAX_VALUE).map { day -> food(day, "meal-$day", "food-$day") }
        val status = RewardsEvaluator.evaluate(RewardsInput(entries, 0)).status(RewardId.SEVEN_DAYS_RECORDED)
        val finalDay = RewardsEvaluator.evaluate(RewardsInput(entries, Int.MAX_VALUE))
            .status(RewardId.SEVEN_DAYS_RECORDED)
        assertEquals(0, status.progress.current)
        assertEquals(1, finalDay.progress.current)
    }

    @Test
    fun `recorded run deduplicates meals caps seven and supports the first calendar day`() {
        val entries = (0..10).map { day -> food(day, "meal-$day", "food-$day") } +
            food(10, "another-meal", "another-food")
        val fullRun = RewardsEvaluator.evaluate(RewardsInput(entries, 10)).status(RewardId.SEVEN_DAYS_RECORDED)
        val firstDay = RewardsEvaluator.evaluate(RewardsInput(entries, 0)).status(RewardId.SEVEN_DAYS_RECORDED)
        val tomorrow = RewardsEvaluator.evaluate(RewardsInput(entries.take(1), 1)).status(RewardId.SEVEN_DAYS_RECORDED)
        assertEquals(7, fullRun.progress.current)
        assertEquals(1, firstDay.progress.current)
        assertEquals(1, tomorrow.progress.current)
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
    fun `fermented card counts different days and seven day card counts consecutive days`() {
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
        assertEquals(0, snapshot.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
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
