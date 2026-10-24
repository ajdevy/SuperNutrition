package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogAttribution
import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.CatalogProduct
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogProvenance
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.PartialNutrientProfile

internal fun uiCatalogProduct(): CatalogProduct = CatalogProduct(
    NutritionCatalogSnapshot(NutritionCatalogIdentity("usda-sr-legacy", "2018-04"), "US", "a".repeat(64)),
    "food:usda-srl-01227",
    "Cheese, dry white, queso seco",
    PartialNutrientProfile(325.0, 24.51, 2.04, 24.35, null, .55, 1808.0),
)

internal fun uiCatalogMeal(product: CatalogProduct = uiCatalogProduct()): SavedMeal = diaryMealFixture().copy(
    name = product.displayName,
    portionGrams = 50.0,
    ingredients = emptyList(),
    catalogBinding = CatalogMealBinding(product.snapshot, product.canonicalItemId),
)

internal class UiCatalogFixture(val product: CatalogProduct = uiCatalogProduct()) : ApprovedNutritionCatalog {
    override var snapshot = product.snapshot
    override val provenance = NutritionCatalogProvenance(
        "USDA FoodData Central · SR Legacy 2018-04",
        "https://example.test/usda",
        "CC0 1.0 Universal",
        "https://example.test/cc0",
        "US catalog coverage",
        listOf("USDA", "FAO AGROVOC", "Wikidata", "OpenCorpora").map { name ->
            CatalogAttribution(
                name,
                "$name attribution notice",
                "https://example.test/$name/licence",
                "https://example.test/$name",
            )
        },
    )
    var results = listOf(CatalogProductSearchResult(product, listOf("fried")))
    var searchFailure: Exception? = null
    var lookupFailure: Exception? = null
    var lookupResult: CatalogProduct? = product
    val queries = mutableListOf<String>()

    override fun search(query: String): List<CatalogProductSearchResult> {
        queries += query
        searchFailure?.let { throw it }
        return results
    }

    override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? {
        lookupFailure?.let { throw it }
        return lookupResult
    }
}
