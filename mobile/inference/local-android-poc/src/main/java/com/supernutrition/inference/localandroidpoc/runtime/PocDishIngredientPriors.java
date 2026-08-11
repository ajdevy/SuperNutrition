package com.supernutrition.inference.localandroidpoc.runtime;

import com.supernutrition.domain.photoanalysis.DishIngredientPriorTable;

import java.util.List;

/** Compatibility bridge; the prior table has one canonical KMP owner. */
final class PocDishIngredientPriors {
    private PocDishIngredientPriors() {
    }

    static List<String> forDish(String dishLabel) {
        return DishIngredientPriorTable.forDish(dishLabel);
    }
}
