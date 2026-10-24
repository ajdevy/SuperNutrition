package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolver
import com.supernutrition.domain.nutrition.CatalogMealNutritionUnknownReason
import com.supernutrition.domain.nutrition.CatalogProduct

/** Only an explicit choice supplies the exact product label and binding; the entered grams remain editable. */
fun MealDraft.withCatalogProduct(product: CatalogProduct): MealDraft = copy(
    name = product.displayName,
    ingredients = "",
    catalogBinding = CatalogMealBinding(product.snapshot, product.canonicalItemId),
)

fun MealDraft.withEditedName(value: String): MealDraft = copy(name = value, catalogBinding = null)

fun MealDraft.withEditedIngredients(value: String): MealDraft = copy(ingredients = value, catalogBinding = null)

/** Historical bindings can stay stored without claiming values for a renamed or ingredient-bearing record. */
fun SavedMeal.resolveCatalogNutrition(catalog: ApprovedNutritionCatalog?): CatalogMealNutritionResolution =
    resolveCatalogMealIdentity(name, ingredients, catalogBinding, portionGrams, catalog)

internal fun resolveCatalogMealIdentity(
    name: String,
    ingredients: List<String>,
    binding: CatalogMealBinding?,
    grams: Double?,
    catalog: ApprovedNutritionCatalog?,
): CatalogMealNutritionResolution {
    val resolution = CatalogMealNutritionResolver(catalog).resolve(binding, grams)
    return if (resolution is CatalogMealNutritionResolution.Grounded &&
        (resolution.result.product.displayName != name || ingredients.isNotEmpty())
    ) {
        CatalogMealNutritionResolution.Unknown(CatalogMealNutritionUnknownReason.RECORD_MISMATCH)
    } else {
        resolution
    }
}
