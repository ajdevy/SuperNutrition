package com.supernutrition.domain.nutrition

import kotlin.coroutines.cancellation.CancellationException

enum class CatalogMealNutritionUnknownReason {
    SOURCE_UNAVAILABLE,
    MISSING_BINDING,
    MISSING_SERVING,
    STALE_BINDING,
    PRODUCT_NOT_FOUND,
    RECORD_MISMATCH,
    INVALID_SERVING,
}

data class CatalogMealNutrition(
    val binding: CatalogMealBinding,
    val product: CatalogProduct,
    val grams: Double,
    val nutrients: PartialNutrientProfile,
)

sealed interface CatalogMealNutritionResolution {
    data class Grounded(val result: CatalogMealNutrition) : CatalogMealNutritionResolution
    data class Unknown(val reason: CatalogMealNutritionUnknownReason) : CatalogMealNutritionResolution
}

/** Only an explicit product binding and entered grams can reach deterministic source arithmetic. */
class CatalogMealNutritionResolver(private val source: ApprovedNutritionCatalog?) {
    fun resolve(binding: CatalogMealBinding?, grams: Double?): CatalogMealNutritionResolution = try {
        resolveSelected(binding, grams)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        unknown(CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE)
    }

    private fun resolveSelected(binding: CatalogMealBinding?, grams: Double?): CatalogMealNutritionResolution {
        val catalog = source ?: return unknown(CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE)
        val selected = binding ?: return unknown(CatalogMealNutritionUnknownReason.MISSING_BINDING)
        val weight = grams ?: return unknown(CatalogMealNutritionUnknownReason.MISSING_SERVING)
        if (selected.snapshot != catalog.snapshot) return unknown(CatalogMealNutritionUnknownReason.STALE_BINDING)
        val product = catalog.lookup(selected.snapshot, selected.canonicalItemId)
            ?: return unknown(CatalogMealNutritionUnknownReason.PRODUCT_NOT_FOUND)
        if (product.snapshot != selected.snapshot || product.canonicalItemId != selected.canonicalItemId) {
            return unknown(CatalogMealNutritionUnknownReason.RECORD_MISMATCH)
        }
        val nutrients = product.nutrientsPer100Grams.scaledToGrams(weight)
            ?: return unknown(CatalogMealNutritionUnknownReason.INVALID_SERVING)
        return CatalogMealNutritionResolution.Grounded(CatalogMealNutrition(selected, product, weight, nutrients))
    }

    private fun unknown(reason: CatalogMealNutritionUnknownReason) = CatalogMealNutritionResolution.Unknown(reason)
}
