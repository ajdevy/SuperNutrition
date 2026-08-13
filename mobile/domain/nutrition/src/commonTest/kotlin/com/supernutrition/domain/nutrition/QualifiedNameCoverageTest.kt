package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Typing "chicken" — spelled perfectly, no typo at all — answered Egg.
 *
 * `coverageOf` scores a fraction: tokens the query accounted for, divided by
 * the total tokens in whichever searchable form matched. Chicken's own name
 * is "Chicken, broilers or fryers, meat and skin and giblets and neck, raw"
 * — twelve tokens — and no shorter form of it existed anywhere in the
 * catalog, so the query's one token scored a low fraction even on the
 * record's own name. Egg's alternate name "chicken egg" is two tokens, and
 * scored a HIGH fraction for the same reason. The bug was found first as a
 * fuzzy-tier typo defect (`chiken`, `yogert`, `sammon`, `cafe`), then
 * discovered to run one tier stronger, at TOKEN_SUBSET, on words spelled
 * exactly right — a materially more common and more important case.
 *
 * Every one of the four foods below shares the identical shape: no bare,
 * single-word form of the food's own name existed, because AGROVOC's source
 * data either had the word and a generator filter silently dropped it for
 * looking redundant (`chicken`, already in AGROVOC source but discarded as
 * "restating the head"), didn't have the word at all (`yogurt`, AGROVOC's
 * entire English entry was the British spelling with no match list), or
 * needed a word that AGROVOC files under one head shared by dozens of
 * unrelated foods with no existing mechanism to reach only these records
 * (`salmon` under the shared head "Fish"; `cafe`/`café` under "Beverages").
 *
 * Fixed as data, not as a ranking change, and this is the deliberate
 * choice, not the easy one. This session designed and measured four
 * candidate ranking-code fixes (a coverage floor, a display-name-priority
 * tiebreak, a formula reweight, and others) before rejecting all of them: a
 * ranking fix scoped narrowly enough to be safe fixed at most 3 of these 4
 * cases and never reached the plain-spelling defect below, while the
 * broadest ranking fix considered was measured to introduce a repeated,
 * systematic new failure — common staples losing to a processed variant of
 * themselves (`shrimp` to `Ostrich`, `walnut` to `Cheese`, `platano` to
 * `Plantains` instead of `Bananas`) on realistic misspellings, a materially
 * worse trade than the defect it was meant to fix. A one-word addition to
 * the record that already deserved it, reviewed and dated the same way
 * `MANUAL_TERMS` in scripts/generate-food-synonyms.py already is, cannot
 * make an unrelated record win, because every tier requires the query's
 * tokens to be accounted for by ONE form, and a one-token form contains none
 * of the tokens ("egg", "tofu", "oil", "cane") that used to win the tie.
 */
class QualifiedNameCoverageTest {
    private val search = CatalogSearch(generatedCatalog)

    private fun top(query: String): String? = search.search(query).firstOrNull()?.displayName

    @Test
    fun `chicken, yogurt, salmon and cafe now answer themselves, not an unrelated food`() {
        assertTrue(top("chicken")?.startsWith("Chicken") == true, "\"chicken\" answered ${top("chicken")}")
        assertTrue(top("yogurt")?.startsWith("Yogurt") == true, "\"yogurt\" answered ${top("yogurt")}")
        assertTrue(top("salmon")?.startsWith("Fish, salmon") == true, "\"salmon\" answered ${top("salmon")}")
        assertTrue(top("cafe")?.startsWith("Beverages, coffee") == true, "\"cafe\" answered ${top("cafe")}")
        assertTrue(top("café")?.startsWith("Beverages, coffee") == true, "\"café\" answered ${top("café")}")
    }

    @Test
    fun `the realistic typos that first surfaced this also resolve correctly now`() {
        assertTrue(top("chiken")?.startsWith("Chicken") == true, "\"chiken\" answered ${top("chiken")}")
        assertTrue(top("yogert")?.startsWith("Yogurt") == true, "\"yogert\" answered ${top("yogert")}")
        assertTrue(top("sammon")?.startsWith("Fish, salmon") == true, "\"sammon\" answered ${top("sammon")}")
    }

    @Test
    fun `a preparation word composed with the fixed word still resolves correctly`() {
        // "grilled chicken" is the everyday two-word case, not a synthetic one:
        // "grilled" is a recognised preparation word, so the food-only
        // fallback re-runs search on "chicken" alone after stripping it --
        // which used to land on Egg from inside a perfectly ordinary query.
        val results = search.search("grilled chicken")
        assertTrue(results.isNotEmpty(), "\"grilled chicken\" reached no record")
        assertTrue(
            results.first().displayName.startsWith("Chicken"),
            "\"grilled chicken\" answered ${results.first().displayName}",
        )
    }

    @Test
    fun `the fix cannot steal a match from the record that already depended on the shared word`() {
        // The property that makes a one-word addition safe by construction:
        // every tier requires ALL of a query's tokens to be accounted for by
        // ONE form, and "chicken"/"yogurt"/"salmon"/"cafe" contain none of
        // "egg", "tofu", "oil", "cane" -- so a query naming those foods
        // cannot be redirected by the new one-word forms.
        assertTrue(top("chicken egg")?.startsWith("Egg") == true, "\"chicken egg\" answered ${top("chicken egg")}")
        assertTrue(top("tofu yogurt")?.startsWith("Tofu") == true, "\"tofu yogurt\" answered ${top("tofu yogurt")}")
        val fishOil = top("fish oil, salmon")
        assertTrue(fishOil?.startsWith("Fish oil") == true, "\"fish oil, salmon\" answered $fishOil")
        assertTrue(top("syrup, cane")?.startsWith("Syrup") == true, "\"syrup, cane\" answered ${top("syrup, cane")}")
    }

    @Test
    fun `bare qualified-name foods elsewhere in the catalog still lose -- known, tracked, not fixed here`() {
        // The SAME failure shape, confirmed to affect other heads this pass
        // deliberately did not touch. Recorded so the gap stays visible
        // rather than being "discovered" and patched one word at a time
        // without a whole-catalog sweep -- exactly the mistake a dedicated
        // LEMMA tier and a form-multiplicity ranking term each made earlier
        // in this same investigation, both measured and rejected for it.
        assertTrue(top("rice")?.startsWith("Rice crackers") == true, "\"rice\" answered ${top("rice")}")
        assertTrue(
            top("poultry")?.startsWith("Spices, poultry seasoning") == true,
            "\"poultry\" answered ${top("poultry")}",
        )
        assertTrue(
            top("chocolate")?.startsWith("Beverages, chocolate syrup") == true,
            "\"chocolate\" answered ${top("chocolate")}",
        )
        assertTrue(
            top("popcorn")?.startsWith("Snacks, popcorn") == true,
            "\"popcorn\" answered ${top("popcorn")}",
        )
    }
}
