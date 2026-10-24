package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PartialNutrientProfileTest {
    @Test
    fun `sourced half portion retains known nutrients and missing fiber`() {
        val source = PartialNutrientProfile(325.0, 24.51, 2.04, 24.35, null, 0.55, 1808.0)
        val portion = source.scaledToGrams(50.0)
        assertEquals(162.5, portion?.energyKcal)
        assertEquals(12.255, portion?.proteinGrams)
        assertNull(portion?.fiberGrams)
        assertEquals(904.0, portion?.sodiumMilligrams)
    }

    @Test
    fun `known zero and every unknown nutrient retain their meaning`() {
        val missing = PartialNutrientProfile(null, null, null, null, null, null, null)
        assertEquals(missing, missing.scaledToGrams(100.0))
        val zero = PartialNutrientProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(zero, zero.scaledToGrams(150.0))
    }

    @Test
    fun `invalid source values are rejected rather than normalized`() {
        for (invalid in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                PartialNutrientProfile(invalid, null, null, null, null, null, null)
            }
        }
    }

    @Test
    fun `invalid and overflowing grams cannot yield nutrition`() {
        val source = PartialNutrientProfile(325.0, 24.51, 2.04, 24.35, null, 0.55, 1808.0)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertNull(source.scaledToGrams(invalid))
        }
    }
}
