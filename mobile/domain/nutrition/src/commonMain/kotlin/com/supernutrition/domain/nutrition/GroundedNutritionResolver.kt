package com.supernutrition.domain.nutrition

enum class GroundedNutritionAbstentionReason {
    MISSING_CATALOG_SNAPSHOT,
    MISSING_CONFIRMATION,
    CONFIRMATION_NOT_BOUND_TO_SNAPSHOT,
    MISSING_SERVING,
    RECORD_NOT_FOUND,
    RECORD_NOT_BOUND_TO_SNAPSHOT,
    RECORD_TYPE_MISMATCH,
}

sealed interface GroundedNutritionResolution {
    data class Grounded(val result: GroundedNutritionResult) : GroundedNutritionResolution

    data class Abstained(val reason: GroundedNutritionAbstentionReason) : GroundedNutritionResolution
}

data class GroundedNutritionResult(
    val catalogSnapshot: NutritionCatalogSnapshot,
    val record: NutritionCatalogRecord,
    val serving: NutritionServing,
    val nutrients: CatalogNutrientProfile,
)

/**
 * Resolves only an explicit user binding against an immutable catalog snapshot.
 * Inference candidates, labels, and mass estimates cannot enter this API.
 */
class GroundedNutritionResolver(private val catalog: NutritionCatalogSource) {
    fun resolve(
        catalogSnapshot: NutritionCatalogSnapshot?,
        confirmation: CatalogItemConfirmation?,
        serving: NutritionServing?,
    ): GroundedNutritionResolution {
        val snapshot = catalogSnapshot
            ?: return GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.MISSING_CATALOG_SNAPSHOT,
            )
        val binding = confirmation
            ?: return GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.MISSING_CONFIRMATION,
            )
        if (binding.catalogContentDigest != snapshot.contentDigest) {
            return GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.CONFIRMATION_NOT_BOUND_TO_SNAPSHOT,
            )
        }
        val selectedServing = serving
            ?: return GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.MISSING_SERVING,
            )
        val record = when (binding.itemType) {
            NutritionCatalogItemType.INGREDIENT -> catalog.lookupIngredient(snapshot, binding.canonicalItemId)
            NutritionCatalogItemType.DISH -> catalog.lookupDish(snapshot, binding.canonicalItemId)
        } ?: return GroundedNutritionResolution.Abstained(
            GroundedNutritionAbstentionReason.RECORD_NOT_FOUND,
        )
        if (record.catalogContentDigest != snapshot.contentDigest) {
            return GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.RECORD_NOT_BOUND_TO_SNAPSHOT,
            )
        }
        if (record.canonicalItemId != binding.canonicalItemId || record.itemType != binding.itemType) {
            return GroundedNutritionResolution.Abstained(
                GroundedNutritionAbstentionReason.RECORD_TYPE_MISMATCH,
            )
        }
        return GroundedNutritionResolution.Grounded(
            GroundedNutritionResult(
                catalogSnapshot = snapshot,
                record = record,
                serving = selectedServing,
                nutrients = record.nutrientsPer100Grams.scaledTo(selectedServing),
            ),
        )
    }
}
