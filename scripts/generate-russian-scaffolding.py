#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-russian-scaffolding.py -- BUILD-TIME generator for the Russian
SCAFFOLDING table: words that frame a meal description without naming any part
of it.

This script runs at build time only. It never runs on device, nothing it touches
is a runtime dependency, and its single output is one deterministic Kotlin file:

    mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/
    nutrition/GeneratedRussianScaffolding.kt

    THIRD-PARTY DEPENDENCY, declared here rather than assumed:
        pymorphy3 (MIT) + pymorphy3-dicts-ru, which packages the OpenCorpora
        dictionary. Python 3.9+. Install it into a throwaway environment; it
        must never appear under mobile/ or in a device build.


WHY THIS IS A SECOND TABLE AND MUST STAY ONE
============================================
`russianFormToLemma` in GeneratedRussianForms.kt is a FOLD APPLIED BY THE
MATCHER TO BOTH SIDES of every token comparison. Anything in it can change which
food a query reaches. That is exactly right for food vocabulary and exactly
wrong for `съел`: a verb form that also belonged to some food's paradigm would
silently redirect a food query, and the redirect would be invisible because both
sides folded the same way.

Scaffolding therefore lives in its own set, which only the stripping logic may
consult. A word here can remove itself from a query; it can never move a query
to a different record. Merging the two tables would delete that property in one
edit and nothing downstream would fail loudly enough to notice, which is why
this file says so in its own header too.

AGENTS.md is emphatic that two code paths which must agree should share one
implementation rather than be kept in step. That rule is about DUPLICATED
vocabulary, and this is the opposite: one vocabulary, deliberately reachable
from one place. `с` and `со` make the distinction concrete -- they are already
WEAK_SEPARATORS, they are already reached through `isSeparatorWord`, and copying
them here would be the duplication AGENTS.md warns about. This generator refuses
them by rule (see REFUSAL 4) rather than by comment.


WHAT IS IN SCOPE
================
Five lemmas, named in SEEDS below, each with the frame it expresses. Nothing is
discovered: a scaffolding word is by definition NOT catalog vocabulary, so there
is no corpus to read it out of the way generate-russian-lemmas.py reads the
synonym table. The list is short, explicit and reviewable in the diff, and every
addition has to argue for itself in the same place.

Only finite verb forms, imperatives and infinitives are kept (VERB/INFN), plus
the preposition's own forms (PREP). Participles and gerunds are dropped on
purpose: `приготовленный`, `съеденный` and `сварен` say something about the
STATE of the food, and this repository already treats preparation words as
information that must not be discarded in silence -- nutrition per 100 g moves
between raw and cooked. A table that stripped them would delete the very word
the search is supposed to report as unmatched.


REFUSALS -- FOUR RULES, COUNTED, NEVER RESOLVED
===============================================
1. NEGATION AND EXCLUSION. `без` and its kin are refused outright. Stripping a
   negation is worse than not handling it: `без хурмы` resolves to nothing
   today, which is a harmless non-answer, and would resolve to persimmon -- a
   food the user explicitly did not eat -- the moment `без` were stripped. The
   suite would score that as a PASS, so the number would rise while the
   behaviour degraded. The denylist is a guard on the seed list, not a negation
   detector; detecting negation in a query is a different job and this table
   must not pretend to do it.

2. CATALOG COLLISION. Any form that occurs as a token of a display name or an
   alternate name in GeneratedCatalog.kt or in russianPhraseCatalog is refused.
   A scaffolding word that is part of a food name deletes that food from the
   query. Both files are PARSED here on every run, never trusted from a note:
   the refusal has to re-derive itself whenever the catalog changes.

3. FOOD PARADIGM. Any form that is already a key of `russianFormToLemma` is
   refused. Being a key means the matcher folds that word onto a food lemma, so
   the word belongs to a food whatever else it also means.

4. EXISTING VOCABULARY. Any form already in QUANTITY_WORDS, WEAK_SEPARATORS or
   CONJUNCTIONS is refused, because it is already reachable through the
   predicate that owns it. This is the rule that keeps `с` and `со` out.

A lemma may lose some of its forms and still ship the rest. A lemma that loses
ALL of them is a hard failure, not a silent omission: it means a word was
proposed as scaffolding that the catalog already owns, and the seed has to be
withdrawn by a human rather than disappear into a generated file.


HOW ё IS HANDLED
================
`normalizeForSearch` folds Latin diacritics and leaves Cyrillic alone, so `ё`
and `е` are different characters to the matcher. Any form containing `ё` is
therefore emitted twice, once as written and once folded, so both spellings
reach the strip. None of today's five lemmas has a `ё` anywhere in its paradigm
and the rule fires zero times; it is implemented anyway, because the next seed
is one `ёж`-shaped word away from needing it and a rule that only exists when
someone remembers it is not a rule.


DETERMINISM
===========
Same inputs, byte-identical output. Everything is sorted, no wall-clock time and
no filesystem order reaches the file, and the header records input COUNTS rather
than a timestamp. Verify with two runs and a checksum:

    python3 scripts/generate-russian-scaffolding.py
    shasum -a 256 <output>
    python3 scripts/generate-russian-scaffolding.py
    shasum -a 256 <output>


LICENCE -- RECORDED, NOT RESOLVED
=================================
The forms are generated from the OpenCorpora dictionary shipped inside
pymorphy3-dicts-ru. That package's own metadata declares MIT, while OpenCorpora
publishes its dictionary under CC BY-SA. Those are not the same obligation, and
share-alike -- if it applies -- attaches to the generated table, not to this
script. It is a question for counsel.

Unlike GeneratedRussianForms.kt this table carries NO AGROVOC and NO Wikidata
obligation, because its word list is not derived from the synonym table: the
seeds are five ordinary Russian function words chosen here. That difference is
stated rather than left to be inferred, because a blended credit would claim FAO
attribution for words FAO never supplied.
"""

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
NUTRITION = REPO_ROOT / "mobile/domain/nutrition/src"
CATALOG_KT = NUTRITION / "commonMain/kotlin/com/supernutrition/domain/nutrition/GeneratedCatalog.kt"
PHRASE_KT = NUTRITION / "commonTest/kotlin/com/supernutrition/domain/nutrition/RussianPhraseCorpus.kt"
FORMS_KT = NUTRITION / "commonMain/kotlin/com/supernutrition/domain/nutrition/GeneratedRussianForms.kt"
FRAGMENTS_KT = NUTRITION / "commonMain/kotlin/com/supernutrition/domain/nutrition/TextFragments.kt"
OUTPUT_KT = NUTRITION / "commonMain/kotlin/com/supernutrition/domain/nutrition/GeneratedRussianScaffolding.kt"

MAX_LINE = 120
CHUNK_LINE_BUDGET = 300
INDENT = " " * 8

CYRILLIC = set("абвгдежзийклмнопрстуфхцчшщъыьэюяё")

# Parts of speech a frame is built from. VERB and INFN cover the finite forms,
# the imperatives and the infinitive; PREP covers a preposition's own variants
# (`из`/`изо`). Everything else in a verb's paradigm is dropped -- see the module
# docstring on participles and gerunds.
FRAME_PARTS = frozenset({"VERB", "INFN", "PREP"})

# REFUSAL 1. Words that negate or exclude rather than frame. Stripping one turns
# a harmless non-answer into a confident wrong food, and the test suite counts
# that as a pass. Named individually because each is a decision, not a pattern.
NEGATING_WORDS = {
    "без": "without -- the excluded food must not resolve",
    "безо": "without, before a consonant cluster",
    "не": "plain negation",
    "ни": "plain negation, correlative",
    "нет": "plain negation, predicative",
    "кроме": "except -- excludes the food it governs",
    "вместо": "instead of -- names a food that was NOT eaten",
}

# The scaffolding vocabulary itself. One entry per lemma, with the frame it
# expresses, because every addition has to argue for itself here in the diff.
SEEDS = (
    ("из", "preposition: names the material a dish was made FROM"),
    ("есть", "verb: to eat, imperfective -- `ел гречневую муку`"),
    ("съесть", "verb: to eat up, perfective -- `съел локву`"),
    ("добавить", "verb: to add -- `добавил папайю`"),
    ("приготовить", "verb: to cook or prepare -- `приготовил соевую муку`"),
)


class GeneratorError(Exception):
    """A failure the operator has to fix, reported instead of a partial table."""


def load_analyzer():
    """Import pymorphy3 late, so --help works without it, and fail loudly.

    An absent dependency must not degrade into an empty table: an empty set has
    the same type and the same call sites as a working one, so the failure would
    surface months later as scaffolding quietly not being stripped.
    """
    try:
        import pymorphy3
    except ImportError as error:
        raise GeneratorError(
            "pymorphy3 is not installed, so no Russian scaffolding forms can be generated.\n"
            "  This generator refuses to emit an empty table -- an empty table is\n"
            "  indistinguishable from a working one at the call site.\n"
            "  It is a BUILD-TIME dependency and must never reach mobile/ or a device:\n"
            "      python3 -m venv /tmp/ru-scaffolding\n"
            "      /tmp/ru-scaffolding/bin/pip install pymorphy3\n"
            "      /tmp/ru-scaffolding/bin/python scripts/generate-russian-scaffolding.py"
        ) from error
    return pymorphy3.MorphAnalyzer(), pymorphy3


def dictionary_provenance(analyzer):
    """The exact OpenCorpora revision behind the forms, for the file header."""
    meta = dict(getattr(analyzer.dictionary, "meta", {}) or {})
    return {
        "source": str(meta.get("source", "unknown")),
        "source_version": str(meta.get("source_version", "unknown")),
        "source_revision": str(meta.get("source_revision", "unknown")),
    }


# ---------------------------------------------------------------------------
# Reading the sources the refusals are measured against.
# ---------------------------------------------------------------------------


def normalize(value):
    """Lowercase and fold ё to е, the spelling comparisons are made in."""
    return value.lower().replace("ё", "е")


def tokenize(value):
    """Split on anything that is not a letter or a digit.

    Mirrors `tokenizeNormalized`, so a name this splits differently is a name
    the refusal would be measured against wrongly.
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


def read_source(path):
    """Read one input, turning a missing or unreadable file into a named error.

    A traceback is loud but anonymous; the operator needs to know WHICH of the
    four sources moved, because each one disables a different refusal rule.
    """
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        raise GeneratorError(
            f"cannot read {path}: {error.strerror}. This generator measures its refusals against that file, "
            "so it stops rather than emit a table whose refusals were never checked."
        ) from error


def strip_kotlin_comments(text):
    """Remove // and /* */ comments without touching string literals.

    A regex over multi-line Kotlin has already produced two confident wrong
    findings in this work, so the scanner is written out longhand: it tracks
    whether it is inside a string and never guesses where a construct ends.
    """
    out = []
    index = 0
    length = len(text)
    while index < length:
        character = text[index]
        if character == '"':
            out.append(character)
            index += 1
            while index < length:
                out.append(text[index])
                if text[index] == "\\":
                    index += 1
                    if index < length:
                        out.append(text[index])
                        index += 1
                    continue
                if text[index] == '"':
                    index += 1
                    break
                index += 1
            continue
        if text.startswith("//", index):
            while index < length and text[index] != "\n":
                index += 1
            continue
        if text.startswith("/*", index):
            index += 2
            while index < length and not text.startswith("*/", index):
                index += 1
            index += 2
            continue
        out.append(character)
        index += 1
    return "".join(out)


def read_kotlin_string(text, index):
    """text[index] is an opening quote. Returns (value, index after the close)."""
    index += 1
    characters = []
    while index < len(text):
        character = text[index]
        if character == "\\":
            characters.append(text[index + 1])
            index += 2
            continue
        if character == '"':
            return "".join(characters), index + 1
        characters.append(character)
        index += 1
    raise GeneratorError("unterminated Kotlin string literal while reading a catalog source")


def catalog_record_strings(text):
    """Every string literal inside every CatalogRecord(...) call.

    Balanced-paren scanning rather than a line regex, because 460 of
    GeneratedCatalog.kt's 3519 records are written across several lines and a
    line-oriented reader silently sees 3059 of them -- a 13% blind spot that
    looks exactly like a clean result.
    """
    calls = []
    for match in re.finditer(r"CatalogRecord\(", text):
        index = match.end()
        depth = 1
        strings = []
        while index < len(text) and depth > 0:
            character = text[index]
            if character == '"':
                value, index = read_kotlin_string(text, index)
                strings.append(value)
                continue
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            index += 1
        if depth != 0:
            raise GeneratorError("unbalanced CatalogRecord(...) call; the source is not what this reader expects")
        calls.append(strings)
    return calls


def object_strings(text, object_prefix):
    """Every string literal inside `internal object <prefix>...` declarations."""
    strings = []
    for match in re.finditer(r"internal object " + object_prefix + r"\w*\s*\{", text):
        index = match.end()
        depth = 1
        while index < len(text) and depth > 0:
            character = text[index]
            if character == '"':
                value, index = read_kotlin_string(text, index)
                strings.append(value)
                continue
            if character == "{":
                depth += 1
            elif character == "}":
                depth -= 1
            index += 1
        if depth != 0:
            raise GeneratorError(f"unbalanced `internal object {object_prefix}...` body")
    return strings


def load_catalog_name_tokens(catalog_path, phrase_path):
    """Every token of every display name and alternate name in both catalogs.

    Two record sets, read the same way. The alternate names of GeneratedCatalog
    live in shared `GeneratedNames*` objects rather than inline, so both shapes
    have to be read or the check would pass by not looking.
    """
    catalog_text = strip_kotlin_comments(read_source(catalog_path))
    phrase_text = strip_kotlin_comments(read_source(phrase_path))

    catalog_calls = catalog_record_strings(catalog_text)
    if not catalog_calls:
        raise GeneratorError(f"{catalog_path} yielded no CatalogRecord calls; refusing to check against nothing")
    displays = [call[1] for call in catalog_calls if len(call) >= 2]
    alternates = object_strings(catalog_text, "GeneratedNames")
    if not alternates:
        raise GeneratorError(f"{catalog_path} yielded no alternate names; the collision check would be half blind")

    phrase_calls = catalog_record_strings(phrase_text)
    if not phrase_calls:
        raise GeneratorError(f"{phrase_path} yielded no CatalogRecord calls; refusing to check against nothing")
    phrase_names = [value for call in phrase_calls for value in call if not value.startswith("food:")]

    tokens = {}
    for source, names in (("GeneratedCatalog.kt", displays + alternates), ("russianPhraseCatalog", phrase_names)):
        for name in names:
            for token in tokenize(name):
                entry = tokens.setdefault(token, {"sources": set(), "names": set()})
                entry["sources"].add(source)
                entry["names"].add(name)
    counts = {
        "catalog_records": len(catalog_calls),
        "catalog_alternates": len(alternates),
        "phrase_records": len(phrase_calls),
        "phrase_names": len(phrase_names),
        "name_tokens": len(tokens),
    }
    return tokens, counts


def load_food_forms(path):
    """The keys of `russianFormToLemma`, decoded from its stored lexeme lines.

    Read rather than reimplemented: the stored shape is `"lemma form form ..."`
    and the emitted Kotlin reader takes words[1:] as the keys, so this takes the
    same slice. A form that is a key here belongs to a food paradigm the matcher
    folds through, whatever else the word also means.
    """
    text = strip_kotlin_comments(read_source(path))
    forms = set()
    for lexeme in re.findall(r'"([^"]+)"', text):
        words = lexeme.split(" ")
        forms.update(words[1:])
    if not forms:
        raise GeneratorError(f"{path} yielded no lemma forms; the food-paradigm refusal would never fire")
    return forms


def load_existing_vocabulary(path):
    """QUANTITY_WORDS, WEAK_SEPARATORS and CONJUNCTIONS out of TextFragments.kt.

    Parsed rather than copied, for the reason AGENTS.md gives: a duplicated
    quantity vocabulary drifted within days in this repository and produced
    user-visible wrong answers. This fails hard if a declaration moves, rather
    than quietly checking against half a list.
    """
    source = read_source(path)
    vocabulary = {}
    declarations = (
        ("QUANTITY_WORDS", r"val QUANTITY_WORDS: Set<String> = setOf\((.*?)\n\)"),
        ("WEAK_SEPARATORS", r"val WEAK_SEPARATORS = setOf\((.*?)\n\)"),
        ("CONJUNCTIONS", r"val CONJUNCTIONS = setOf\((.*?)\)"),
    )
    for name, pattern in declarations:
        block = re.search(pattern, source, re.S)
        if block is None:
            raise GeneratorError(
                f"{name} was not found in {path}. It moved or was renamed; this generator reads it rather "
                "than keeping a second copy, so fix the pattern instead of pasting the list."
            )
        words = {normalize(word) for word in re.findall(r'"([^"]+)"', block.group(1))}
        if not words:
            raise GeneratorError(f"{name} in {path} parsed as empty; the existing-vocabulary refusal would never fire")
        vocabulary[name] = words
    return vocabulary


# ---------------------------------------------------------------------------
# Generating the forms.
# ---------------------------------------------------------------------------


def paradigm_of(analyzer, seed):
    """The frame-bearing surface forms of one seed lemma.

    `is_known` keeps pymorphy3's guesser out entirely: an invented paradigm for
    a word the dictionary does not have is precisely the failure this table
    exists to avoid, one table over. Requiring `normal_form == seed` keeps out
    readings of a homograph -- `есть` also analyses as the 3rd person of `быть`,
    whose paradigm is `был`, `будет`, `суть`, none of which frames a meal.
    """
    parses = [
        parse
        for parse in analyzer.parse(seed)
        if parse.is_known and normalize(parse.normal_form) == seed and str(parse.tag.POS) in FRAME_PARTS
    ]
    if not parses:
        raise GeneratorError(
            f"`{seed}` has no known {'/'.join(sorted(FRAME_PARTS))} reading whose lemma is itself. "
            "It is not the word this generator thinks it is; withdraw the seed rather than widen the filter."
        )
    forms = set()
    for parse in parses:
        for form in parse.lexeme:
            if str(form.tag.POS) not in FRAME_PARTS:
                continue
            forms.update(spellings_of(form.word))
    if not forms:
        raise GeneratorError(f"`{seed}` produced no surface forms at all; refusing to emit a partial table")
    return sorted(forms)


def spellings_of(word):
    """The spellings of one form a query could arrive in.

    A form written with ё is emitted twice, because the matcher folds Latin
    diacritics and leaves Cyrillic alone: the two spellings are one word to
    everybody except the string comparison.
    """
    lowered = word.lower()
    folded = normalize(lowered)
    if not is_cyrillic(folded):
        return []
    if lowered == folded:
        return [folded]
    return [folded, lowered]


def refusals_for(form, name_tokens, food_forms, vocabulary):
    """Every refusal rule this form trips, as (rule, detail) pairs.

    All four rules are evaluated, not short-circuited, so a form refused twice
    is reported under both reasons instead of hiding one behind the other.
    """
    reasons = []
    if form in NEGATING_WORDS:
        reasons.append(("negation", NEGATING_WORDS[form]))
    if form in name_tokens:
        entry = name_tokens[form]
        names = sorted(entry["names"])
        examples = ", ".join(f"`{name}`" for name in names[:2])
        if len(names) > 2:
            examples += f" and {len(names) - 2} more"
        reasons.append(("catalog", f"{', '.join(sorted(entry['sources']))} names {len(names)}: {examples}"))
    if form in food_forms:
        reasons.append(("food paradigm", "already a key of russianFormToLemma"))
    for name in sorted(vocabulary):
        if form in vocabulary[name]:
            reasons.append(("existing vocabulary", f"already in {name}"))
    return reasons


def build_lexemes(analyzer, name_tokens, food_forms, vocabulary):
    """Per seed: the forms that survive, and the ones each rule refused."""
    lexemes = []
    refused = []
    for seed, role in SEEDS:
        if seed in NEGATING_WORDS:
            raise GeneratorError(
                f"`{seed}` is a negation and cannot be a scaffolding seed: {NEGATING_WORDS[seed]}. "
                "Stripping it would turn a harmless non-answer into a confident wrong food."
            )
        forms = paradigm_of(analyzer, seed)
        kept = []
        for form in forms:
            reasons = refusals_for(form, name_tokens, food_forms, vocabulary)
            if reasons:
                refused.append((seed, form, reasons))
            else:
                kept.append(form)
        if not kept:
            raise GeneratorError(
                f"every form of `{seed}` was refused, so it would ship as nothing at all. "
                "That means the catalog already owns this word; withdraw the seed here rather than "
                "let it disappear into a generated file."
            )
        lexemes.append((seed, role, kept, len(forms)))
    return lexemes, refused


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


def render_lexeme(seed, role, forms, total, refused_here):
    """One lexeme as a comment and its `"form form ..."` lines.

    The comment names the lemma and says how many of its forms survived, so a
    reader of the generated file sees the refusal at the place it happened
    rather than having to reconcile the header against the data.

    The strings hold ONLY surviving forms. The lemma is not stored as the first
    word the way GeneratedRussianForms.kt stores it, because here the lemma is
    itself a candidate form and may have been refused -- `из` is -- and a reader
    that added the first word back would reinstate exactly what was refused.
    """
    lines = wrapped_comment(f"{seed} -- {role}.", f"{INDENT}// ", f"{INDENT}// ")
    tally = f"{len(forms)} of {total} forms."
    if refused_here:
        tally = f"{tally[:-1]}; refused: {', '.join(refused_here)}."
    lines += wrapped_comment(tally, f"{INDENT}// ", f"{INDENT}// ")

    budget = MAX_LINE - len(INDENT) - len('"",')
    current = []
    width = -1
    for form in forms:
        if current and width + 1 + len(form) > budget:
            lines.append(f"{INDENT}{kotlin_string(' '.join(current))},")
            current = []
            width = -1
        current.append(form)
        width += 1 + len(form)
    if current:
        lines.append(f"{INDENT}{kotlin_string(' '.join(current))},")
    return lines


def chunk_blocks(blocks, budget):
    """Group rendered lexemes so no generated object grows unbounded.

    Two limits at once, the same pair GeneratedRussianForms.kt and
    GeneratedCatalog.kt split for: a JVM method may not exceed 64 KB of bytecode
    and one property holding every form would put all of it in a single file
    initializer, and Detekt's LargeClass threshold applies to the declaration
    regardless of what the JVM would take. Today's table needs one holder; the
    split is data-driven so it becomes two the run after it needs to.
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


def wrapped_comment(text, first_prefix, continuation_prefix):
    """Wrap one comment sentence to the line limit, measured in CHARACTERS.

    Cyrillic is one character and two UTF-8 bytes; ktlint counts characters, and
    a wrapper that measured bytes would fold these lines at half the width for
    no reason. `len` on a Python str is already the character count.
    """
    lines = []
    prefix = first_prefix
    current = None
    for word in text.split(" "):
        if current is None:
            current = prefix + word
            continue
        candidate = f"{current} {word}"
        if len(candidate) > MAX_LINE:
            lines.append(current)
            prefix = continuation_prefix
            current = prefix + word
        else:
            current = candidate
    if current is not None:
        lines.append(current)
    return lines


def header_lines(stats, dictionary, refused):
    lines = [
        "package com.supernutrition.domain.nutrition",
        "",
        "// GENERATED FILE. Produced by scripts/generate-russian-scaffolding.py from the",
        "// seed lemmas named in that script, inflected against the OpenCorpora dictionary.",
        "// Do not edit by hand: the next run of the generator overwrites every line.",
        "//",
        "// PROVENANCE",
        f"//   inflection   OpenCorpora dictionary via pymorphy3 {dictionary['pymorphy3']}",
        f"//                (pymorphy3-dicts-ru {dictionary['package']}, source"
        f" {dictionary['source']} {dictionary['source_version']} rev {dictionary['source_revision']})",
        f"//   word list    {stats['seeds']} scaffolding lemmas named in the generator, not read from any",
        "//                catalog -- a word that frames a meal is by definition not a food word",
        f"//   refusals     checked against {stats['catalog_records']} GeneratedCatalog records,"
        f" {stats['phrase_records']} russianPhraseCatalog",
        f"//                records ({stats['name_tokens']} distinct name tokens),"
        f" {stats['food_forms']} russianFormToLemma keys,",
        f"//                and {stats['vocabulary_words']} words of existing"
        " QUANTITY_WORDS/WEAK_SEPARATORS/CONJUNCTIONS",
        "//",
        f"// REFUSED, RECORDED HERE RATHER THAN SILENTLY DROPPED -- {stats['refused']} of"
        f" {stats['generated']} generated forms.",
    ]
    for seed, form, reasons in refused:
        detail = "; ".join(f"{rule}: {why}" for rule, why in reasons)
        lines += wrapped_comment(f"{form} (of {seed}) -- {detail}", "//   ", "//     ")
    lines += [
        f"//   {stats['negation_refusals']} by negation, {stats['catalog_refusals']} by catalog collision,"
        f" {stats['paradigm_refusals']} by food paradigm,",
        f"//   {stats['vocabulary_refusals']} by existing vocabulary. A refused form is never resolved to a"
        " best guess:",
        "//   the word simply stays in the query as typed, which is today's behaviour.",
        "//",
        "// LICENCE -- ONE OBLIGATION, AND DELIBERATELY NOT THREE.",
        "//   * The inflected forms are derived from the OpenCorpora dictionary. The",
        "//     pymorphy3-dicts-ru package declares MIT in its own metadata, while",
        "//     OpenCorpora publishes the dictionary under CC BY-SA. Those are not the",
        "//     same obligation, and if share-alike applies it attaches to THIS TABLE,",
        "//     not to the generator that wrote it. Recorded here, unresolved: it is a",
        "//     question for counsel before release, not one for a build script.",
        "//   * No AGROVOC and no Wikidata credit applies to this file, unlike",
        "//     GeneratedRussianForms.kt. The seeds are ordinary Russian function words",
        "//     chosen in the generator, not terms taken from the synonym table, and",
        "//     claiming FAO attribution for words FAO never supplied would be wrong in",
        "//     the direction that matters.",
        "",
    ]
    return lines


def documentation_lines(stats, lexemes):
    named = ", ".join(f"`{seed}`" for seed, _role, _forms, _total in lexemes)
    yo_sentence = (
        "none of today's forms has one, so that rule added no entries"
        if stats["yo_variants"] == 0
        else f"that rule added {stats['yo_variants']} entries"
    )
    return [
        "/**",
        " * Russian words that frame a meal description without naming any part of it.",
        " *",
        f" * {stats['entries']} surface forms over {stats['emitted_lemmas']} lemmas: {named}.",
        " * A user writes `съел локву` and `приготовил соевую муку`; the food is the",
        " * object, and the verb is furniture the catalog has no record for.",
        " *",
        " * A SEPARATE TABLE FROM [russianFormToLemma], AND THAT IS THE WHOLE POINT.",
        " * That table is a fold the matcher applies to BOTH SIDES of every token",
        " * comparison, so anything in it can change which food a query reaches. A verb",
        " * form that also belonged to some food's paradigm would silently redirect a",
        " * food query, and the redirect would be invisible precisely because both sides",
        " * folded the same way. This set is consulted only by the stripping logic, so a",
        " * word here can remove itself from a query and can never move the query to a",
        " * different record. Do not merge the two tables: the property is destroyed by",
        " * the merge itself, and nothing downstream fails loudly enough to notice.",
        " *",
        " * Not a duplicate vocabulary either, which is the other half of the rule.",
        " * `с` and `со` frame a meal exactly as these do and are deliberately ABSENT:",
        " * they are already [WEAK_SEPARATORS] and already reached through the predicate",
        " * that owns them, and a second copy here is the drift AGENTS.md warns about.",
        " * The generator refuses them by rule rather than by comment, along with any",
        f" * form the catalogs already use in a food name -- {stats['refused']}"
        f" refusal{'' if stats['refused'] == 1 else 's'} in total, all",
        " * named in the header above.",
        " *",
        " * Negations are refused outright and are not an oversight. `без хурмы`",
        " * resolves to nothing today, which is a harmless non-answer; strip the `без`",
        " * and it resolves to persimmon, a food the user explicitly did not eat. The",
        " * suite would score that as a pass, so the number would rise while the",
        " * behaviour got worse. Recognising negation is a different job, and this table",
        " * must not pretend to do it.",
        " *",
        " * Participles and gerunds are absent for a related reason: `приготовленный`",
        " * and `съеденный` describe the state of the food, and preparation words are",
        " * reported as unmatched rather than discarded, because nutrition per 100 g",
        " * moves between raw and cooked.",
        " *",
        " * Every entry is lowercase and bare. A form spelled with `ё` appears alongside",
        " * its `е` spelling, because [normalizeForSearch] folds Latin diacritics and",
        f" * leaves Cyrillic alone; {yo_sentence}.",
        " *",
        " * Regenerate with scripts/generate-russian-scaffolding.py, which is the only",
        " * writer. It re-derives every refusal from the catalogs on each run.",
        " */",
    ]


def render_file(lexemes, refused, stats, dictionary):
    refused_by_seed = {}
    for seed, form, _reasons in refused:
        refused_by_seed.setdefault(seed, []).append(form)
    blocks = [
        render_lexeme(seed, role, forms, total, refused_by_seed.get(seed, []))
        for seed, role, forms, total in lexemes
    ]
    chunks = chunk_blocks(blocks, CHUNK_LINE_BUDGET)
    names = [f"RussianScaffoldingFrames{index}" for index in range(len(chunks))]

    lines = header_lines(stats, dictionary, refused)
    lines += documentation_lines(stats, lexemes)
    lines += ["internal val russianScaffoldingForms: Set<String> = buildRussianScaffoldingForms()", ""]
    lines += [
        "/**",
        " * Expands the stored frames into the flat set the stripping logic uses.",
        " *",
        " * The data is stored one lexeme per string, with a comment naming the lemma it",
        " * came from, because that is where a reader checks a refusal. Every word in a",
        " * string is a form that survived; the lemma is NOT stored as the first word the",
        " * way GeneratedRussianForms.kt stores it, because here the lemma is itself a",
        " * candidate and may have been refused, and a reader that added it back would",
        " * reinstate exactly what the generator threw out.",
        " */",
        "private fun buildRussianScaffoldingForms(): Set<String> {",
        "    val forms = mutableSetOf<String>()",
    ]
    lines += [f"    forms.addFrames({name}.frames)" for name in names]
    lines += [
        "    return forms",
        "}",
        "",
        "private fun MutableSet<String>.addFrames(frames: List<String>) {",
        "    for (frame in frames) {",
        "        for (word in frame.split(' ')) {",
        "            add(word)",
        "        }",
        "    }",
        "}",
    ]

    for name, chunk in zip(names, chunks):
        lines += ["", f"internal object {name} {{", "    val frames: List<String> = listOf("]
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


def check_table(lexemes, name_tokens, food_forms, vocabulary):
    """The invariants the emitted encoding depends on and Kotlin cannot restate."""
    problems = []
    emitted = []
    for seed, _role, forms, _total in lexemes:
        if not forms:
            problems.append(f"`{seed}` emitted no forms; a partial table must fail instead")
        emitted.extend(forms)
    if not emitted:
        problems.append("the table is empty; an empty set is indistinguishable from a working one at the call site")
    seen = set()
    for form in emitted:
        if form in seen:
            problems.append(f"{form!r} is emitted twice; the space-separated encoding would hide it")
        seen.add(form)
        if not is_cyrillic(form):
            problems.append(f"{form!r} is not bare lowercase Cyrillic")
        if form != form.lower() or form.strip() != form or " " in form:
            problems.append(f"{form!r} is not lowercase and bare")
        for rule, why in refusals_for(form, name_tokens, food_forms, vocabulary):
            problems.append(f"{form!r} survived but trips the {rule} rule ({why})")
        if normalize(form) != form and normalize(form) not in seen and normalize(form) not in emitted:
            problems.append(f"{form!r} is a ё spelling with no е spelling beside it")
    return problems


def check_output(text):
    """The formatting rules this repository enforces and this script cannot run:
    line length in CHARACTERS, trailing whitespace, and exactly one final newline."""
    problems = []
    for number, line in enumerate(text.split("\n"), start=1):
        if len(line) > MAX_LINE:
            problems.append(f"line {number} is {len(line)} characters")
        if line != line.rstrip():
            problems.append(f"line {number} has trailing whitespace")
    if not text.endswith("\n") or text.endswith("\n\n"):
        problems.append("file must end with exactly one newline")
    return problems


def report(stats, lexemes, refused, path, problems):
    print(f"seed lemmas       {stats['seeds']}")
    print(f"forms generated   {stats['generated']}")
    print(f"refused           {stats['refused']} "
          f"(negation {stats['negation_refusals']}, catalog {stats['catalog_refusals']}, "
          f"paradigm {stats['paradigm_refusals']}, vocabulary {stats['vocabulary_refusals']})")
    for seed, form, reasons in refused:
        print(f"  refused         {form} (of {seed}) -- {'; '.join(f'{r}: {w}' for r, w in reasons)}")
    print(f"yo variants       {stats['yo_variants']}")
    print(f"entries emitted   {stats['entries']} over {stats['emitted_lemmas']} lemmas")
    for seed, _role, forms, total in lexemes:
        print(f"  {seed:14} {len(forms)} of {total}")
    print(f"checked against   {stats['catalog_records']} catalog records, {stats['phrase_records']} phrase records, "
          f"{stats['name_tokens']} name tokens, {stats['food_forms']} lemma keys, "
          f"{stats['vocabulary_words']} vocabulary words")
    print(f"objects           {stats['objects']}")
    print(f"output            {path}")
    print(f"size              {stats['bytes']} bytes, {stats['lines']} lines")
    if problems:
        print("PROBLEMS")
        for problem in problems:
            print(f"  {problem}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--catalog", default=str(CATALOG_KT), help="GeneratedCatalog.kt to check collisions against")
    parser.add_argument("--phrase-catalog", default=str(PHRASE_KT), help="Kotlin file holding russianPhraseCatalog")
    parser.add_argument("--forms", default=str(FORMS_KT), help="Kotlin file holding russianFormToLemma")
    parser.add_argument("--fragments", default=str(FRAGMENTS_KT), help="Kotlin file holding the existing vocabulary")
    parser.add_argument("--out", default=str(OUTPUT_KT), help="Kotlin file to write")
    parser.add_argument("--dry-run", action="store_true", help="do not write the file")
    args = parser.parse_args(argv)

    try:
        analyzer, pymorphy3 = load_analyzer()
        name_tokens, counts = load_catalog_name_tokens(Path(args.catalog), Path(args.phrase_catalog))
        food_forms = load_food_forms(Path(args.forms))
        vocabulary = load_existing_vocabulary(Path(args.fragments))
        lexemes, refused = build_lexemes(analyzer, name_tokens, food_forms, vocabulary)
    except GeneratorError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    meta = dictionary_provenance(analyzer)
    meta["pymorphy3"] = pymorphy3.__version__
    meta["package"] = getattr(__import__("pymorphy3_dicts_ru"), "__version__", "unknown")

    generated = sum(total for _seed, _role, _forms, total in lexemes)
    entries = sum(len(forms) for _seed, _role, forms, _total in lexemes)
    rule_counts = {"negation": 0, "catalog": 0, "food paradigm": 0, "existing vocabulary": 0}
    for _seed, _form, reasons in refused:
        for rule, _why in reasons:
            rule_counts[rule] += 1

    stats = {
        "seeds": len(SEEDS),
        "generated": generated,
        "refused": len(refused),
        "negation_refusals": rule_counts["negation"],
        "catalog_refusals": rule_counts["catalog"],
        "paradigm_refusals": rule_counts["food paradigm"],
        "vocabulary_refusals": rule_counts["existing vocabulary"],
        "yo_variants": sum(
            1 for _seed, _role, forms, _total in lexemes for form in forms if normalize(form) != form
        ),
        "entries": entries,
        "emitted_lemmas": len(lexemes),
        "food_forms": len(food_forms),
        "vocabulary_words": len(set().union(*vocabulary.values())),
        **counts,
    }
    text, objects = render_file(lexemes, refused, stats, meta)
    stats["objects"] = objects
    stats["bytes"] = len(text.encode("utf-8"))
    stats["lines"] = text.count("\n")

    problems = check_table(lexemes, name_tokens, food_forms, vocabulary) + check_output(text)
    out_path = Path(args.out)
    if not args.dry_run and not problems:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(text, encoding="utf-8")
    report(stats, lexemes, refused, out_path, problems)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
