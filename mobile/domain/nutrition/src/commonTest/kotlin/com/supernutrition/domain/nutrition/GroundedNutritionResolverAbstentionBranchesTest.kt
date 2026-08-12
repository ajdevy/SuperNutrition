package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

class GroundedNutritionResolverAbstentionBranchesTest {
    private val snapshot = NutritionCatalogSnapshot(
        identity = NutritionCatalogIdentity("catalog-us", "2026.08"),
        catalogCountry = "US",
        contentDigest = digest,
    )
    private val ingredient = record("ingredient:apple", NutritionCatalogItemType.INGREDIENT)
    private val dish = record("dish:stew", NutritionCatalogItemType.DISH)

    @Test
    fun `each missing input abstains before touching the catalog`() {
        val source = RecordingCatalog(ingredient, dish)
        val resolver = GroundedNutritionResolver(source)

        assertReason(
            GroundedNutritionAbstentionReason.MISSING_CATALOG_SNAPSHOT,
            resolver.resolve(null, confirmation(ingredient), serving()),
        )
        assertReason(
            GroundedNutritionAbstentionReason.MISSING_CONFIRMATION,
            resolver.resolve(snapshot, null, serving()),
        )
        assertReason(
            GroundedNutritionAbstentionReason.MISSING_SERVING,
            resolver.resolve(snapshot, confirmation(ingredient), null),
        )
        assertEquals(0, source.ingredientLookups + source.dishLookups)
    }

    @Test
    fun `digest binding and record lookup failures abstain without nutrients`() {
        val staleConfirmation = confirmation(ingredient).copy(catalogContentDigest = otherDigest)
        assertReason(
            GroundedNutritionAbstentionReason.CONFIRMATION_NOT_BOUND_TO_SNAPSHOT,
            GroundedNutritionResolver(RecordingCatalog(ingredient, dish))
                .resolve(snapshot, staleConfirmation, serving()),
        )
        assertReason(
            GroundedNutritionAbstentionReason.RECORD_NOT_FOUND,
            GroundedNutritionResolver(RecordingCatalog(null, null))
                .resolve(snapshot, confirmation(ingredient), serving()),
        )
        assertReason(
            GroundedNutritionAbstentionReason.RECORD_NOT_FOUND,
            GroundedNutritionResolver(RecordingCatalog(null, null))
                .resolve(snapshot, confirmation(dish), serving()),
        )
        assertReason(
            GroundedNutritionAbstentionReason.RECORD_NOT_BOUND_TO_SNAPSHOT,
            GroundedNutritionResolver(RecordingCatalog(ingredient.copy(catalogContentDigest = otherDigest), dish))
                .resolve(snapshot, confirmation(ingredient), serving()),
        )
    }

    @Test
    fun `record identity and type mismatches abstain`() {
        assertReason(
            GroundedNutritionAbstentionReason.RECORD_TYPE_MISMATCH,
            GroundedNutritionResolver(RecordingCatalog(ingredient.copy(itemType = NutritionCatalogItemType.DISH), dish))
                .resolve(snapshot, confirmation(ingredient), serving()),
        )
        assertReason(
            GroundedNutritionAbstentionReason.RECORD_TYPE_MISMATCH,
            GroundedNutritionResolver(
                RecordingCatalog(
                    ingredient.copy(canonicalItemId = "ingredient:pear"),
                    dish,
                    matchRequestedId = false,
                ),
            )
                .resolve(snapshot, confirmation(ingredient), serving()),
        )
    }

    private fun assertReason(expected: GroundedNutritionAbstentionReason, actual: GroundedNutritionResolution) {
        assertEquals(GroundedNutritionResolution.Abstained(expected), actual)
    }

    private fun confirmation(record: NutritionCatalogRecord) = CatalogItemConfirmation(
        canonicalItemId = record.canonicalItemId,
        itemType = record.itemType,
        confirmationId = "confirmation-${record.canonicalItemId}",
        catalogContentDigest = digest,
    )

    private fun serving() = NutritionServing(100.0, "g", 100.0)

    private fun record(id: String, type: NutritionCatalogItemType) = NutritionCatalogRecord(
        canonicalItemId = id,
        displayName = id,
        itemType = type,
        catalogContentDigest = digest,
        nutrientsPer100Grams = CatalogNutrientProfile(100.0, 10.0, 20.0, 5.0, 3.0, 40.0),
        defaultServing = null,
    )

    private class RecordingCatalog(
        private val ingredient: NutritionCatalogRecord?,
        private val dish: NutritionCatalogRecord?,
        private val matchRequestedId: Boolean = true,
    ) : NutritionCatalogSource {
        var ingredientLookups = 0
        var dishLookups = 0

        override fun lookupIngredient(
            snapshot: NutritionCatalogSnapshot,
            canonicalItemId: String,
        ): NutritionCatalogRecord? {
            ingredientLookups += 1
            return ingredient?.takeIf { !matchRequestedId || it.canonicalItemId == canonicalItemId }
        }

        override fun lookupDish(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): NutritionCatalogRecord? {
            dishLookups += 1
            return dish?.takeIf { !matchRequestedId || it.canonicalItemId == canonicalItemId }
        }
    }

    private companion object {
        val digest = "a".repeat(64)
        val otherDigest = "b".repeat(64)
    }
}
