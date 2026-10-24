package com.supernutrition.data.nutrition

import com.supernutrition.data.nutrition.db.UsdaCatalogDatabase
import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogProduct
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import com.supernutrition.domain.nutrition.CatalogRecord
import com.supernutrition.domain.nutrition.CatalogSearch
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.PartialNutrientProfile
import com.supernutrition.domain.nutrition.generatedCatalog

/** SQLDelight is the numeric authority. Search reuses the existing deterministic identity matcher. */
internal class SqlDelightUsdaCatalog(
    private val database: UsdaCatalogDatabase,
    override val snapshot: NutritionCatalogSnapshot,
) : ApprovedNutritionCatalog {
    override val provenance = usdaCatalogProvenance
    private val search = CatalogSearch(
        database.catalogQueries.listFoods(snapshot.contentDigest).executeAsList().map { row ->
            CatalogRecord(row.canonical_id, row.display_name, aliases[row.canonical_id].orEmpty())
        },
    )

    override fun search(query: String): List<CatalogProductSearchResult> = search.search(query).mapNotNull { hit ->
        lookup(snapshot, hit.canonicalItemId)?.let { CatalogProductSearchResult(it, hit.unmatchedPreparation) }
    }

    override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? {
        if (snapshot != this.snapshot) return null
        val row = database.catalogQueries.findFood(snapshot.contentDigest, canonicalItemId).executeAsOneOrNull()
            ?: return null
        return CatalogProduct(
            snapshot,
            row.canonical_id,
            row.display_name,
            PartialNutrientProfile(
                row.energy_kcal,
                row.protein_g,
                row.carbohydrate_g,
                row.fat_g,
                row.fiber_g,
                row.sugar_g,
                row.sodium_mg,
            ),
        )
    }

    private companion object {
        private val aliases = generatedCatalog.associate { it.canonicalItemId to it.alternateNames }
    }
}
