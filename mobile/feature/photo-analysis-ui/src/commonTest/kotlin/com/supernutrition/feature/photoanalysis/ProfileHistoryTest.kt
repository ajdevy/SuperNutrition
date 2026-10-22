package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.CatalogNutrientProfile
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.GroundedNutritionResult
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogItemType
import com.supernutrition.domain.nutrition.NutritionCatalogRecord
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.NutritionServing
import com.supernutrition.domain.nutrition.RewardFoodAttributes
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProfileHistoryTest {
    @Test
    fun `history counts meals rather than food entries and shows grounded names`() = runComposeUiTest {
        val foods = listOf(
            profileConfirmedFood("entry-1", "breakfast", "Egg"),
            profileConfirmedFood("entry-2", "breakfast", "Toast"),
            profileConfirmedFood("entry-3", "lunch", "Lentils"),
        )
        setContent { ProfileScreen("Alex", foods, ProfileScreenActions({}, {})) }
        onNodeWithText("2 meals saved").assertIsDisplayed()
        onNodeWithText("3 meals saved").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).performClick()
        onNodeWithText("Egg, Toast").assertIsDisplayed()
        onNodeWithText("Lentils").assertIsDisplayed()
    }

    @Test
    fun `one meal uses the singular history description`() = runComposeUiTest {
        setContent {
            ProfileScreen(
                "Alex",
                listOf(profileConfirmedFood("entry-1", "meal", "Rice")),
                ProfileScreenActions({}, {}),
            )
        }
        onNodeWithText("1 meal saved").assertIsDisplayed()
    }
}

internal fun profileConfirmedFood(entryId: String, mealId: String, name: String): ConfirmedConsumedFood {
    val digest = "a".repeat(64)
    val nutrients = CatalogNutrientProfile(100.0, 5.0, 10.0, 2.0, 2.0, 20.0)
    val record = NutritionCatalogRecord(
        canonicalItemId = entryId,
        displayName = name,
        itemType = NutritionCatalogItemType.INGREDIENT,
        catalogContentDigest = digest,
        nutrientsPer100Grams = nutrients,
        defaultServing = null,
    )
    return ConfirmedConsumedFood.fromGroundedResult(
        entryId = entryId,
        localDay = 10,
        mealId = mealId,
        grounded = GroundedNutritionResult(
            catalogSnapshot = NutritionCatalogSnapshot(NutritionCatalogIdentity("test", "1"), "US", digest),
            record = record,
            serving = NutritionServing(100.0, "g", 100.0),
            nutrients = nutrients,
        ),
        attributes = RewardFoodAttributes(hasProtein = false),
    )
}
