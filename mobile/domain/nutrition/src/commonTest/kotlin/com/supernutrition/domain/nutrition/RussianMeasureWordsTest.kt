package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Russian measure words found missing during a slang/edge-case sweep.
 *
 * `горсть` (a handful), `кусочек` (a little piece, the diminutive of the
 * already-covered `кусок`), and `долька`/`половинка` (a segment and a
 * half, the forms a citrus wedge or a fruit half is actually described by)
 * are ordinary ways to quantify a food that were simply absent from
 * [QUANTITY_WORDS]. Without them "кусочек сыра" stripped nothing, left
 * `кусочек` as a token no record satisfies, and the whole fragment failed —
 * the same shape the spec's own success signal already named for "two
 * boiled eggs".
 *
 * None of these forms fold through [russianFormToLemma] onto an existing
 * entry: that table is generated from catalog words plus the Cyrillic
 * quantity words already in this file, and none of these four is a catalog
 * word. Unlike `двух` -> `два` elsewhere in this file, there is no lemma
 * shortcut, so the case forms actually typed are listed explicitly, the way
 * the original Russian entries in [QUANTITY_WORDS] already are.
 */
class RussianMeasureWordsTest {
    private val search = CatalogSearch(generatedCatalog)

    private fun top(query: String): String? = search.search(query).firstOrNull()?.displayName

    @Test
    fun `a measure word that was missing no longer blocks the food behind it`() {
        assertEquals("Cheese, blue", top("кусочек сыра"))
        val nuts = top("горсть орехов")
        assertTrue(nuts?.startsWith("Nuts") == true, "\"горсть орехов\" answered $nuts")
        val persimmon = top("долька хурмы")
        assertTrue(persimmon?.startsWith("Persimmons") == true, "\"долька хурмы\" answered $persimmon")
    }

    @Test
    fun `other case forms of the same words are covered too, not only the one probed`() {
        for (query in listOf("кусочка сыра", "кусочку сыра", "горсти орехов", "дольку хурмы")) {
            assertTrue(search.search(query).isNotEmpty(), "\"$query\" reached no record")
        }
    }

    @Test
    fun `a measure word composes with a numeral from the same edge`() {
        // две дольки лимона: a numeral and the new measure word share the
        // leading edge, the same composition property already proven for
        // scaffolding verbs plus quantities.
        assertTrue(top("две дольки лимона")?.startsWith("Lemon") == true, "answered ${top("две дольки лимона")}")
    }

    @Test
    fun `none of the four measure words is itself a food this catalog carries`() {
        // The collision check performed before adding them, restated as a
        // test: a measure word must never itself be answerable, or it would
        // stop being safe to strip.
        for (query in listOf("горсть", "горсти", "кусочек", "кусочка", "долька", "дольки", "половинка", "половинки")) {
            assertEquals(emptyList(), search.search(query), "\"$query\" reached a record on its own")
        }
    }
}
