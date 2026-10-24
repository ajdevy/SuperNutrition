package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MealEditValidatorTest {
    @Test
    fun `shared formatter preserves authored text and formats integer optional and decimal portions`() {
        val saved = meal().copy(note = "  Original note\nsecond line  ")
        val draft = saved.toMealDraft()
        assertEquals(saved.name, draft.name)
        assertEquals("125", draft.portion)
        assertEquals("carrots, cooked, 豆腐", draft.ingredients)
        assertEquals(saved.note, draft.note)
        assertEquals("125.25", saved.copy(portionGrams = 125.25).toMealDraft().portion)
        assertEquals("", saved.copy(portionGrams = null).toMealDraft().portion)
        assertEquals(Double.MAX_VALUE.toString(), saved.copy(portionGrams = Double.MAX_VALUE).toMealDraft().portion)
    }

    @Test
    fun `changing name and portion preserves unchanged structured comma labels exactly`() {
        val saved = meal()
        val draft = saved.toMealDraft().copy(name = "New name", portion = "150,5")
        val result = valid(draft, saved)
        assertEquals("New name", result.name)
        assertEquals(150.5, result.portionGrams)
        assertEquals(listOf("carrots, cooked", "豆腐"), result.ingredients)
        assertEquals(saved.note, result.note)
    }

    @Test
    fun `editing ingredients explicitly uses normal comma separated parsing`() {
        val saved = meal()
        val result = valid(saved.toMealDraft().copy(ingredients = " onion, lentils, , 豆腐 "), saved)
        assertEquals(listOf("onion", "lentils", "豆腐"), result.ingredients)
        val repaired = saved.copy(ingredients = listOf(" "))
        assertEquals(listOf("onion"), valid(repaired.toMealDraft().copy(ingredients = "onion"), repaired).ingredients)
    }

    @Test
    fun `unchanged structured ingredients retain their own count and content budget`() {
        val labels = List(MealDraftLimits.INGREDIENTS_MAX_COUNT) {
            "i".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH - 3) + ",x"
        }
        val saved = meal().copy(ingredients = labels)
        val result = valid(saved.toMealDraft().copy(name = "Renamed"), saved)
        assertEquals(labels, result.ingredients)
        assertEquals(MealDraftLimits.INGREDIENTS_MAX_COUNT, result.ingredients.size)
    }

    @Test
    fun `preserved structured ingredients are validated without splitting their labels`() {
        assertEquals(setOf(MealDraftError.INGREDIENT_REQUIRED), errors(meal().copy(ingredients = listOf(" "))))
        assertEquals(
            setOf(MealDraftError.INGREDIENT_TOO_LONG),
            errors(meal().copy(ingredients = listOf("x".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH) + ","))),
        )
        assertEquals(
            setOf(MealDraftError.TOO_MANY_INGREDIENTS),
            errors(meal().copy(ingredients = List(MealDraftLimits.INGREDIENTS_MAX_COUNT + 1) { "pea" })),
        )
        assertEquals(
            setOf(MealDraftError.INGREDIENTS_TOO_LONG),
            errors(
                meal().copy(
                    ingredients = List(MealDraftLimits.INGREDIENTS_MAX_COUNT) {
                        "x".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH)
                    },
                ),
            ),
        )
    }

    @Test
    fun `preserving ingredient identities still validates the other editable fields`() {
        val saved = meal()
        val result = assertIs<MealDraftValidation.Invalid>(
            MealDraftValidator.validate(
                saved.toMealDraft().copy(
                    name = " ",
                    portion = "NaN",
                    note = "x".repeat(MealDraftLimits.NOTE_MAX_LENGTH + 1),
                ),
                saved,
            ),
        )
        assertEquals(
            setOf(MealDraftError.NAME_REQUIRED, MealDraftError.PORTION_INVALID, MealDraftError.NOTE_TOO_LONG),
            result.errors,
        )
        val emptyIngredients = saved.copy(ingredients = emptyList())
        assertEquals(emptyList(), valid(emptyIngredients.toMealDraft(), emptyIngredients).ingredients)
    }

    private fun valid(draft: MealDraft, existing: SavedMeal): ValidatedMealDraft =
        assertIs<MealDraftValidation.Valid>(MealDraftValidator.validate(draft, existing)).value

    private fun errors(existing: SavedMeal): Set<MealDraftError> =
        assertIs<MealDraftValidation.Invalid>(MealDraftValidator.validate(existing.toMealDraft(), existing)).errors

    private fun meal() = SavedMeal(
        id = "one",
        localDay = 20_020,
        timeLabel = "12:34",
        name = "Carrots and tofu",
        portionGrams = 125.0,
        ingredients = listOf("carrots, cooked", "豆腐"),
        note = "Original note",
        source = SavedMealSource.PHOTO,
    )
}
