#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-russian-lemmas.py -- BUILD-TIME generator for the Russian surface-form
to lemma table used by deterministic catalog search.

This script runs at build time only. It never runs on device, nothing it touches
is a runtime dependency, and its single output is one deterministic Kotlin file:

    mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/
    nutrition/GeneratedRussianForms.kt

    THIRD-PARTY DEPENDENCY, declared here rather than assumed:
        pymorphy3 (MIT) + pymorphy3-dicts-ru, which packages the OpenCorpora
        dictionary. Python 3.9+. Install it into a throwaway environment; it
        must never appear under mobile/ or in a device build.


WHY THIS EXISTS
===============
Russian is an inflecting language and the synonym table is not. The table holds
`луциан`, `свекла`, `морские окуни` -- one surface form each -- so a single-word
lookup works and everything else does not. `двух морских окуней` names the same
fish as `морские окуни`, but no tier in CatalogSearch can see that: EXACT and
TOKEN_SUBSET need the same string, PREFIX needs the record's token to start with
the query's (`морские`.startsWith(`морских`) is false), and the fuzzy tier is
deliberately bounded at edit distance one for tokens this short, which `морской`
/ `морские` already exceeds. The language matrix declares Russian as full
lemmatization across cases; this table is the data half of that.

The table is a FOLD, not a query rewrite. Its keys include the catalog's own
surface forms, so the intended use is to push both sides -- query tokens and
record tokens -- through the same map before comparing them. Folding only the
query would leave `морской` (a lemma) against `морские` (a record) and match
nothing, which is the same miss one step further along.


WHAT IS IN SCOPE, AND WHY IT IS THIS SMALL
==========================================
Only words that already earn their place somewhere the product ships:

  1. every Cyrillic word occurring in a Russian display name or match term in
     food-synonyms.json, and
  2. every Cyrillic word in QUANTITY_WORDS in TextFragments.kt, read out of that
     file rather than copied, because a second copy of that vocabulary is
     exactly the drift AGENTS.md warns about.

Nothing else. This is not a Russian lemmatizer and must not grow into one: a
form that no shipped record and no quantity word can reach has nothing to
resolve to, and every extra form is another chance to fold an unrelated word
onto a food.


COLLISIONS ARE EXCLUDED, NEVER RESOLVED
=======================================
Some surface forms belong to two lemmas that are both in scope. `аниса` is the
genitive of `анис` and also the nominative of a different dictionary entry; a
homograph like that has no correct answer here. Picking the higher-scoring
analysis would be picking silently, and a wrong lemma does not degrade the
search -- it sends a Russian word to the wrong food, confidently. Every such
form is dropped from the table and counted in the report instead. Dropping it
costs nothing worse than today's behaviour: the word simply stays as typed.


HOW ё IS HANDLED
================
`normalizeForSearch` folds Latin diacritics and leaves Cyrillic alone, so `ё`
and `е` are different characters to the matcher. AGROVOC writes `свекла`;
OpenCorpora writes `свёкла`; users type both. Lemmas are therefore emitted in
their `е` spelling, and any form that contains `ё` is emitted twice -- once as
written and once folded -- so both spellings reach the same lemma.


LICENCE -- RECORDED, NOT RESOLVED
=================================
The forms are generated from the OpenCorpora dictionary shipped inside
pymorphy3-dicts-ru. That package's own metadata declares MIT, while OpenCorpora
publishes its dictionary under CC BY-SA. Those are not the same obligation, and
share-alike -- if it applies -- attaches to the generated table, not to this
script. The generator records the exact dictionary revision it used in the
emitted file's header and stops there. It is a question for counsel.

The word list the table is keyed on carries its own separate terms: AGROVOC
(CC-BY IGO 3.0, (c) FAO, attribution is a licence condition) and Wikidata (CC0).
See ATTRIBUTION.md beside the synonym table. The three licences are recorded
separately and must never be merged into one blended credit.
"""

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_JSON = REPO_ROOT / "mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json"
QUANTITY_SOURCE = (
    REPO_ROOT / "mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/TextFragments.kt"
)
OUTPUT_KT = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/GeneratedRussianForms.kt"
)

MAX_LINE = 120
CHUNK_LINE_BUDGET = 300
INDENT = " " * 8

CYRILLIC = set("абвгдежзийклмнопрстуфхцчшщъыьэюяё")

# The shortest word worth inflecting. Everything below it in QUANTITY_WORDS is an
# abbreviation -- г, кг, мл, шт, ст -- and pymorphy3 answers for abbreviations by
# guessing, which is how a unit symbol acquires a noun paradigm it does not have.
MIN_WORD_LENGTH = 3

# Parts of speech a food or an amount can be named by. A verb, an adverb or a
# preposition in a record name is a word of the name, not a word that inflects
# with it, and expanding those paradigms adds forms nothing can ever resolve to.
INFLECTED_PARTS = frozenset({"NOUN", "ADJF", "PRTF", "NUMR"})

# Forms inside an otherwise wanted paradigm that nobody types when naming food:
# short adjectives (`красен`), comparatives (`краснее`), short participles
# (`сварен`). They are grammatically real and practically noise.
SKIPPED_PARTS = frozenset({"ADJS", "COMP", "PRTS"})

# Seeds that are not the word the dictionary thinks they are. `пол` is in
# QUANTITY_WORDS as the bound half- of `полкило` and `пол-литра`; OpenCorpora
# knows it as the noun meaning floor, and inflecting it fills the quantity
# vocabulary with `полы`, `полов` and `поле`. One entry, named rather than
# pattern-matched, because the next one will be wrong in its own way.
EXCLUDED_SEEDS = frozenset({"пол"})


class GeneratorError(Exception):
    """A failure the operator has to fix, reported instead of an empty table."""


def load_analyzer():
    """Import pymorphy3 late, so --help works without it, and fail loudly.

    An absent dependency must not degrade into an empty table: an empty map has
    the same type and the same call sites as a working one, so the failure would
    surface as `двух морских окуней` quietly not resolving, months later.
    """
    try:
        import pymorphy3
    except ImportError as error:
        raise GeneratorError(
            "pymorphy3 is not installed, so no Russian forms can be generated.\n"
            "  This generator refuses to emit an empty table -- an empty table is\n"
            "  indistinguishable from a working one at the call site.\n"
            "  It is a BUILD-TIME dependency and must never reach mobile/ or a device:\n"
            "      python3 -m venv /tmp/ru-lemmas\n"
            "      /tmp/ru-lemmas/bin/pip install pymorphy3\n"
            "      /tmp/ru-lemmas/bin/python scripts/generate-russian-lemmas.py"
        ) from error
    return pymorphy3.MorphAnalyzer(), pymorphy3


def dictionary_provenance(analyzer):
    """The exact OpenCorpora revision behind the forms, for the file header."""
    meta = dict(getattr(analyzer.dictionary, "meta", {}) or {})
    return {
        "source": str(meta.get("source", "unknown")),
        "source_version": str(meta.get("source_version", "unknown")),
        "source_revision": str(meta.get("source_revision", "unknown")),
        "format_version": str(meta.get("format_version", "unknown")),
    }


# ---------------------------------------------------------------------------
# Reading the two sources of scope.
# ---------------------------------------------------------------------------


def normalize(value):
    """Lowercase and fold ё to е, the spelling the table is canonical in."""
    return value.lower().replace("ё", "е")


def tokenize(value):
    """Split on anything that is not a letter or a digit.

    Mirrors `tokenizeNormalized`: the table is keyed on the tokens the matcher
    will actually hand it, so a term this splits differently is a term the map
    can never be consulted for.
    """
    tokens = []
    current = []
    for character in value.lower():
        if character.isalnum():
            current.append(character)
        elif current:
            tokens.append("".join(current))
            current = []
    if current:
        tokens.append("".join(current))
    return tokens


def is_cyrillic(word):
    return bool(word) and all(character in CYRILLIC for character in word)


def load_catalog_words(path):
    """Every Cyrillic word in a Russian display name or match term, ё folded.

    Folded on the way in, not on the way out. Fifteen terms in the table are
    spelled with ё -- `мёд`, `свёкла`, `цыплёнок` -- and leaving them that way
    would silently disable the citation-form tie-break for exactly those words,
    because the lemma it compares against is folded and they would not be.
    """
    document = json.loads(path.read_text(encoding="utf-8"))
    concepts = document.get("concepts") or []
    if not concepts:
        raise GeneratorError(f"{path} declares no concepts; refusing to emit a table from nothing")
    words = set()
    terms = 0
    for concept in concepts:
        russian = concept.get("ru") or {}
        names = ([russian["display"]] if russian.get("display") else []) + list(russian.get("match") or [])
        terms += len(names)
        for name in names:
            words.update(normalize(token) for token in tokenize(name) if is_cyrillic(token))
    return words, terms, document.get("provenance", {})


def load_quantity_words(path):
    """The Cyrillic members of QUANTITY_WORDS, read out of TextFragments.kt.

    Parsed rather than copied. A duplicated quantity vocabulary drifted within
    days once already in this repository and produced user-visible wrong
    answers, so this fails hard if the declaration moves rather than silently
    generating a table that has quietly lost half its scope.
    """
    source = path.read_text(encoding="utf-8")
    block = re.search(r"val QUANTITY_WORDS: Set<String> = setOf\((.*?)\n\)", source, re.S)
    if block is None:
        raise GeneratorError(
            f"QUANTITY_WORDS was not found in {path}. It moved or was renamed; this generator "
            "reads it rather than keeping a second copy, so fix the pattern instead of pasting the list."
        )
    words = {normalize(word) for word in re.findall(r'"([^"]+)"', block.group(1))}
    russian = {word for word in words if is_cyrillic(word)}
    if not russian:
        raise GeneratorError(f"QUANTITY_WORDS in {path} holds no Russian entries; the scope would be half empty")
    return russian, len(words)


# ---------------------------------------------------------------------------
# Generating the forms.
# ---------------------------------------------------------------------------


def analyses_of(analyzer, word):
    """Dictionary analyses of a seed word: best-scoring, and citation-form first.

    `is_known` keeps out pymorphy3's guesser, which will happily invent a noun
    paradigm for a transliterated species name. Restricting to the top score
    keeps out the long tail of rare readings whose paradigm would otherwise
    collide with the real one and take the real one down with it.

    Two tie-breaks apply, in this order, and both are categorical rules rather
    than probability judgements.

    An adjective reading beats a participle reading of the same word.
    pymorphy3 normalizes a participle to its infinitive, so `замороженные`
    analysed as a participle lemmatizes to the verb `заморозить` -- a word no
    food record can contain. Where the dictionary also offers the adjective
    `замороженный`, that is the form a food name is actually built from. Words
    that have only the participle reading keep it: the lemma is then an
    infinitive nobody types, which costs nothing, because this table is a fold
    applied to both sides and its values are keys, not answers.

    A reading whose lemma is the stored word itself beats one whose lemma is
    some other word, but only when the tied readings agree on part of speech.
    `арека` is the nominative of `арека` and the genitive plural of a different
    word `арек`, scored identically; generating both paradigms would collide
    them and leave the palm with no forms at all, and the synonym table stores
    citation forms -- display names and match terms are how a lexicographer
    writes a word, not how a sentence inflects it -- so the reading in which
    the stored word IS the lemma is the one the table meant. Across parts of
    speech that inference fails: `мороженое` is a noun (ice cream) and an
    adjective (`мороженый`), and preferring either would be choosing a food.
    Mixed-POS ties therefore get no preference and fall to the collision rule,
    which drops them.
    """
    parses = [
        parse
        for parse in analyzer.parse(word)
        if parse.is_known and str(parse.tag.POS) in INFLECTED_PARTS
    ]
    if not parses:
        return []
    best = max(parse.score for parse in parses)
    parses = [parse for parse in parses if parse.score >= best]
    adjectives = [parse for parse in parses if str(parse.tag.POS) == "ADJF"]
    if adjectives and any(str(parse.tag.POS) == "PRTF" for parse in parses):
        parses = adjectives
    if len({str(parse.tag.POS) for parse in parses}) == 1:
        citation = [parse for parse in parses if normalize(parse.normal_form) == word]
        if citation:
            parses = citation
    chosen = []
    seen = set()
    for parse in parses:
        if parse.normal_form in seen:
            continue
        seen.add(parse.normal_form)
        chosen.append(parse)
    return chosen


def keys_for(word):
    """The spellings of one form that a query could arrive in.

    A form written with ё is emitted twice, because the matcher folds Latin
    diacritics and leaves Cyrillic alone: `свёклы` and `свеклы` are two
    different strings to it and one word to everybody else.
    """
    folded = normalize(word)
    if not is_cyrillic(folded):
        return []
    if word.lower() == folded:
        return [folded]
    return [folded, word.lower()]


def build_form_index(analyzer, seeds):
    """form -> the set of in-scope lemmas that form belongs to."""
    index = {}
    lemmas = set()
    unanalysed = []
    for word in sorted(seeds):
        if len(word) < MIN_WORD_LENGTH or word in EXCLUDED_SEEDS:
            continue
        parses = analyses_of(analyzer, word)
        if not parses:
            unanalysed.append(word)
            continue
        for parse in parses:
            lemma = normalize(parse.normal_form)
            if not is_cyrillic(lemma):
                continue
            lemmas.add(lemma)
            for form in parse.lexeme:
                if str(form.tag.POS) in SKIPPED_PARTS:
                    continue
                for key in keys_for(form.word):
                    index.setdefault(key, set()).add(lemma)
    return index, lemmas, unanalysed


def resolve_collisions(index):
    """Split the index into an unambiguous table and the forms that are not.

    A form claimed by two lemmas is dropped outright. The alternative -- taking
    the more probable analysis -- is a silent choice between two foods, and the
    one this codebase has already learned to distrust: confident, plausible and
    wrong is worse than absent, because absent is visible.
    """
    table = {}
    collisions = {}
    for form in sorted(index):
        lemmas = index[form]
        if len(lemmas) > 1:
            collisions[form] = sorted(lemmas)
        elif form != next(iter(lemmas)):
            table[form] = next(iter(lemmas))
    return table, collisions


def group_by_lemma(table):
    """lemma -> its forms, which is how the file stores them.

    One line per lemma instead of one line per form: the lemma is written once
    rather than repeated beside every one of its dozen forms, which is most of
    the file's size, and the grouped shape keeps the generated declarations to a
    few hundred string constants rather than thirteen thousand `to` calls.
    """
    grouped = {}
    for form, lemma in table.items():
        grouped.setdefault(lemma, []).append(form)
    return [(lemma, sorted(grouped[lemma])) for lemma in sorted(grouped)]


# ---------------------------------------------------------------------------
# Emitting Kotlin.
# ---------------------------------------------------------------------------


def kotlin_string(value):
    escaped = (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f'"{escaped}"'


def render_lexeme(lemma, forms):
    """One lexeme as `"lemma form form ..."` lines, each inside the line limit.

    A lexeme too long for one line continues on the next with the lemma
    repeated, which the reader in the emitted file handles without knowing: it
    reads the first word of every line as that line's lemma.
    """
    budget = MAX_LINE - len(INDENT) - len('"",')
    lines = []
    current = [lemma]
    width = len(lemma)
    for form in forms:
        if current[1:] and width + 1 + len(form) > budget:
            lines.append(f"{INDENT}{kotlin_string(' '.join(current))},")
            current = [lemma]
            width = len(lemma)
        current.append(form)
        width += 1 + len(form)
    if current[1:]:
        lines.append(f"{INDENT}{kotlin_string(' '.join(current))},")
    return lines


def chunk_blocks(blocks, budget):
    """Group rendered lexemes so no generated object grows unbounded.

    Two limits at once, the same pair MealTextCorpus.kt splits for: a JVM method
    may not exceed 64 KB of bytecode and one property holding every lexeme would
    put all of it in a single file initializer, and Detekt's LargeClass
    threshold applies to the declaration regardless of what the JVM would take.
    """
    chunks = []
    current = []
    current_lines = 0
    for block in blocks:
        if current and current_lines + len(block) > budget:
            chunks.append(current)
            current = []
            current_lines = 0
        current.append(block)
        current_lines += len(block)
    if current:
        chunks.append(current)
    return chunks


def header_lines(stats, provenance, dictionary):
    sources = {source.get("name", "?"): source for source in provenance.get("sources", [])}
    agrovoc = sources.get("AGROVOC Multilingual Thesaurus", {})
    return [
        "package com.supernutrition.domain.nutrition",
        "",
        "// GENERATED FILE. Produced by scripts/generate-russian-lemmas.py from the Russian",
        "// terms in mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json",
        "// and the Cyrillic entries of QUANTITY_WORDS in TextFragments.kt.",
        "// Do not edit by hand: the next run of the generator overwrites every line.",
        "//",
        "// PROVENANCE",
        f"//   inflection   OpenCorpora dictionary via pymorphy3 {dictionary['pymorphy3']}",
        f"//                (pymorphy3-dicts-ru {dictionary['package']}, source"
        f" {dictionary['source']} {dictionary['source_version']} rev {dictionary['source_revision']})",
        f"//   word list    AGROVOC concept scheme {agrovoc.get('version', 'unknown')}"
        f" plus Wikidata, via the synonym table",
        f"//                generated {provenance.get('generated_utc', 'unknown')}",
        f"//   scope        {stats['catalog_words']} catalog words + {stats['quantity_words']}"
        " Russian quantity words",
        "//",
        "// LICENCE -- THREE SEPARATE OBLIGATIONS, DELIBERATELY NOT MERGED.",
        "//   * The inflected forms are derived from the OpenCorpora dictionary. The",
        "//     pymorphy3-dicts-ru package declares MIT in its own metadata, while",
        "//     OpenCorpora publishes the dictionary under CC BY-SA. Those are not the",
        "//     same obligation, and if share-alike applies it attaches to THIS TABLE,",
        "//     not to the generator that wrote it. Recorded here, unresolved: it is a",
        "//     question for counsel before release, not one for a build script.",
        "//   * The Russian words the table is keyed on come from the AGROVOC",
        "//     Multilingual Thesaurus, (c) Food and Agriculture Organization of the",
        "//     United Nations (FAO), licensed under CC-BY IGO 3.0 --",
        "//     https://creativecommons.org/licenses/by/3.0/igo/. That credit is a",
        "//     licence condition, not a courtesy.",
        "//   * Colloquial and regional names come from Wikidata, CC0 1.0, which imposes",
        "//     no attribution requirement. See ATTRIBUTION.md beside the source JSON.",
        "",
    ]


def documentation_lines(stats):
    return [
        "/**",
        " * Russian inflected surface form to the lemma it belongs to.",
        " *",
        f" * {stats['entries']} forms over {stats['emitted_lemmas']} lemmas: every Cyrillic word in a Russian display",
        " * name or match term in the shipped synonym table, plus the Cyrillic members of",
        " * [QUANTITY_WORDS], and nothing else. A form that no record and no amount can",
        " * reach has nothing to resolve to, and every form beyond those is one more",
        " * chance to fold an unrelated word onto a food.",
        " *",
        " * Use it as a FOLD APPLIED TO BOTH SIDES, not as a query rewrite. The keys",
        " * include the catalog's own surface forms -- `морские` is here as surely as",
        " * `морских` is -- because folding only the query leaves the lemma `морской`",
        " * against the record `морские`, which is the same miss one step further along.",
        " * A token the table does not know is left exactly as typed.",
        " *",
        " * Folding both sides cannot break an equality: this is a function, so tokens",
        " * that were equal stay equal. It is not equally free for the PREFIX tier, where",
        " * a half-typed token that is no form of anything can stop being a prefix of the",
        " * record token once that token folds -- `морски` prefixes `морские` and does not",
        " * prefix `морской`. Where the fold is applied is therefore a real choice.",
        " *",
        " * Why a table and not a rule: Russian case endings are not suffix arithmetic.",
        " * The fuzzy tier already refuses to bridge `морской` and `морские` on purpose,",
        " * because at that edit distance it would also bridge foods that are genuinely",
        f" * different. A further {stats['collisions']} surface forms are absent for that same reason:",
        " * each belongs to two in-scope lemmas at once -- `гранат` is a pomegranate under",
        " * either reading, `белые` is not -- and a lemmatization that guessed would not",
        " * degrade a search, it would send a Russian word to the wrong food. Their",
        " * absence costs nothing beyond today's behaviour, which is that the word stays",
        " * as typed.",
        " *",
        " * Spellings with `ё` appear alongside their `е` spelling, because",
        " * [normalizeForSearch] folds Latin diacritics and leaves Cyrillic alone, so the",
        " * two are different strings to the matcher and one word to everyone else.",
        " *",
        " * Regenerate with scripts/generate-russian-lemmas.py after any change to the",
        " * synonym table or to [QUANTITY_WORDS]; it reads both and is the only writer.",
        " */",
    ]


def render_file(lexemes, stats, provenance, dictionary):
    blocks = [render_lexeme(lemma, forms) for lemma, forms in lexemes]
    chunks = chunk_blocks(blocks, CHUNK_LINE_BUDGET)
    names = [f"GeneratedRussianLexemes{index}" for index in range(len(chunks))]

    lines = header_lines(stats, provenance, dictionary)
    lines += documentation_lines(stats)
    lines += ["internal val russianFormToLemma: Map<String, String> = buildRussianFormToLemma()", ""]
    lines += [
        "/**",
        " * Expands the stored lexemes into the flat lookup the matcher uses.",
        " *",
        " * The data is stored grouped -- one lemma and its forms per string -- because a",
        " * flat literal would write every lemma out once per form, and thirteen thousand",
        " * `to` calls in one file initializer is a JVM bytecode limit waiting to be hit.",
        " * A lexeme too wide for one line continues on the next with its lemma repeated,",
        " * so every line is self-describing: first word lemma, rest forms.",
        " */",
        "private fun buildRussianFormToLemma(): Map<String, String> {",
        "    val forms = mutableMapOf<String, String>()",
    ]
    lines += [f"    forms.putLexemes({name}.lexemes)" for name in names]
    lines += [
        "    return forms",
        "}",
        "",
        "private fun MutableMap<String, String>.putLexemes(lexemes: List<String>) {",
        "    for (lexeme in lexemes) {",
        "        val words = lexeme.split(' ')",
        "        for (index in 1 until words.size) {",
        "            put(words[index], words[0])",
        "        }",
        "    }",
        "}",
    ]

    for name, chunk in zip(names, chunks):
        lines += ["", f"internal object {name} {{", "    val lexemes: List<String> = listOf("]
        for block in chunk:
            lines += block
        lines += ["    )", "}"]

    text = "\n".join(line.rstrip() for line in lines)
    while "\n\n\n" in text:
        text = text.replace("\n\n\n", "\n\n")
    return text.rstrip("\n") + "\n", len(chunks)


# ---------------------------------------------------------------------------
# Self-checks, reporting, entry point.
# ---------------------------------------------------------------------------


def check_table(table, lexemes):
    """The invariants the emitted encoding depends on and Kotlin cannot restate."""
    problems = []
    for form, lemma in table.items():
        if not is_cyrillic(form) or not is_cyrillic(lemma):
            problems.append(f"non-Cyrillic entry {form!r} -> {lemma!r}")
        if " " in form or " " in lemma:
            problems.append(f"space inside {form!r} -> {lemma!r} would corrupt the space-separated encoding")
    seen = set()
    for lemma, forms in lexemes:
        for form in forms:
            if form in seen:
                problems.append(f"{form!r} is emitted twice; one lemma would silently overwrite the other")
            seen.add(form)
            if form == lemma:
                problems.append(f"{form!r} maps to itself and carries no information")
    if len(seen) != len(table):
        problems.append(f"grouped {len(seen)} forms but the table holds {len(table)}")
    return problems


def check_output(text):
    """The formatting rules this repository enforces and this script cannot run:
    line length, trailing whitespace, and exactly one final newline."""
    problems = []
    for number, line in enumerate(text.split("\n"), start=1):
        if len(line) > MAX_LINE:
            problems.append(f"line {number} is {len(line)} characters")
        if line != line.rstrip():
            problems.append(f"line {number} has trailing whitespace")
    if not text.endswith("\n") or text.endswith("\n\n"):
        problems.append("file must end with exactly one newline")
    return problems


def report(stats, collisions, path, problems):
    print(f"catalog words     {stats['catalog_words']}")
    print(f"quantity words    {stats['quantity_words']} Russian of {stats['quantity_words_total']}")
    print(f"seed words        {stats['seeds']}")
    print(f"unanalysed seeds  {stats['unanalysed']} (no known {'/'.join(sorted(INFLECTED_PARTS))} reading)")
    print(f"lemmas            {stats['lemmas']} in scope, {stats['emitted_lemmas']} with at least one emitted form")
    print(f"forms generated   {stats['generated']}")
    print(f"collisions        {stats['collisions']} excluded, never resolved")
    print(f"identity dropped  {stats['identity']}")
    print(f"entries emitted   {stats['entries']}")
    print(f"objects           {stats['objects']}")
    print(f"output            {path}")
    print(f"size              {stats['bytes']} bytes, {stats['lines']} lines")
    for form in sorted(collisions)[: stats['collision_sample']]:
        print(f"  collision       {form} -> {', '.join(collisions[form])}")
    if problems:
        print("PROBLEMS")
        for problem in problems:
            print(f"  {problem}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--source", default=str(SOURCE_JSON), help="synonym table to read")
    parser.add_argument("--quantity-source", default=str(QUANTITY_SOURCE), help="Kotlin file holding QUANTITY_WORDS")
    parser.add_argument("--out", default=str(OUTPUT_KT), help="Kotlin file to write")
    parser.add_argument("--collision-sample", type=int, default=12, help="how many collisions to print")
    parser.add_argument("--dry-run", action="store_true", help="do not write the file")
    args = parser.parse_args(argv)

    try:
        analyzer, pymorphy3 = load_analyzer()
        catalog_words, terms, provenance = load_catalog_words(Path(args.source))
        quantity_words, quantity_total = load_quantity_words(Path(args.quantity_source))
    except GeneratorError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    seeds = catalog_words | quantity_words
    index, lemmas, unanalysed = build_form_index(analyzer, seeds)
    table, collisions = resolve_collisions(index)
    lexemes = group_by_lemma(table)

    meta = dictionary_provenance(analyzer)
    meta["pymorphy3"] = pymorphy3.__version__
    meta["package"] = getattr(__import__("pymorphy3_dicts_ru"), "__version__", "unknown")

    stats = {
        "catalog_words": len(catalog_words),
        "quantity_words": len(quantity_words),
        "quantity_words_total": quantity_total,
        "terms": terms,
        "seeds": len(seeds),
        "unanalysed": len(unanalysed),
        "lemmas": len(lemmas),
        "emitted_lemmas": len({lemma for _, lemma in table.items()}),
        "generated": len(index),
        "collisions": len(collisions),
        "identity": len(index) - len(collisions) - len(table),
        "entries": len(table),
        "collision_sample": args.collision_sample,
    }
    text, objects = render_file(lexemes, stats, provenance, meta)
    stats["objects"] = objects
    stats["bytes"] = len(text.encode("utf-8"))
    stats["lines"] = text.count("\n")

    problems = check_table(table, lexemes) + check_output(text)
    out_path = Path(args.out)
    if not args.dry_run and not problems:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(text, encoding="utf-8")
    report(stats, collisions, out_path, problems)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
