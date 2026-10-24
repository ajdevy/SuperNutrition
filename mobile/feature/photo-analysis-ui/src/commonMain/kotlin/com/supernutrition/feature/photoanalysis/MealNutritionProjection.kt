package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.NutritionCatalogProvenance
import com.supernutrition.domain.nutrition.PartialNutrientProfile
import com.supernutrition.domain.nutrition.aggregatePartialNutrients
import com.supernutrition.domain.nutrition.per100GramsFromPortion

/** Derived presentation only. These values never enter the persisted meal document. */
data class MealNutritionProjection(
    val portion: PartialNutrientProfile,
    val per100Grams: PartialNutrientProfile?,
    val provenance: NutritionCatalogProvenance? = null,
)

internal fun SavedMeal.nutritionProjection(catalog: ApprovedNutritionCatalog?): MealNutritionProjection? {
    val source = catalog ?: return null
    val resolution = resolveCatalogNutrition(source)
    if (resolution !is CatalogMealNutritionResolution.Grounded) return null
    return MealNutritionProjection(
        resolution.result.nutrients,
        resolution.result.product.nutrientsPer100Grams,
        source.provenance,
    )
}

internal fun groundedNutritionProjection(foods: List<ConfirmedConsumedFood>): MealNutritionProjection {
    val portion = aggregatePartialNutrients(
        foods.map { food ->
            val values = food.grounded.nutrients
            PartialNutrientProfile(
                values.energyKcal,
                values.proteinGrams,
                values.carbohydrateGrams,
                values.fatGrams,
                values.fiberGrams,
                null,
                values.sodiumMilligrams,
            )
        },
    )
    val grams = groundedMealGrams(foods)
    return MealNutritionProjection(portion, portion.per100GramsFromPortion(grams))
}

/** Unknown entries remain contributors: they cannot be filtered away to produce a partial daily claim. */
fun todayMealNutrients(meals: List<MealHistoryMeal>, localDay: Int): PartialNutrientProfile =
    aggregatePartialNutrients(meals.filter { it.id.localDay == localDay }.map { it.nutrition(false) })
