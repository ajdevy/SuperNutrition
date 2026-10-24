package com.supernutrition.feature.photoanalysis

/** One shared representation for editors and unchanged-ingredient validation. */
fun SavedMeal.toMealDraft(): MealDraft = MealDraft(
    name = name,
    portion = if (portionGrams == null) "" else portionGrams.toString().removeSuffix(".0"),
    ingredients = ingredients.joinToString(", "),
    note = note,
    catalogBinding = catalogBinding,
)
