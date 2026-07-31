package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A text engine does not have to be a language model. This one segments on
 * ordinary separators and resolves each fragment through the catalog, so it runs
 * on any supported device with no model, no download, and no G-3 dependency.
 *
 * It is deliberately worse than a model at understanding language and
 * deliberately better at being available. When G-3 approves an on-device model,
 * it becomes a swap behind the same port rather than a prerequisite.
 */
class DeterministicTextEngineTest {
    private val engine = DeterministicTextEngine(CatalogSearch(SeedCatalog.records))

    @Test
    fun `splits a two-item meal on the conjunction`() {
        val outcome = engine.identify("boiled egg and rye bread")

        val items = (outcome as TextEngineOutcome.Segmented).items
        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye"),
            items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `splits on commas as well as conjunctions`() {
        val outcome = engine.identify("boiled egg, rye bread, white rice")

        assertEquals(3, (outcome as TextEngineOutcome.Segmented).items.size)
    }

    @Test
    fun `each item span indexes the words it came from`() {
        val description = "boiled egg and rye bread"
        val outcome = engine.identify(description)

        (outcome as TextEngineOutcome.Segmented).items.forEach { item ->
            val span = item.sourceSpan
            assertTrue(span.indexes(description), "span must index the description")
            // The span must cover text that actually resolved, not whitespace or
            // a separator, or the provenance shown to the user is a lie.
            assertTrue(description.substring(span.start, span.end).isNotBlank())
        }
    }

    @Test
    fun `abstains when no fragment resolves rather than guessing`() {
        val outcome = engine.identify("motorcycle and gravel")

        assertTrue(outcome is TextEngineOutcome.Abstained)
    }

    @Test
    fun `abstains when only some fragments resolve`() {
        // Returning just the eggs would silently drop half the meal while
        // looking authoritative -- FM-6. Partial segmentation abstains.
        val outcome = engine.identify("boiled egg and motorcycle")

        assertTrue(
            outcome is TextEngineOutcome.Abstained,
            "a partially resolvable description must abstain, not return a subset",
        )
    }

    @Test
    fun `resolves a single item with no separator`() {
        val outcome = engine.identify("rye bread")

        assertEquals(
            listOf("food:bread-rye"),
            (outcome as TextEngineOutcome.Segmented).items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `works through the identification port with no model present`() {
        // The whole point: Noah, offline, low-end device, no model, still
        // reaches a segmented meal.
        val identification = TextIdentification(
            floor = CatalogSearch(SeedCatalog.records),
            engine = engine::identify,
        )
        val description = "boiled egg and rye bread"

        val outcome = identification.identify(description)

        // Offsets are derived from the description rather than hand-counted.
        // Counting them by eye produced an off-by-one here and again in the v2
        // fixtures; a span that is wrong by one character still looks right in
        // a diff and misreports which words produced an item.
        fun spanOf(phrase: String) =
            description.indexOf(phrase).let { TextSpan(it, it + phrase.length) }

        assertEquals(
            TextIdentificationOutcome.Items(
                listOf(
                    DetectedItem("food:egg-boiled", spanOf("boiled egg")),
                    DetectedItem("food:bread-rye", spanOf("rye bread")),
                ),
            ),
            outcome,
        )
    }
}
