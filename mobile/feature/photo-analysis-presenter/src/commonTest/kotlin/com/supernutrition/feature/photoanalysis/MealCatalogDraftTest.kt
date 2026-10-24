package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogMealBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MealCatalogDraftTest {
    @Test
    fun `explicit product preserves its exact long comma-bearing label as one product`() {
        val product = presenterCatalogProduct("Rice, " + "x".repeat(128))
        val draft = MealDraft("Old meal", "150,5", "Old, ingredients", "Note").withCatalogProduct(product)
        val validated = valid(draft, PresenterCatalogFixture(product))
        assertEquals(product.displayName, validated.name)
        assertEquals(emptyList(), validated.ingredients)
        assertEquals(150.5, validated.portionGrams)
        assertEquals("Note", validated.note)
        assertEquals(CatalogMealBinding(product.snapshot, product.canonicalItemId), validated.catalogBinding)
    }

    @Test
    fun `editor conversion preserves binding while explicit identity edits clear it permanently`() {
        val meal = presenterCatalogMeal()
        val draft = meal.toMealDraft()
        assertEquals(meal.catalogBinding, draft.catalogBinding)
        assertEquals("150", draft.portion)
        assertNull(draft.withEditedName("Other").catalogBinding)
        assertNull(draft.withEditedIngredients("Onion").catalogBinding)
        val revertedText = draft.withEditedName("Other").withEditedName(meal.name)
        assertNull(valid(revertedText, existing = meal).catalogBinding)
    }

    @Test
    fun `central validation invalidates historical identity edits without relying on editor helpers`() {
        val meal = presenterCatalogMeal()
        val original = meal.toMealDraft()
        assertNull(valid(original.copy(name = "Other"), existing = meal).catalogBinding)
        assertNull(valid(original.copy(name = " ${meal.name} "), existing = meal).catalogBinding)
        val ingredients = valid(original.copy(ingredients = "Onion, pepper"), existing = meal)
        assertEquals(listOf("Onion", "pepper"), ingredients.ingredients)
        assertNull(ingredients.catalogBinding)
        val optionalWeight = valid(original.copy(name = "Other", portion = ""), existing = meal)
        assertNull(optionalWeight.portionGrams)
        assertNull(optionalWeight.catalogBinding)
    }

    @Test
    fun `note and weight edits preserve a historical binding with no current source`() {
        val meal = presenterCatalogMeal()
        val validated = valid(meal.toMealDraft().copy(portion = "200", note = "  Less salt  "), existing = meal)
        assertEquals(meal.catalogBinding, validated.catalogBinding)
        assertEquals(200.0, validated.portionGrams)
        assertEquals("Less salt", validated.note)
    }

    @Test
    fun `reselecting a different explicit product is verified rather than discarded as a name edit`() {
        val meal = presenterCatalogMeal()
        val replacement = presenterCatalogProduct("Butter, salted", "food:usda-srl-01001")
        val draft = meal.toMealDraft().withCatalogProduct(replacement)
        val validated = valid(draft, PresenterCatalogFixture(replacement), meal)
        assertEquals(replacement.displayName, validated.name)
        assertEquals(draft.catalogBinding, validated.catalogBinding)
    }

    @Test
    fun `unbound drafts and unchanged structured comma labels retain their previous behavior`() {
        val existing = presenterCatalogMeal().copy(catalogBinding = null, ingredients = listOf("Rice, white"))
        val unchanged = valid(existing.toMealDraft().copy(note = "Note"), existing = existing)
        assertEquals(listOf("Rice, white"), unchanged.ingredients)
        assertNull(unchanged.catalogBinding)
        val plain = assertIs<MealDraftValidation.Valid>(MealDraftValidator.validate(MealDraft("Soup"))).value
        assertNull(plain.catalogBinding)
        assertNull(plain.portionGrams)
    }

    private fun valid(
        draft: MealDraft,
        catalog: PresenterCatalogFixture? = null,
        existing: SavedMeal? = null,
    ): ValidatedMealDraft =
        assertIs<MealDraftValidation.Valid>(MealDraftValidator.validate(draft, existing, catalog)).value
}
