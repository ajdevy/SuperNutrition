package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.CatalogMealNutritionUnknownReason
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealDiaryCatalogWritesTest {
    @Test
    fun `internal add and both update paths retain verified binding and original record context`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        val store = InMemoryMealDiaryStore()
        val diary = diary(store, session)
        val saved = assertNotNull(diary.add(catalog.structuredDraft(), SavedMealSource.PHOTO))
        assertEquals(catalog.structuredDraft().catalogBinding, saved.catalogBinding)
        assertTrue(diary.update(saved.id, saved.toMealDraft().copy(portion = "75", note = "Edited")))
        val updated = diary.meals.value.single()
        assertEquals(saved.copy(portionGrams = 75.0, note = "Edited"), updated)
        assertTrue(diary.update(saved.id, catalog.structuredDraft(100.0).copy(note = "Structured")))
        assertEquals(saved.copy(portionGrams = 100.0, note = "Structured"), diary.meals.value.single())
        assertEquals(diary.meals.value, MealDiary(store = store).meals.value)
        session.close()
    }

    @Test
    fun `new structured bindings cannot bypass source validation or write invalid labels`() {
        val catalog = FeatureCatalogFixture()
        val store = CountingCatalogStore()
        val diary = diary(store)
        assertNull(diary.add(catalog.structuredDraft(), SavedMealSource.TEXT))
        val saved = assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertFalse(diary.update(saved.id, catalog.structuredDraft()))
        assertFalse(diary.update(saved.id, catalog.structuredDraft().copy(ingredients = listOf("Rice, white"))))
        assertNull(diary.add(ValidatedMealDraft("Soup", null, listOf(" "), ""), SavedMealSource.PHOTO))
        assertFalse(diary.update(saved.id, ValidatedMealDraft("Soup", null, listOf(" "), "")))
        assertFalse(diary.update("missing", catalog.structuredDraft()))
        assertEquals(1, store.saves)
        assertEquals(listOf(saved), diary.meals.value)
    }

    @Test
    fun `raw and internal edits share validation and preserve structured comma labels`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        val diary = diary(session = session)
        val saved = assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertTrue(diary.update(saved.id, MealDraft(portion = "50").withCatalogProduct(catalog.product)))
        assertEquals(catalog.structuredDraft().catalogBinding, diary.meals.value.single().catalogBinding)
        assertTrue(diary.update(saved.id, catalog.structuredDraft().copy(name = "Renamed")))
        assertNull(diary.meals.value.single().catalogBinding)
        assertTrue(diary.update(saved.id, catalog.structuredDraft()))
        val labels = listOf("Rice, white", "carrots, cooked")
        assertTrue(diary.update(saved.id, catalog.structuredDraft().copy(ingredients = labels)))
        assertEquals(labels, diary.meals.value.single().ingredients)
        assertNull(diary.meals.value.single().catalogBinding)
        assertTrue(diary.update(saved.id, catalog.structuredDraft()))
        val current = diary.meals.value.single()
        assertTrue(diary.update(saved.id, current.toMealDraft().copy(name = "Other name")))
        assertNull(diary.meals.value.single().catalogBinding)
        session.close()
    }

    @Test
    fun `unavailable and stale historical bindings remain editable without claiming numeric values`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        val store = InMemoryMealDiaryStore()
        val first = diary(store, session)
        val saved = assertNotNull(first.add(catalog.structuredDraft(), SavedMealSource.TEXT))
        session.close()
        val restored = diary(store)
        assertTrue(restored.update(saved.id, saved.toMealDraft().copy(note = "Note", portion = "75")))
        val unknown = assertIs<CatalogMealNutritionResolution.Unknown>(
            restored.meals.value.single().resolveCatalogNutrition(restored.catalogSession.catalog),
        )
        assertEquals(CatalogMealNutritionUnknownReason.SOURCE_UNAVAILABLE, unknown.reason)
        catalog.snapshot = catalog.snapshot.copy(contentDigest = "b".repeat(64))
        val staleSession = catalog.openSession()
        val stale = diary(store, staleSession)
        assertTrue(stale.update(saved.id, catalog.structuredDraft(100.0).copy(note = "Still editable")))
        assertEquals(saved.catalogBinding, stale.meals.value.single().catalogBinding)
        assertEquals(
            CatalogMealNutritionUnknownReason.STALE_BINDING,
            assertIs<CatalogMealNutritionResolution.Unknown>(
                stale.meals.value.single().resolveCatalogNutrition(staleSession.catalog),
            ).reason,
        )
        staleSession.close()
    }

    @Test
    fun `ordinary source errors reject new bindings and cancellation propagates before any write`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        val store = CountingCatalogStore()
        val diary = diary(store, session)
        catalog.failure = IllegalStateException("source closed")
        assertNull(diary.add(catalog.structuredDraft(), SavedMealSource.TEXT))
        assertNull(diary.add(MealDraft(portion = "50").withCatalogProduct(catalog.product), SavedMealSource.TEXT))
        catalog.failure = null
        val saved = assertNotNull(diary.add(catalog.structuredDraft(), SavedMealSource.TEXT))
        val bytes = store.encoded
        catalog.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { diary.add(catalog.structuredDraft(), SavedMealSource.TEXT) }
        assertFailsWith<CancellationException> {
            diary.add(MealDraft(portion = "50").withCatalogProduct(catalog.product), SavedMealSource.TEXT)
        }
        assertFailsWith<CancellationException> { diary.update(saved.id, catalog.structuredDraft(75.0)) }
        assertFailsWith<CancellationException> { diary.update(saved.id, saved.toMealDraft().copy(portion = "75")) }
        assertEquals(1, store.saves)
        assertEquals(bytes, store.encoded)
        assertEquals(listOf(saved), diary.meals.value)
        session.close()
    }

    @Test
    fun `loading v1 leaves bytes unchanged until explicit save and corrupt bytes are preserved`() {
        val old = CountingCatalogStore("SNMD1:0:")
        val diary = diary(old)
        assertEquals(0, old.saves)
        assertEquals("SNMD1:0:", old.encoded)
        assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertTrue(assertNotNull(old.encoded).startsWith("SNMD2:"))
        val corrupt = CountingCatalogStore("SNMD3:0:")
        val blocked = diary(corrupt)
        assertNull(blocked.add(FeatureCatalogFixture().structuredDraft(), SavedMealSource.TEXT))
        assertFalse(blocked.update("meal", FeatureCatalogFixture().structuredDraft()))
        assertEquals(0, corrupt.saves)
        assertEquals("SNMD3:0:", corrupt.encoded)
    }

    private fun diary(
        store: MealDiaryStore = InMemoryMealDiaryStore(),
        session: MealCatalogSession = MealCatalogSession(),
    ) = MealDiary({ MealRecordContext("meal", 20_020, "12:34") }, store, session)
}

private class CountingCatalogStore(var encoded: String? = null) : MealDiaryStore {
    var saves = 0

    override fun load(): String? = encoded

    override fun save(encoded: String): Boolean {
        saves++
        this.encoded = encoded
        return true
    }
}
