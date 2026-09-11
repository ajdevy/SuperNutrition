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
    fun `recording grounded food refreshes retained snapshot`() {
        val feature = RewardsFeature(localDay = { 10 })

        feature.recordGroundedFood(
            entryId = "entry-1",
            localDay = 10,
            mealId = "breakfast",
            grounded = grounded("egg"),
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

    private fun RewardsSnapshot.status(id: RewardId) = cards.first { it.id == id }

    private fun confirmedFood(entryId: String, id: String): ConfirmedConsumedFood =
        ConfirmedConsumedFood.fromGroundedResult(
            entryId = entryId,
            localDay = 10,
            mealId = "breakfast",
            grounded = grounded(id),
            attributes = RewardFoodAttributes(hasProtein = true),
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
