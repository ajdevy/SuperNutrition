package com.supernutrition.feature.photoanalysis

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MealHistoryCalendarCrossCheckTest {
    @Test
    fun `common date conversion agrees with the independent Java calendar across supported years`() {
        (0..2_932_896 step 97).forEach { epochDay ->
            val expected = LocalDate.ofEpochDay(epochDay.toLong())
            assertEquals(
                HistoryCalendarDate(expected.year, expected.monthValue, expected.dayOfMonth),
                historyCalendarDate(epochDay),
                "Epoch day $epochDay",
            )
        }
    }
}
