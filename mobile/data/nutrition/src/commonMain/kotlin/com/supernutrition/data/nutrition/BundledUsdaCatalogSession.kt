package com.supernutrition.data.nutrition

import app.cash.sqldelight.db.SqlDriver
import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogProduct
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot

/**
 * Owns one private catalog database. Hosts serialize access, retain it outside recomposition,
 * and close it on disposal, including results that finish initialization after disposal.
 */
class BundledUsdaCatalogSession internal constructor(
    private val source: ApprovedNutritionCatalog,
    private val driver: SqlDriver,
) : ApprovedNutritionCatalog by source {
    private var closed = false

    val catalog: ApprovedNutritionCatalog get() = this

    override fun search(query: String): List<CatalogProductSearchResult> {
        check(!closed) { "Catalog session is closed" }
        return source.search(query)
    }

    override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? {
        check(!closed) { "Catalog session is closed" }
        return source.lookup(snapshot, canonicalItemId)
    }

    /** Idempotent even when the underlying driver's first close fails. */
    fun close() {
        if (!closed) {
            closed = true
            driver.close()
        }
    }
}
