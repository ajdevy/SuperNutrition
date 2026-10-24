package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.RewardId

data class RewardDetailCopy(
    val category: String,
    val progressTitle: String,
    val rules: List<String>,
    val groups: List<Pair<String, String>>,
)

/** Nutrient rules require confirmed food facts; rhythm credits explicit recording dates. */
internal fun rewardDetailCopy(id: RewardId): RewardDetailCopy = when (id) {
    RewardId.PROTEIN_ALL_DAY -> proteinRewardDetailCopy()
    RewardId.FATS_PRESENT -> fatsRewardDetailCopy()
    RewardId.FIBER_PRESENT -> fiberRewardDetailCopy()
    RewardId.COLORS_OF_DAY -> colorsRewardDetailCopy()
    RewardId.THIRTY_PRODUCTS -> diversityRewardDetailCopy()
    RewardId.FERMENTED_THREE_DAYS -> fermentedRewardDetailCopy()
    RewardId.SEVEN_DAYS_RECORDED -> rhythmRewardDetailCopy()
}

private fun proteinRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "PROTEIN · DAILY",
    "Meals with protein today",
    listOf(
        "Every meal you log today includes a confirmed protein source.",
        "At least 2 meals a day, otherwise the reward isn't given.",
        "Animal or plant protein, both count. Daily progress resets each day.",
    ),
    proteinExampleGroups(),
)

private fun fatsRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "HEALTHY FATS · DAILY",
    "Meals with healthy fats today",
    listOf(
        "At least one meal today includes a confirmed food from one of the fat groups.",
        "Daily progress resets each day.",
    ),
    listOf(
        "Oily fish & seafood" to "Mackerel, herring, sardines, salmon, trout, tuna and fish roe.",
        "Nuts & seeds" to "Walnuts, almonds, peanuts, pumpkin, sunflower, sesame, chia and flax seeds.",
        "Oils" to "Olive, sunflower, flaxseed, sesame and pumpkin oil.",
        "Avocado & olives" to "The food's confirmed classification determines whether it counts.",
    ),
)

private fun fiberRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "FIBER · DAILY",
    "Meals with fiber today",
    listOf("Food from a confirmed fiber group in 2 meals today.", "Daily progress resets each day."),
    listOf(
        "Vegetables & roots" to "Broccoli, carrots, cabbage, beets, tomatoes, peppers and potatoes with skin.",
        "Fruit & berries" to "Apples, pears, plums, oranges, kiwi, blueberries, raspberries and strawberries.",
        "Whole grains & bread" to "Oats, buckwheat, pearl barley, brown rice, quinoa and whole-grain bread.",
        "Legumes" to "Lentils, beans, chickpeas, peas, soybeans, hummus and edamame.",
        "Nuts & seeds" to "Almonds, walnuts, peanuts, pumpkin, sunflower, sesame, chia and flax seeds.",
        "Leafy greens & herbs" to "Spinach, arugula, lettuce, parsley, dill and basil.",
    ),
)

private fun colorsRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "COLORS · DAILY",
    "Colors on your plate today",
    listOf(
        "Foods of 3 different colors out of 4 today.",
        "Frozen counts the same as fresh. Daily progress resets each day.",
        "White foods still count toward other rewards when their classification supports it.",
    ),
    listOf(
        "Green" to "Leafy greens, broccoli, peas, cucumber, avocado, kiwi and green apple.",
        "Red" to "Tomatoes, red peppers, beets, strawberries, cherries and red apple.",
        "Orange & yellow" to "Carrots, pumpkin, sweet potato, corn, yellow peppers, oranges and mango.",
        "Blue & purple" to "Blueberries, blackberries, dark grapes, plums, eggplant and red cabbage.",
    ),
)

private fun diversityRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "DIVERSITY · WEEKLY",
    "Different foods · last 7 days",
    listOf(
        "30 different foods confirmed over the last 7 days.",
        "Different catalog foods count once each, even when you record them in several meals.",
        "Herbs and spices count as no more than 5 foods, so the reward can't be earned on seasonings alone.",
    ),
    listOf(
        "All food groups" to "Vegetables, fruit, berries, grains, legumes, nuts, seeds, " +
            "greens, meat, fish, eggs, dairy and oils.",
    ),
)

private fun fermentedRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "MICROBIOTA · WEEKLY",
    "Days with fermented food · last 7 days",
    listOf(
        "Confirmed fermented food on 3 different days within the last 7 days.",
        "Several meals on one day still count as one day.",
    ),
    listOf(
        "Cultured dairy" to "Kefir, plain yogurt, Greek yogurt, skyr and live cultured drinks.",
        "Naturally fermented vegetables" to "Sauerkraut, kimchi and naturally fermented vegetables.",
        "Soy" to "Miso, tempeh, natto and naturally brewed soy sauce.",
        "Drinks" to "Kombucha and naturally fermented kvass.",
        "Bread" to "Sourdough bread.",
    ),
)

private fun rhythmRewardDetailCopy(): RewardDetailCopy = RewardDetailCopy(
    "RHYTHM · WEEKLY",
    "Days in a row with entries",
    listOf(
        "An entry on each of 7 calendar days in a row.",
        "Continuity matters, not the number of entries.",
        "You can continue yesterday's run by adding an entry today.",
        "Skipped a day? The count starts again from your next entry.",
    ),
    listOf(
        "Recorded meals" to "Saved diary entries and confirmed food records both count. " +
            "Nutrition data is not required for this reward.",
    ),
)

private fun proteinExampleGroups(): List<Pair<String, String>> = listOf(
    "Meat & poultry" to "Beef, pork, lamb, chicken, turkey, duck and rabbit.",
    "Fish & seafood" to "Salmon, cod, mackerel, trout, tuna, shrimp, squid and mussels.",
    "Eggs & dairy" to "Eggs, cottage cheese, cheese, plain yogurt, kefir and milk.",
    "Legumes & soy" to "Lentils, beans, chickpeas, peas, tofu, tempeh and edamame.",
    "Nuts & seeds" to "Almonds, walnuts, peanuts, pumpkin, sunflower, hemp and chia seeds.",
    "High-protein grains" to "Quinoa, amaranth, buckwheat, oats, spelt and bulgur.",
)
