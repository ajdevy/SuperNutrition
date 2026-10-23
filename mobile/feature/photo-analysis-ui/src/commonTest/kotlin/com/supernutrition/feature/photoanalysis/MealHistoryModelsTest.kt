package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MealHistoryModelsTest {
    @Test
    fun `adapter groups by day and meal while keeping known insertion order`() {
        val foods = listOf(
            profileConfirmedFood("1", "repeat", "Egg", 10),
            profileConfirmedFood("2", "later", "Beans", 11),
            profileConfirmedFood("3", "repeat", "Toast", 10),
            profileConfirmedFood("4", "repeat", "Rice", 11),
            profileConfirmedFood("5", "last", "Lentils", 11),
        )
        val meals = confirmedMealHistory(foods)
        assertEquals(listOf("later", "repeat", "last", "repeat"), meals.map { it.id.mealId })
        assertEquals(listOf(11, 11, 11, 10), meals.map { it.id.localDay })
        assertEquals(listOf("Egg", "Toast"), meals.last().ingredients)
        assertEquals("Egg", meals.last().title)
        assertTrue(meals.all { it.metadata.timeLabel == null && it.metadata.image == null })
        assertEquals(5, foods.size)
        assertTrue(confirmedMealHistory(emptyList()).isEmpty())
    }

    @Test
    fun `search trims query and matches title or any ingredient without changing source`() {
        val meals = listOf(
            MealHistoryMeal(MealHistoryId(10, "a"), "Lunch", listOf("Lentils", "Brown rice")),
            MealHistoryMeal(MealHistoryId(10, "b"), "Omelette", listOf("Egg", "Toast")),
        )
        assertSame(meals, searchMealHistory(meals, "  "))
        assertEquals(listOf(meals[0]), searchMealHistory(meals, "  RICE "))
        assertEquals(listOf(meals[1]), searchMealHistory(meals, "oMeL"))
        assertEquals(listOf(meals[1]), searchMealHistory(meals, "egg"))
        assertEquals(listOf(meals[1]), searchMealHistory(meals, "  egg   TOAST  "))
        assertEquals(listOf(meals[0]), searchMealHistory(meals, "LUNCH\n rice"))
        assertTrue(searchMealHistory(meals, "egg lentils").isEmpty())
        assertTrue(searchMealHistory(meals, "salmon").isEmpty())
        assertEquals(2, meals.size)
    }

    @Test
    fun `search accepts pasted Unicode whitespace between ingredient words`() {
        val meals = listOf(MealHistoryMeal(MealHistoryId(10, "a"), "Omelette", listOf("Egg", "Toast")))
        listOf('\u00A0', '\u2009', '\u2003', '\t', '\n').forEach { separator ->
            assertEquals(
                meals,
                searchMealHistory(meals, " Egg${separator}Toast "),
                "Separator U+${separator.code.toString(16)}",
            )
        }
    }

    @Test
    fun `search supports mixed case Cyrillic names`() {
        val meals = listOf(MealHistoryMeal(MealHistoryId(10, "a"), "Обед", listOf("Гречка", "Курица")))
        assertEquals(meals, searchMealHistory(meals, "  КУРИЦА гречка  "))
        assertTrue(searchMealHistory(meals, "рыба").isEmpty())
    }

    @Test
    fun `calendar conversion handles leap rules year boundaries and unsupported days`() {
        assertEquals(HistoryCalendarDate(1970, 1, 1), historyCalendarDate(0))
        assertEquals(HistoryCalendarDate(2000, 2, 29), historyCalendarDate(11016))
        assertEquals(HistoryCalendarDate(2000, 3, 1), historyCalendarDate(11017))
        assertEquals(HistoryCalendarDate(2100, 2, 28), historyCalendarDate(47540))
        assertEquals(HistoryCalendarDate(2100, 3, 1), historyCalendarDate(47541))
        assertEquals(HistoryCalendarDate(2025, 12, 31), historyCalendarDate(20453))
        assertEquals(HistoryCalendarDate(2026, 1, 1), historyCalendarDate(20454))
        assertEquals(HistoryCalendarDate(9999, 12, 31), historyCalendarDate(2932896))
        listOf(-1, Int.MIN_VALUE, 2932897, Int.MAX_VALUE).forEach { assertNull(historyCalendarDate(it)) }
    }

    @Test
    fun `day labels use the supplied reference date and keep older years explicit`() {
        val copy = MealHistoryCopy()
        assertEquals("Today · Oct 21", mealHistoryDayLabel(20747, 20747, copy))
        assertEquals("Yesterday · Oct 20", mealHistoryDayLabel(20746, 20747, copy))
        assertEquals("Oct 19", mealHistoryDayLabel(20745, 20747, copy))
        assertEquals("Dec 31, 2025", mealHistoryDayLabel(20453, 20747, copy))
        assertEquals("Yesterday · Dec 31, 2025", mealHistoryDayLabel(20453, 20454, copy))
        assertEquals(copy.dateUnavailable, mealHistoryDayLabel(Int.MAX_VALUE, Int.MIN_VALUE, copy))
        assertEquals("Jan 1, 1970", mealHistoryDayLabel(0, Int.MIN_VALUE, copy))
    }

    @Test
    fun `local clock applies offsets before flooring at midnight`() {
        assertEquals(1, historyLocalDay(86_399.0, 3_600))
        assertEquals(0, historyLocalDay(86_400.0, -3_600))
        assertEquals(-1, historyLocalDay(-1.0, 0))
    }

    @Test
    fun `opening history captures the current day again after midnight`() {
        var day = 10
        val state = ProfileFlowState(currentDay = { day })
        state.showHistory()
        assertEquals(10, state.referenceDay)
        assertTrue(state.historyVisible)
        state.showProfile()
        day = 11
        state.showHistory()
        assertEquals(11, state.referenceDay)
    }
}
