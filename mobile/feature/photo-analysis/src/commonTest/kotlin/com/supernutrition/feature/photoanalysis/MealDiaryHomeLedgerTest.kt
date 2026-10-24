package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogNutrientProfile
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.GroundedNutritionResult
import com.supernutrition.domain.nutrition.NutritionCatalogItemType
import com.supernutrition.domain.nutrition.NutritionCatalogRecord
import com.supernutrition.domain.nutrition.NutritionServing
import com.supernutrition.domain.nutrition.RewardFoodAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MealDiaryHomeLedgerTest {
    @Test
    fun `daily filtering preserves authored supersession and unknown contributors across both ledgers`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        try {
            val diary = MealDiary({ MealRecordContext("shared", 20_020, "12:34") }, catalogSession = session)
            val today = assertNotNull(diary.add(catalog.structuredDraft(), SavedMealSource.TEXT))
            val yesterday = today.copy(id = "historical", localDay = 20_019)
            val foods = listOf(
                homeGroundedFood(catalog, 20_020, "shared"),
                homeGroundedFood(catalog, 20_020, "other"),
                homeGroundedFood(catalog, 20_019, "shared"),
            )
            catalog.lookups = 0
            val home = diary.homeScreenModel("Today", 20_020, foods, listOf(today, yesterday), session.state.value)
            assertEquals("2 meals recorded", home.dailySummaryValue)
            assertEquals("17.26 g", home.protein.value)
            assertEquals("—", home.fiber.value)
            assertEquals(1, catalog.lookups)
        } finally {
            session.close()
        }
    }
}

private fun homeGroundedFood(catalog: FeatureCatalogFixture, day: Int, mealId: String): ConfirmedConsumedFood {
    val nutrients = CatalogNutrientProfile(100.0, 5.0, 10.0, 2.0, 2.0, 20.0)
    val record = NutritionCatalogRecord(
        canonicalItemId = "grounded-$day-$mealId",
        displayName = "Grounded test food",
        itemType = NutritionCatalogItemType.INGREDIENT,
        catalogContentDigest = catalog.snapshot.contentDigest,
        nutrientsPer100Grams = nutrients,
        defaultServing = null,
    )
    return ConfirmedConsumedFood.fromGroundedResult(
        entryId = "entry-$day-$mealId",
        localDay = day,
        mealId = mealId,
        grounded = GroundedNutritionResult(catalog.snapshot, record, NutritionServing(100.0, "g", 100.0), nutrients),
        attributes = RewardFoodAttributes(hasProtein = false),
    )
}
