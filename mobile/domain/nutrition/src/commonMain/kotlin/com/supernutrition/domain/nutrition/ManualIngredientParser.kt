package com.supernutrition.domain.nutrition

/** One catalog ingredient added from the user's typed meal description. */
data class ManualIngredient(val canonicalItemId: String, val displayName: String, val sourceDishName: String? = null)

/** A bounded parse result. Unresolved text remains visible to the caller. */
data class ManualIngredientParseResult(val ingredients: List<ManualIngredient>, val unresolvedFragments: List<String>)

/**
 * Converts one user-entered description into catalog ingredients.
 *
 * A whole query is tried first so a dish such as "chicken and rice" is expanded
 * before the list segmenter treats "and" as an ingredient separator. If the
 * whole query is not a dish, the same shared segmenter resolves comma-,
 * conjunction-, and line-separated ingredients one by one.
 */
class ManualIngredientParser(private val mealSearch: MealSearch, private val displayNameFor: (String) -> String?) {
    fun parse(description: String): ManualIngredientParseResult {
        val normalized = description.trim()
        if (normalized.isEmpty()) return ManualIngredientParseResult(emptyList(), emptyList())

        val wholeQueryHit = mealSearch.search(normalized).firstOrNull()
        if (wholeQueryHit is MealSearchHit.Dish) {
            return expandDish(wholeQueryHit)
        }

        val fragments = TextFragments.of(normalized)
        if (fragments.size == 1 && wholeQueryHit is MealSearchHit.Food) {
            return ManualIngredientParseResult(
                ingredients = listOf(wholeQueryHit.toIngredient()),
                unresolvedFragments = emptyList(),
            )
        }

        val ingredients = mutableListOf<ManualIngredient>()
        val unresolved = mutableListOf<String>()
        fragments.forEach { fragment ->
            when (val hit = mealSearch.search(fragment.text).firstOrNull()) {
                is MealSearchHit.Food -> ingredients += hit.toIngredient()
                is MealSearchHit.Dish -> ingredients += expandDish(hit).ingredients
                null -> unresolved += fragment.text
            }
        }
        return ManualIngredientParseResult(
            ingredients = ingredients.distinctBy(ManualIngredient::canonicalItemId),
            unresolvedFragments = unresolved.distinct(),
        )
    }

    private fun expandDish(hit: MealSearchHit.Dish): ManualIngredientParseResult {
        val ingredients = hit.ingredients.mapNotNull { ingredient ->
            displayNameFor(ingredient.canonicalItemId)?.let { displayName ->
                ManualIngredient(
                    canonicalItemId = ingredient.canonicalItemId,
                    displayName = displayName,
                    sourceDishName = hit.displayName,
                )
            }
        }
        return ManualIngredientParseResult(ingredients.distinctBy(ManualIngredient::canonicalItemId), emptyList())
    }

    private fun MealSearchHit.Food.toIngredient(): ManualIngredient = ManualIngredient(
        canonicalItemId = canonicalItemId,
        displayName = displayName,
    )
}

/** Production identity-only catalogs used by the manual meal input path. */
fun productionManualIngredientParser(): ManualIngredientParser {
    val catalog = CatalogSearch(generatedCatalog)
    return ManualIngredientParser(
        mealSearch = MealSearch(
            foods = catalog,
            dishes = DishExpansion(generatedDishes),
            localePreference = DishLocalePreference(emptyList()),
        ),
        displayNameFor = catalog::displayNameFor,
    )
}
