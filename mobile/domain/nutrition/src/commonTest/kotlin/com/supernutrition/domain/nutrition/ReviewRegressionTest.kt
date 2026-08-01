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
}
