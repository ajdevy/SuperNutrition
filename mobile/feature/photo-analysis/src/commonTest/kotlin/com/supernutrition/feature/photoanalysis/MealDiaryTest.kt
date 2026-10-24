package com.supernutrition.feature.photoanalysis

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealDiaryTest {
    @Test
    fun `explicit saves restore structured meals without photos or nutrient claims`() {
        val store = InMemoryMealDiaryStore()
        val diary = diary(store)
        val saved = assertNotNull(diary.add(MealDraft("Oatmeal", "300", "Banana, oats"), SavedMealSource.TEXT))
        assertEquals("meal-1", saved.id)
        assertEquals(20_020, saved.localDay)
        assertEquals("08:10", saved.timeLabel)
        assertEquals(300.0, saved.portionGrams)
        assertEquals(listOf("Banana", "oats"), saved.ingredients)
        assertEquals(listOf(saved), diary(store).meals.value)
        assertNull(diary.failure.value)
        assertFalse(diary.loadFailed)
    }

    @Test
    fun `invalid drafts never call storage or allocate IDs`() {
        var calls = 0
        val diary = MealDiary(recordContext = {
            calls++
            context()
        })
        assertNull(diary.add(MealDraft(), SavedMealSource.TEXT))
        assertNull(diary.add(MealDraft("Toast", "-1"), SavedMealSource.TEXT))
        assertEquals(0, calls)
        assertTrue(diary.meals.value.isEmpty())
    }

    @Test
    fun `editing replaces the same entry and preserves its original recording context`() {
        var sequence = 0
        val diary = MealDiary(recordContext = { context().copy(id = "meal-${++sequence}") })
        val first = assertNotNull(diary.add(MealDraft("Toast"), SavedMealSource.PHOTO))
        assertTrue(diary.update(first.id, MealDraft("Egg toast", "125,5", "Egg, bread", "Less sauce")))
        val edited = diary.meals.value.single()
        assertEquals(first.id, edited.id)
        assertEquals(first.localDay, edited.localDay)
        assertEquals(first.timeLabel, edited.timeLabel)
        assertEquals(SavedMealSource.PHOTO, edited.source)
        assertEquals("Egg toast", edited.name)
        assertEquals(125.5, edited.portionGrams)
        assertEquals("Less sauce", edited.note)
        assertEquals(1, sequence)
        assertFalse(diary.update(first.id, MealDraft("")))
        assertFalse(diary.update("stale-id", MealDraft("Soup")))
        assertEquals(edited, diary.meals.value.single())
    }

    @Test
    fun `delete is explicit and stale deletion cannot manufacture an entry`() {
        val store = InMemoryMealDiaryStore()
        val diary = diary(store)
        assertFalse(diary.delete("unknown"))
        val saved = assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertTrue(diary.delete(saved.id))
        assertFalse(diary.delete(saved.id))
        assertTrue(diary(store).meals.value.isEmpty())
    }

    @Test
    fun `a failed write leaves observable data unchanged and can be retried`() {
        var writable = false
        val store = object : MealDiaryStore {
            override fun load(): String? = null
            override fun save(encoded: String): Boolean = writable
        }
        val diary = diary(store)
        assertNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertEquals(MealDiaryFailure.WRITE_FAILED, diary.failure.value)
        assertTrue(diary.meals.value.isEmpty())
        writable = true
        assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertNull(diary.failure.value)
    }

    @Test
    fun `failed edit and delete keep the last saved state`() {
        var writable = true
        var stored: String? = null
        val store = object : MealDiaryStore {
            override fun load() = stored
            override fun save(encoded: String): Boolean {
                if (writable) stored = encoded
                return writable
            }
        }
        val diary = diary(store)
        val saved = assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        writable = false
        assertFalse(diary.update(saved.id, MealDraft("Salad")))
        assertFalse(diary.delete(saved.id))
        assertEquals(listOf(saved), diary.meals.value)
        assertEquals(listOf(saved), diary(store).meals.value)
    }

    @Test
    fun `corrupt storage is reported and never overwritten with an empty diary`() {
        var saves = 0
        val store = object : MealDiaryStore {
            override fun load() = "invalid stored document"
            override fun save(encoded: String): Boolean {
                saves++
                return true
            }
        }
        val diary = diary(store)
        assertEquals(MealDiaryFailure.CORRUPT_STORAGE, diary.failure.value)
        assertTrue(diary.loadFailed)
        assertNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertEquals(0, saves)
    }

    @Test
    fun `load and write exceptions have typed failures without leaking raw exceptions`() {
        val failedLoad = diary(object : MealDiaryStore {
            override fun load(): String? = error("private storage path")
            override fun save(encoded: String) = true
        })
        assertEquals(MealDiaryFailure.LOAD_FAILED, failedLoad.failure.value)
        assertNull(failedLoad.add(MealDraft("Soup"), SavedMealSource.TEXT))
        val failedWrite = diary(object : MealDiaryStore {
            override fun load(): String? = null
            override fun save(encoded: String): Boolean = error("private storage path")
        })
        assertNull(failedWrite.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertEquals(MealDiaryFailure.WRITE_FAILED, failedWrite.failure.value)
    }

    @Test
    fun `cancellation is never converted into storage failure`() {
        assertFailsWith<CancellationException> {
            diary(object : MealDiaryStore {
                override fun load(): String? = throw CancellationException()
                override fun save(encoded: String) = true
            })
        }
        val diary = diary(object : MealDiaryStore {
            override fun load(): String? = null
            override fun save(encoded: String): Boolean = throw CancellationException()
        })
        assertFailsWith<CancellationException> { diary.add(MealDraft("Soup"), SavedMealSource.TEXT) }
    }

    @Test
    fun `duplicate generated IDs cannot overwrite an earlier meal`() {
        val diary = diary()
        val first = assertNotNull(diary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        assertNull(diary.add(MealDraft("Salad"), SavedMealSource.TEXT))
        assertEquals(listOf(first), diary.meals.value)
    }

    private fun context() = MealRecordContext("meal-1", 20_020, "08:10")

    private fun diary(store: MealDiaryStore = InMemoryMealDiaryStore()) = MealDiary(
        recordContext = ::context,
        store = store,
    )
}
