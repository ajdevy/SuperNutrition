package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MealCatalogOwnershipTest {
    @Test
    fun `a throwing catalog accessor closes the candidate and permits retry`() {
        var closes = 0
        var attempts = 0
        val good = FeatureCatalogHandle(FeatureCatalogFixture())
        val broken = object : MealCatalogHandle {
            override val catalog: ApprovedNutritionCatalog get() = error("source closed during open")

            override fun close() {
                closes++
            }
        }
        val session = MealCatalogSession { if (++attempts == 1) broken else good }
        session.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        assertEquals(1, closes)
        session.retry()
        assertIs<MealCatalogState.Ready>(session.state.value)
        session.close()
        assertEquals(1, good.closes)
    }
}
