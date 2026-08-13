package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the generated multilingual corpus against the real engine.
 *
 * The corpus is built from AGROVOC and Wikidata rather than hand-written, so it
 * exercises names nobody on this project chose — which is the point. A suite
 * whose cases were invented by the same person who wrote the matcher tests that
 * person's assumptions, not the matcher.
 *
 * Failures are reported per kind and per language rather than as one number,
 * because the kinds are in genuinely different states: English is implemented,
 * Russian and Spanish conjunction and quantity vocabularies are not. Collapsing
 * those into a single pass/fail would either hide the working half or block on
 * the unimplemented one.
 */
class MealTextCorpusTest {
    private val floor = CatalogSearch(corpusCatalog)
    private val identification =
        TextIdentification(floor) { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) }

    private fun resolve(query: String): List<String> = when (val outcome = identification.identify(query)) {
        is TextIdentificationOutcome.Floor -> outcome.items.map { it.canonicalItemId }
        is TextIdentificationOutcome.Items -> outcome.items.map { it.canonicalItemId }
        TextIdentificationOutcome.Empty -> emptyList()
    }

    private fun failures(cases: List<MealTextCase>): List<String> = cases.mapNotNull { case ->
        val actual = resolve(case.query)
        if (actual == case.expected) {
            null
        } else {
            "[${case.kind}/${case.language}] \"${case.query}\" -> $actual, expected ${case.expected}"
        }
    }

    @Test
    fun `the corpus is large enough and measures both directions`() {
        assertTrue(corpusCases.size >= 1000, "corpus holds ${corpusCases.size} cases")
        val negatives = corpusCases.count { it.expected.isEmpty() }
        assertTrue(negatives >= 100, "only $negatives cases assert a non-answer")
    }

    @Test
    fun `every catalog display name resolves to its own record`() {
        // The baseline. A failure here is ambiguity in the generated catalog
        // rather than a matcher defect, so it is worth separating.
        assertEquals(emptyList(), failures(corpusCases.filter { it.kind == "exact" }))
    }

    @Test
    fun `every alternate name reaches the record it belongs to`() {
        // The class that actually tests the synonym table, and the reason
        // Russian and Spanish single-word lookup works at all.
        assertEquals(emptyList(), failures(corpusCases.filter { it.kind == "alias" }))
    }

    @Test
    fun `English multi-item and quantity phrases resolve`() {
        // English is the implemented language. Russian and Spanish are measured
        // separately below and are expected to fail until their vocabularies
        // exist, so asserting them here would block on known work.
        val english = corpusCases.filter { it.language == "en" && it.kind in setOf("two-item", "quantity") }
        assertEquals(emptyList(), failures(english))
    }

    @Test
    fun `nothing is invented for a query that names no food`() {
        // The half that stops the suite rewarding a matcher which answers
        // everything. A bare conjunction or a single character resolving to a
        // food is the FM-1 shape: confident, plausible, and wrong.
        assertEquals(emptyList(), failures(corpusCases.filter { it.expected.isEmpty() }))
    }

    @Test
    fun `Russian and Spanish phrase support is reported, not gated`() {
        // Measured and printed rather than asserted. These fail today because
        // the ru/es conjunction and quantity vocabularies are not implemented —
        // recorded in knownFailingKinds. Printing the number keeps the gap
        // visible without pretending it is closed or blocking on it.
        val pending = corpusCases.filter {
            it.language in setOf("ru", "es") && it.kind in knownFailingKinds
        }
        val broken = failures(pending)
        val rate = if (pending.isEmpty()) 0 else broken.size * 100 / pending.size

        println("ru/es phrase support: ${pending.size - broken.size}/${pending.size} pass ($rate% failing)")
        assertTrue(pending.isNotEmpty(), "the pending set must not silently empty itself")
    }
}
