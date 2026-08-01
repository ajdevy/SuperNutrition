package com.supernutrition.domain.nutrition

/**
 * One ingredient a dish typically contains.
 *
 * [grams] is nullable and stays null unless an approved package supplies a
 * figure. An invented weight is fabricated nutrition wearing a provenance chain —
 * the number would be signed, versioned, and arithmetically sound, and still
 * describe a meal nobody ate. Unknown is a legitimate answer here; a plausible
 * average is not (DC-3, DFM-2).
 */
data class DishIngredient(val canonicalItemId: String, val grams: Int? = null)

/**
 * A dish: a name that means a composition rather than a record.
 *
 * [alternateNames] carries the same load it does for foods — regional names,
 * transliterations, and cross-script spellings all reaching one dish.
 */
data class DishRecord(
    val canonicalDishId: String,
    val displayName: String,
    val alternateNames: List<String> = emptyList(),
    val ingredients: List<DishIngredient> = emptyList(),
)

/**
 * What a dish name resolved to.
 *
 * A proposal, never a conclusion. [dishDisplayName] travels with it because
 * DFM-1 turns on the user being able to see which dish was matched before
 * reviewing what it contains: reading "biryani" as "birria" proposes five
 * individually plausible foods, and the only cheap way to catch that is to show
 * the name the ingredients came from.
 */
data class DishProposal(
    val canonicalDishId: String,
    val dishDisplayName: String,
    val ingredients: List<DishIngredient>,
)

/**
 * Resolves a dish name to the ingredients it typically contains.
 *
 * Deliberately built on [CatalogSearch] rather than beside it. Dish matching is
 * the same problem as food matching over a different record set — normalization,
 * synonyms, tiers, deterministic ranking — and a second matcher would be a
 * second source of truth about what a name means. This repository has paid for
 * that twice already: the engine and floor each segmenting descriptions, and a
 * quantity vocabulary kept in two lists. Both drifted, and both were fixed by
 * deleting the duplicate rather than synchronising it.
 *
 * One consequence worth stating: every improvement to catalog matching —
 * quantity handling, plural folding, preparation reporting — applies to dish
 * names for free, and cannot apply to one and not the other.
 */
class DishExpansion(dishes: List<DishRecord>) {
    private val byId = dishes.associateBy { it.canonicalDishId }
    private val search = CatalogSearch(
        dishes.map { CatalogRecord(it.canonicalDishId, it.displayName, it.alternateNames) },
    )

    /**
     * Dishes matching [query], best first, each with what it contains.
     *
     * Returns every match rather than only the best one, because DFM-1 is a
     * near-miss failure: when "biryani" and "birria" both match, the caller
     * needs both to offer a choice instead of silently taking the winner.
     */
    fun expand(query: String): List<DishProposal> = search.search(query).map { hit ->
        val dish = byId.getValue(hit.canonicalItemId)
        DishProposal(dish.canonicalDishId, dish.displayName, dish.ingredients)
    }
}
