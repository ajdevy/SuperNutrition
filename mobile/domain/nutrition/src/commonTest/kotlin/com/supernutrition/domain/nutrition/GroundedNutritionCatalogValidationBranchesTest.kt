package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroundedNutritionCatalogValidationBranchesTest {
    @Test
    fun `catalog identities snapshots and records reject blank or unbound values`() {
        assertFailsWith<IllegalArgumentException> { NutritionCatalogIdentity("", "2026.08") }
        assertFailsWith<IllegalArgumentException> { NutritionCatalogIdentity("catalog", "") }
        assertFailsWith<IllegalArgumentException> { NutritionCatalogSnapshot(identity(), "", digest) }
        assertFailsWith<IllegalArgumentException> { NutritionCatalogSnapshot(identity(), "US", "not-a-digest") }
        assertFailsWith<IllegalArgumentException> {
            record(canonicalItemId = "", displayName = "Apple")
        }
        assertFailsWith<IllegalArgumentException> { record(displayName = "") }
        assertFailsWith<IllegalArgumentException> { record(catalogContentDigest = "bad") }
        assertFailsWith<IllegalArgumentException> { confirmation(canonicalItemId = "") }
        assertFailsWith<IllegalArgumentException> { confirmation(confirmationId = "") }
        assertFailsWith<IllegalArgumentException> { confirmation(catalogContentDigest = "bad") }
    }

    @Test
    fun `servings and nutrient profiles reject every non-finite or negative boundary`() {
        assertFailsWith<IllegalArgumentException> { NutritionServing(0.0, "g", 10.0) }
        assertFailsWith<IllegalArgumentException> { NutritionServing(10.0, "", 10.0) }
        assertFailsWith<IllegalArgumentException> { NutritionServing(10.0, "g", 0.0) }
        assertFailsWith<IllegalArgumentException> { NutritionServing(10.0, "g", -1.0) }
        assertFailsWith<IllegalArgumentException> { NutritionServing(10.0, "g", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { NutritionServing(10.0, "g", Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { NutritionServing(Double.NaN, "g", 10.0) }

        nutrientValues().forEachIndexed { index, value ->
            val invalid = nutrientValues().toMutableList().also { it[index] = value.copy(value = -1.0) }
            assertFailsWith<IllegalArgumentException> { nutrientProfile(invalid.map { it.value }) }
        }
        assertFailsWith<IllegalArgumentException> {
            nutrientProfile(listOf(Double.NaN, 10.0, 20.0, 5.0, 3.0, 40.0))
        }
        val scaled = nutrientProfile(listOf(100.0, 10.0, 20.0, 5.0, 3.0, 40.0))
            .scaledTo(NutritionServing(150.0, "g", 150.0))
        assertEquals(150.0, scaled.energyKcal)
        assertEquals(15.0, scaled.proteinGrams)
        assertEquals(60.0, scaled.sodiumMilligrams)
    }

    private fun identity() = NutritionCatalogIdentity("catalog-us", "2026.08")

    private fun record(
        canonicalItemId: String = "ingredient:apple",
        displayName: String = "Apple",
        catalogContentDigest: String = digest,
    ) = NutritionCatalogRecord(
        canonicalItemId = canonicalItemId,
        displayName = displayName,
        itemType = NutritionCatalogItemType.INGREDIENT,
        catalogContentDigest = catalogContentDigest,
        nutrientsPer100Grams = nutrientProfile(listOf(100.0, 10.0, 20.0, 5.0, 3.0, 40.0)),
        defaultServing = null,
    )

    private fun confirmation(
        canonicalItemId: String = "ingredient:apple",
        confirmationId: String = "confirmation-apple",
        catalogContentDigest: String = digest,
    ) = CatalogItemConfirmation(
        canonicalItemId = canonicalItemId,
        itemType = NutritionCatalogItemType.INGREDIENT,
        confirmationId = confirmationId,
        catalogContentDigest = catalogContentDigest,
    )

    private data class NutrientValue(val value: Double)

    private fun nutrientValues() = listOf(
        NutrientValue(100.0),
        NutrientValue(10.0),
        NutrientValue(20.0),
        NutrientValue(5.0),
        NutrientValue(3.0),
        NutrientValue(40.0),
    )

    private fun nutrientProfile(values: List<Double>) = CatalogNutrientProfile(
        energyKcal = values[0],
        proteinGrams = values[1],
        carbohydrateGrams = values[2],
        fatGrams = values[3],
        fiberGrams = values[4],
        sodiumMilligrams = values[5],
    )

    private companion object {
        val digest = "a".repeat(64)
    }
}
