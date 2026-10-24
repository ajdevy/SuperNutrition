package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutrientDisplayNumberTest {
    @Test
    fun `rounding uses decimal half up and hides binary tails`() {
        for ((value, expected) in listOf(
            0.0 to "0", -0.0 to "0", 1.0 to "1", 1.234 to "1.23", 1.235 to "1.24",
            2.675 to "2.68", 1.995 to "2", 9.995 to "10", 99.999 to "100", 12.255 to "12.26",
            0.009 to "0.01", 0.005 to "0.01", 0.004 to "0", 1.2000000000000002 to "1.2",
        )) {
            assertEquals(expected, nutrientDisplayNumber(value), "$value")
        }
    }

    @Test
    fun `unknown and invalid values never become zero`() {
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals("—", nutrientDisplayNumber(value))
        }
    }

    @Test
    fun `scientific notation remains finite and rounds without multiplication overflow`() {
        assertEquals("100000000000000000000", nutrientDisplayNumber(1e20))
        assertEquals("0", nutrientDisplayNumber(Double.MIN_VALUE))
        val maximum = nutrientDisplayNumber(Double.MAX_VALUE)
        assertEquals(309, maximum.length)
        assertTrue(maximum.startsWith("17976931348623157"))
        assertTrue(maximum.all(Char::isDigit))
    }

    @Test
    fun `platform scientific spellings share the same expansion`() {
        assertEquals("12.345", expandedNutrientDecimal("1.2345E+1"))
        assertEquals("100", expandedNutrientDecimal("1e2"))
        assertEquals("0.1", expandedNutrientDecimal("1E-1"))
        assertEquals("1.25", expandedNutrientDecimal("1.25"))
    }
}
