package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealDiaryMutationTest {
    @Test
    fun `editing a name keeps unchanged source ingredient labels containing commas`() {
        val diary = MealDiary({ MealRecordContext("meal", 20_020, "10:00") })
        val saved = assertNotNull(
            diary.add(
                ValidatedMealDraft("Bowl", 150.0, listOf("Rice, white"), ""),
                SavedMealSource.PHOTO,
            ),
        )
        assertTrue(diary.update(saved.id, MealDraft("Corrected bowl", "150", "Rice, white")))
        assertEquals(listOf("Rice, white"), diary.meals.value.single().ingredients)
    }

    @Test
    fun `newest entries appear first while editing another entry preserves the list order`() {
        var sequence = 0
        val store = InMemoryMealDiaryStore()
        val diary = MealDiary({ MealRecordContext("meal-${++sequence}", 20_020, "10:00") }, store)
        val first = assertNotNull(diary.add(MealDraft("First"), SavedMealSource.TEXT))
        val second = assertNotNull(diary.add(MealDraft("Second"), SavedMealSource.PHOTO))
        assertEquals(listOf(second, first), diary.meals.value)
        assertTrue(diary.update(first.id, MealDraft("First corrected")))
        assertEquals(listOf("Second", "First corrected"), diary.meals.value.map { it.name })
        assertEquals(diary.meals.value, MealDiary(store = store).meals.value)
        assertTrue(diary.delete(first.id))
        assertEquals(listOf(second), diary.meals.value)
    }

    @Test
    fun `failed restoration blocks every write without allocating a new context`() {
        var allocations = 0
        var saves = 0
        val diary = MealDiary(
            recordContext = {
                allocations++
                MealRecordContext("meal", 20_020, "10:00")
            },
            store = object : MealDiaryStore {
                override fun load() = "SNMD3:0:"
                override fun save(encoded: String): Boolean {
                    saves++
                    return true
                }
            },
        )
        assertNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertFalse(diary.update("meal", MealDraft("Soup")))
        assertFalse(diary.delete("meal"))
        assertEquals(0, allocations)
        assertEquals(0, saves)
        assertEquals(MealDiaryFailure.CORRUPT_STORAGE, diary.failure.value)
    }

    @Test
    fun `invalid clock metadata fails before replacing stored data`() {
        val store = InMemoryMealDiaryStore()
        val diary = MealDiary({ MealRecordContext("", 20_020, "10:00") }, store)
        assertNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertTrue(diary.meals.value.isEmpty())
        assertNull(store.load())
        assertEquals(MealDiaryFailure.WRITE_FAILED, diary.failure.value)
    }

    @Test
    fun `review labels use the shared codec validation without CSV reinterpretation`() {
        var sequence = 0
        val diary = MealDiary({ MealRecordContext("meal-${++sequence}", 20_020, "10:00") })
        val draft = ValidatedMealDraft("Soup", null, listOf("Rice, white"), "")
        val saved = assertNotNull(diary.add(draft, SavedMealSource.PHOTO))
        assertEquals(listOf("Rice, white"), saved.ingredients)
        assertFalse(diary.update(saved.id, draft.copy(ingredients = listOf(""))))
        assertEquals(listOf(saved), diary.meals.value)
        assertTrue(diary.update(saved.id, draft.copy(note = "Edited")))
        assertNull(diary.failure.value)
    }
}
