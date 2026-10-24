package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealDiaryModelsTest {
    @Test
    fun `authored entries merge without duplicate meals or old nutrient claims`() {
        val saved = diaryMealFixture(id = "same")
        val foods = listOf(profileConfirmedFood("1", "same", "Old catalog meal"))
        val merged = mergedMealHistory(foods, listOf(saved, saved))
        assertEquals(1, merged.size)
        assertEquals(saved.name, merged.single().title)
        assertEquals("13:15", merged.single().metadata.timeLabel)
        assertNull(merged.single().metadata.image)
        assertTrue(merged.single().groundedFoods.isEmpty())
        assertNull(merged.single().nutrition(false))
        assertNull(merged.single().nutrition(true))
    }

    @Test
    fun `grounded serving totals normalize only from real nonzero mass`() {
        val meal = confirmedMealHistory(
            listOf(profileConfirmedFood("1", "a", "Egg"), profileConfirmedFood("2", "a", "Toast")),
        ).single()
        assertEquals(200.0, meal.portionGrams())
        assertEquals(10.0, meal.nutrition(false)?.proteinGrams)
        assertEquals(5.0, meal.nutrition(true)?.proteinGrams)
        val unknown = mergedMealHistory(emptyList(), listOf(diaryMealFixture(portion = null))).single()
        assertNull(unknown.portionGrams())
        assertNull(unknown.nutrition(true))
    }

    @Test
    fun `shared Back unwinds editor details history and name mode in order`() {
        val state = ProfileFlowState()
        assertFalse(state.canGoBack)
        assertFalse(state.back())
        state.showHistory()
        state.showMeal(MealHistoryId(10, "meal"))
        state.beginMealEdit()
        assertTrue(state.back())
        assertFalse(state.mealEditing)
        assertEquals("meal", state.selectedMealId?.mealId)
        assertTrue(state.back())
        assertNull(state.selectedMealId)
        assertTrue(state.historyVisible)
        assertTrue(state.back())
        assertFalse(state.historyVisible)
        state.beginNameEdit()
        assertTrue(state.back())
        assertFalse(state.canGoBack)
    }

    @Test
    fun `live Home shows supplied day and actual count without targets or fake progress`() {
        val model = liveHomeScreenModel("Saturday, Oct 24", 2)
        assertEquals("Saturday, Oct 24", model.dateLabel)
        assertEquals("2 meals recorded", model.rhythmSubtitle)
        assertEquals("1 meal recorded", liveHomeScreenModel("Today", 1).rhythmSubtitle)
        listOf(model.fiber, model.protein, model.fats, model.colors).forEach {
            assertEquals("—", it.value)
            assertNull(it.progressFraction)
        }
        assertEquals("Let's add your first meal", liveHomeScreenModel("Today", 0).careQuestion)
        assertFailsWith<IllegalArgumentException> { liveHomeScreenModel("Today", -1) }
    }

    @Test
    fun `reward detail Back clears the selected identity`() {
        val state = RewardsFlowState()
        assertFalse(state.back())
        state.showReward(com.supernutrition.domain.nutrition.RewardId.FIBER_PRESENT)
        assertTrue(state.canGoBack)
        assertTrue(state.back())
        assertFalse(state.canGoBack)
    }

    @Test
    fun `opaque meal destination restores without storing the meal or draft`() {
        val state = ProfileFlowState().apply {
            showMeal(MealHistoryId(10, "meal"))
            beginMealEdit()
        }
        val saved = with(ProfileFlowState.StateSaver) { SaverScope { true }.save(state) }
        val restored = assertNotNull(ProfileFlowState.StateSaver.restore(assertNotNull(saved)))
        assertEquals(MealHistoryId(10, "meal"), restored.selectedMealId)
        assertTrue(restored.mealEditing)
        assertTrue(restored.historyVisible)
        assertNull(ProfileFlowState.StateSaver.restore(listOf(false, false, 10, "meal", true)))
        assertNull(ProfileFlowState.StateSaver.restore(listOf(true, false, -4, "meal", false)))
    }
}

internal fun diaryMealFixture(id: String = "meal", portion: Double? = 300.0): SavedMeal = SavedMeal(
    id = id,
    localDay = 10,
    timeLabel = "13:15",
    name = "Oatmeal",
    portionGrams = portion,
    ingredients = listOf("Oats", "Apple"),
    source = SavedMealSource.TEXT,
)
