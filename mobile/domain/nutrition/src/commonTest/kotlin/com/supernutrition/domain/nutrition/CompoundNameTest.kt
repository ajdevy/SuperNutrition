package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The joining word of a compound food name is the same word that separates two
 * foods, in every language checked.
 *
 * Measured before this existed: `macaroni with cheese` segmented to
 * `[macaroni, cheese]`, so a record with that name was unreachable. Spanish and
 * Russian escaped only because `con` and `с` had never been added to the
 * conjunction list — the three languages behaved differently by omission rather
 * than by decision.
 *
 * The fix is not a list of exceptions per language. It is that the catalog
 * decides: a fragment resolves whole if it can, and is split only when it
 * cannot.
 */
class CompoundNameTest {
    private val compounds = CatalogSearch(
        listOf(
            CatalogRecord("food:macaroni-and-cheese", "Macaroni with cheese"),
            CatalogRecord("food:rice-with-chicken", "Arroz con pollo", listOf("курица с рисом")),
            CatalogRecord("food:egg-boiled", "Egg, boiled", listOf("eggs")),
            CatalogRecord("food:bread-rye", "Bread, rye", listOf("rye toast")),
            CatalogRecord("food:cheese-cheddar", "Cheese, cheddar", listOf("cheese")),
            CatalogRecord("food:macaroni-dry", "Macaroni, dry", listOf("macaroni")),
        ),
    )

    private fun identify(text: String) =
        TextIdentification(compounds) { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) }
            .identify(text) as TextIdentificationOutcome.Floor

    @Test
    fun `a compound name in the catalog resolves whole rather than splitting`() {
        // Both halves are also catalog foods, so an unconditional split would
        // have succeeded and returned a plausible two-item meal. That is the
        // failure worth preventing: a miss is recoverable, a confident wrong
        // answer is not.
        val outcome = identify("macaroni with cheese")

        assertEquals(listOf("food:macaroni-and-cheese"), outcome.items.map { it.canonicalItemId })
        assertEquals(emptyList(), outcome.unresolvedFragments)
    }

    @Test
    fun `Spanish con and Russian с behave the same way as English with`() {
        // The point of the change. Previously these worked by accident and
        // English did not work at all.
        assertEquals(
            listOf("food:rice-with-chicken"),
            identify("arroz con pollo").items.map { it.canonicalItemId },
        )
        assertEquals(
            listOf("food:rice-with-chicken"),
            identify("курица с рисом").items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `a phrase that names no single record still splits into its foods`() {
        // The other direction, and the reason splitting on failure beats never
        // splitting: nothing is called "eggs with rye toast", so it is two items.
        val outcome = identify("eggs with rye toast")

        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye"),
            outcome.items.map { it.canonicalItemId },
        )
    }

    @Test
    fun `a split is taken only when every part resolves`() {
        // Half a split is worse than none: it would report one real food beside
        // an invented fragment as though the description had named them
        // separately. "wombat" is in no catalog, so the whole fragment is
        // reported unresolved instead.
        val outcome = identify("eggs with wombat")

        assertEquals(emptyList(), outcome.items.map { it.canonicalItemId })
        assertEquals(listOf("eggs with wombat"), outcome.unresolvedFragments)
    }

    @Test
    fun `spans still index the original description after a weak split`() {
        // A part whose span pointed into the fragment rather than the whole
        // description would show provenance from the wrong offset — the same
        // defect the engine path is guarded against.
        val description = "eggs with rye toast"
        val outcome = identify(description)

        assertEquals(
            listOf("eggs", "rye toast"),
            outcome.items.map { description.substring(it.sourceSpan.start, it.sourceSpan.end) },
        )
    }

    @Test
    fun `strong conjunctions still split without needing to fail first`() {
        // "and" is unambiguous in a way "with" is not, so it keeps splitting on
        // sight. Demoting it would make every two-item meal pay a failed lookup.
        val outcome = identify("eggs and rye toast")

        assertEquals(
            listOf("food:egg-boiled", "food:bread-rye"),
            outcome.items.map { it.canonicalItemId },
        )
    }
}
