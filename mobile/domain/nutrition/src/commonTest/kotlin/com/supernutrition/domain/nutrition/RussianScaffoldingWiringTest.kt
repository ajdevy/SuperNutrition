package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `russianScaffoldingForms` wired into search, and the guard that has to land
 * with it in the same change.
 *
 * The wiring itself is two lines: `namesNoFood` now refuses scaffolding words
 * alongside separators and quantities, and `stripNonFoodEdgeWords` (renamed
 * from `stripQuantityWords`) removes them from a fragment's two ends. Neither
 * line is safe alone.
 *
 * `namesNoFood` runs once, on the query exactly as typed, before anything is
 * stripped. Without teaching it about scaffolding, "приготовил и съел" —
 * scaffolding, weak separator, scaffolding — passes that guard (not every
 * token is a quantity word or a separator), reaches the stripping step, and
 * strips to the bare word "и". Several real record names carry "и" as an
 * interior token — `фрукты и ягоды` is one, present in the shipped
 * [russianPhraseCatalog] today — so that bare token can resolve to a food at
 * TOKEN_SUBSET. A description that named nothing edible would return one,
 * which is the FM-1 shape this file exists to prevent everywhere else.
 */
class RussianScaffoldingWiringTest {
    private val search = CatalogSearch(generatedCatalog)

    private fun top(query: String): String = search.search(query).firstOrNull()?.displayName ?: "(nothing)"

    @Test
    fun `a scaffolding verb no longer blocks the food behind it`() {
        assertTrue(top("съел локву").startsWith("Loquat"), "\"съел локву\" answered ${top("съел локву")}")
        assertTrue(top("добавил папайю").startsWith("Papaya"), "\"добавил папайю\" answered ${top("добавил папайю")}")
        assertTrue(
            top("приготовил гречневую муку").contains("Buckwheat", ignoreCase = true) ||
                top("приготовил гречневую муку").contains("гречневая", ignoreCase = true),
            "\"приготовил гречневую муку\" answered ${top("приготовил гречневую муку")}",
        )
    }

    @Test
    fun `a scaffolding verb composes with a quantity word from the same edge`() {
        // съел два яйца: a scaffolding word and a quantity word share the
        // leading edge. A single combined strip is what lets them come off
        // together regardless of which one sits closer to the food.
        assertTrue(top("съел два яйца").startsWith("Egg"), "\"съел два яйца\" answered ${top("съел два яйца")}")
    }

    @Test
    fun `scaffolding words alone still name no food`() {
        for (query in listOf("ел", "съел", "добавил", "приготовил", "изо", "ел и съел")) {
            assertEquals(emptyList(), search.search(query), "\"$query\" reached a record")
        }
    }

    @Test
    fun `scaffolding either side of a weak separator does not leak the separator through`() {
        // The defect the guard exists for, on a minimal fixture built to carry
        // exactly the dangerous shape -- a record whose own name contains "и"
        // as one of its tokens -- so the property holds regardless of what
        // GeneratedCatalog.kt happens to contain on any given day.
        val fixture = CatalogSearch(
            listOf(
                CatalogRecord("food:fruit-and-berries", "Fruit and berries", listOf("фрукты и ягоды")),
            ),
        )

        for (query in listOf("приготовил и съел", "ел и добавил", "съел и приготовил")) {
            assertEquals(emptyList(), fixture.search(query), "\"$query\" reached a record")
        }
    }

    @Test
    fun `the same shape is checked against the real corpus catalog, not only a fixture`() {
        // russianPhraseCatalog carries `фрукты и ягоды` today (confirmed by
        // inspection, not assumed), so this is the fixture case restated
        // against real generated data rather than a hand-built stand-in.
        val floor = CatalogSearch(russianPhraseCatalog)

        assertEquals(emptyList(), floor.search("приготовил и съел"), "leaked through the real corpus catalog")
    }

    @Test
    fun `TextIdentification's floor path stays silent on scaffolding-only text`() {
        // The guard has to hold through the caller that actually reaches
        // users, not only through CatalogSearch directly: TextIdentification
        // segments a description into fragments and resolves each one
        // through the same floor.
        val identification =
            TextIdentification(search) { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) }

        val outcome = identification.identify("приготовил и съел")

        val items = when (outcome) {
            is TextIdentificationOutcome.Floor -> outcome.items
            is TextIdentificationOutcome.Items -> outcome.items
            TextIdentificationOutcome.Empty -> emptyList()
        }
        assertEquals(emptyList(), items, "TextIdentification invented a food for scaffolding-only text")
    }
}
