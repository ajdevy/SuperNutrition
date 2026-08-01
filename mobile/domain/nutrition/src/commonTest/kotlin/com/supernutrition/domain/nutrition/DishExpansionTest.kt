package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DishExpansionTest {
    private val dishes = DishExpansion(SeedDishes.records)
    private val preference = DishLocalePreference(SeedDishes.marketAttribution)

    @Test
    fun `a dish name resolves to what it contains`() {
        val proposal = dishes.expand("chicken and rice").first()

        assertEquals("dish:chicken-and-rice", proposal.canonicalDishId)
        assertEquals(
            listOf("food:chicken-breast-raw", "food:rice-white-cooked"),
            proposal.ingredients.map { it.canonicalItemId },
        )
    }

    @Test
    fun `no seed ingredient carries a weight`() {
        // DC-3 and DFM-2. A gram figure with no source is fabricated nutrition
        // with a valid provenance chain, and five of them read as a researched
        // recipe. This test exists to fail the moment someone fills them in.
        val everyIngredient = SeedDishes.records.flatMap { it.ingredients }

        assertTrue(everyIngredient.isNotEmpty(), "the seed set must actually contain ingredients")
        assertEquals(
            emptyList(),
            everyIngredient.filter { it.grams != null },
            "weights arrive with an approved package or not at all",
        )
    }

    @Test
    fun `an ambiguous dish name returns every candidate rather than picking one`() {
        // DFM-1 is a near-miss failure: one wrong dish proposes several
        // individually plausible foods. The caller cannot offer a choice it was
        // never given, so expand returns every match at the winning tier.
        //
        // "bean" rather than "bean bowl" deliberately. "bean bowl" is an exact
        // alternate name of one dish, and an exact match is supposed to win
        // outright — first-tier-wins is the rule that stops a weaker tier
        // diluting a confident match. Ambiguity is what this test needs.
        val proposals = dishes.expand("bean")

        assertTrue(proposals.size >= 2, "expected both bowl dishes, got ${proposals.map { it.canonicalDishId }}")
        assertTrue(proposals.any { it.canonicalDishId == "dish:black-bean-bowl" })
        assertTrue(proposals.any { it.canonicalDishId == "dish:bean-and-egg-bowl" })
    }

    @Test
    fun `the dish name travels with its ingredients`() {
        // The only cheap way for a user to catch a wrong dish match is to see
        // which dish the ingredients came from before confirming them.
        val proposal = dishes.expand("egg on toast").first()

        assertEquals("Egg on rye toast", proposal.dishDisplayName)
    }

    @Test
    fun `a dish inherits every improvement made to food matching`() {
        // The point of building on CatalogSearch instead of beside it: quantity
        // stripping and plural folding were written for foods and apply here
        // without a line of dish-specific code.
        assertEquals("dish:black-bean-bowl", dishes.expand("2 black bean bowls").first().canonicalDishId)
        assertEquals("dish:chicken-and-rice", dishes.expand("курица с рисом").first().canonicalDishId)
    }

    @Test
    fun `locale reorders dishes and removes none of them`() {
        // DFM-3. A dish outside the active market moves down and stays
        // reachable: someone travelling eats what is in front of them.
        val proposals = dishes.expand("bean")

        val us = preference.prefer(proposals, DishLocale(market = "US"))
        val ru = preference.prefer(proposals, DishLocale(market = "RU"))

        assertEquals("dish:black-bean-bowl", us.first().canonicalDishId)
        assertEquals("dish:bean-and-egg-bowl", ru.first().canonicalDishId)
        assertEquals(proposals.toSet(), us.toSet(), "ranking must not drop a dish")
        assertEquals(proposals.toSet(), ru.toSet(), "ranking must not drop a dish")
    }

    @Test
    fun `an unset market is not a signal`() {
        // An unset default must stay unset rather than quietly becoming one.
        val proposals = dishes.expand("bean")

        assertEquals(proposals, preference.prefer(proposals, DishLocale.Unspecified))
        assertEquals(proposals, preference.prefer(proposals, DishLocale(inputLanguage = "ru")))
    }

    @Test
    fun `an unattributed dish ranks between local and foreign`() {
        // Missing attribution is missing data, not evidence that a dish is
        // foreign, so it must not be pushed to the bottom.
        val proposals = listOf(
            DishProposal("dish:bean-and-egg-bowl", "Bean and egg bowl", emptyList()),
            DishProposal("dish:egg-on-rye", "Egg on rye toast", emptyList()),
            DishProposal("dish:black-bean-bowl", "Black bean bowl", emptyList()),
        )

        val ranked = preference.prefer(proposals, DishLocale(market = "US")).map { it.canonicalDishId }

        assertEquals(
            listOf("dish:black-bean-bowl", "dish:egg-on-rye", "dish:bean-and-egg-bowl"),
            ranked,
        )
    }

    @Test
    fun `all three target languages reach the same dish`() {
        // English, Russian, and Spanish are the declared targets. One dish, one
        // canonical id, three ways in — which is the whole point of carrying
        // alternate names rather than one wording per market.
        for (query in listOf("chicken rice", "курица с рисом", "pollo con arroz")) {
            assertEquals(
                "dish:chicken-and-rice",
                dishes.expand(query).first().canonicalDishId,
                "$query should reach the dish",
            )
        }
    }

    @Test
    fun `an exact dish name wins outright instead of listing near misses`() {
        // The other side of the ambiguity rule, asserted so that widening the
        // candidate list later cannot quietly weaken it. "bean bowl" names one
        // dish exactly, and a confident match is not a menu.
        assertEquals(
            listOf("dish:black-bean-bowl"),
            dishes.expand("bean bowl").map { it.canonicalDishId },
        )
    }
}
