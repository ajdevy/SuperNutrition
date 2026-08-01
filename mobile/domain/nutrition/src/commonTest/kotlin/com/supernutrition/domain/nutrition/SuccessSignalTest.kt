package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The SPEC success signal, asserted verbatim.
 *
 * "Noah — offline, on a lower-tier device, with no local vision engine
 * available — types 'two boiled eggs and a slice of rye toast', confirms the
 * proposed records and servings, and reaches a Grounded Nutrition Result."
 *
 * This existed only as prose in the spec and in commit messages. Nothing
 * asserted it, and an adversarial review found it failed end to end on
 * committed code in two independent ways: the floor received the whole
 * description and returned nothing, and the engine abstained because quantity
 * words matched no record.
 *
 * It is a test now so it cannot silently regress again.
 */
class SuccessSignalTest {
    private val description = "two boiled eggs and a slice of rye toast"
    private val floor = CatalogSearch(SeedCatalog.records)

    @Test
    fun `the success signal resolves with no engine at all`() {
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) },
        )

        val outcome = identification.identify(description)

        val resolved = when (outcome) {
            is TextIdentificationOutcome.Items -> outcome.items.map { it.canonicalItemId }
            is TextIdentificationOutcome.Floor -> outcome.canonicalItemIds
            TextIdentificationOutcome.Empty -> emptyList()
        }
        assertEquals(listOf("food:egg-boiled", "food:bread-rye"), resolved)
    }

    @Test
    fun `the success signal resolves through the deterministic engine`() {
        val identification = TextIdentification(
            floor = floor,
            engine = DeterministicTextEngine(floor)::identify,
        )

        val outcome = identification.identify(description)

        assertTrue(
            outcome is TextIdentificationOutcome.Items,
            "the deterministic engine must segment the success signal, not abstain",
        )
        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye"),
            outcome.items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `quantity words do not prevent a food resolving`() {
        // "two boiled eggs" and "a slice of rye toast" are how people type
        // meals. A catalog match must survive the counting words around it.
        assertEquals(
            listOf("food:egg-boiled"),
            floor.search("two boiled eggs").map { it.canonicalItemId },
        )
        assertEquals(
            listOf("food:bread-rye"),
            floor.search("a slice of rye toast").map { it.canonicalItemId },
        )
    }

    @Test
    fun `a fragment the floor cannot resolve is reported, not dropped`() {
        // FM-6 verbatim: the result is not wrong about what it shows, it is
        // wrong about what it omits. "quinoa" is deliberately absent from the
        // seed catalog, so this asserts the omission travels with the result
        // instead of the meal quietly shrinking to its resolvable half.
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Abstained("engine-declined") },
        )

        val outcome = identification.identify("boiled egg and quinoa")

        assertEquals(
            TextIdentificationOutcome.Floor(
                canonicalItemIds = listOf("food:egg-boiled"),
                unresolvedFragments = listOf("quinoa"),
            ),
            outcome,
        )
    }

    @Test
    fun `the floor resolves each item of a multi-item description`() {
        // The floor is documented as one record per query, so handing it a
        // whole meal was a category error. It must segment the same way the
        // engine does before searching.
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Abstained("engine-declined") },
        )

        val outcome = identification.identify("boiled egg and rye bread")

        assertEquals(
            TextIdentificationOutcome.Floor(listOf("food:egg-boiled", "food:bread-rye")),
            outcome,
        )
    }
}
