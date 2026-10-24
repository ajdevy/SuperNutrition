package com.supernutrition.data.nutrition

import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot

internal data class UsdaPackagePayload(val snapshot: NutritionCatalogSnapshot, val payload: String, val foodCount: Int)
