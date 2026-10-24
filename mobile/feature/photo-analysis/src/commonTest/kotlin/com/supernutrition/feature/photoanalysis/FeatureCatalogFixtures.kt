package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogProduct
import com.supernutrition.domain.nutrition.CatalogProductSearchResult
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogProvenance
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.PartialNutrientProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

internal class FeatureCatalogFixture : ApprovedNutritionCatalog {
    override var snapshot = NutritionCatalogSnapshot(NutritionCatalogIdentity("usda", "2018-04"), "US", "a".repeat(64))
    val product = CatalogProduct(
        snapshot,
        "food:usda-srl-01227",
        "Cheese, dry white, queso seco",
        PartialNutrientProfile(325.0, 24.51, 2.04, 24.35, null, 0.55, 1808.0),
    )
    override val provenance = NutritionCatalogProvenance(
        "USDA",
        "https://example.test/source",
        "CC0",
        "https://example.test/licence",
        "US",
        emptyList(),
    )
    var failure: Exception? = null

    override fun lookup(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): CatalogProduct? {
        failure?.let { throw it }
        return product
    }

    override fun search(query: String): List<CatalogProductSearchResult> {
        failure?.let { throw it }
        return listOf(CatalogProductSearchResult(product))
    }
}

internal class FeatureCatalogHandle(override val catalog: ApprovedNutritionCatalog) : MealCatalogHandle {
    var closes = 0
    var closeFailure: Exception? = null

    override fun close() {
        closes++
        closeFailure?.let { throw it }
    }
}

internal class ImmediateCatalogDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
}

internal fun FeatureCatalogFixture.openSession(): MealCatalogSession = MealCatalogSession {
    FeatureCatalogHandle(this)
}.also { it.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher()) }

internal fun FeatureCatalogFixture.structuredDraft(grams: Double? = 50.0): ValidatedMealDraft {
    val selected = MealDraft().withCatalogProduct(product)
    return ValidatedMealDraft(selected.name, grams, emptyList(), "", selected.catalogBinding)
}

internal class QueuedCatalogDispatcher : CoroutineDispatcher() {
    private val tasks = mutableListOf<Runnable>()
    var running = false
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
    }

    fun runNext() {
        running = true
        try {
            tasks.removeAt(0).run()
        } finally {
            running = false
        }
    }

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }
}
