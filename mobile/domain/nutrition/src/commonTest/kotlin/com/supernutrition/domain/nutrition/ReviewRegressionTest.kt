package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressions from the adversarial review of E7.2.
 *
 * Each test here is a defect that was confirmed by execution or by hand-tracing
 * the committed code, not a hypothetical. They are grouped in one file because
 * they share a cause: the first pass fixed the spec's success signal for the
 * exact wording the spec used, and every one of these is a phrasing a user
 * reaches in one keystroke from it.
 */
class ReviewRegressionTest {
    private val floor = CatalogSearch(SeedCatalog.records)

    @Test
    fun `digits quantify a food just as spelled-out numbers do`() {
        // "two boiled eggs" resolved and "2 boiled eggs" did not, which is a
        // difference no user would predict. A digit is a token class, not one
        // more word for the quantity list.
        assertEquals(listOf("food:egg-boiled"), floor.search("2 boiled eggs").map { it.canonicalItemId })
        assertEquals(listOf("food:bread-rye"), floor.search("1 slice of rye toast").map { it.canonicalItemId })
        assertEquals(
            listOf("food:chicken-breast-raw"),
            floor.search("100 g chicken breast").map { it.canonicalItemId },
        )
    }

    @Test
    fun `a digit glued to its unit is still a quantity`() {
        assertEquals(listOf("food:avocado-raw"), floor.search("200g avocado").map { it.canonicalItemId })
    }

    @Test
    fun `a short record name is not reachable by a fuzzy near-miss`() {
        // "ribs" reached "ris" — the Latin-script alternate of рис, white rice —
        // because the allowance was keyed on the query token alone, so a
        // 4-character query still got distance 1 against a 3-character record.
        // search-mechanics.md is explicit that no match never falls back to a
        // nearest guess; proposing white rice to someone typing ribs is exactly
        // that.
        assertEquals(emptyList(), floor.search("ribs").map { it.canonicalItemId })
        assertEquals(emptyList(), floor.search("draw").map { it.canonicalItemId })
    }

    @Test
    fun `plurals still resolve, because that is not a fuzzy match`() {
        // The tightened allowance must not take plurals with it. English needs
        // nothing beyond plurals per the language matrix, so they are handled
        // as morphology rather than as edit distance.
        assertEquals(listOf("food:egg-boiled"), floor.search("boiled eggs").map { it.canonicalItemId })
        assertEquals(listOf("food:bean-black"), floor.search("black bean").map { it.canonicalItemId })
    }

    @Test
    fun `spans index the description the caller passed, not a trimmed copy`() {
        // A leading space is what a paste or a mobile keyboard produces. The
        // spans were computed against the trimmed string and validated against
        // it too, so the offset was self-consistently wrong and the guard that
        // exists to reject false provenance could not see it.
        val description = "  boiled egg and rye bread"
        val identification = TextIdentification(floor = floor, engine = DeterministicTextEngine(floor)::identify)

        val outcome = identification.identify(description)

        assertTrue(outcome is TextIdentificationOutcome.Items, "expected the engine to segment, got $outcome")
        val quoted = outcome.items.map { description.substring(it.sourceSpan.start, it.sourceSpan.end) }
        assertEquals(listOf("boiled egg", "rye bread"), quoted)
    }

    @Test
    fun `a newline separates items just as a comma does`() {
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) },
        )

        assertEquals(
            TextIdentificationOutcome.Floor(
                listOf(
                    DetectedItem("food:egg-boiled", TextSpan(0, 10)),
                    DetectedItem("food:bread-rye", TextSpan(11, 20)),
                ),
            ),
            identification.identify("boiled egg\nrye bread"),
        )
    }

    @Test
    fun `a conjunction still separates when a newline sits beside it`() {
        // Separators carried their own literal spaces, so " and " failed to
        // match when the preceding character was a newline. The conjunction
        // then leaked into the query as the token "and", which no record
        // satisfies, and the whole description resolved to nothing.
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) },
        )

        assertEquals(
            TextIdentificationOutcome.Floor(
                listOf(
                    DetectedItem("food:egg-boiled", TextSpan(0, 10)),
                    DetectedItem("food:bread-rye", TextSpan(15, 24)),
                ),
            ),
            identification.identify("boiled egg\nand rye bread"),
        )
    }

    @Test
    fun `coverage is measured on the form that matched, not the best form`() {
        // coverageOf took the maximum over every searchable form, including
        // forms that failed the tier. A record carrying a short synonym was
        // therefore scored on that synonym even when a different form won,
        // which inverts search-mechanics.md's rule that "rye bread" ranks
        // `bread, rye` above `bread, rye, reduced sodium, toasted`.
        val search = CatalogSearch(
            listOf(
                CatalogRecord("food:rice-white-cooked", "Rice, white, cooked"),
                CatalogRecord(
                    "food:rice-white-restaurant",
                    "Rice, white, cooked, salted, restaurant style",
                    alternateNames = listOf("rice"),
                ),
            ),
        )

        assertEquals(
            listOf("food:rice-white-cooked", "food:rice-white-restaurant"),
            search.search("white rice").map { it.canonicalItemId },
            "the staple must outrank the qualified variant; the bare synonym did not win the tier",
        )
    }

    @Test
    fun `a unit quantifies whether or not a space precedes it`() {
        // Two vocabularies had grown apart: the glued form was checked against a
        // 7-entry unit list and the spaced form against the ~30-entry quantity
        // list. "500mg rice" resolved and "500 mg rice" did not; "2 tbsp rice"
        // resolved and "2tbsp rice" did not. Whether the user typed a space is
        // not a distinction the catalog should notice.
        val rice = listOf("food:rice-white-cooked")
        assertEquals(rice, floor.search("500 mg rice").map { it.canonicalItemId })
        assertEquals(rice, floor.search("500mg rice").map { it.canonicalItemId })
        assertEquals(rice, floor.search("2 tbsp rice").map { it.canonicalItemId })
        assertEquals(rice, floor.search("2tbsp rice").map { it.canonicalItemId })
        assertEquals(rice, floor.search("200grams rice").map { it.canonicalItemId })
    }

    @Test
    fun `a preparation the catalog cannot honour is reported, not dropped`() {
        // "grilled chicken breast" used to resolve to nothing at all, because
        // every tier needs all query tokens on one record. Matching the food and
        // reporting the preparation is the honest middle: the user sees that a
        // raw record was matched and that "grilled" did not land, instead of
        // getting raw-chicken numbers for a grilled meal.
        val hits = floor.search("grilled chicken breast")

        assertEquals(listOf("food:chicken-breast-raw"), hits.map { it.canonicalItemId })
        assertEquals(listOf("grilled"), hits.single().unmatchedPreparation)
    }

    @Test
    fun `a preparation the catalog does carry is matched, not reported`() {
        // The relaxation is a last resort. A record that carries the
        // preparation must match on it outright and report nothing.
        for (query in listOf("boiled egg", "raw chicken breast", "cooked white rice")) {
            val hits = floor.search(query)
            assertTrue(hits.isNotEmpty(), "$query should resolve")
            assertEquals(emptyList(), hits.single().unmatchedPreparation, "$query matched its preparation")
        }
    }

    @Test
    fun `preparation is never silently stripped the way a quantity word is`() {
        // The two vocabularies are treated differently on purpose. A quantity
        // word is deleted because the amount is asked for again at serving
        // confirmation; a preparation word is part of what the food is.
        val quantity = floor.search("two boiled eggs").single()
        val preparation = floor.search("smoked rye bread").single()

        assertEquals(emptyList(), quantity.unmatchedPreparation, "a stripped quantity reports nothing")
        assertEquals(listOf("smoked"), preparation.unmatchedPreparation, "an unhonoured preparation is reported")
    }

    @Test
    fun `the floor carries unmatched preparation to the item the user reviews`() {
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) },
        )

        val outcome = identification.identify("grilled chicken breast")

        assertEquals(
            TextIdentificationOutcome.Floor(
                listOf(DetectedItem("food:chicken-breast-raw", TextSpan(0, 22), listOf("grilled"))),
            ),
            outcome,
        )
    }

    @Test
    fun `Spanish reaches the catalog, with and without its accents`() {
        // Spanish is a declared target again, and it is the only one on the
        // light-stemming-with-diacritics profile — which is what makes the
        // accent fold load-bearing rather than incidental. A user typing
        // without accents must still reach the record.
        assertEquals(listOf("food:eggplant-raw"), floor.search("berenjena").map { it.canonicalItemId })
        assertEquals(listOf("food:avocado-raw"), floor.search("aguacate").map { it.canonicalItemId })
        assertEquals(listOf("food:avocado-raw"), floor.search("palta").map { it.canonicalItemId })
        assertEquals(listOf("food:rice-white-cooked"), floor.search("arroz").map { it.canonicalItemId })
    }
}
