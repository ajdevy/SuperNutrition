package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the identification fixture set against the real catalog.
 *
 * E7.4 requires the set to declare outcomes in both directions. These two tests
 * are what makes that requirement bite: one fails when a fixture stops
 * resolving, the other fails when the catalog becomes confident about something
 * it should decline. A suite with only the first would pass for an engine that
 * answered everything.
 */
class IdentificationFixtureTest {
    private val floor = CatalogSearch(SeedCatalog.records)

    @Test
    fun `every must-resolve fixture reaches its declared record`() {
        val wrong = IdentificationFixtures.mustResolve.mapNotNull { fixture ->
            val actual = floor.search(fixture.query).firstOrNull()?.canonicalItemId
            if (actual == fixture.expected) {
                null
            } else {
                "${fixture.query} -> $actual, expected ${fixture.expected} (${fixture.because})"
            }
        }

        assertEquals(emptyList(), wrong, "fixtures that stopped resolving")
    }

    @Test
    fun `every must-not-resolve fixture declines rather than guessing`() {
        // The direction that a resolvable-only suite cannot measure. Each of
        // these has a plausible near neighbour in the catalog, which is exactly
        // why a confident answer here would look reasonable and be wrong.
        val guessed = IdentificationFixtures.mustNotResolve.mapNotNull { fixture ->
            val actual = floor.search(fixture.query).firstOrNull()?.canonicalItemId
            if (actual == null) null else "${fixture.query} -> $actual (${fixture.because})"
        }

        assertEquals(emptyList(), guessed, "queries the catalog should have declined")
    }

    @Test
    fun `the set measures both directions rather than only the flattering one`() {
        // A guard on the fixture set itself. Someone adding only resolvable
        // cases would weaken the suite without any test noticing, because every
        // assertion above would still pass.
        assertTrue(
            IdentificationFixtures.mustNotResolve.size >= 5,
            "the must-not-resolve half is what stops the suite rewarding a guesser",
        )
        assertTrue(IdentificationFixtures.mustResolve.size >= 10, "and the resolvable half must stay substantial")
    }

    @Test
    fun `abstention rate is reported as a measurement, not enforced as a gate`() {
        // The spec is explicit that this is measured and not a release gate:
        // nothing blocks on it because the deterministic floor always answers.
        // Recording it here keeps the number visible without turning it into a
        // threshold someone would then tune the catalog against.
        val declined = IdentificationFixtures.entries.count { floor.search(it.query).isEmpty() }
        val rate = declined * 100 / IdentificationFixtures.entries.size

        println("identification fixture abstention rate: $rate% ($declined of ${IdentificationFixtures.entries.size})")
        // Asserting the rate is between 0 and 100 would be arithmetically true
        // whatever the catalog did. What is worth pinning is that the measured
        // number matches what the set declares: a fixture whose declared
        // direction and actual behaviour disagree makes the reported rate a
        // number about something other than abstention.
        assertEquals(
            IdentificationFixtures.mustNotResolve.size,
            declined,
            "the measured abstention count must match the set's own declaration",
        )
    }
}
