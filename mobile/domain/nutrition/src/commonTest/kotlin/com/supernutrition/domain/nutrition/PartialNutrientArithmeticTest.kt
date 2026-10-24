package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PartialNutrientArithmeticTest {
    private val knownValues = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)
    private val known = profile(knownValues)
    private val unknown = profile(List(7) { null })
    private val zero = profile(List(7) { 0.0 })

    @Test
    fun `empty or unavailable contributions keep every total unknown`() {
        assertEquals(unknown, aggregatePartialNutrients(emptyList()))
        assertEquals(unknown, aggregatePartialNutrients(listOf(null)))
        assertEquals(unknown, aggregatePartialNutrients(listOf(known, null)))
        assertEquals(unknown, aggregatePartialNutrients(listOf(null, known)))
        assertEquals(unknown, aggregatePartialNutrients(listOf(known, unknown)))
    }

    @Test
    fun `known contributions sum all seven fields and retain known zeros`() {
        val other = profile(listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0))
        val expected = profile(listOf(11.0, 22.0, 33.0, 44.0, 55.0, 66.0, 77.0))
        assertEquals(expected, aggregatePartialNutrients(listOf(known, other)))
        assertEquals(known, aggregatePartialNutrients(listOf(zero, known, zero)))
        assertEquals(zero, aggregatePartialNutrients(listOf(zero, zero)))
    }

    @Test
    fun `missing source fields affect only their own totals`() {
        for (field in knownValues.indices) {
            val partial = profile(knownValues.mapIndexed { index, value -> if (index == field) null else value })
            val expected = profile(
                listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0)
                    .mapIndexed { index, value -> if (index == field) null else value },
            )
            assertEquals(expected, aggregatePartialNutrients(listOf(known, partial)), "field $field")
        }
    }

    @Test
    fun `sum overflow affects only the overflowing nutrient`() {
        for (field in knownValues.indices) {
            val large = profile(
                knownValues.mapIndexed { index, value ->
                    if (index == field) Double.MAX_VALUE else value
                },
            )
            val expected = profile(
                listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0)
                    .mapIndexed { index, value -> if (index == field) null else value },
            )
            assertEquals(expected, aggregatePartialNutrients(listOf(large, large)), "field $field")
        }
    }

    @Test
    fun `portion normalization preserves all nutrient units and calculation precision`() {
        val portion = known.copy(energyKcal = 1.0009765625)
        val expected = profile(listOf(200.1953125, 400.0, 600.0, 800.0, 1000.0, 1200.0, 1400.0))
        assertEquals(expected, portion.per100GramsFromPortion(0.5))
    }

    @Test
    fun `normalization preserves each independently missing field`() {
        for (field in knownValues.indices) {
            val partial = profile(knownValues.mapIndexed { index, value -> if (index == field) null else value })
            val expected = profile(
                listOf(200.0, 400.0, 600.0, 800.0, 1000.0, 1200.0, 1400.0)
                    .mapIndexed { index, value -> if (index == field) null else value },
            )
            assertEquals(expected, partial.per100GramsFromPortion(0.5), "field $field")
        }
        assertEquals(unknown, unknown.per100GramsFromPortion(0.5))
    }

    @Test
    fun `nonpositive or nonfinite mass cannot normalize a portion`() {
        for (mass in listOf(0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(known.per100GramsFromPortion(mass), "$mass")
        }
    }

    @Test
    fun `a hundred gram portion preserves its exact source values`() {
        val portion = profile(List(7) { Double.MAX_VALUE })
        assertSame(portion, portion.per100GramsFromPortion(100.0))
    }

    @Test
    fun `known zeros stay known for tiny positive serving masses`() {
        assertEquals(zero, zero.per100GramsFromPortion(1e-308))
        assertEquals(zero, zero.per100GramsFromPortion(Double.MIN_VALUE))
    }

    @Test
    fun `normalization overflow affects only the overflowing nutrient`() {
        for (field in knownValues.indices) {
            val large = profile(
                knownValues.mapIndexed { index, value ->
                    if (index == field) Double.MAX_VALUE else value
                },
            )
            val expected = profile(
                listOf(200.0, 400.0, 600.0, 800.0, 1000.0, 1200.0, 1400.0)
                    .mapIndexed { index, value -> if (index == field) null else value },
            )
            assertEquals(expected, large.per100GramsFromPortion(0.5), "field $field")
        }
    }

    @Test
    fun `finite extreme ratios do not overflow an intermediate scale factor`() {
        val expected = profile(List(7) { 100.0 })
        val tiny = profile(List(7) { 1e-308 })
        assertEquals(expected, tiny.per100GramsFromPortion(1e-308))
        val large = profile(List(7) { Double.MAX_VALUE })
        assertEquals(expected, large.per100GramsFromPortion(Double.MAX_VALUE))
    }

    private fun profile(values: List<Double?>) = PartialNutrientProfile(
        values[0],
        values[1],
        values[2],
        values[3],
        values[4],
        values[5],
        values[6],
    )
}
