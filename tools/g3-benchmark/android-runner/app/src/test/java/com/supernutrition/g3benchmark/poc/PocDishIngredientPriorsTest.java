package com.supernutrition.g3benchmark.poc;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PocDishIngredientPriorsTest {
    @Test
    public void greekSaladPriorDoesNotIncludeBroccoli() {
        List<String> ingredients = PocDishIngredientPriors.forDish("greek_salad");

        assertTrue(ingredients.contains("cucumber"));
        assertTrue(ingredients.contains("tomato"));
        assertTrue(!ingredients.contains("broccoli"));
    }

    @Test
    public void porkChopPriorKeepsMainProteinAndRejectsUnrelatedFruit() {
        List<String> ingredients = PocDishIngredientPriors.forDish("pork_chop");

        assertTrue(ingredients.contains("pork_chop"));
        assertTrue(ingredients.contains("pork"));
        assertTrue(ingredients.contains("carrot"));
        assertTrue(!ingredients.contains("orange"));
        assertTrue(!ingredients.contains("broccoli"));
    }

    @Test
    public void unknownDishHasNoDishPrior() {
        assertEquals(0, PocDishIngredientPriors.forDish("unknown_dish").size());
        assertEquals(0, PocDishIngredientPriors.forDish(null).size());
    }
}
