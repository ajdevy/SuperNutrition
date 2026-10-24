package com.supernutrition.sharedkit

import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolver
import com.supernutrition.feature.photoanalysis.LocalUserData
import com.supernutrition.feature.photoanalysis.MealCatalogSession
import com.supernutrition.feature.photoanalysis.MealCatalogState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSThread
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real Native adapter fixture; compile evidence alone is never reported as execution. */
@OptIn(ExperimentalForeignApi::class)
class SharedKitUsdaCatalogTest {
    @Test
    fun nativeDriverOpensInBackgroundAndGoldenValuesAreQueriedAndClosedOnMain() = runBlocking<Unit> {
        assertTrue(NSThread.isMainThread)
        val factory = SharedKitUsdaCatalogFactory()
        val handle = assertNotNull(withContext(Dispatchers.Default) { factory.open() })
        val catalog = handle.catalog
        try {
            assertTrue(NSThread.isMainThread)
            val product = assertNotNull(catalog.lookup(catalog.snapshot, "food:usda-srl-01227"))
            assertEquals("Cheese, dry white, queso seco", product.displayName)
            assertEquals("US", catalog.snapshot.catalogCountry)
            assertEquals("2018-04", catalog.snapshot.identity.version)
            val result = assertIs<CatalogMealNutritionResolution.Grounded>(
                CatalogMealNutritionResolver(catalog).resolve(
                    CatalogMealBinding(catalog.snapshot, product.canonicalItemId),
                    50.0,
                ),
            )
            assertEquals(162.5, result.result.nutrients.energyKcal)
            assertEquals(0.275, result.result.nutrients.sugarGrams)
            assertNull(result.result.nutrients.fiberGrams)
            assertEquals(
                setOf("USDA FoodData Central", "FAO AGROVOC", "Wikidata", "OpenCorpora"),
                catalog.provenance.attributions.map { it.name }.toSet(),
            )
            val dispatcher = ImmediateNativeCatalogDispatcher()
            val owner = MealCatalogSession { handle }
            owner.start(CoroutineScope(dispatcher), dispatcher)
            val photo = SharedKitPhotoAnalysisSession(
                "native-catalog-owner",
                localizedPhotoAnalysisCopy(),
                LocalUserData(owner),
            )
            try {
                photo.cancel()
                assertSame(catalog, photo.feature.mealDiary.catalogSession.catalog)
                assertIs<MealCatalogState.Ready>(owner.state.value)
            } finally {
                photo.closeCatalog()
                photo.closeCatalog()
            }
            assertEquals(MealCatalogState.Unavailable, owner.state.value)
        } finally {
            handle.close()
            handle.close()
        }
        assertFailsWith<IllegalStateException> { catalog.lookup(catalog.snapshot, "food:usda-srl-01227") }
        assertIs<CatalogMealNutritionResolution.Unknown>(
            CatalogMealNutritionResolver(catalog).resolve(
                CatalogMealBinding(catalog.snapshot, "food:usda-srl-01227"),
                50.0,
            ),
        )
    }

    @Test
    fun realFactoryRejectsMainThreadOpeningBeforeCreatingADriver() {
        assertTrue(NSThread.isMainThread)
        assertFailsWith<IllegalStateException> { SharedKitUsdaCatalogFactory().open() }
    }

    @Test
    fun legacySessionsStayIsolatedAndLifecycleMethodsAreExplicitAndIdempotent() {
        assertTrue(NSThread.isMainThread)
        val first = SharedKitPhotoAnalysisSession("legacy-first")
        val second = SharedKitPhotoAnalysisSession("legacy-second")
        assertEquals(MealCatalogState.Unavailable, first.feature.mealDiary.catalogSession.state.value)
        first.startCatalog()
        first.startCatalog()
        first.cancel()
        assertEquals(MealCatalogState.Unavailable, first.feature.mealDiary.catalogSession.state.value)
        first.closeCatalog()
        first.closeCatalog()
        first.startCatalog()
        assertNull(first.feature.mealDiary.catalogSession.catalog)
        assertEquals(MealCatalogState.Unavailable, second.feature.mealDiary.catalogSession.state.value)
        second.closeCatalog()
    }
}

private class ImmediateNativeCatalogDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}
