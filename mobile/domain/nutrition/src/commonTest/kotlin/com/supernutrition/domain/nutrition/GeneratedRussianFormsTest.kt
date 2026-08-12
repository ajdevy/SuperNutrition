package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generated Russian form table.
 *
 * Russian nouns decline across six cases, so a catalog holding surface forms
 * answers `курица` and not `курицу`. The table is the build-time answer: forms
 * are expanded from OpenCorpora by pymorphy3 once, and the runtime stays a hash
 * lookup, because there is no pure-KMP morphology stack to run on a phone.
 *
 * These assertions are not new work — the generating agent verified every one of
 * them by hand in Python and said plainly that the evidence lived in its report
 * rather than in the repository. That is the gap this file closes. A property
 * checked once by a person who has moved on is a property nothing protects.
 *
 * The table is not wired into `CatalogSearch` yet, and that is deliberate:
 * folding a query to its lemma can regress the PREFIX tier, because `морски` is
 * a prefix of the record token `морские` but not of its lemma `морской`. Someone
 * mid-word would lose a hit that works today, so where the fold is applied is a
 * decision to take on purpose rather than in passing.
 */
class GeneratedRussianFormsTest {
    @Test
    fun `the table is present and substantial`() {
        assertTrue(
            russianFormToLemma.size > 10_000,
            "table holds ${russianFormToLemma.size} forms; a small one means the generator silently found nothing",
        )
    }

    @Test
    fun `inflected forms reach the nominative the catalog carries`() {
        // The gap this exists for. Every one of these is a form a person types
        // and the catalog does not hold.
        assertEquals("курица", russianFormToLemma["курицу"])
        assertEquals("курица", russianFormToLemma["курицы"])
        assertEquals("курица", russianFormToLemma["курицей"])
        assertEquals("грамм", russianFormToLemma["граммов"])
        assertEquals("грамм", russianFormToLemma["граммами"])
    }

    @Test
    fun `no form maps to itself`() {
        // An identity entry is a lookup that changes nothing while costing a
        // hash probe, and it hides whether the table actually covers a word.
        val identity = russianFormToLemma.filter { (form, lemma) -> form == lemma }

        assertEquals(emptyMap(), identity)
    }

    @Test
    fun `ё is folded on the way in and never on the way out`() {
        // A user types `свекла` or `свёкла` interchangeably, so the key side must
        // accept both. The value must stay the form the catalog actually holds,
        // or the lookup would resolve to a word no record carries.
        val valuesWithYo = russianFormToLemma.values.filter { it.contains('ё') }

        assertEquals(emptyList(), valuesWithYo, "a lemma containing ё cannot match a folded catalog term")
    }

    @Test
    fun `ambiguous forms are absent rather than guessed`() {
        // The generator excluded 175 forms whose reading was ambiguous instead of
        // picking one. `гранат` is pomegranate or garnet depending on the
        // paradigm, and a silent pick sends a Russian word to the wrong food —
        // the FM-1 shape. Absence is the correct answer here, and this test
        // states that so nobody later "fixes" it by choosing.
        assertNull(russianFormToLemma["гранаты"], "an ambiguous form must be absent, not resolved to one reading")
    }

    @Test
    fun `every value is a bare lowercase word`() {
        // The values are looked up against catalog terms, so a stray space,
        // capital or punctuation would simply never match and would do so
        // silently.
        val malformed = russianFormToLemma.values.filter { value ->
            value.isEmpty() || value != value.lowercase() || value.any { it.isWhitespace() }
        }

        assertEquals(emptyList(), malformed.take(10))
    }
}
