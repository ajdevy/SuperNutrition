package com.supernutrition.feature.photoanalysis

import kotlinx.coroutines.CoroutineDispatcher

/** Optional app-root seam, independent of the photo inference runtime provider. */
interface PhotoAnalysisAndroidCatalogProvider {
    val photoAnalysisCatalogFactory: MealCatalogFactory
    val photoAnalysisCatalogBackgroundDispatcher: CoroutineDispatcher
}
