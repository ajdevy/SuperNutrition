package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The generated Russian scaffolding table, checked against the same catalogs
 * the generator itself checked, independently rather than on its word.
 *
 * `russianScaffoldingForms` names words that frame a meal description without
 * naming any part of it — `ел`, `съел`, `добавил`, `приготовил`, `из`/`изо` —
 * so the query behind them can be found once they are stripped. It is a
 * second table rather than an extension of [russianFormToLemma] on purpose:
 * that table folds on BOTH SIDES of every token comparison the matcher makes,
 * so a verb form sharing a spelling with some food's paradigm would silently
 * redirect a food query. This table is consulted only by the stripping logic
 * in `stripNonFoodEdgeWords`, so a word here can remove itself from a query
 * and can never move the query to a different record — which is exactly why
 * these two properties are worth asserting independently rather than trusted
 * from the generator's own report.
 */
class RussianScaffoldingTest {
    @Test
    fun `the table is present and holds what the generator reported`() {
        assertEquals(54, russianScaffoldingForms.size, "table holds ${russianScaffoldingForms.size} forms")
    }

    @Test
    fun `every entry is a bare lowercase Cyrillic word`() {
        val malformed = russianScaffoldingForms.filter { form ->
            form.isEmpty() || form != form.lowercase() || form.any { !it.isLetter() || it.code < 0x0400 }
        }
        assertEquals(emptyList(), malformed.take(10))
    }

    @Test
    fun `no entry collides with a name the shipped catalog carries`() {
        // Re-checked here, independently of the generator's own report, over
        // both catalogs the matcher can actually be built from: the full
        // 3519-record shipped catalog and the curated 260-record corpus
        // catalog. A form that is also a real food-name token would let a
        // stripped query resolve through the very word being stripped.
        val catalogTokens = generatedCatalog
            .flatMap { listOf(it.displayName) + it.alternateNames }
            .flatMap { tokenizeNormalized(normalizeForSearch(it)) }
            .toSet()
        val corpusTokens = russianPhraseCatalog
            .flatMap { listOf(it.displayName) + it.alternateNames }
            .flatMap { tokenizeNormalized(normalizeForSearch(it)) }
            .toSet()

        val collisions = russianScaffoldingForms.filter { it in catalogTokens || it in corpusTokens }

        assertEquals(emptyList(), collisions)
    }

    @Test
    fun `no entry is also a key of the food lemma table`() {
        // The property that keeps the two tables from being able to disagree
        // silently: a form that named both a verb and a food's inflected form
        // would be genuinely ambiguous, and this table refuses ambiguity
        // rather than picking a reading.
        val collisions = russianScaffoldingForms.filter { it in russianFormToLemma }

        assertEquals(emptyList(), collisions)
    }

    @Test
    fun `no entry duplicates an existing separator or quantity word`() {
        // с, со and и are the Cyrillic members of the matcher's own weak
        // separators, already reached through isSeparatorWord — a second copy
        // here is exactly the drift AGENTS.md warns this file about.
        for (separator in listOf("с", "со", "и")) {
            assertFalse(separator in russianScaffoldingForms, "\"$separator\" is already a separator")
        }
        val quantityOverlap = russianScaffoldingForms.filter { it in QUANTITY_WORDS }
        assertEquals(emptyList(), quantityOverlap)
    }

    @Test
    fun `a negation is never in the table`() {
        // без, не, ни, нет, кроме, вместо -- stripping any of these would turn
        // a harmless non-answer ("без хурмы" resolving to nothing) into a
        // confident wrong food (resolving to persimmon), which is worse than
        // the miss it replaces.
        for (negation in listOf("без", "безо", "не", "ни", "нет", "кроме", "вместо")) {
            assertFalse(negation in russianScaffoldingForms, "\"$negation\" is a negation and must be absent")
        }
    }

    @Test
    fun `a participle or gerund is never in the table`() {
        // приготовленный and съеденный describe the state of the food, and
        // preparation is reported rather than discarded (unmatchedPreparation)
        // because it moves nutrition per 100 g. Stripping these would silently
        // drop information the matcher elsewhere goes out of its way to keep.
        val participlesAndGerunds = listOf(
            "приготовленный", "приготовленная", "приготовленное", "приготовив",
            "съеденный", "съеденная", "съеденное", "съев",
            "добавленный", "добавленная", "добавленное", "добавив",
        )
        for (form in participlesAndGerunds) {
            assertFalse(form in russianScaffoldingForms, "\"$form\" is a participle or gerund and must be absent")
        }
    }

    @Test
    fun `the refused preposition из is absent while its second form изо is kept`() {
        // из collides with two real catalog names (мука из проса, мука из
        // сорго); изо does not collide with anything and is kept.
        assertFalse("из" in russianScaffoldingForms, "\"из\" should have been refused for its catalog collision")
        assertTrue("изо" in russianScaffoldingForms, "\"изо\" should have survived -- it has no collision")
    }
}
