package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogMealBinding

object MealCatalogBindingLimits {
    const val IDENTIFIER_MAX_LENGTH = 256
    const val COUNTRY_MAX_LENGTH = 32
    const val DIGEST_LENGTH = 64
}

internal fun isBoundedCatalogBinding(binding: CatalogMealBinding): Boolean =
    binding.snapshot.identity.catalogId.length <= MealCatalogBindingLimits.IDENTIFIER_MAX_LENGTH &&
        binding.snapshot.identity.version.length <= MealCatalogBindingLimits.IDENTIFIER_MAX_LENGTH &&
        binding.snapshot.catalogCountry.length <= MealCatalogBindingLimits.COUNTRY_MAX_LENGTH &&
        binding.canonicalItemId.length <= MealCatalogBindingLimits.IDENTIFIER_MAX_LENGTH
