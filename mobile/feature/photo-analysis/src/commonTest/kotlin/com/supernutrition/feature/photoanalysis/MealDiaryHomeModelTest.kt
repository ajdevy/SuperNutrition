package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MealDiaryHomeModelTest {
    @Test
    fun `home resolves only the requested day instead of the retained historical catalog meals`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        try {
            val diary = MealDiary({ MealRecordContext("today", 20_020, "12:34") }, catalogSession = session)
            val today = assertNotNull(diary.add(catalog.structuredDraft(), SavedMealSource.TEXT))
            val historical = List(1_000) { today.copy(id = "older-$it", localDay = 20_019) }
            catalog.lookups = 0
            val home = diary.homeScreenModel(
                "Today",
                20_020,
                emptyList(),
                listOf(today) + historical,
                session.state.value,
            )
            assertEquals("1 meal recorded", home.dailySummaryValue)
            assertEquals("12.26 g", home.protein.value)
            assertEquals(1, catalog.lookups)
        } finally {
            session.close()
        }
    }

    @Test
    fun `home count and nutrients use today's merged saved source and preserve missing values`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        var sequence = 0
        val diary = MealDiary({ MealRecordContext("meal-${++sequence}", 20_020, "12:34") }, catalogSession = session)
        val first = assertNotNull(diary.add(catalog.structuredDraft(), SavedMealSource.TEXT))
        val yesterday = first.copy(id = "yesterday", localDay = 20_019)
        val model = diary.homeScreenModel("Today", 20_020, emptyList(), listOf(first, yesterday), session.state.value)
        assertEquals("1 meal recorded", model.dailySummaryValue)
        assertEquals("12.26 g", model.protein.value)
        assertEquals("12.18 g", model.fats.value)
        assertEquals("—", model.fiber.value)
        val unknown = assertNotNull(diary.add(MealDraft("Unbound"), SavedMealSource.TEXT))
        val mixed = diary.homeScreenModel("Today", 20_020, emptyList(), listOf(first, unknown), session.state.value)
        assertEquals("2 meals recorded", mixed.dailySummaryValue)
        assertEquals("—", mixed.protein.value)
        session.close()
        val unavailable = diary.homeScreenModel("Today", 20_020, emptyList(), listOf(first), session.state.value)
        assertEquals("—", unavailable.protein.value)
    }

    @Test
    fun `failed storage load masks count and source values together`() {
        val catalog = FeatureCatalogFixture()
        val session = catalog.openSession()
        val valid = MealDiary({ MealRecordContext("meal", 20_020, "12:34") }, catalogSession = session)
        val saved = assertNotNull(valid.add(catalog.structuredDraft(), SavedMealSource.TEXT))
        val blocked = MealDiary(store = InMemoryMealDiaryStore("SNMD3:0:"))
        val home = blocked.homeScreenModel("Today", 20_020, emptyList(), listOf(saved), session.state.value)
        assertEquals("—", home.dailySummaryValue)
        assertEquals("—", home.protein.value)
        session.close()
    }
}
