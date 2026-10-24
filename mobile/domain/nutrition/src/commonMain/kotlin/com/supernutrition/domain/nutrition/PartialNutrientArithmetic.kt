package com.supernutrition.domain.nutrition

/**
 * Canonical totals for contributing portions, with no calculation rounding.
 * Empty input or an unavailable portion leaves all totals unknown. A missing
 * field or overflowing sum affects only that nutrient; known zero stays known.
 */
fun aggregatePartialNutrients(portions: List<PartialNutrientProfile?>): PartialNutrientProfile = PartialNutrientProfile(
    sumKnownNutrient(portions) { it.energyKcal },
    sumKnownNutrient(portions) { it.proteinGrams },
    sumKnownNutrient(portions) { it.carbohydrateGrams },
    sumKnownNutrient(portions) { it.fatGrams },
    sumKnownNutrient(portions) { it.fiberGrams },
    sumKnownNutrient(portions) { it.sugarGrams },
    sumKnownNutrient(portions) { it.sodiumMilligrams },
)

/**
 * Converts a portion to the 100 g basis without rounding; format only for display.
 * Invalid masses return null. Missing or overflowing fields stay independently
 * unknown. Division precedes multiplication so tiny positive masses preserve zero.
 */
fun PartialNutrientProfile.per100GramsFromPortion(grams: Double): PartialNutrientProfile? {
    if (!grams.isFinite() || grams <= 0.0) return null
    if (grams == 100.0) return this
    return PartialNutrientProfile(
        normalizeKnownNutrient(energyKcal, grams),
        normalizeKnownNutrient(proteinGrams, grams),
        normalizeKnownNutrient(carbohydrateGrams, grams),
        normalizeKnownNutrient(fatGrams, grams),
        normalizeKnownNutrient(fiberGrams, grams),
        normalizeKnownNutrient(sugarGrams, grams),
        normalizeKnownNutrient(sodiumMilligrams, grams),
    )
}

private fun sumKnownNutrient(
    portions: List<PartialNutrientProfile?>,
    select: (PartialNutrientProfile) -> Double?,
): Double? {
    if (portions.isEmpty()) return null
    var total = 0.0
    for (portion in portions) {
        val value = portion?.let(select) ?: return null
        total += value
        if (!total.isFinite()) return null
    }
    return total
}

private fun normalizeKnownNutrient(value: Double?, grams: Double): Double? {
    if (value == null) return null
    val normalized = value / grams * 100.0
    return if (normalized.isFinite()) normalized else null
}
