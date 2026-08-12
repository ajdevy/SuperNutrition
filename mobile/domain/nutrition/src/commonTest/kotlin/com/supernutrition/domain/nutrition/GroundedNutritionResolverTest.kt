package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

class GroundedNutritionResolverTest {
    private val digest = "a".repeat(64)
    private val snapshot = NutritionCatalogSnapshot(
        identity = NutritionCatalogIdentity("catalog-us", "2026.08"),
        catalogCountry = "US",
        contentDigest = digest,
    )
    private val ingredient = record(
        id = "ingredient:apple",
        type = NutritionCatalogItemType.INGREDIENT,
        name = "Apple",
        digest = digest,
    )
    private val dish = record(
        id = "dish:stew",
        type = NutritionCatalogItemType.DISH,
        name = "Vegetable stew",
        digest = digest,
    )

    @Test
    fun `matching confirmation binds snapshot identity and scales nutrients`() {
        val source = FakeCatalog(ingredient = ingredient)
        val result = GroundedNutritionResolver(source).resolve(
            catalogSnapshot = snapshot,
            confirmation = confirmation(ingredient),
            serving = NutritionServing(amount = 150.0, unit = "g", grams = 150.0),
        ) as GroundedNutritionResolution.Grounded

        assertEquals(snapshot.identity, result.result.catalogSnapshot.identity)
        assertEquals("2026.08", result.result.catalogSnapshot.identity.version)
        assertEquals("ingredient:apple", result.result.record.canonicalItemId)
        assertEquals(300.0, result.result.nutrients.energyKcal)
        assertEquals(15.0, result.result.nutrients.proteinGrams)
    }

    @Test
    fun `ingredient and dish confirmations use their separate lookup ports`() {
        val source = FakeCatalog(ingredient = ingredient, dish = dish)
        val resolver = GroundedNutritionResolver(source)

        val ingredientResult = resolver.resolve(snapshot, confirmation(ingredient), serving())
        val dishResult = resolver.resolve(snapshot, confirmation(dish), serving())

        assertEquals("ingredient:apple", grounded(ingredientResult).record.canonicalItemId)
        assertEquals("dish:stew", grounded(dishResult).record.canonicalItemId)
        assertEquals(1, source.ingredientLookups)
        assertEquals(1, source.dishLookups)
    }

    @Test
    fun `missing snapshot or confirmation abstains before catalog lookup`() {
        val source = FakeCatalog(ingredient = ingredient)
        val resolver = GroundedNutritionResolver(source)

        assertEquals(
            GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.MISSING_CATALOG_SNAPSHOT,
            ),
            resolver.resolve(null, confirmation(ingredient), serving()),
        )
        assertEquals(
            GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.MISSING_CONFIRMATION,
            ),
            resolver.resolve(snapshot, null, serving()),
        )
        assertEquals(0, source.ingredientLookups)
        assertEquals(0, source.dishLookups)
    }

    @Test
    fun `stale confirmation and missing serving abstain`() {
        val source = FakeCatalog(ingredient = ingredient)
        val resolver = GroundedNutritionResolver(source)
        val staleConfirmation = CatalogItemConfirmation(
            canonicalItemId = ingredient.canonicalItemId,
            itemType = ingredient.itemType,
            confirmationId = "confirmation-stale",
            catalogContentDigest = "b".repeat(64),
        )

        assertEquals(
            GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.CONFIRMATION_NOT_BOUND_TO_SNAPSHOT,
            ),
            resolver.resolve(snapshot, staleConfirmation, serving()),
        )
        assertEquals(
            GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.MISSING_SERVING,
            ),
            resolver.resolve(snapshot, confirmation(ingredient), null),
        )
        assertEquals(0, source.ingredientLookups)
    }

    @Test
    fun `missing or stale records abstain instead of producing nutrients`() {
        val missing = GroundedNutritionResolver(FakeCatalog()).resolve(
            snapshot,
            confirmation(ingredient),
            serving(),
        )
        assertEquals(
            GroundedNutritionResolution.Abstained(GroundedNutritionAbstentionReason.RECORD_NOT_FOUND),
            missing,
        )

        val staleRecord = ingredient.copy(catalogContentDigest = "b".repeat(64))
        val stale = GroundedNutritionResolver(FakeCatalog(ingredient = staleRecord)).resolve(
            snapshot,
            confirmation(ingredient),
            serving(),
        )
        assertEquals(
            GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.RECORD_NOT_BOUND_TO_SNAPSHOT,
            ),
            stale,
        )
    }

    @Test
    fun `record type and identity must match the confirmed item`() {
        val wrongType = ingredient.copy(itemType = NutritionCatalogItemType.DISH)
        val wrongIdentity = ingredient.copy(canonicalItemId = "ingredient:pear")

        assertEquals(
            GroundedNutritionResolution.Abstained(GroundedNutritionAbstentionReason.RECORD_TYPE_MISMATCH),
            GroundedNutritionResolver(FakeCatalog(ingredient = wrongType)).resolve(
                snapshot,
                confirmation(ingredient),
                serving(),
            ),
        )
        assertEquals(
            GroundedNutritionResolution.Abstained(GroundedNutritionAbstentionReason.RECORD_TYPE_MISMATCH),
            GroundedNutritionResolver(
                FakeCatalog(ingredient = wrongIdentity, matchRequestedId = false),
            ).resolve(
                snapshot,
                confirmation(ingredient),
                serving(),
            ),
        )
    }

    private fun confirmation(record: NutritionCatalogRecord) = CatalogItemConfirmation(
        canonicalItemId = record.canonicalItemId,
        itemType = record.itemType,
        confirmationId = "confirmation-${record.canonicalItemId}",
        catalogContentDigest = digest,
    )

    private fun serving() = NutritionServing(amount = 100.0, unit = "g", grams = 100.0)

    private fun grounded(result: GroundedNutritionResolution) = (result as GroundedNutritionResolution.Grounded).result

    private fun record(
        id: String,
        type: NutritionCatalogItemType,
        name: String,
        digest: String,
    ): NutritionCatalogRecord = NutritionCatalogRecord(
        canonicalItemId = id,
        displayName = name,
        itemType = type,
        catalogContentDigest = digest,
        nutrientsPer100Grams = CatalogNutrientProfile(
            energyKcal = 200.0,
            proteinGrams = 10.0,
            carbohydrateGrams = 30.0,
            fatGrams = 5.0,
            fiberGrams = 4.0,
            sodiumMilligrams = 100.0,
        ),
        defaultServing = NutritionServing(amount = 1.0, unit = "piece", grams = 100.0),
    )

    private class FakeCatalog(
        private val ingredient: NutritionCatalogRecord? = null,
        private val dish: NutritionCatalogRecord? = null,
        private val matchRequestedId: Boolean = true,
    ) : NutritionCatalogSource {
        var ingredientLookups: Int = 0
        var dishLookups: Int = 0

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
}
