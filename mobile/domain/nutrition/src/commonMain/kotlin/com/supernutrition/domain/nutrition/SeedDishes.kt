package com.supernutrition.domain.nutrition

/**
 * A small fake dish set standing in for a licensed composition package.
 *
 * The same standing as [SeedCatalog], for the same reason: G-2 blocks grounding
 * beyond a fake catalog, not development against one, so this exists to give the
 * expansion mechanism something real to resolve before an approved package lands.
 *
 * Every ingredient here carries identity and no weight. That is not an omission
 * to be filled in later by whoever touches this file next — a gram figure with
 * no source is exactly the fabricated number the grounding chain exists to
 * prevent, and it would be more dangerous here than a fake calorie count,
 * because five plausible weights read as a researched recipe. Weights arrive
 * with the licensed package or not at all (DC-3, DFM-2).
 *
 * The dishes are chosen to exercise the mechanism rather than to be a menu:
 * one that collides with a food name, one pair that are near-misses of each
 * other, and entries whose names cross scripts.
 */
object SeedDishes {
    val records: List<DishRecord> = listOf(
        DishRecord(
            canonicalDishId = "dish:chicken-and-rice",
            displayName = "Chicken and rice",
            alternateNames = listOf("chicken rice", "курица с рисом", "pollo con arroz"),
            ingredients = listOf(
                DishIngredient("food:chicken-breast-raw"),
                DishIngredient("food:rice-white-cooked"),
            ),
        ),
        DishRecord(
            canonicalDishId = "dish:egg-on-rye",
            displayName = "Egg on rye toast",
            alternateNames = listOf("egg on toast", "яйцо на ржаном хлебе", "huevo con pan"),
            ingredients = listOf(
                DishIngredient("food:egg-boiled"),
                DishIngredient("food:bread-rye"),
            ),
        ),
        DishRecord(
            canonicalDishId = "dish:black-bean-bowl",
            displayName = "Black bean bowl",
            alternateNames = listOf("bean bowl", "фасолевая миска", "tazon de frijoles"),
            ingredients = listOf(
                DishIngredient("food:bean-black"),
                DishIngredient("food:rice-white-cooked"),
                DishIngredient("food:avocado-raw"),
                DishIngredient("food:coriander-leaf"),
            ),
        ),
        // Deliberately close to "black bean bowl" above. DFM-1 is a near-miss
        // failure, and a seed set with no near misses in it cannot exercise the
        // control that exists to catch them.
        DishRecord(
            canonicalDishId = "dish:bean-and-egg-bowl",
            displayName = "Bean and egg bowl",
            alternateNames = listOf("egg bean bowl"),
            ingredients = listOf(
                DishIngredient("food:bean-black"),
                DishIngredient("food:egg-boiled"),
            ),
        ),
    )

    /**
     * Which markets carry which dish. Data rather than conditionals, so a market
     * list can change without search code changing.
     *
     * Attribution is deliberately partial: `dish:egg-on-rye` is left
     * unattributed so the neutral-ranking case is exercised rather than assumed.
     */
    val marketAttribution: List<DishMarkets> = listOf(
        DishMarkets("dish:chicken-and-rice", setOf("US", "RU")),
        DishMarkets("dish:black-bean-bowl", setOf("US")),
        DishMarkets("dish:bean-and-egg-bowl", setOf("RU")),
    )
}
