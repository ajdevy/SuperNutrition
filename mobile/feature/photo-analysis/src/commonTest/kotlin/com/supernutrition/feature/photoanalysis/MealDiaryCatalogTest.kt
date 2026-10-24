package com.supernutrition.feature.photoanalysis

import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MealDiaryCatalogTest {
    @Test
    fun `verified explicit product binding survives central save and restart`() {
        val catalog = FeatureCatalogFixture()
        val session = MealCatalogSession { FeatureCatalogHandle(catalog) }
        session.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        val store = InMemoryMealDiaryStore()
        val diary = MealDiary({ MealRecordContext("bound", 20_020, "12:34") }, store, session)
        val draft = MealDraft(portion = "50").withCatalogProduct(catalog.product)
        val saved = assertNotNull(diary.add(draft, SavedMealSource.TEXT))
        assertEquals(draft.catalogBinding, saved.catalogBinding)
        assertEquals(saved, MealDiary(store = store).meals.value.single())
        session.close()
    }
}
