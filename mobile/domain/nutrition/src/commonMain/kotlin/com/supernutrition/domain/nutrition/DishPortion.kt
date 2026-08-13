package com.supernutrition.domain.nutrition

/**
 * How much of a dish the user had.
 *
 * The default is one serving of the whole dish and no per-ingredient decisions,
 * because that is the answer almost every time and it should cost nothing to
 * give. [ingredientScales] exists for the case the default gets wrong — a
 * biryani with extra chicken — and is reached through a deliberate "adjust
 * ingredients" action rather than being asked for up front.
 *
 * This is a multiplier, not a weight. The catalog carries no grams until an
 * approved package supplies them, so a portion here scales whatever that
 * package eventually provides. Storing a gram figure now would be inventing the
 * number the whole grounding chain exists to prevent, and doing it in a place
 * that looks like arithmetic rather than a guess.
 */
data class DishPortion(val dishServings: Double = 1.0, val ingredientScales: Map<String, Double> = emptyMap()) {
    init {
        require(dishServings > 0.0) { "A dish portion must be a positive number of servings" }
        require(ingredientScales.values.all { it > 0.0 }) {
            "An ingredient scale must be positive; remove the ingredient instead of scaling it to zero"
        }
    }

    /**
     * The multiplier for one ingredient: its own override when the user set one,
     * and the dish serving otherwise.
     *
     * An override replaces the dish serving rather than compounding with it. A
     * user who says "double the chicken" means double, not double-of-whatever-
     * the-dish-scale-happens-to-be, and compounding would make the same words
     * mean different amounts depending on an unrelated earlier choice.
     */
    fun scaleFor(canonicalItemId: String): Double = ingredientScales[canonicalItemId] ?: dishServings

    /** True when nothing was adjusted, which is the state the surface should present by default. */
    val isDefault: Boolean get() = dishServings == 1.0 && ingredientScales.isEmpty()

    fun adjust(canonicalItemId: String, scale: Double): DishPortion =
        copy(ingredientScales = ingredientScales + (canonicalItemId to scale))

    /** Drops one override, returning that ingredient to the dish serving. */
    fun clearAdjustment(canonicalItemId: String): DishPortion =
        copy(ingredientScales = ingredientScales - canonicalItemId)
}
