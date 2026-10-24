package com.supernutrition.domain.nutrition

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class CatalogMealNutritionResolverTest {
    private val snapshot = NutritionCatalogSnapshot(
        NutritionCatalogIdentity("usda-sr-legacy", "2018-04"),
        "US",
        "a".repeat(64),
    )
    private val product = CatalogProduct(
        snapshot,
        "food:usda-srl-01227",
        "Cheese, dry white, queso seco",
        PartialNutrientProfile(325.0, 24.51, 2.04, 24.35, null, 0.55, 1808.0),
    )
    private val binding = CatalogMealBinding(snapshot, product.canonicalItemId)

    @Test
    fun `explicit exact product and grams ground partial nutrition`() {
        val grounded = assertIs<CatalogMealNutritionResolution.Grounded>(resolver().resolve(binding, 50.0)).result
        assertEquals(product, grounded.product)
        assertEquals(binding, grounded.binding)
        assertEquals(50.0, grounded.grams)
        assertEquals(162.5, grounded.nutrients.energyKcal)
        assertNull(grounded.nutrients.fiberGrams)
        assertEquals(GROUNDED_NUTRITION_CONTRACT_V2, "grounded-nutrition/v2")
    }

    @Test
    fun `missing source choice or grams abstain`() {
        assertUnknown(
            CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE,
            CatalogMealNutritionResolver(null).resolve(binding, 50.0),
        )
        assertUnknown(CatalogMealNutritionUnknownReason.MISSING_BINDING, resolver().resolve(null, 50.0))
        assertUnknown(CatalogMealNutritionUnknownReason.MISSING_SERVING, resolver().resolve(binding, null))
    }

    @Test
    fun `stale snapshot is rejected before lookup`() {
        val stale = binding.copy(snapshot = snapshot.copy(contentDigest = "b".repeat(64)))
        assertUnknown(CatalogMealNutritionUnknownReason.STALE_BINDING, resolver().resolve(stale, 50.0))
    }

    @Test
    fun `missing mismatched and invalid source result cannot supply values`() {
        assertUnknown(CatalogMealNutritionUnknownReason.PRODUCT_NOT_FOUND, resolver(null).resolve(binding, 50.0))
        assertUnknown(
            CatalogMealNutritionUnknownReason.RECORD_MISMATCH,
            resolver(product.copy(snapshot = snapshot.copy(contentDigest = "b".repeat(64)))).resolve(binding, 50.0),
        )
        assertUnknown(
            CatalogMealNutritionUnknownReason.RECORD_MISMATCH,
            resolver(product.copy(canonicalItemId = "food:other")).resolve(binding, 50.0),
        )
        assertUnknown(CatalogMealNutritionUnknownReason.INVALID_SERVING, resolver().resolve(binding, 0.0))
    }

    @Test
    fun `product and selected identity cannot be blank`() {
        assertFailsWith<IllegalArgumentException> { product.copy(canonicalItemId = " ") }
        assertFailsWith<IllegalArgumentException> { product.copy(displayName = " ") }
        assertFailsWith<IllegalArgumentException> { binding.copy(canonicalItemId = " ") }
    }

    @Test
    fun `unavailable adapter read is unknown while cancellation propagates`() {
        assertUnknown(
            CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE,
            throwingResolver(IllegalStateException("Unavailable database")).resolve(binding, 50.0),
        )
        assertFailsWith<CancellationException> {
            throwingResolver(CancellationException("cancelled")).resolve(binding, 50.0)
        }
    }

    private fun throwingResolver(failure: Exception): CatalogMealNutritionResolver {
        val source = object : ApprovedNutritionCatalog {
            override val snapshot = this@CatalogMealNutritionResolverTest.snapshot
            override val provenance = NutritionCatalogProvenance("USDA", "source", "CC0", "licence", "US", emptyList())
            override fun search(query: String): List<CatalogProductSearchResult> = emptyList()
            override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? =
                throw failure
        }
        return CatalogMealNutritionResolver(source)
    }

    private fun resolver(record: CatalogProduct? = product): CatalogMealNutritionResolver {
        val source = object : ApprovedNutritionCatalog {
            override val snapshot = this@CatalogMealNutritionResolverTest.snapshot
            override val provenance = NutritionCatalogProvenance("USDA", "source", "CC0", "licence", "US", emptyList())
            override fun search(query: String): List<CatalogProductSearchResult> = emptyList()
            override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? = record
        }
        return CatalogMealNutritionResolver(source)
    }

    private fun assertUnknown(reason: CatalogMealNutritionUnknownReason, actual: CatalogMealNutritionResolution) {
        assertEquals(CatalogMealNutritionResolution.Unknown(reason), actual)
    }
}
