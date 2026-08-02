package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real Russian meal text, run against the real engine.
 *
 * This corpus exists because the multilingual one was measuring the wrong thing.
 * It built its Russian quantity cases by gluing a counting word onto an
 * untouched nominative — `две луцианы` — which is not how the language is
 * written, and it reported 154/160 while no lemmatizer existed at all. A number
 * that high, from a feature that absent, was the tell.
 *
 * Every ending here is computed by pymorphy3 from the OpenCorpora dictionary,
 * for the case each construction actually governs: `из` takes the genitive, `с`
 * the instrumental, `двух` the genitive plural, and adjectives agree in case,
 * number, gender and animacy. Nothing was hand-written.
 *
 * The generator refused cases rather than emit doubtful ones — 333 that were
 * already reachable without any fold, 155 whose lemmas were ambiguous, 53 whose
 * name belonged to more than one record. A positive that could pass without
 * lemmatization would have flattered the result, and it is precisely what the
 * old corpus was full of.
 *
 * The positives are measured and printed, not gated, because they are known to
 * be partly unimplemented and the remaining causes are named below. The
 * negatives ARE gated: they were built to stay unreachable even after folding,
 * so one of them resolving is a real defect rather than pending work.
 */
class RussianPhraseCorpusTest {
    private val floor = CatalogSearch(russianPhraseCatalog)
    private val identification =
        TextIdentification(floor) { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) }

    private fun resolve(query: String): List<String> = when (val outcome = identification.identify(query)) {
        is TextIdentificationOutcome.Floor -> outcome.items.map { it.canonicalItemId }
        is TextIdentificationOutcome.Items -> outcome.items.map { it.canonicalItemId }
        TextIdentificationOutcome.Empty -> emptyList()
    }

    private fun failures(cases: List<MealTextCase>): List<String> = cases.mapNotNull { case ->
        val actual = resolve(case.query)
        if (actual == case.expected) null else "[${case.kind}] \"${case.query}\" -> $actual, want ${case.expected}"
    }

    @Test
    fun `the corpus is large enough and measures both directions`() {
        assertTrue(russianPhraseCases.size >= 400, "corpus holds ${russianPhraseCases.size} cases")
        val negatives = russianPhraseCases.count { it.expected.isEmpty() }
        assertTrue(negatives >= 40, "only $negatives cases assert a non-answer")
    }

    @Test
    fun `every case is written in an oblique case, not the dictionary form`() {
        // The property that makes this corpus worth having, asserted rather than
        // assumed. If a regeneration ever started emitting nominatives again it
        // would raise the pass rate and measure nothing, which is the exact
        // failure being corrected here.
        val dictionaryForms = russianPhraseCases.filter { case ->
            case.expected.isNotEmpty() &&
                russianPhraseCatalog.any { record ->
                    record.canonicalItemId in case.expected &&
                        (record.displayName == case.query || case.query in record.alternateNames)
                }
        }

        assertEquals(emptyList(), dictionaryForms.map { it.query }.take(10))
    }

    @Test
    fun `nothing is invented for Russian text that names no food`() {
        // Gated, not measured. These were built to be beyond the fuzzy tier's
        // reach as well as the lemma table's, so they stay negative as the fold
        // improves. A hit here is a wrong food, which is worse than a miss.
        assertEquals(emptyList(), failures(russianPhraseCases.filter { it.expected.isEmpty() }))
    }

    @Test
    fun `phrases that exclude a food are counted apart from phrases that name one`() {
        // A defect in the corpus, not in the matcher, and it is worth stating
        // rather than quietly filtering.
        //
        // 58 of the generated positives open with `без` — "without" — and expect
        // the food to resolve: `без гречневой муки` expects buckwheat flour. The
        // generator treated `без` as scaffolding that governs the genitive,
        // which is true of the grammar and backwards for a food diary. A person
        // writing "without buckwheat flour" is naming what they did not eat, and
        // logging it is the opposite of what they asked for.
        //
        // Left in the measurement they would flatter nothing and confuse
        // everything: they are counted as failures today, and they would count
        // as passes the moment the fold got good enough to resolve them, which
        // would make the number go up as the behaviour got worse.
        //
        // Negation is unimplemented — there is no exclusion handling anywhere in
        // the parser — so this is measured and named, not gated. What it must
        // not do is silently disappear.
        val excluding = russianPhraseCases.filter { it.expected.isNotEmpty() && it.query.startsWith("без ") }
        assertTrue(excluding.isNotEmpty(), "the exclusion cases must not silently empty themselves")

        val wouldLogAnExcludedFood = excluding.count { resolve(it.query).isNotEmpty() }
        println(
            "exclusion phrases (`без …`): ${excluding.size} cases, " +
                "$wouldLogAnExcludedFood would log a food the user said they did not eat",
        )
    }

    @Test
    fun `inflected Russian support is reported per kind, not gated`() {
        // Printed per kind because the kinds fail for different, known reasons,
        // and one number would hide which.
        //
        // Single: the case ending alone. This is what the lemma fold addresses.
        // Quantity: the oblique numerals двух/трёх/пяти are not in
        //   QUANTITY_WORDS, and isQuantityToken does not consult the lemma
        //   table, so the counting word survives as a token no record satisfies.
        // Two-item: the above, plus prepositions and verbs (из, без, ел, съел,
        //   добавил) which are neither quantity words nor separators.
        val summary = russianPhraseCases
            .filter { it.expected.isNotEmpty() && !it.query.startsWith("без ") }
            .groupBy { it.kind }
            .toSortedMap()
            .map { (kind, cases) ->
                val broken = failures(cases)
                "$kind: ${cases.size - broken.size}/${cases.size}"
            }

        println("inflected Russian support -> ${summary.joinToString(", ")}")
        assertTrue(
            russianPhraseCases.any { it.expected.isNotEmpty() },
            "the measured set must not silently empty itself",
        )
    }
}
