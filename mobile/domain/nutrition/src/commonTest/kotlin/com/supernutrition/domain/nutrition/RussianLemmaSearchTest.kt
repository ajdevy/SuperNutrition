package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Russian case forms, folded inside token comparison.
 *
 * Russian declines through six cases, so every record in the catalog is spelled
 * the one way nobody types. `курица` is stored; `курицу`, `курицы` and `курицей`
 * are what people write.
 *
 * The case that decided the design is `курицы`. It is the genitive, and so it is
 * the form in every phrase shaped like "200 граммов курицы" — and it was already
 * a literal token of a stored synonym, `яйцо курицы`, hen's egg. It therefore
 * matched at TOKEN_SUBSET and returned **eggs** to someone asking for chicken.
 * Both the egg record and the chicken record cover one word out of two, and the
 * tie fell to whichever canonical id sorted first, which is `01123`, an egg.
 *
 * That is why the fold lives in token comparison rather than in a tier of its
 * own. A tier placed below TOKEN_SUBSET is never reached when TOKEN_SUBSET has
 * already matched, so it cannot correct a wrong match — only add a late one.
 * Folding inside the comparison brings the record actually named `курица` into
 * the same tier, where covering a whole name beats covering half a longer one.
 *
 * PREFIX deliberately does not fold, and the prefix cases below are what keep it
 * that way: a half-typed word is a prefix of a stored form and not of its lemma.
 */
class RussianLemmaSearchTest {
    private val real = CatalogSearch(generatedCatalog)

    private fun names(query: String): List<String> = real.search(query).map { it.displayName }

    private fun firstName(query: String): String? = real.search(query).firstOrNull()?.displayName

    @Test
    fun `the genitive of chicken no longer answers with eggs`() {
        // The defect, named. Not a miss — a confident, fluent, wrong food, which
        // is FM-1 exactly, and it was reachable by typing the commonest phrase
        // shape in the language.
        for (query in listOf("курицы", "200 граммов курицы", "курицу", "курицей")) {
            val answer = firstName(query)
            assertTrue(
                answer != null && answer.startsWith("Chicken"),
                "\"$query\" answered $answer",
            )
        }
    }

    @Test
    fun `eggs stay reachable by the name that means eggs`() {
        // The other half, and the reason the fix is a ranking change rather than
        // a removal: `яйцо курицы` is a real synonym for a real record and must
        // keep working. Ranking the chicken above it is correct; hiding it is
        // not.
        for (query in listOf("яйцо", "яйцо курицы", "яйца")) {
            val answer = firstName(query)
            assertTrue(answer != null && answer.startsWith("Egg"), "\"$query\" answered $answer")
        }
    }

    @Test
    fun `an inflected query reaches the nominative the catalog stores`() {
        for (typed in listOf("курицу", "курицы", "курицей", "свинины", "свинину", "моркови", "морковью")) {
            assertTrue(real.search(typed).isNotEmpty(), "\"$typed\" reached no record")
        }
    }

    @Test
    fun `an inflected query reaches the same record its nominative does`() {
        // Reaching *a* record is not the point — reaching the right one is.
        for ((inflected, nominative) in listOf("курицу" to "курица", "свинины" to "свинина", "моркови" to "морковь")) {
            assertEquals(
                firstName(nominative),
                firstName(inflected),
                "\"$inflected\" and \"$nominative\" are one word and must answer alike",
            )
        }
    }

    @Test
    fun `a half-typed word still matches on prefix`() {
        // The regression the ordering avoids, stated so it cannot be undone by
        // accident. `брюссельска` is a prefix of the stored `брюссельская` and
        // is not a prefix of its lemma `брюссельский` — they part at the
        // eleventh character. A folded index would take that away mid-keystroke.
        for (partial in listOf("брюссельска", "брюссельс", "морков", "свинин")) {
            assertTrue(real.search(partial).isNotEmpty(), "\"$partial\" lost its prefix match")
        }
    }

    @Test
    fun `a stronger tier is never given up for a fold`() {
        // The tier loop returns the first non-empty tier outright, so a mistake
        // here would not fail loudly — it would quietly return a different food.
        for (exact in listOf("морковь", "свинина", "курица")) {
            val hits = real.search(exact)
            assertTrue(hits.isNotEmpty(), "\"$exact\" reached no record")
            assertEquals(MatchTier.EXACT, hits.first().strength.tier, "\"$exact\" stopped matching exactly")
        }
    }

    @Test
    fun `ё and е spellings of one word reach one record`() {
        // The catalog stores `свёкла`; a keyboard and a habit produce `свекла`.
        // normalizeForSearch folds Latin diacritics and leaves Cyrillic alone,
        // so these are two strings to the matcher and one word to everyone else.
        // The lemma table carries both spellings, which settles it without a
        // second mechanism — but only because the fold reaches the stored side.
        assertEquals(names("свёкла"), names("свекла"), "the two spellings answered differently")
        assertTrue(names("свекла").isNotEmpty(), "neither spelling reached a record")
    }

    @Test
    fun `folding never makes a counting or joining word name a food`() {
        // FM-1, re-checked. The fold gives every token a second spelling to
        // match on, which is one more chance for a word that names no food to
        // find one — `граммов` folding to `грамм` is precisely that shape.
        for (query in listOf("и", "с", "со", "два", "две", "граммов", "граммами", "ломтик")) {
            assertEquals(emptyList(), real.search(query), "\"$query\" reached a record")
        }
    }

    @Test
    fun `a word the table does not know is left exactly as typed`() {
        // The fold defaults to the token itself rather than to nothing, which is
        // load-bearing: a lemma is never a key of its own table, so the
        // nominative sitting in the catalog resolves through that default and no
        // other. It also means English and Spanish change nothing.
        assertTrue(sharesLemma("broccoli", "broccoli"), "an unknown token stopped equalling itself")
        assertTrue(!sharesLemma("broccoli", "butter"), "two unrelated tokens folded together")
        assertTrue(sharesLemma("курицу", "курица"), "an inflected form did not reach its lemma")
        assertTrue(!sharesLemma("курицу", "свинина"), "two different foods folded together")
    }

    @Test
    fun `covering a whole name outranks mentioning it inside a longer one`() {
        // The ranking rule the fix depends on, isolated from the real catalog so
        // the reason is legible. Without the lemma clause in coverage both
        // records score zero, the tie falls to canonical id, and the qualified
        // variant wins by alphabet — which is how eggs won in the first place.
        val floor = CatalogSearch(
            listOf(
                CatalogRecord("food:egg", "Egg", listOf("яйцо курицы")),
                CatalogRecord("food:chicken", "Chicken", listOf("курица", "мясо курицы")),
            ),
        )

        assertEquals(listOf("food:chicken", "food:egg"), floor.search("курицы").map { it.canonicalItemId })
        assertEquals(listOf("food:egg"), floor.search("яйцо").map { it.canonicalItemId })
    }
}
