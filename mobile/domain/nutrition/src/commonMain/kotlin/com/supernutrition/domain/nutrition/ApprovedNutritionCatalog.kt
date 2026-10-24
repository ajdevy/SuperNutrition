package com.supernutrition.domain.nutrition

import com.supernutrition.domain.inference.requireNonBlankValue

const val GROUNDED_NUTRITION_CONTRACT_V2 = "grounded-nutrition/v2"

/** A source-owned exact product, including its preparation and immutable package identity. */
data class CatalogProduct(
    val snapshot: NutritionCatalogSnapshot,
    val canonicalItemId: String,
    val displayName: String,
    val nutrientsPer100Grams: PartialNutrientProfile,
) {
    init {
        requireNonBlankValue(canonicalItemId, "Catalog product ID must not be blank")
        requireNonBlankValue(displayName, "Catalog product name must not be blank")
    }
}

data class CatalogProductSearchResult(val product: CatalogProduct, val unmatchedPreparation: List<String> = emptyList())

/** An explicit product choice; a label or search result does not create this binding automatically. */
data class CatalogMealBinding(val snapshot: NutritionCatalogSnapshot, val canonicalItemId: String) {
    init {
        requireNonBlankValue(canonicalItemId, "Selected catalog product ID must not be blank")
    }
}

data class CatalogAttribution(val name: String, val notice: String, val licenceUrl: String, val sourceUrl: String)

data class NutritionCatalogProvenance(
    val sourceName: String,
    val sourceUrl: String,
    val licenceName: String,
    val licenceUrl: String,
    val coverageDescription: String,
    val attributions: List<CatalogAttribution>,
)

/** Read-only approved source port. Hosts provide an adapter; UI and domain own no database. */
interface ApprovedNutritionCatalog {
    val snapshot: NutritionCatalogSnapshot
    val provenance: NutritionCatalogProvenance

    fun search(query: String): List<CatalogProductSearchResult>

    fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct?
}
