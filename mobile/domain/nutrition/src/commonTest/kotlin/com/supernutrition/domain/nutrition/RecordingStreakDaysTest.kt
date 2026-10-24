package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordingStreakDaysTest {
    @Test
    fun `today starts the descending credited sequence capped at seven`() {
        assertEquals(listOf(10, 9, 8, 7, 6, 5, 4), recordingStreakDays((0..12).toSet(), 10))
    }

    @Test
    fun `yesterday grace credits all seven days without requiring today`() {
        assertEquals(listOf(9, 8, 7, 6, 5, 4, 3), recordingStreakDays((3..9).toSet(), 10))
    }

    @Test
    fun `a gap ends the current sequence and excludes disconnected dates`() {
        assertEquals(listOf(10, 9, 8), recordingStreakDays(setOf(4, 6, 8, 9, 10), 10))
        assertEquals(listOf(9, 8), recordingStreakDays(setOf(4, 6, 8, 9), 10))
    }

    @Test
    fun `a whole missed day leaves no credited sequence`() {
        assertEquals(emptyList(), recordingStreakDays(setOf(6, 7, 8), 10))
    }

    @Test
    fun `empty and future only dates do not invent a current streak`() {
        assertEquals(emptyList(), recordingStreakDays(emptySet(), 10))
        assertEquals(emptyList(), recordingStreakDays(setOf(11, 12, Int.MAX_VALUE), 10))
    }

    @Test
    fun `first calendar day never credits negative or future dates`() {
        assertEquals(listOf(0), recordingStreakDays(setOf(-1, 0, 1), 0))
        assertEquals(emptyList(), recordingStreakDays(setOf(-2, -1, 1), 0))
    }

    @Test
    fun `final integer calendar day remains ordered without overflow`() {
        assertEquals(
            listOf(Int.MAX_VALUE, Int.MAX_VALUE - 1, Int.MAX_VALUE - 2),
            recordingStreakDays(setOf(Int.MAX_VALUE, Int.MAX_VALUE - 1, Int.MAX_VALUE - 2), Int.MAX_VALUE),
        )
    }

    @Test
    fun `negative reference day is rejected as elsewhere in rewards input`() {
        assertFailsWith<IllegalArgumentException> { recordingStreakDays(emptySet(), -1) }
    }
}
