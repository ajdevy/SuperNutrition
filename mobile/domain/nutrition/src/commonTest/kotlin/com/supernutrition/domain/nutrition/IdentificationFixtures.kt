package com.supernutrition.domain.nutrition

/**
 * The identification fixture set, declaring expected outcomes in both
 * directions.
 *
 * A suite built only from resolvable cases rewards guessing: an engine that
 * answered everything confidently would pass it completely, and the failure that
 * matters most — a plausible wrong food, FM-1 — would never be measured. So
 * every entry here declares either the canonical id it must reach or that it
 * must not resolve at all, and over-confidence fails a test rather than passing
 * a suite that never asked.
 *
 * The must-not-resolve entries are not padding. Several are real defects this
 * catalog has already had: `ribs` resolved to white rice through the Latin-script
 * entry for рис, and `draw` reached raw through the same over-wide fuzzy
 * allowance. They are here so that fixing them once is permanent.
 */
data class IdentificationFixture(
    val query: String,
    /** The canonical id this must reach, or null when it must not resolve. */
    val expected: String?,
    val because: String,
)

object IdentificationFixtures {
    val entries: List<IdentificationFixture> = listOf(
        // --- must resolve -----------------------------------------------------
        IdentificationFixture("boiled egg", "food:egg-boiled", "the plain case"),
        IdentificationFixture("two boiled eggs", "food:egg-boiled", "spelled-out quantity"),
        IdentificationFixture("2 boiled eggs", "food:egg-boiled", "digit quantity"),
        IdentificationFixture("200g avocado", "food:avocado-raw", "digit glued to its unit"),
        IdentificationFixture("500 mg rice", "food:rice-white-cooked", "spaced unit"),
        IdentificationFixture("boiled eggs", "food:egg-boiled", "plural is morphology, not a near miss"),
        IdentificationFixture("a slice of rye toast", "food:bread-rye", "the spec success signal, second half"),
        IdentificationFixture("rye bread", "food:bread-rye", "the same record by another name"),
        IdentificationFixture("aubergine", "food:eggplant-raw", "regional English"),
        IdentificationFixture("berenjena", "food:eggplant-raw", "Spanish alternate name, matched exactly"),
        IdentificationFixture("aguacate", "food:avocado-raw", "Spanish regional variant"),
        IdentificationFixture("palta", "food:avocado-raw", "the other Spanish regional variant"),
        IdentificationFixture("курица", "food:chicken-breast-raw", "Russian nominative"),
        IdentificationFixture("курицы", "food:chicken-breast-raw", "Russian oblique form"),
        IdentificationFixture("kuritsa", "food:chicken-breast-raw", "Latin-script entry of a Cyrillic record"),
        IdentificationFixture("raw chicken breast", "food:chicken-breast-raw", "preparation the catalog carries"),
        IdentificationFixture(
            "grilled chicken breast",
            "food:chicken-breast-raw",
            "preparation it does not, reported not dropped",
        ),

        // --- must not resolve -------------------------------------------------
        IdentificationFixture("ribs", null, "reached white rice through ris, the Latin entry for рис"),
        IdentificationFixture("draw", null, "reached raw through the same over-wide fuzzy allowance"),
        IdentificationFixture("risk", null, "one edit from ris and not a food in this catalog"),
        IdentificationFixture("wildebeest", null, "a real food, absent from this catalog"),
        IdentificationFixture("asdfgh", null, "not a word"),
        IdentificationFixture("two slices of", null, "quantity words with nothing to quantify"),
        IdentificationFixture("and", null, "a separator alone"),
        IdentificationFixture("", null, "nothing typed"),
    )

    val mustResolve: List<IdentificationFixture> get() = entries.filter { it.expected != null }
    val mustNotResolve: List<IdentificationFixture> get() = entries.filter { it.expected == null }
}
