package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.CatalogProduct
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogProvenance
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.PartialNutrientProfile

internal fun presenterCatalogProduct(
    name: String = "Rice, brown, long-grain, raw",
    id: String = "food:usda-srl-20037",
    snapshot: NutritionCatalogSnapshot = NutritionCatalogSnapshot(
        NutritionCatalogIdentity("usda-sr-legacy", "2018-04"),
        "US",
        "a".repeat(64),
    ),
): CatalogProduct = CatalogProduct(snapshot, id, name, PartialNutrientProfile(200.0, 5.0, 20.0, 2.0, 0.0, null, 4.0))

internal fun presenterCatalogMeal(product: CatalogProduct = presenterCatalogProduct()): SavedMeal = SavedMeal(
    "catalog-meal", 20_020, "12:34", product.displayName, 150.0, emptyList(), "", SavedMealSource.TEXT,
    CatalogMealBinding(product.snapshot, product.canonicalItemId),
)

internal class PresenterCatalogFixture(val product: CatalogProduct = presenterCatalogProduct()) :
    ApprovedNutritionCatalog {
    override var snapshot = product.snapshot
    override val provenance = NutritionCatalogProvenance(
        "USDA test source",
        "https://example.test/source",
        "CC0",
        "https://example.test/licence",
        "US",
        emptyList(),
    )
    var lookupResult: CatalogProduct? = product
    var failure: Exception? = null

    override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? {
        failure?.let { throw it }
        return lookupResult
    }

    override fun search(query: String): List<CatalogProductSearchResult> = listOf(CatalogProductSearchResult(product))
}
