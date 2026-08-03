package com.supernutrition.domain.nutrition

// GENERATED FILE. Produced by scripts/generate-russian-scaffolding.py from the
// seed lemmas named in that script, inflected against the OpenCorpora dictionary.
// Do not edit by hand: the next run of the generator overwrites every line.
//
// PROVENANCE
//   inflection   OpenCorpora dictionary via pymorphy3 2.0.6
//                (pymorphy3-dicts-ru 2.4.417150.4580142, source opencorpora.org 0.92 rev 417150)
//   word list    5 scaffolding lemmas named in the generator, not read from any
//                catalog -- a word that frames a meal is by definition not a food word
//   refusals     checked against 3519 GeneratedCatalog records, 260 russianPhraseCatalog
//                records (3505 distinct name tokens), 13284 russianFormToLemma keys,
//                and 142 words of existing QUANTITY_WORDS/WEAK_SEPARATORS/CONJUNCTIONS
//
// REFUSED, RECORDED HERE RATHER THAN SILENTLY DROPPED -- 1 of 55 generated forms.
//   из (of из) -- catalog: GeneratedCatalog.kt names 2: `мука из проса`, `мука из сорго`
//   0 by negation, 1 by catalog collision, 0 by food paradigm,
//   0 by existing vocabulary. A refused form is never resolved to a best guess:
//   the word simply stays in the query as typed, which is today's behaviour.
//
// LICENCE -- ONE OBLIGATION, AND DELIBERATELY NOT THREE.
//   * The inflected forms are derived from the OpenCorpora dictionary. The
//     pymorphy3-dicts-ru package declares MIT in its own metadata, while
//     OpenCorpora publishes the dictionary under CC BY-SA. Those are not the
//     same obligation, and if share-alike applies it attaches to THIS TABLE,
//     not to the generator that wrote it. Recorded here, unresolved: it is a
//     question for counsel before release, not one for a build script.
//   * No AGROVOC and no Wikidata credit applies to this file, unlike
//     GeneratedRussianForms.kt. The seeds are ordinary Russian function words
//     chosen in the generator, not terms taken from the synonym table, and
//     claiming FAO attribution for words FAO never supplied would be wrong in
//     the direction that matters.

/**
 * Russian words that frame a meal description without naming any part of it.
 *
 * 54 surface forms over 5 lemmas: `из`, `есть`, `съесть`, `добавить`, `приготовить`.
 * A user writes `съел локву` and `приготовил соевую муку`; the food is the
 * object, and the verb is furniture the catalog has no record for.
 *
 * A SEPARATE TABLE FROM [russianFormToLemma], AND THAT IS THE WHOLE POINT.
 * That table is a fold the matcher applies to BOTH SIDES of every token
 * comparison, so anything in it can change which food a query reaches. A verb
 * form that also belonged to some food's paradigm would silently redirect a
 * food query, and the redirect would be invisible precisely because both sides
 * folded the same way. This set is consulted only by the stripping logic, so a
 * word here can remove itself from a query and can never move the query to a
 * different record. Do not merge the two tables: the property is destroyed by
 * the merge itself, and nothing downstream fails loudly enough to notice.
 *
 * Not a duplicate vocabulary either, which is the other half of the rule.
 * `с` and `со` frame a meal exactly as these do and are deliberately ABSENT:
 * they are already [WEAK_SEPARATORS] and already reached through the predicate
 * that owns them, and a second copy here is the drift AGENTS.md warns about.
 * The generator refuses them by rule rather than by comment, along with any
 * form the catalogs already use in a food name -- 1 refusal in total, all
 * named in the header above.
 *
 * Negations are refused outright and are not an oversight. `без хурмы`
 * resolves to nothing today, which is a harmless non-answer; strip the `без`
 * and it resolves to persimmon, a food the user explicitly did not eat. The
 * suite would score that as a pass, so the number would rise while the
 * behaviour got worse. Recognising negation is a different job, and this table
 * must not pretend to do it.
 *
 * Participles and gerunds are absent for a related reason: `приготовленный`
 * and `съеденный` describe the state of the food, and preparation words are
 * reported as unmatched rather than discarded, because nutrition per 100 g
 * moves between raw and cooked.
 *
 * Every entry is lowercase and bare. A form spelled with `ё` appears alongside
 * its `е` spelling, because [normalizeForSearch] folds Latin diacritics and
 * leaves Cyrillic alone; none of today's forms has one, so that rule added no entries.
 *
 * Regenerate with scripts/generate-russian-scaffolding.py, which is the only
 * writer. It re-derives every refusal from the catalogs on each run.
 */
internal val russianScaffoldingForms: Set<String> = buildRussianScaffoldingForms()

/**
 * Expands the stored frames into the flat set the stripping logic uses.
 *
 * The data is stored one lexeme per string, with a comment naming the lemma it
 * came from, because that is where a reader checks a refusal. Every word in a
 * string is a form that survived; the lemma is NOT stored as the first word the
 * way GeneratedRussianForms.kt stores it, because here the lemma is itself a
 * candidate and may have been refused, and a reader that added it back would
 * reinstate exactly what the generator threw out.
 */
private fun buildRussianScaffoldingForms(): Set<String> {
    val forms = mutableSetOf<String>()
    forms.addFrames(RussianScaffoldingFrames0.frames)
    return forms
}

private fun MutableSet<String>.addFrames(frames: List<String>) {
    for (frame in frames) {
        for (word in frame.split(' ')) {
            add(word)
        }
    }
}

internal object RussianScaffoldingFrames0 {
    val frames: List<String> = listOf(
        // из -- preposition: names the material a dish was made FROM.
        // 1 of 2 forms; refused: из.
        "изо",
        // есть -- verb: to eat, imperfective -- `ел гречневую муку`.
        // 12 of 12 forms.
        "едим едите едят ел ела ели ело ем ест есть ешь ешьте",
        // съесть -- verb: to eat up, perfective -- `съел локву`.
        // 13 of 13 forms.
        "съедим съедимте съедите съедят съел съела съели съело съем съест съесть съешь съешьте",
        // добавить -- verb: to add -- `добавил папайю`.
        // 14 of 14 forms.
        "добавил добавила добавили добавило добавим добавимте добавит добавите добавить добавишь добавлю добавь",
        "добавьте добавят",
        // приготовить -- verb: to cook or prepare -- `приготовил соевую муку`.
        // 14 of 14 forms.
        "приготовил приготовила приготовили приготовило приготовим приготовимте приготовит приготовите приготовить",
        "приготовишь приготовлю приготовь приготовьте приготовят",
    )
}
