package com.supernutrition.domain.nutrition

import com.supernutrition.domain.inference.requireNonBlankValue
import com.supernutrition.domain.inference.requireSha256Digest

/** Versioned contract shared by a catalog adapter and the grounded resolver. */
const val GROUNDED_NUTRITION_CONTRACT_V1 = "grounded-nutrition/v1"

/** Stable identity of one catalog package; it is not a processing or market policy. */
data class NutritionCatalogIdentity(val catalogId: String, val version: String) {
    init {
        requireNonBlankValue(catalogId, "Catalog ID must not be blank")
        requireNonBlankValue(version, "Catalog version must not be blank")
    }
}

/** Immutable catalog package binding captured before a grounded lookup. */
data class NutritionCatalogSnapshot(
    val identity: NutritionCatalogIdentity,
    val catalogCountry: String,
    val contentDigest: String,
) {
    init {
        requireNonBlankValue(catalogCountry, "Catalog country must not be blank")
        requireSha256Digest(contentDigest, "Catalog content digest must be a lowercase SHA-256 digest")
    }
}

enum class NutritionCatalogItemType {
    INGREDIENT,
    DISH,
}

/** A user-selected serving expressed with an explicit display unit and normalized grams. */
data class NutritionServing(val amount: Double, val unit: String, val grams: Double) {
    init {
        require(amount.isFinite() && amount > 0.0) { "Serving amount must be finite and positive" }
        requireNonBlankValue(unit, "Serving unit must not be blank")
        require(grams.isFinite() && grams > 0.0) { "Serving grams must be finite and positive" }
    }
}

/** Nutrient values stored by a catalog record on a 100 gram basis. */
data class CatalogNutrientProfile(
    val energyKcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
    val sodiumMilligrams: Double,
) {
    init {
        requireNonNegativeFinite(energyKcal, "Energy")
        requireNonNegativeFinite(proteinGrams, "Protein")
        requireNonNegativeFinite(carbohydrateGrams, "Carbohydrate")
        requireNonNegativeFinite(fatGrams, "Fat")
        requireNonNegativeFinite(fiberGrams, "Fiber")
        requireNonNegativeFinite(sodiumMilligrams, "Sodium")
    }

    fun scaledTo(serving: NutritionServing): CatalogNutrientProfile {
        val factor = serving.grams / GRAMS_PER_REFERENCE_SERVING
        return CatalogNutrientProfile(
            energyKcal = energyKcal * factor,
            proteinGrams = proteinGrams * factor,
            carbohydrateGrams = carbohydrateGrams * factor,
            fatGrams = fatGrams * factor,
            fiberGrams = fiberGrams * factor,
            sodiumMilligrams = sodiumMilligrams * factor,
        )
    }

    private fun requireNonNegativeFinite(value: Double, name: String) {
        require(value.isFinite() && value >= 0.0) { "$name must be finite and non-negative" }
    }

    private companion object {
        const val GRAMS_PER_REFERENCE_SERVING = 100.0
    }
}

/** Catalog nutrition record; the digest binds the row to one immutable package. */
data class NutritionCatalogRecord(
    val canonicalItemId: String,
    val displayName: String,
    val itemType: NutritionCatalogItemType,
    val catalogContentDigest: String,
    val nutrientsPer100Grams: CatalogNutrientProfile,
    val defaultServing: NutritionServing?,
) {
    init {
        requireNonBlankValue(canonicalItemId, "Canonical catalog item ID must not be blank")
        requireNonBlankValue(displayName, "Catalog display name must not be blank")
        requireSha256Digest(
            catalogContentDigest,
            "Catalog record digest must be a lowercase SHA-256 digest",
        )
    }
}

/** Explicit user decision binding one canonical record to one catalog snapshot. */
data class CatalogItemConfirmation(
    val canonicalItemId: String,
    val itemType: NutritionCatalogItemType,
    val confirmationId: String,
    val catalogContentDigest: String,
) {
    init {
        requireNonBlankValue(canonicalItemId, "Confirmed catalog item ID must not be blank")
        requireNonBlankValue(confirmationId, "Catalog confirmation ID must not be blank")
        requireSha256Digest(
            catalogContentDigest,
            "Confirmed catalog digest must be a lowercase SHA-256 digest",
        )
    }
}

/**
 * Database-facing port. Implementations may use SQLDelight, a bundled package,
 * or another approved adapter, but this domain module owns no data source or
 * food dataset.
 */
interface NutritionCatalogSource {
    fun lookupIngredient(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): NutritionCatalogRecord?

    fun lookupDish(snapshot: NutritionCatalogSnapshot, canonicalItemId: String): NutritionCatalogRecord?
}
