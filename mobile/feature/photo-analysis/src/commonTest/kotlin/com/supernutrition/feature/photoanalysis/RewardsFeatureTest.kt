package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogNutrientProfile
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.GroundedNutritionResult
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogItemType
import com.supernutrition.domain.nutrition.NutritionCatalogRecord
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.NutritionServing
import com.supernutrition.domain.nutrition.RewardFoodAttributes
import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.domain.nutrition.RewardsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardsFeatureTest {
    @Test
    fun `authored replacement hides obsolete grounded facts from visible foods and nutrient rewards`() {
        val original = confirmedFood("original", "egg")
        val feature = RewardsFeature(localDay = { 10 }, initialFoods = listOf(original))
        val replacement = SavedMeal(
            "breakfast",
            10,
            "12:00",
            "Corrected bowl",
            null,
            listOf("Rice"),
            source = SavedMealSource.TEXT,
        )

        feature.updateRecordedMeals(listOf(replacement))

        assertTrue(feature.confirmedFoods.value.isEmpty())
        assertEquals(0, feature.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        assertEquals(1, feature.snapshot.value.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(listOf(original), feature.ledger.entries.value)
    }

    @Test
    fun `supersession is scoped to both meal ID and local day and preserves unrelated facts`() {
        val original = confirmedFood("original", "egg")
        val unrelated = confirmedFood("unrelated", "beans", mealId = "lunch")
        val previousDay = confirmedFood("previous-day", "egg", localDay = 9)
        val feature = RewardsFeature(localDay = { 10 }, initialFoods = listOf(original, unrelated, previousDay))
        val replacement = SavedMeal(
            "breakfast",
            10,
            "12:00",
            "Corrected bowl",
            null,
            listOf("Rice"),
            source = SavedMealSource.TEXT,
        )

        feature.updateRecordedMeals(listOf(replacement))

        assertEquals(listOf(unrelated, previousDay), feature.confirmedFoods.value)
        assertEquals(1, feature.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        assertEquals(listOf(original, unrelated, previousDay), feature.ledger.entries.value)
    }

    @Test
    fun `removing an authored replacement never reveals superseded grounded facts`() {
        val original = confirmedFood("original", "egg")
        val feature = RewardsFeature(localDay = { 10 }, initialFoods = listOf(original))
        val replacement = SavedMeal(
            "breakfast",
            10,
            "12:00",
            "Corrected bowl",
            null,
            emptyList(),
            source = SavedMealSource.TEXT,
        )
        feature.updateRecordedMeals(listOf(replacement))

        feature.updateRecordedMeals(emptyList())

        assertTrue(feature.confirmedFoods.value.isEmpty())
        assertEquals(0, feature.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        assertEquals(0, feature.snapshot.value.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(listOf(original), feature.ledger.entries.value)
    }

    @Test
    fun `later ledger changes use the same projection and cannot revive superseded facts`() {
        val original = confirmedFood("original", "egg")
        val feature = RewardsFeature(localDay = { 10 }, initialFoods = listOf(original))
        val replacement = SavedMeal(
            "breakfast",
            10,
            "12:00",
            "Corrected bowl",
            null,
            emptyList(),
            source = SavedMealSource.TEXT,
        )
        feature.updateRecordedMeals(listOf(replacement))
        val refreshed = confirmedFood("refreshed", "egg")
        val unrelated = confirmedFood("unrelated", "beans", mealId = "lunch")

        feature.replaceConfirmedFoods(listOf(refreshed, unrelated))
        feature.recordConfirmedFood(original)

        assertEquals(listOf(unrelated), feature.confirmedFoods.value)
        assertEquals(1, feature.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        assertTrue(feature.removeConfirmedFood(unrelated.entryId))
        assertTrue(feature.confirmedFoods.value.isEmpty())
        assertEquals(0, feature.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        assertEquals(listOf(refreshed, original), feature.ledger.entries.value)
    }

    @Test
    fun `recording grounded food refreshes retained snapshot`() {
        val feature = RewardsFeature(localDay = { 10 })

        feature.recordGroundedFood(
            entryId = "entry-1",
            localDay = 10,
            mealId = "breakfast",
            grounded = rewardsTestGroundedResult("egg"),
            attributes = RewardFoodAttributes(hasProtein = true),
        )

        val status = feature.snapshot.value.cards.first { it.id == RewardId.PROTEIN_ALL_DAY }
        assertEquals(1, status.progress.current)
        assertEquals(2, status.progress.target)
        assertEquals(1, feature.confirmedFoods.value.size)
    }

    @Test
    fun `ledger updates rewards and survives through its store`() {
        val store = InMemoryConsumedFoodLedgerStore()
        val feature = RewardsFeature(localDay = { 10 }, ledgerStore = store)
        val food = confirmedFood("entry-1", "egg")

        feature.recordConfirmedFood(food)
        assertEquals(listOf(food), store.load())
        assertEquals(listOf(food), feature.ledger.entriesForDay(10))

        val restored = RewardsFeature(localDay = { 10 }, ledgerStore = store)
        assertEquals(listOf(food), restored.confirmedFoods.value)
        assertEquals(1, restored.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        assertTrue(restored.removeConfirmedFood("entry-1"))
        assertFalse(restored.removeConfirmedFood("missing"))
        assertTrue(restored.confirmedFoods.value.isEmpty())
    }

    @Test
    fun `manual diary dates refresh habit without awarding nutrient facts and deletion reverses progress`() {
        val feature = RewardsFeature(localDay = { 10 })
        val meal = SavedMeal("manual", 10, "12:00", "Soup", null, emptyList(), source = SavedMealSource.TEXT)
        feature.updateRecordedMeals(listOf(meal))
        assertEquals(1, feature.snapshot.value.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
        assertEquals(0, feature.snapshot.value.status(RewardId.PROTEIN_ALL_DAY).progress.current)
        feature.updateRecordedMeals(emptyList())
        assertEquals(0, feature.snapshot.value.status(RewardId.SEVEN_DAYS_RECORDED).progress.current)
    }

    private fun RewardsSnapshot.status(id: RewardId) = cards.first { it.id == id }

    private fun confirmedFood(
        entryId: String,
        id: String,
        mealId: String = "breakfast",
        localDay: Int = 10,
    ): ConfirmedConsumedFood = ConfirmedConsumedFood.fromGroundedResult(
        entryId = entryId,
        localDay = localDay,
        mealId = mealId,
        grounded = rewardsTestGroundedResult(id),
        attributes = RewardFoodAttributes(hasProtein = true),
    )
}

internal fun rewardsTestGroundedResult(id: String): GroundedNutritionResult {
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
