package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MealDraftValidatorTest {
    @Test
    fun `requires an authored name without inventing ingredients or portion`() {
        assertEquals(
            setOf(MealDraftError.NAME_REQUIRED),
            assertIs<MealDraftValidation.Invalid>(MealDraftValidator.validate(MealDraft())).errors,
        )
        val result = valid(MealDraft(name = "  Lentil bowl  "))
        assertEquals("Lentil bowl", result.name)
        assertNull(result.portionGrams)
        assertEquals(emptyList(), result.ingredients)
        assertEquals("", result.note)
    }

    @Test
    fun `normalizes only explicit whitespace and comma separated ingredients`() {
        val result = valid(
            MealDraft(
                name = " Суп 🥣 ",
                portion = " 150.5 ",
                ingredients = " lentils,  морковь , , lentils,\nлук ",
                note = "  Без соли\nпожалуйста  ",
            ),
        )
        assertEquals("Суп 🥣", result.name)
        assertEquals(150.5, result.portionGrams)
        assertEquals(listOf("lentils", "морковь", "lentils", "лук"), result.ingredients)
        assertEquals("Без соли\nпожалуйста", result.note)
    }

    @Test
    fun `rejects invalid zero negative and nonfinite portions without dropping them`() {
        listOf("0", "-0", "-1", "NaN", "Infinity", "-Infinity", "1e999", "a", "1 2", "1,2,3", "1,234.5")
            .forEach { portion ->
                val result = assertIs<MealDraftValidation.Invalid>(
                    MealDraftValidator.validate(MealDraft(name = "Soup", portion = portion)),
                )
                assertEquals(setOf(MealDraftError.PORTION_INVALID), result.errors, portion)
            }
    }

    @Test
    fun `accepts one decimal comma without treating ingredients as numbers`() {
        val result = valid(MealDraft(name = "Суп", portion = "12,5", ingredients = "морковь, лук"))
        assertEquals(12.5, result.portionGrams)
        assertEquals(listOf("морковь", "лук"), result.ingredients)
    }

    @Test
    fun `collects field errors without silently truncating text`() {
        val result = assertIs<MealDraftValidation.Invalid>(
            MealDraftValidator.validate(
                MealDraft(
                    name = "n".repeat(MealDraftLimits.NAME_MAX_LENGTH + 1),
                    portion = "x".repeat(MealDraftLimits.PORTION_MAX_LENGTH + 1),
                    ingredients = "i".repeat(MealDraftLimits.INGREDIENTS_MAX_LENGTH + 1),
                    note = "n".repeat(MealDraftLimits.NOTE_MAX_LENGTH + 1),
                ),
            ),
        )
        assertEquals(
            setOf(
                MealDraftError.NAME_TOO_LONG,
                MealDraftError.PORTION_INVALID,
                MealDraftError.INGREDIENTS_TOO_LONG,
                MealDraftError.INGREDIENT_TOO_LONG,
                MealDraftError.NOTE_TOO_LONG,
            ),
            result.errors,
        )
    }

    @Test
    fun `limits the ingredient count and each explicit ingredient independently`() {
        val tooMany = MealDraft(
            name = "Soup",
            ingredients = List(MealDraftLimits.INGREDIENTS_MAX_COUNT + 1) { "pea" }.joinToString(","),
        )
        assertEquals(
            setOf(MealDraftError.TOO_MANY_INGREDIENTS),
            assertIs<MealDraftValidation.Invalid>(MealDraftValidator.validate(tooMany)).errors,
        )
        val tooLong = MealDraft(
            name = "Soup",
            ingredients = "x".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH + 1),
        )
        assertEquals(
            setOf(MealDraftError.INGREDIENT_TOO_LONG),
            assertIs<MealDraftValidation.Invalid>(MealDraftValidator.validate(tooLong)).errors,
        )
    }

    @Test
    fun `accepts exact field limits and positive finite portions`() {
        val result = valid(
            MealDraft(
                name = "n".repeat(MealDraftLimits.NAME_MAX_LENGTH),
                portion = Double.MAX_VALUE.toString(),
                ingredients = "i".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH),
                note = "n".repeat(MealDraftLimits.NOTE_MAX_LENGTH),
            ),
        )
        assertEquals(Double.MAX_VALUE, result.portionGrams)
        assertEquals(MealDraftLimits.NAME_MAX_LENGTH, result.name.length)
        assertEquals(MealDraftLimits.NOTE_MAX_LENGTH, result.note.length)
    }

    private fun valid(draft: MealDraft): ValidatedMealDraft =
        assertIs<MealDraftValidation.Valid>(MealDraftValidator.validate(draft)).value
}
