package com.supernutrition.domain.nutrition

/**
 * One thing the user can pick, from either catalog.
 *
 * Foods and dishes appear in one list because that is one search box and no
 * rule to learn. They are separate types because tapping them does different
 * things — a food adds one item, a dish adds several — and a surface that
 * cannot tell them apart cannot say so before the tap.
 */
sealed interface MealSearchHit {
    val strength: MatchStrength

    data class Food(
        val canonicalItemId: String,
        val displayName: String,
        val unmatchedPreparation: List<String>,
        override val strength: MatchStrength,
    ) : MealSearchHit

    /**
     * [ingredients] is carried rather than counted so the caller can show what a
     * tap will add. `Chicken curry · 4 ingredients` is the difference between a
     * user choosing a composition and discovering one.
     */
    data class Dish(
        val canonicalDishId: String,
        val displayName: String,
        val ingredients: List<DishIngredient>,
        override val strength: MatchStrength,
    ) : MealSearchHit
}

/**
 * One search across foods and dishes.
 *
 * Ranked by match strength rather than by kind. A fixed precedence — dishes
 * always first, or foods always first — gets one of the two common cases wrong:
 * "chicken" means the food and "chicken curry" means the dish, and only the
 * strength of the match distinguishes them. That also means no new ranking
 * rule: both catalogs already rank the same way, and the merge just compares
 * what they report.
 *
 * Locale reorders dishes before the merge, so a market preference can move a
 * dish up the combined list without ever removing it.
 */
class MealSearch(
    private val foods: CatalogSearch,
    private val dishes: DishExpansion,
    private val localePreference: DishLocalePreference,
) {
    fun search(query: String, locale: DishLocale = DishLocale.Unspecified): List<MealSearchHit> {
        val foodHits = foods.search(query).map {
            MealSearchHit.Food(it.canonicalItemId, it.displayName, it.unmatchedPreparation, it.strength)
        }
        val dishHits = localePreference.prefer(dishes.expand(query), locale).map {
            MealSearchHit.Dish(it.canonicalDishId, it.dishDisplayName, it.ingredients, it.strength)
        }

        // Sorted by tier alone, and the sort is stable, so hits matching at the
        // same tier keep the order they arrived in: foods in catalog ranking,
        // dishes in locale ranking.
        //
        // Tier rather than full strength on purpose. A stronger tier must win
        // regardless of market — a market that could outrank an exact match is
        // the silent bias DFM-3 forbids. Within one tier the coverage
        // difference is often an artifact of how many words a record's name
        // happens to have, which is a weak enough signal that a market
        // preference may order it.
        return (foodHits + dishHits).sortedBy { it.strength.tier.ordinal }
    }
}
