package com.supernutrition.domain.nutrition

/** Version two retains unknown source values independently; zero remains a known value. */
data class PartialNutrientProfile(
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
    val fiberGrams: Double?,
    val sugarGrams: Double?,
    val sodiumMilligrams: Double?,
) {
    init {
        values().forEach { value ->
            require(value == null || (value.isFinite() && value >= 0.0)) {
                "Known nutrients must be finite and non-negative"
            }
        }
    }

    /** Invalid or overflowing portions cannot produce a numeric nutrition claim. */
    fun scaledToGrams(grams: Double): PartialNutrientProfile? {
        if (!grams.isFinite() || grams <= 0.0) return null
        val scaled = values().map { value -> value?.let { scalePerHundredGrams(it, grams) } }
        if (scaled.any { it != null && !it.isFinite() }) return null
        return PartialNutrientProfile(scaled[0], scaled[1], scaled[2], scaled[3], scaled[4], scaled[5], scaled[6])
    }

    private fun values(): List<Double?> = listOf(
        energyKcal,
        proteinGrams,
        carbohydrateGrams,
        fatGrams,
        fiberGrams,
        sugarGrams,
        sodiumMilligrams,
    )
}

/** One arithmetic implementation serves the frozen v1 and partial v2 contracts. */
internal fun scalePerHundredGrams(value: Double, grams: Double): Double = value * (grams / 100.0)
