package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MealRecordContextTest {
    @Test
    fun `pre-epoch timestamps use the preceding local calendar day`() {
        assertEquals(MealRecordContext("before", -1, "23:59"), mealRecordContext(-1, 0, "before"))
    }

    @Test
    fun `platform clocks generate distinct opaque recording IDs and local time labels`() {
        val first = currentMealRecordContext()
        val second = currentMealRecordContext()
        assertTrue(first.id.isNotBlank())
        assertNotEquals(first.id, second.id)
        assertTrue(first.localDay > 20_000)
        assertTrue(Regex("[0-2][0-9]:[0-5][0-9]").matches(first.timeLabel))
    }

    @Test
    fun `positive offset records the next local calendar day near UTC midnight`() {
        val context = mealRecordContext(20_020L * 86_400 + 23 * 3600 + 30 * 60, 3 * 3600, "meal")
        assertEquals(MealRecordContext("meal", 20_021, "02:30"), context)
    }

    @Test
    fun `negative offset records the previous local calendar day near UTC midnight`() {
        val context = mealRecordContext(20_020L * 86_400 + 30 * 60, -2 * 3600, "meal")
        assertEquals(MealRecordContext("meal", 20_019, "22:30"), context)
    }

    @Test
    fun `UTC midnight and minute formatting are deterministic`() {
        assertEquals(MealRecordContext("a", 20_020, "00:00"), mealRecordContext(20_020L * 86_400, 0, "a"))
        assertEquals(
            MealRecordContext("b", 20_020, "08:05"),
            mealRecordContext(20_020L * 86_400 + 8 * 3600 + 5 * 60, 0, "b"),
        )
    }
}
