package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.RewardFoodAttributes
import com.supernutrition.domain.nutrition.RewardId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MealDiaryHistoryActionsTest {
    @Test
    fun `a history correction refreshes rewards synchronously without changing its grounded source`() {
        val diary = diary()
        val saved = assertNotNull(diary.add(MealDraft("Egg bowl"), SavedMealSource.PHOTO))
        val original = confirmedFood("original")
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original))

        assertTrue(diary.updateFromHistory(saved.id, MealDraft("Rice bowl", ingredients = "Rice"), rewards))

        assertEquals("Rice bowl", diary.meals.value.single().name)
        assertTrue(rewards.confirmedFoods.value.isEmpty())
        assertEquals(0, progress(rewards, RewardId.PROTEIN_ALL_DAY))
        assertEquals(1, progress(rewards, RewardId.SEVEN_DAYS_RECORDED))
        assertEquals(listOf(original), rewards.ledger.entries.value)
    }

    @Test
    fun `failed and invalid history corrections leave both diary and rewards unchanged`() {
        val store = HistoryTestStore()
        val diary = diary(store)
        val saved = assertNotNull(diary.add(MealDraft("Egg bowl"), SavedMealSource.PHOTO))
        val original = confirmedFood("original")
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original))
        store.writable = false

        assertFalse(diary.updateFromHistory(saved.id, MealDraft("Rice bowl"), rewards))
        assertFalse(diary.updateFromHistory(saved.id, MealDraft(), rewards))

        assertEquals(listOf(saved), diary.meals.value)
        assertEquals(listOf(original), rewards.confirmedFoods.value)
        assertEquals(1, progress(rewards, RewardId.PROTEIN_ALL_DAY))
        assertEquals(MealDiaryFailure.WRITE_FAILED, diary.failure.value)
    }

    @Test
    fun `successful deletion suppresses an overlapping grounded row even before the first diary refresh`() {
        val diary = diary()
        val saved = assertNotNull(diary.add(MealDraft("Corrected bowl"), SavedMealSource.TEXT))
        val original = confirmedFood("original")
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original))

        assertTrue(diary.deleteFromHistory(saved.id, rewards))
        rewards.updateRecordedMeals(diary.meals.value)

        assertTrue(diary.meals.value.isEmpty())
        assertTrue(rewards.confirmedFoods.value.isEmpty())
        assertEquals(0, progress(rewards, RewardId.PROTEIN_ALL_DAY))
        assertEquals(0, progress(rewards, RewardId.SEVEN_DAYS_RECORDED))
        assertEquals(listOf(original), rewards.ledger.entries.value)
    }

    @Test
    fun `failed deletion preserves visible grounded facts and can be retried`() {
        val store = HistoryTestStore()
        val diary = diary(store)
        val saved = assertNotNull(diary.add(MealDraft("Corrected bowl"), SavedMealSource.TEXT))
        val original = confirmedFood("original")
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original))
        store.writable = false

        assertFalse(diary.deleteFromHistory(saved.id, rewards))
        assertEquals(listOf(saved), diary.meals.value)
        assertEquals(listOf(original), rewards.confirmedFoods.value)
        assertEquals(1, progress(rewards, RewardId.PROTEIN_ALL_DAY))

        store.writable = true
        assertTrue(diary.deleteFromHistory(saved.id, rewards))
        assertTrue(rewards.confirmedFoods.value.isEmpty())
        assertTrue(diary.meals.value.isEmpty())
    }

    @Test
    fun `deleting a corrected meal preserves other meal IDs and other calendar days`() {
        val diary = diary()
        val saved = assertNotNull(diary.add(MealDraft("Corrected bowl"), SavedMealSource.TEXT))
        val original = confirmedFood("original")
        val lunch = confirmedFood("lunch", mealId = "lunch")
        val yesterday = confirmedFood("yesterday", localDay = 9)
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original, lunch, yesterday))

        assertTrue(diary.deleteFromHistory(saved.id, rewards))

        assertEquals(listOf(lunch, yesterday), rewards.confirmedFoods.value)
        assertEquals(1, progress(rewards, RewardId.PROTEIN_ALL_DAY))
        assertEquals(listOf(original, lunch, yesterday), rewards.ledger.entries.value)
    }

    @Test
    fun `stale history actions cannot mutate the diary or reward projection`() {
        val diary = diary()
        val saved = assertNotNull(diary.add(MealDraft("Corrected bowl"), SavedMealSource.TEXT))
        val original = confirmedFood("original")
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original))

        assertFalse(diary.updateFromHistory("missing", MealDraft("Soup"), rewards))
        assertFalse(diary.deleteFromHistory("missing", rewards))

        assertEquals(listOf(saved), diary.meals.value)
        assertEquals(listOf(original), rewards.confirmedFoods.value)
    }

    @Test
    fun `history storage cancellation preserves both sources and is propagated`() {
        val store = HistoryTestStore()
        val diary = diary(store)
        val saved = assertNotNull(diary.add(MealDraft("Corrected bowl"), SavedMealSource.TEXT))
        val original = confirmedFood("original")
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(original))
        store.cancelled = true

        assertFailsWith<CancellationException> { diary.deleteFromHistory(saved.id, rewards) }
        assertFailsWith<CancellationException> { diary.updateFromHistory(saved.id, MealDraft("Soup"), rewards) }

        assertEquals(listOf(saved), diary.meals.value)
        assertEquals(listOf(original), rewards.confirmedFoods.value)
    }

    @Test
    fun `deletion hides obsolete facts before a diary observer can render the removed row`() {
        val diary = diary()
        val saved = assertNotNull(diary.add(MealDraft("Corrected bowl"), SavedMealSource.TEXT))
        val rewards = RewardsFeature({ 10 }, initialFoods = listOf(confirmedFood("original")))
        val observations = mutableListOf<Pair<Boolean, Int>>()
        val scope = CoroutineScope(ImmediateHistoryDispatcher())
        scope.launch {
            diary.meals.collect { meals ->
                if (meals.isEmpty()) {
                    observations += rewards.confirmedFoods.value.isEmpty() to
                        progress(rewards, RewardId.PROTEIN_ALL_DAY)
                }
            }
        }
        try {
            assertTrue(diary.deleteFromHistory(saved.id, rewards))
            assertEquals(listOf(true to 0), observations)
        } finally {
            scope.cancel()
        }
    }
}

private fun diary(store: MealDiaryStore = InMemoryMealDiaryStore()): MealDiary =
    MealDiary({ MealRecordContext("breakfast", 10, "12:00") }, store)

private fun confirmedFood(entryId: String, mealId: String = "breakfast", localDay: Int = 10): ConfirmedConsumedFood =
    ConfirmedConsumedFood.fromGroundedResult(
        entryId = entryId,
        localDay = localDay,
        mealId = mealId,
        grounded = rewardsTestGroundedResult("egg"),
        attributes = RewardFoodAttributes(hasProtein = true),
    )

private fun progress(rewards: RewardsFeature, id: RewardId): Int =
    rewards.snapshot.value.cards.first { it.id == id }.progress.current

private class HistoryTestStore : MealDiaryStore {
    var writable = true
    var cancelled = false
    private var encoded: String? = null

    override fun load(): String? = encoded

    override fun save(encoded: String): Boolean {
        if (cancelled) throw CancellationException()
        if (writable) this.encoded = encoded
        return writable
    }
}

/** Runs collectors immediately so a transient stale projection cannot hide behind dispatch. */
private class ImmediateHistoryDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}
