package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.VisibleComponent

/** A dish-prior hypothesis; it is never visual evidence by itself. */
data class DishIngredientHint(
    val ingredientId: String,
    val label: String,
    val support: Double,
    val sourceDishCandidateIds: List<String>,
) {
    init {
        require(ingredientId.startsWith("ingredient:")) {
            "Dish ingredient hint IDs must use the ingredient namespace"
        }
        require(label.isNotBlank()) { "Dish ingredient hint labels must not be blank" }
        require(support.isFinite() && support in 0.0..1.0) {
            "Dish ingredient hint support must be finite and bounded"
        }
        require(sourceDishCandidateIds.isNotEmpty()) {
            "Dish ingredient hints must retain their contributing dish candidates"
        }
        require(sourceDishCandidateIds.distinct().size == sourceDishCandidateIds.size) {
            "Dish ingredient hint source dish IDs must be unique"
        }
    }
}

/**
 * Additive, fail-safe fusion for the photo review boundary.
 *
 * Dish candidates can add composition hypotheses, but they never filter,
 * relabel, or manufacture the `VisibleComponent` list. The table is a small
 * versioned product prior; it is not a nutrition catalog or a detector.
 */
object BoundedDishIngredientFusion {
    const val POLICY_VERSION = "e0-suggestion-policy.v1"

    private const val MAX_HINTS = 24

    fun merge(dishes: List<InferenceCandidate>, photoEvidence: List<VisibleComponent>): CandidateReviewDraft =
        CandidateReviewDraft(
            mainDishProposals = dishes,
            photoEvidence = photoEvidence,
            compositionHints = hintsFor(dishes),
        )

    private fun hintsFor(dishes: List<InferenceCandidate>): List<DishIngredientHint> {
        val accumulated = linkedMapOf<String, MutableHint>()
        dishes.take(3).forEach { dish ->
            DishIngredientPriorTable.forDish(dish)
                .forEach { ingredientId ->
                    val current = accumulated.getOrPut(ingredientId) { MutableHint(ingredientId) }
                    current.support += dish.confidence.score
                    current.sourceDishCandidateIds += dish.candidateId
                }
        }
        val maximum = accumulated.values.maxOfOrNull(MutableHint::support) ?: return emptyList()
        return accumulated.values
            .asSequence()
            .take(MAX_HINTS)
            .sortedByDescending(MutableHint::support)
            .map { hint ->
                DishIngredientHint(
                    ingredientId = hint.ingredientId,
                    label = displayLabel(hint.ingredientId),
                    support = if (maximum == 0.0) 0.0 else hint.support / maximum,
                    sourceDishCandidateIds = hint.sourceDishCandidateIds.toList(),
                )
            }
            .toList()
    }

    private class MutableHint(val ingredientId: String) {
        var support: Double = 0.0
        val sourceDishCandidateIds: MutableList<String> = mutableListOf()
    }

    private fun displayLabel(ingredientId: String): String = ingredientId
        .removePrefix("ingredient:")
        .split('_')
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
}

private object DishIngredientPriorTable {
    private val priors: Map<String, List<String>> = linkedMapOf(
        "greek_salad" to listOf(
            "ingredient:cucumber",
            "ingredient:tomato",
            "ingredient:red_onion",
            "ingredient:bell_pepper",
            "ingredient:feta_cheese",
            "ingredient:olives",
            "ingredient:olive_oil",
        ),
        "caesar_salad" to listOf(
            "ingredient:romaine_lettuce",
            "ingredient:chicken",
            "ingredient:parmesan",
            "ingredient:croutons",
            "ingredient:caesar_dressing",
        ),
        "garden_salad" to listOf(
            "ingredient:lettuce",
            "ingredient:tomato",
            "ingredient:cucumber",
            "ingredient:carrot",
            "ingredient:onion",
            "ingredient:bell_pepper",
        ),
        "chopped_salad" to listOf(
            "ingredient:lettuce",
            "ingredient:tomato",
            "ingredient:cucumber",
            "ingredient:carrot",
            "ingredient:corn",
            "ingredient:onion",
        ),
        "hot_and_sour_soup" to listOf(
            "ingredient:tofu",
            "ingredient:mushroom",
            "ingredient:bamboo_shoots",
            "ingredient:pork",
            "ingredient:egg",
            "ingredient:vinegar",
        ),
        "miso_soup" to listOf(
            "ingredient:miso",
            "ingredient:tofu",
            "ingredient:seaweed",
            "ingredient:green_onion",
            "ingredient:mushroom",
        ),
        "ramen" to listOf(
            "ingredient:noodles",
            "ingredient:pork",
            "ingredient:egg",
            "ingredient:seaweed",
            "ingredient:green_onion",
            "ingredient:broth",
        ),
        "pizza" to listOf(
            "ingredient:dough",
            "ingredient:tomato_sauce",
            "ingredient:mozzarella",
            "ingredient:cheese",
            "ingredient:pepperoni",
            "ingredient:mushroom",
            "ingredient:onion",
        ),
        "pork_chop" to listOf(
            "ingredient:pork_chop",
            "ingredient:pork",
            "ingredient:carrot",
            "ingredient:potato",
            "ingredient:green_beans",
            "ingredient:apple_sauce",
        ),
        "steak" to listOf(
            "ingredient:beef",
            "ingredient:potato",
            "ingredient:green_beans",
            "ingredient:salad",
            "ingredient:sauce",
        ),
        "grilled_salmon" to listOf(
            "ingredient:salmon",
            "ingredient:lemon",
            "ingredient:rice",
            "ingredient:broccoli",
            "ingredient:asparagus",
        ),
        "sushi" to listOf(
            "ingredient:rice",
            "ingredient:salmon",
            "ingredient:tuna",
            "ingredient:avocado",
            "ingredient:cucumber",
            "ingredient:seaweed",
        ),
        "fried_rice" to listOf(
            "ingredient:rice",
            "ingredient:egg",
            "ingredient:peas",
            "ingredient:carrot",
            "ingredient:green_onion",
        ),
        "bibimbap" to listOf(
            "ingredient:rice",
            "ingredient:beef",
            "ingredient:egg",
            "ingredient:carrot",
            "ingredient:spinach",
            "ingredient:mushroom",
        ),
        "chicken_curry" to listOf(
            "ingredient:chicken",
            "ingredient:rice",
            "ingredient:onion",
            "ingredient:tomato",
            "ingredient:coconut_milk",
        ),
        "hamburger" to listOf(
            "ingredient:beef",
            "ingredient:bun",
            "ingredient:lettuce",
            "ingredient:tomato",
            "ingredient:onion",
            "ingredient:pickle",
            "ingredient:cheese",
        ),
        "lasagna" to listOf(
            "ingredient:pasta",
            "ingredient:tomato_sauce",
            "ingredient:beef",
            "ingredient:cheese",
            "ingredient:ricotta",
        ),
        "spaghetti_bolognese" to listOf(
            "ingredient:pasta",
            "ingredient:beef",
            "ingredient:tomato_sauce",
            "ingredient:parmesan",
        ),
        "waffles" to listOf(
            "ingredient:flour",
            "ingredient:egg",
            "ingredient:milk",
            "ingredient:butter",
            "ingredient:syrup",
            "ingredient:berries",
        ),
    )

    fun forDish(candidate: InferenceCandidate): List<String> {
        val keys = listOf(candidate.candidateId, candidate.label).map(::normalize).distinct()
        return keys.firstNotNullOfOrNull(priors::get).orEmpty()
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(' ', '_')
}
