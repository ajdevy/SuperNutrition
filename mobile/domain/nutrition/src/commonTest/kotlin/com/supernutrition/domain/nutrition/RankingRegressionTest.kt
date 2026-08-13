package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Foods a Russian speaker types every day, and the record each must reach.
 *
 * These are regressions, not new capability. Every query here answered
 * correctly before the lemma fold landed in the TOKEN_SUBSET tier, and
 * answered with a different food after it. Found by adversarial review
 * against a model of the matcher and confirmed here on the real engine,
 * because a model agreeing with itself is the weakest evidence in the room.
 *
 * `свиней` is an ID TIE, not a coverage gap: Lard and all twenty pork cuts
 * cover the bare token `свинья` completely, at coverage 1000, and the tie
 * used to fall to whichever canonical id sorted first — Lard. The unused
 * evidence was that pork also carries the typed word *literally*, in
 * `мясо свиней`, where Lard reaches it only through the fold. Ranking now
 * breaks a TOKEN_SUBSET tie in favour of a record reached without the fold,
 * which cannot disturb `курицы`: chicken and egg are both literal there, and
 * coverage already separates them 1000 to 500.
 *
 * `паст` is different in kind from `свиней`, not degree. It is four
 * characters into `пастернак` (parsnip) and simultaneously the complete
 * genitive plural of `паста`, so the fold promotes it into TOKEN_SUBSET, and
 * first-tier-wins then skips PREFIX entirely — parsnip is not demoted, it is
 * absent from the result set. No ranking rule can reach a record that is not
 * in the tier at all, so `searchExact` now evaluates PREFIX as well whenever
 * nothing in TOKEN_SUBSET was reached literally, and appends what it finds.
 *
 * `масло` — the ordinary word for butter — was not a ranking defect at all.
 * No record carried it: AGROVOC's only Russian name for butter is the
 * qualified `сливочное масло`, while the oils concept's sole Russian name is
 * the bare plural `масла`, and the lemma fold sent the singular there. A
 * ranking rule strong enough to fix it was measured to cost other, commoner
 * words — `сыра` (of cheese) demoted to "Cheese substitute", `груша` (pear,
 * a dictionary form) demoted to "Avocados" — for the same reason `масло` was
 * broken: an AGROVOC gap, not a matcher defect. The real fix is the missing
 * word, added as a curated, reviewed, dated entry in
 * scripts/generate-food-synonyms.py's MANUAL_TERMS table (the JUNK_ALIASES
 * pattern read backwards) and applied offline to the checked-in
 * food-synonyms.json without re-crawling AGROVOC or Wikidata for a single
 * string.
 */
class RankingRegressionTest {
    private val search = CatalogSearch(generatedCatalog)

    private fun top(query: String): String = search.search(query).firstOrNull()?.displayName ?: "(nothing)"

    private fun reaches(query: String, prefix: String): Boolean =
        search.search(query).any { it.displayName.startsWith(prefix) }

    @Test
    fun `the ordinary word for butter reaches butter`() {
        // Fixed as a vocabulary gap, not a ranking rule: масло is now a
        // literal alternate name of Butter and matches at EXACT, above every
        // tier a ranking change could touch.
        assertTrue(top("масло").startsWith("Butter"), "\"масло\" answered ${top("масло")}")
    }

    @Test
    fun `every oblique form of the newly added word also reaches butter`() {
        // The word was added once, in the nominative; every other case form
        // reaches the same record only because the lemma fold already knew
        // масло's whole paradigm — via масла, which was already indexed as
        // the oils concept's own name. Enumerable exactly, not sampled: a
        // form can only move if every one of its tokens can satisfy some
        // record, and масло has exactly one token.
        for (form in listOf("масл", "масел", "масле", "маслом", "маслу", "маслам", "маслами", "маслах")) {
            assertTrue(top(form).startsWith("Butter"), "\"$form\" answered ${top(form)}")
        }
    }

    @Test
    fun `the oils concept keeps its own plural name`() {
        // масла is not масло: it is the oils concept's own stored term, and
        // adding the singular to butter must not take it away.
        assertTrue(top("масла").startsWith("Oil"), "\"масла\" answered ${top("масла")}")
    }

    @Test
    fun `the genitive plural of pig reaches pork`() {
        for (query in listOf("свиней", "200 граммов свиней")) {
            assertTrue(top(query).startsWith("Pork"), "\"$query\" answered ${top(query)}")
        }
    }

    @Test
    fun `свиней is a coverage tie, and the tiebreak must not be replaced by moving coverage`() {
        // States the mechanism so a future change that "fixes" the ranking
        // by inflating Pork's coverage, rather than by preferring literal
        // evidence at an existing tie, gets caught here rather than looking
        // like an unrelated improvement.
        val hits = search.search("свиней")
        val lard = hits.first { it.displayName.startsWith("Lard") }
        val pork = hits.first { it.displayName.startsWith("Pork") }
        assertEquals(lard.strength.tier, pork.strength.tier, "Lard and Pork must tie at the same tier")
        assertEquals(lard.strength.coverage, pork.strength.coverage, "the свиней tie must be a real coverage tie")
    }

    @Test
    fun `a half-typed word does not lose its record to a homographic form`() {
        // Absent, not demoted, is what made this a tier-selection defect
        // rather than a ranking one: the tier loop returns the first
        // non-empty tier outright, so being promoted into TOKEN_SUBSET
        // removed every PREFIX candidate from the result set entirely.
        assertTrue(reaches("паст", "Parsnips"), "\"паст\" reached ${top("паст")}")
        assertTrue(reaches("спарже", "Broccoli"), "\"спарже\" reached ${top("спарже")}")
        assertTrue(reaches("корне", "Celeriac"), "\"корне\" reached ${top("корне")}")
    }

    @Test
    fun `паст still shows pasta first -- presence was fixed, not rank`() {
        // Stated so nobody mistakes the union for a ranking fix it never
        // claimed to be. Four keystrokes into пастернак the user still sees
        // pasta; parsnip is merely reachable now instead of absent. If the
        // product intent becomes "a half-typed word should rank", this test
        // is the marker that the union alone does not deliver that.
        assertTrue(top("паст").startsWith("Pasta"), "\"паст\" now tops with ${top("паст")}")
    }

    @Test
    fun `the defect the fold was written to fix stays fixed`() {
        // The constraint that makes this hard rather than tedious: курицы
        // and масло are structurally identical situations (a one-token
        // fold-only form against a two-token literal one), and a rule strong
        // enough to fix масло by ranking demotes this pair the same way it
        // demotes сыра and груша. The tiebreak, gated on an actual tie,
        // cannot reach this pair at all, because coverage already separates
        // them 1000 to 500 before any tiebreak runs.
        for (query in listOf("курицы", "курицу", "курицей", "200 граммов курицы")) {
            assertTrue(top(query).startsWith("Chicken"), "\"$query\" answered ${top(query)}")
        }
        for (query in listOf("яйцо", "яйца", "яйцо курицы")) {
            assertTrue(top(query).startsWith("Egg"), "\"$query\" answered ${top(query)}")
        }
    }

    @Test
    fun `an accepted loss -- птицы now answers the literal match, not chicken`() {
        // Named rather than left to be discovered. Chicken and Poultry tie
        // at coverage 500; Poultry carries the typed word literally in
        // `одомашненные птицы`, Chicken reaches `домашняя птица` only
        // through the fold, and the tiebreak that correctly promotes Pork
        // over Lard promotes Poultry over Chicken here for the identical
        // reason. There is no version of a literal-evidence tiebreak that
        // keeps this one and takes the other: it is the tie the rule exists
        // to break, and this case happens to read worse to a user than
        // свиней did. Recorded as accepted, not as a bug nobody noticed.
        val answer = top("птицы")
        assertTrue(answer.startsWith("Poultry"), "\"птицы\" answered $answer, expected the accepted answer")
    }
}
