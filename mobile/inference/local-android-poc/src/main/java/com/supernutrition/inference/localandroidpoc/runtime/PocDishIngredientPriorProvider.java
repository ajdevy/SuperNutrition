package com.supernutrition.inference.localandroidpoc.runtime;

import java.util.List;

/** Supplies bounded dish-composition hints without coupling the engine to a host data source. */
@FunctionalInterface
public interface PocDishIngredientPriorProvider {
    /** Returns the canonical hints for a dish label, or an empty list when none are available. */
    List<String> forDish(String dishLabel);
}
