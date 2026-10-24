package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.PartialNutrientProfile
import com.supernutrition.domain.nutrition.nutrientDisplayNumber

/** Only existing catalog-grounded serving results can supply these presentation totals. */
internal fun MealHistoryMeal.nutrition(per100Grams: Boolean): PartialNutrientProfile? =
    if (per100Grams) nutritionProjection?.per100Grams else nutritionProjection?.portion

internal fun MealHistoryMeal.portionGrams(): Double? = if (savedMeal != null) {
    savedMeal.portionGrams
} else {
    groundedMealGrams(groundedFoods).takeIf { it.isFinite() && it > 0.0 }
}

internal fun groundedMealGrams(foods: List<ConfirmedConsumedFood>): Double = foods.sumOf { it.grounded.serving.grams }

internal fun diaryNumber(value: Double): String = value.toString().removeSuffix(".0")

internal fun nutrientValue(value: Double?, unit: String = "g"): String =
    if (value == null) "—" else "${nutrientDisplayNumber(value)} $unit"
