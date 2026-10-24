package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovedNutritionCatalogTest {
    @Test
    fun `explicit product search preserves caveats and licence credits`() {
        val snapshot = NutritionCatalogSnapshot(
            NutritionCatalogIdentity("usda-sr-legacy", "2018-04"),
            "US",
            "a".repeat(64),
        )
        val product = CatalogProduct(
            snapshot,
            "food:usda-srl-01227",
            "Cheese, dry white, queso seco",
            PartialNutrientProfile(325.0, 24.51, 2.04, 24.35, null, 0.55, 1808.0),
        )
        val credit = CatalogAttribution(
            "USDA",
            "Source credit",
            "https://example.test/licence",
            "https://example.test/source",
        )
        val source = object : ApprovedNutritionCatalog {
            override val snapshot = product.snapshot
            override val provenance = NutritionCatalogProvenance(
                "USDA",
                credit.sourceUrl,
                "CC0",
                credit.licenceUrl,
                "US, explicit product and grams",
                listOf(credit),
            )
            override fun search(query: String): List<CatalogProductSearchResult> =
                listOf(CatalogProductSearchResult(product, listOf("grilled")))
            override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct = product
        }
        val result = source.search("grilled cheese").single()
        assertEquals(product, result.product)
        assertEquals(listOf("grilled"), result.unmatchedPreparation)
        assertTrue(CatalogProductSearchResult(product).unmatchedPreparation.isEmpty())
        assertEquals(credit, source.provenance.attributions.single())
        assertEquals(credit.licenceUrl, source.provenance.licenceUrl)
    }
}
