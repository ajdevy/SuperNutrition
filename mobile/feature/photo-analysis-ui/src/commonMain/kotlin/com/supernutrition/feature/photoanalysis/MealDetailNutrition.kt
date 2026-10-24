package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogNutrientProfile

/** Only existing catalog-grounded serving results can supply these presentation totals. */
internal fun MealHistoryMeal.nutrition(per100Grams: Boolean): CatalogNutrientProfile? {
    if (savedMeal != null || groundedFoods.isEmpty()) return null
    val grams = portionGrams() ?: return null
    val factor = if (per100Grams) 100.0 / grams else 1.0
    val values = listOf(
        groundedFoods.sumOf { it.grounded.nutrients.energyKcal },
        groundedFoods.sumOf { it.grounded.nutrients.proteinGrams },
        groundedFoods.sumOf { it.grounded.nutrients.carbohydrateGrams },
        groundedFoods.sumOf { it.grounded.nutrients.fatGrams },
        groundedFoods.sumOf { it.grounded.nutrients.fiberGrams },
        groundedFoods.sumOf { it.grounded.nutrients.sodiumMilligrams },
    ).map { it * factor }
    if (values.any { !it.isFinite() || it < 0.0 }) return null
    return CatalogNutrientProfile(values[0], values[1], values[2], values[3], values[4], values[5])
}

internal fun MealHistoryMeal.portionGrams(): Double? = if (savedMeal != null) {
    savedMeal.portionGrams
} else {
    groundedFoods.sumOf { it.grounded.serving.grams }.takeIf { it.isFinite() && it > 0.0 }
}

internal fun diaryNumber(value: Double): String = value.toString().removeSuffix(".0")

internal fun nutrientValue(value: Double?, unit: String = "g"): String =
    value?.let { "${diaryNumber(it)} $unit" } ?: "—"
