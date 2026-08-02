#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-russian-phrase-corpus.py -- BUILD-TIME generator for a corpus of
REALISTIC Russian meal text.

This script runs at build time only. It never runs on device, nothing it touches
is a runtime dependency, and its single output is one deterministic Kotlin file:

    mobile/domain/nutrition/src/commonTest/kotlin/com/supernutrition/domain/
    nutrition/RussianPhraseCorpus.kt

    THIRD-PARTY DEPENDENCY, declared here rather than assumed:
        pymorphy3 (MIT) + pymorphy3-dicts-ru, which packages the OpenCorpora
        dictionary. Python 3.9+. Install it into a throwaway environment; it
        must never appear under mobile/ or in a device build.


WHY THIS EXISTS -- THE MEASUREMENT IT REPLACES
==============================================
MealTextCorpus.kt builds its Russian quantity cases by gluing a quantity word
onto an UNTOUCHED NOMINATIVE display name: `две луцианы`, `два акулы`, `три
груперы`. Every one of those is ungrammatical. `две луцианы` is not a phrase a
Russian speaker types, or could type; the numeral `две` governs the genitive and
the noun is masculine anyway, so what a person would actually write is `двух
луцианов`.

That matters because of what it does to the score. Strip a quantity word off
`две луцианы` and what is left is `луцианы` -- the record's own nominative
string, which the EXACT tier answers without any Russian support whatsoever.
The suite reports 154/160 on ru/es phrases while no lemmatizer exists at all.
The number is not measuring Russian; it is measuring that the generator wrote
the catalog's own spelling into the query.

Real Russian meal text inflects the food. The whole point of this corpus is that
its queries carry the case the construction governs:

    двух морских окуней      genitive plural, governed by the oblique numeral
    200 граммов свинины      genitive, governed by a measure noun
    с куриным мясом          instrumental, governed by the preposition `с`
    три банана               genitive singular, the Russian paucal
    из помидоров             genitive plural, governed by `из`
    яйца с рисом             instrumental on the second food only

None of those endings is written by hand here. Each is produced by pymorphy3
from the OpenCorpora dictionary, for the case named by the construction, with
adjectives agreed to their head in case, number, gender and -- in the accusative
-- animacy.


THE EXPECTATIONS ARE THE CORRECT ANSWER, NOT TODAY'S ANSWER
===========================================================
`expected` on every case is the food a Russian speaker named. Most of these WILL
FAIL against the code as it stands, because nothing folds an inflected form to
its lemma yet: `окуней` is not `окуни`, the PREFIX tier runs the wrong way
round, and the fuzzy tier is bounded below the distance between them on purpose.

That failure is the measurement. A case is never dropped for being expected to
fail, and no expectation is ever relaxed to make one pass. The generator instead
refuses to emit a case it cannot justify -- see the three guards below.


WHAT THE GENERATOR REFUSES TO EMIT
==================================
  1. A phrase it could not analyse. Latin-script names, indeclinables (`мате`,
     `тофу`, `мисо`) and phrases whose head noun has no nominative reading are
     dropped rather than guessed at. A guessed paradigm produces a word that is
     not Russian, and a corpus of those measures nothing.
  2. A phrase whose inflected form is spelled the same as the nominative the
     catalog already holds. The accusative of an inanimate noun is its
     nominative, so `съел рис` is a case that would pass today for reasons that
     have nothing to do with morphology -- exactly the flaw this corpus exists
     to remove. Every emitted positive differs from its record in at least one
     token.
  3. A phrase whose lemmas do not name exactly one record. `expected` has to be
     the correct answer, and a query two records answer to has no single correct
     answer. The check is deliberately conservative: a case is dropped when ANY
     other record has, for every query token, some token sharing a lemma with
     it -- which is what TOKEN_SUBSET would do under a perfect lemmatizer.

Negatives get the mirror of guard 3 plus the fuzzy-reachability test the meal
text generator already owns: a `must-not-resolve` case must be unreachable by
lemma AND unreachable by edit distance, so it stays negative under a fixed
matcher rather than only under a broken one.


WHAT THE CATALOG HOLDS
======================
Nominative Russian terms, and nothing else -- no English, no Spanish. A real
catalog holds one citation form per name, which is why the query has to travel
the distance rather than the record. Mixing the English display in would also
let a case resolve through a language the query is not written in, which would
put the score back where it started.


CASE GOVERNMENT IS DECLARED, INFLECTION IS COMPUTED
===================================================
Which case a construction governs is standard, uncontested Russian grammar and
is written down in the construction tables below, with the government named on
each entry. What is NOT written down anywhere here is a single ending:
`граммов` governs the genitive, and what the genitive of `свинина` looks like is
pymorphy3's answer, from the dictionary, every time.

Two constructions need a note because Russian numerals are irregular:

  * The paucal. `два`/`две`/`три`/`четыре` take a genitive SINGULAR noun --
    `три банана`, not `три бананов`. The agreeing adjective is contested
    (masculine and neuter take the genitive plural, feminine admits both the
    nominative and the genitive plural), so this generator builds the paucal
    ONLY for names with no adjective to agree. That is a restriction on the
    corpus, not a claim about the language.
  * The oblique numeral. `двух`, `трех`, `пяти` are the numeral's own genitive,
    and there the noun and its adjectives are uniformly genitive plural:
    `двух морских окуней`. No agreement is contested, so multi-word names are
    built here.

`пять` and above take the genitive plural throughout, which is also uniform.


THE QUANTITY WORDS ARE THE ONES THE PRODUCT ALREADY KNOWS
=========================================================
Every measure and numeral used to build a quantity case is checked against
QUANTITY_WORDS in TextFragments.kt, read out of that file rather than restated,
for the reason AGENTS.md gives: a second copy of that vocabulary drifts. It also
sharpens the measurement. `порция` is in QUANTITY_WORDS, so `порция свинины`
fails for exactly one reason -- the genitive `свинины` -- and a case that failed
for two reasons at once would not tell anyone which to fix. The oblique numerals
`двух`/`трех`/`пяти` are deliberately NOT in QUANTITY_WORDS and reach it through
the same fold as the food; those cases say so in their own `because`.


HOW ё IS HANDLED
================
`normalizeForSearch` folds Latin diacritics and leaves Cyrillic alone, so `ё`
and `е` are different characters to the matcher. No Russian display name in the
synonym table is spelled with `ё`, and OpenCorpora restores it (`черная` yields
`чёрной`), so queries are emitted in their `е` spelling by default -- the
spelling the catalog uses and the one most people type. A named subset keeps the
`ё` as the dictionary wrote it, because GeneratedRussianForms.kt carries both
spellings and a corpus that only ever asked for one would not measure that.


LICENCE -- RECORDED, NOT RESOLVED
=================================
The inflected forms are generated from the OpenCorpora dictionary shipped inside
pymorphy3-dicts-ru. That package's own metadata declares MIT, while OpenCorpora
publishes the dictionary under CC BY-SA. Those are not the same obligation, and
share-alike -- if it applies -- attaches to the generated corpus, not to this
script. The generator records the exact dictionary revision it used in the
emitted file's header and stops there. It is a question for counsel.

The words the corpus is built from carry their own separate terms: AGROVOC
(CC-BY IGO 3.0, (c) FAO, attribution is a licence condition) and Wikidata (CC0).
See ATTRIBUTION.md beside the synonym table. The three licences are recorded
separately and must never be merged into one blended credit.
"""

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_JSON = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json"
)
QUANTITY_SOURCE = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition"
    / "domain/nutrition/TextFragments.kt"
)
SIBLING_GENERATOR = Path(__file__).resolve().parent / "generate-meal-text-corpus.py"
OUTPUT_KT = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonTest/kotlin/com/supernutrition"
    / "domain/nutrition/RussianPhraseCorpus.kt"
)

ITEM_ID_PREFIX = "food:agrovoc-"

DEFAULT_CONCEPTS = 260
CAP_SINGLE = 180
CAP_QUANTITY = 170
CAP_TWO_ITEM = 120
CAP_NEGATIVE = 60
YO_QUOTA = 18

# Line length is not checked here: `check_output`, imported from the meal-text
# generator, owns that rule along with the trailing-whitespace and final-newline
# checks, and a second limit here would be a second thing to keep in step.
MAX_BECAUSE = 100
MAX_QUERY = 100
CHUNK_LINE_BUDGET = 380

# Parts of speech that can head a food name, and the ones that agree with a head.
HEAD_PARTS = ("NOUN",)
MODIFIER_PARTS = ("ADJF", "PRTF")

# Readings that are a proper noun rather than a word. `бобы` has a Name reading
# and `аргентины` a Geox one; inflecting those fills the corpus with a person's
# paradigm under a food's expectation.
PROPER_GRAMMEMES = frozenset({"Name", "Surn", "Patr", "Geox", "Orgn", "Trad", "Init"})

# The case a post-head dependent is already sitting in. `икра осетровых рыб` and
# `ростки бамбука` are names whose tail never moves; freezing it is what lets the
# head inflect without the generator inventing agreement that is not there.
#
# Genitive and nothing else. Admitting the accusative looked harmless and was
# not: an inanimate plural spells its accusative like its nominative, so `овощи`
# in `корнеклубнеплодные овощи` became freezable, the guessed noun reading of
# `корнеклубнеплодные` won the head, and the phrase came out as `с
# корнеклубнеплодными овощи` -- one word inflected and the actual head left
# standing. A dependent noun inside a Russian food name is genitive.
FROZEN_CASES = frozenset({"gent"})

CYRILLIC = set("абвгдежзийклмнопрстуфхцчшщъыьэюяё")
CONSONANTS = set("бвгджзйклмнпрстфхцчшщ")
NAME_CHARACTERS = CYRILLIC | {" ", "-"}


class GeneratorError(Exception):
    """A failure the operator has to fix, reported instead of a thin corpus."""


# ---------------------------------------------------------------------------
# Shared helpers, imported rather than copied.
# ---------------------------------------------------------------------------

SHARED_NAMES = (
    "normalize_for_search",
    "tokenize_normalized",
    "is_clean_term",
    "kotlin_string",
    "render_call",
    "render_string_list",
    "chunk_blocks",
    "check_output",
    "CatalogTokens",
)


def load_shared():
    """Borrow the meal-text generator's string helpers instead of re-deriving them.

    `normalize_for_search` and `tokenize_normalized` are a mirror of
    CatalogSearch.kt: they decide whether a query token can reach a record
    token, so a second copy here would be two code paths that must agree, which
    is the drift AGENTS.md names. One mirror, imported.
    """
    if not SIBLING_GENERATOR.exists():
        raise GeneratorError(f"{SIBLING_GENERATOR} is missing; it owns the shared helpers")
    spec = importlib.util.spec_from_file_location("meal_text_corpus", SIBLING_GENERATOR)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    missing = [name for name in SHARED_NAMES if not hasattr(module, name)]
    if missing:
        raise GeneratorError(
            f"{SIBLING_GENERATOR.name} no longer exports {missing}. Those helpers "
            "mirror CatalogSearch.kt and this generator reads them from there on "
            "purpose; re-point it rather than copying them back."
        )
    return module


def load_analyzer():
    """Import pymorphy3 late, so --help works without it, and fail loudly.

    An absent dependency must not degrade into a hand-written corpus. Endings
    typed by a person are the failure this file exists to remove, and they would
    look identical in the output to endings the dictionary produced.
    """
    try:
        import pymorphy3
    except ImportError as error:
        raise GeneratorError(
            "pymorphy3 is not installed, so no Russian phrase corpus can be built.\n"
            "  This generator refuses to fall back on hand-written endings: a\n"
            "  hand-written paradigm is indistinguishable in the output from a\n"
            "  dictionary one, and wrong about half the time.\n"
            "  It is a BUILD-TIME dependency and must never reach mobile/ or a device:\n"
            "      python3 -m venv /tmp/ru-phrases\n"
            "      /tmp/ru-phrases/bin/pip install pymorphy3\n"
            "      /tmp/ru-phrases/bin/python scripts/generate-russian-phrase-corpus.py"
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


def fold_yo(value):
    """The `е` spelling the synonym table and most typists use."""
    return value.replace("ё", "е").replace("Ё", "Е")


# ---------------------------------------------------------------------------
# The quantity vocabulary, read from the product rather than restated.
# ---------------------------------------------------------------------------


def read_quantity_words(path):
    """Cyrillic members of QUANTITY_WORDS in TextFragments.kt.

    Read rather than copied, for the reason AGENTS.md gives about the duplicated
    quantity vocabulary that drifted within days. A measure this generator used
    but the product did not know would make every case built on it fail twice,
    and a reader could not tell which failure was the morphology.
    """
    text = path.read_text(encoding="utf-8")
    match = re.search(r"QUANTITY_WORDS:\s*Set<String>\s*=\s*setOf\((.*?)\n\)", text, re.S)
    if not match:
        raise GeneratorError(f"QUANTITY_WORDS not found in {path}")
    words = set(re.findall(r'"([^"]+)"', match.group(1)))
    russian = {word for word in words if set(word) <= CYRILLIC}
    if not russian:
        raise GeneratorError(f"{path} declares no Cyrillic quantity words")
    return russian


def read_separator_words(path):
    """Cyrillic members of CONJUNCTIONS and WEAK_SEPARATORS in TextFragments.kt.

    Read for the same anti-drift reason as the quantity words, and used for one
    decision: a record whose own name contains one of these -- `фрукты и ягоды`
    is a single AGROVOC concept -- never has a case built from it. The segmenter
    splits such a query after it fails to resolve whole, so `из фруктов и ягод`
    has no single well-defined expectation, and the inflection under test would
    not be what the case measured. The record stays in the catalog, because a
    name like that is a real source of ambiguity the guards should still see.
    """
    found = set()
    for name in ("CONJUNCTIONS", "WEAK_SEPARATORS"):
        match = re.search(rf"{name}\s*=\s*setOf\((.*?)\n\)", path.read_text(encoding="utf-8"), re.S)
        if not match:
            raise GeneratorError(f"{name} not found in {path}")
        found |= set(re.findall(r'"([^"]+)"', match.group(1)))
    russian = {word for word in found if set(word) <= CYRILLIC}
    if not russian:
        raise GeneratorError(f"{path} declares no Cyrillic separators")
    return russian


# ---------------------------------------------------------------------------
# Morphology.
# ---------------------------------------------------------------------------


class Morphology:
    """Every question this generator asks the dictionary, in one place."""

    def __init__(self, analyzer):
        self.analyzer = analyzer
        self._parses = {}
        self._lemmas = {}

    def parses(self, word):
        if word not in self._parses:
            self._parses[word] = self.analyzer.parse(word)
        return self._parses[word]

    def readings(self, word, parts, case=None):
        found = []
        for parse in self.parses(word):
            if parse.tag.POS not in parts:
                continue
            if case is not None and parse.tag.case != case:
                continue
            if PROPER_GRAMMEMES & set(parse.tag.grammemes):
                continue
            found.append(parse)
        return found

    def lemmas(self, word):
        """Every lemma this token could belong to, plus the token itself.

        Used only to decide whether two names could collide under a perfect
        lemmatizer, so it is deliberately generous: a reading missed here is a
        collision missed, and a collision missed is a wrong expectation.
        """
        if word not in self._lemmas:
            found = {fold_yo(word)}
            for parse in self.parses(word):
                found.add(fold_yo(parse.normal_form))
            self._lemmas[word] = found
        return self._lemmas[word]


def agrees(modifier, head):
    if modifier.tag.number != head.tag.number:
        return False
    if head.tag.number == "sing" and modifier.tag.gender != head.tag.gender:
        return False
    return True


class Phrase:
    """A Russian name split into the word that inflects and the words that do not.

    [head] is the index of the noun the phrase is about, [modifiers] the
    adjectives that agree with it, and [frozen] the dependents already sitting in
    a case of their own -- `бамбука` in `ростки бамбука` -- which never move.
    """

    __slots__ = ("words", "head", "head_parse", "modifiers", "frozen", "source")

    def __init__(self, words, head, head_parse, modifiers, frozen, source):
        self.words = words
        self.head = head
        self.head_parse = head_parse
        self.modifiers = modifiers
        self.frozen = frozen
        self.source = source

    @property
    def gender(self):
        return self.head_parse.tag.gender

    @property
    def number(self):
        return self.head_parse.tag.number

    @property
    def animacy(self):
        return self.head_parse.tag.animacy or "inan"

    @property
    def has_modifiers(self):
        return bool(self.modifiers)


def analyze_phrase(morph, name):
    """Find the head noun and classify every other word, or give up.

    Heads are tried left to right and the first complete classification wins,
    which is what puts `лосось` before `атлантический` in `лосось атлантический`
    and still lets `напитки` head `алкогольные напитки`: `алкогольные` has no
    nominative noun reading, so position 0 never classifies.

    Giving up is the common case for Latin names and indeclinables, and it is
    the right answer for them. A guessed paradigm is not Russian.
    """
    words = name.split()
    if not words or len(words) > 3:
        return None
    for known_first in (True, False):
        phrase = analyze_with(morph, words, known_first)
        if phrase is not None:
            return phrase
    return None


def analyze_with(morph, words, known_only):
    """One pass of [analyze_phrase], over dictionary heads first and guesses second.

    pymorphy3 answers for a word it has never seen by guessing, and its guess for
    an unfamiliar adjective is often a noun. Taking dictionary heads first keeps
    such a guess from outranking the real head standing next to it.
    """
    for index in range(len(words)):
        readings = morph.readings(words[index], HEAD_PARTS, "nomn")
        for head_parse in readings:
            if known_only and not head_parse.is_known:
                continue
            modifiers = {}
            frozen = set()
            complete = True
            for position, word in enumerate(words):
                if position == index:
                    continue
                agreeing = [
                    parse
                    for parse in morph.readings(word, MODIFIER_PARTS, "nomn")
                    if agrees(parse, head_parse)
                ]
                if agreeing:
                    modifiers[position] = agreeing[0]
                    continue
                if position > index and any(
                    parse.tag.case in FROZEN_CASES
                    for parse in morph.readings(word, HEAD_PARTS + MODIFIER_PARTS)
                ):
                    frozen.add(position)
                    continue
                complete = False
                break
            if not complete:
                continue
            if modifiers and head_parse.tag.number == "sing" and head_parse.tag.gender is None:
                continue
            return Phrase(words, index, head_parse, modifiers, frozen, " ".join(words))
    return None


def modifier_form(parse, case, number, gender, animacy):
    """The agreeing form of an adjective, chosen rather than guessed.

    Animacy is requested only where Russian distinguishes it -- the plural and
    the masculine singular accusative. Asking for it on a feminine singular
    over-constrains the request and pymorphy3 answers with a masculine form:
    `чёрная` + {accs, inan} yields `чёрный`, which is not an inflection of the
    phrase at all.
    """
    wanted = {case, number}
    if number == "sing":
        wanted.add(gender)
    if case == "accs" and (number == "plur" or gender == "masc"):
        wanted.add(animacy)
    return parse.inflect(wanted)


def inflect(phrase, case, number=None, keep_yo=False):
    """The whole phrase in [case], with agreement, or None when it has no such form.

    None is a real answer and is always honoured: `мясо` is singular-only, so it
    has no genitive plural, and a corpus that wrote one would be asserting a word
    that does not exist.

    The accusative additionally requires a head the dictionary actually knows.
    Animate nouns take the genitive for their accusative, and a paradigm pymorphy3
    guessed does not reliably carry that: `рыбы-сабли` is tagged animate and its
    guessed accusative plural comes back as `рыб-сабли`, where Russian has
    `рыб-сабель`. A wrong form under a correct expectation is worse than no case.
    """
    if case == "accs" and not phrase.head_parse.is_known:
        return None
    target_number = number or phrase.number
    head = phrase.head_parse.inflect({case, target_number})
    if head is None:
        return None
    out = []
    for position, word in enumerate(phrase.words):
        if position == phrase.head:
            out.append(head.word)
        elif position in phrase.modifiers:
            form = modifier_form(
                phrase.modifiers[position], case, target_number, phrase.gender, phrase.animacy,
            )
            if form is None:
                return None
            out.append(form.word)
        else:
            out.append(word)
    rendered = " ".join(out)
    return rendered if keep_yo else fold_yo(rendered)


# ---------------------------------------------------------------------------
# Records, and the two indexes every guard consults.
# ---------------------------------------------------------------------------


def load_concepts(path):
    with path.open(encoding="utf-8") as handle:
        document = json.load(handle)
    if document.get("schema_version") != 1:
        raise GeneratorError(f"unexpected schema_version in {path}")
    if "ru" not in document.get("languages", []):
        raise GeneratorError(f"{path} carries no Russian")
    return document["concepts"], document.get("provenance", {})


def russian_terms(concept):
    section = concept.get("ru", {})
    return section.get("display", ""), list(section.get("match", []))


def is_plain_russian(shared, term):
    return bool(term) and shared.is_clean_term(term) and set(term) <= NAME_CHARACTERS


def select_concepts(shared, morph, concepts, limit):
    """Concepts whose Russian display name is clean, short and analysable.

    Ordered by how many Russian match terms they carry, so the records with the
    most alternate names -- the ones a real query is most likely to reach by
    something other than the display name -- are the ones kept.
    """
    eligible = []
    for concept in concepts:
        display, matches = russian_terms(concept)
        if not is_plain_russian(shared, display):
            continue
        phrase = analyze_phrase(morph, display)
        if phrase is None:
            continue
        eligible.append((concept, phrase, len(matches)))
    eligible.sort(key=lambda item: (-item[2], item[0]["id"]))
    return eligible[:limit]


def build_records(shared, selected, separators):
    """One record per concept, carrying nominative Russian and nothing else."""
    records = []
    for concept, phrase, _ in selected:
        display, matches = russian_terms(concept)
        seen = {shared.normalize_for_search(display)}
        alternates = []
        for term in matches:
            if not shared.is_clean_term(term):
                continue
            folded = shared.normalize_for_search(term)
            if folded in seen:
                continue
            seen.add(folded)
            alternates.append(term)
        records.append({
            "id": ITEM_ID_PREFIX + concept["id"].split(":", 1)[1],
            "display": display,
            "alternates": alternates,
            "forms": seen,
            "phrase": phrase,
            "caseable": not (set(display.lower().split()) & separators),
        })
    return records


def build_form_index(shared, records):
    """Searchable form to the records that carry it, for the shared-name guard."""
    index = {}
    for record in records:
        for form in record["forms"]:
            index.setdefault(form, set()).add(record["id"])
    return index


class LemmaIndex:
    """Which records could satisfy a token once both sides fold to lemmas.

    This is the guard that makes `expected` defensible. A positive case is only
    emitted when exactly one record could answer its food tokens under a
    lemmatizer that worked perfectly -- which is the thing the corpus measures
    the absence of, so it has to be computed here rather than observed.
    """

    def __init__(self, shared, morph, records):
        self.shared = shared
        self.morph = morph
        self.by_lemma = {}
        for record in records:
            for form in record["forms"]:
                for token in shared.tokenize_normalized(form):
                    for lemma in morph.lemmas(token):
                        self.by_lemma.setdefault(lemma, set()).add(record["id"])

    def candidates(self, token):
        found = set()
        for lemma in self.morph.lemmas(token):
            found |= self.by_lemma.get(lemma, set())
        return found

    def resolvers(self, tokens):
        """Records that could satisfy every one of [tokens]."""
        found = None
        for token in tokens:
            hits = self.candidates(token)
            found = hits if found is None else (found & hits)
            if not found:
                return set()
        return found or set()


# ---------------------------------------------------------------------------
# Cases.
# ---------------------------------------------------------------------------


class Case:
    __slots__ = ("query", "expected", "language", "kind", "because")

    def __init__(self, query, expected, kind, because):
        self.query = query
        self.expected = expected
        self.language = "ru"
        self.kind = kind
        self.because = because


class CaseBuilder:
    """Builds a segment only when every guard passes, and counts the refusals."""

    def __init__(self, shared, morph, form_index, lemma_index, catalog_tokens):
        self.shared = shared
        self.morph = morph
        self.form_index = form_index
        self.lemma_index = lemma_index
        self.catalog_tokens = catalog_tokens
        self.rejected = {
            "shared name": 0,
            "unchanged by inflection": 0,
            "ambiguous lemmas": 0,
            "no such form": 0,
            "scaffolding resolves": 0,
        }

    def tokens_of(self, text):
        return self.shared.tokenize_normalized(self.shared.normalize_for_search(text))

    def has_unique_name(self, record):
        folded = self.shared.normalize_for_search(record["display"])
        if self.form_index.get(folded, set()) != {record["id"]}:
            self.rejected["shared name"] += 1
            return False
        return True

    def food_is_inflected(self, record, inflected):
        """The emitted phrase must not already be reachable without the fold.

        Checked against every form the record carries, not just its display name,
        and by the rule TOKEN_SUBSET actually applies: a case is refused when some
        stored form of this record satisfies all of the inflected tokens by plain
        equality. Two different things get caught by that, and both would make the
        corpus lie about what it measures.

        The accusative of an inanimate noun is its nominative, so half the
        `съел X` cases would otherwise be the catalog's own string with a verb in
        front -- the very flaw this corpus replaces. Less obviously, some
        alternate names are themselves inflected (`зерно кумина` carries a
        genitive), so a query could resolve today through a synonym while looking
        like a test of morphology. Six cases were passing that way before this
        check compared against all forms.
        """
        tokens = self.tokens_of(inflected)
        for form in record["forms"]:
            form_tokens = set(self.shared.tokenize_normalized(form))
            if form_tokens and all(token in form_tokens for token in tokens):
                self.rejected["unchanged by inflection"] += 1
                return False
        return True

    def names_one_record(self, record, text):
        if self.lemma_index.resolvers(self.tokens_of(text)) != {record["id"]}:
            self.rejected["ambiguous lemmas"] += 1
            return False
        return True

    def food_segment(self, record, case, number=None):
        """An inflected food that has survived every guard, or None."""
        if not record["caseable"] or not self.has_unique_name(record):
            return None
        inflected = inflect(record["phrase"], case, number)
        if inflected is None:
            self.rejected["no such form"] += 1
            return None
        if not self.food_is_inflected(record, inflected):
            return None
        if not self.names_one_record(record, inflected):
            return None
        return inflected

    def nominative_segment(self, record):
        """The record's own name, for the half of a two-item case that is not inflected."""
        if not record["caseable"] or not self.has_unique_name(record):
            return None
        if not self.names_one_record(record, record["display"]):
            return None
        return record["display"]

    def yo_variant(self, record, case, number=None):
        """The same form as the dictionary spells it, when that spelling uses `ё`.

        Only ever offered as a swap for a form that already passed every guard in
        its `е` spelling, so a `ё` case still measures the inflection rather than
        only the spelling.
        """
        spelled = inflect(record["phrase"], case, number, keep_yo=True)
        return spelled if spelled and "ё" in spelled else None

    def scaffolding_is_clear(self, words):
        """No preposition, numeral or measure may name a food by itself."""
        for word in words:
            tokens = self.tokens_of(word)
            if tokens and self.lemma_index.resolvers(tokens):
                self.rejected["scaffolding resolves"] += 1
                return False
        return True


# Single-food constructions, each naming the case it governs.
SINGLE_CONSTRUCTIONS = [
    ("", "gent", "bare genitive, what a half-typed amount leaves behind"),
    ("", "ablt", "bare instrumental, what trimming `с` leaves behind"),
    ("", "accs", "bare accusative; emitted only where it differs from the nominative"),
    ("с", "ablt", "instrumental governed by the preposition `с`"),
    ("из", "gent", "genitive governed by the preposition `из`"),
    ("без", "gent", "genitive governed by the preposition `без`"),
    ("ел", "accs", "accusative object of the verb `ел`"),
    ("съел", "accs", "accusative object of the verb `съел`"),
    ("добавил", "accs", "accusative object of the verb `добавил`"),
    ("приготовил", "accs", "accusative object of the verb `приготовил`"),
]

# Whether a construction counts its food or measures it out.
#
# Countability is read off the catalog rather than decided here: AGROVOC names a
# countable food in the plural (`бананы`, `морские окуни`) and a mass one in the
# singular (`свинина`, `гречневая мука`). Ignoring the difference is how a
# generator produces `семь баранин` -- a form pymorphy3 will happily build and no
# Russian speaker will ever type. It is a heuristic, and it errs toward emitting
# nothing: `лосось атлантический` is countable and singular in the catalog, so it
# never gets a numeral here.
ANY_FOOD, MASS_ONLY, COUNT_ONLY = "any", "mass", "count"

# A glass holds a liquid and a slice does not, and nothing in the synonym table
# says which a food is, so the head lemmas that pour are named here. Without it
# the generator emits `кусок ананасового сока` -- morphologically perfect and not
# a thing anyone types, which is the same class of defect as `две луцианы`.
#
# This is a floor, not a texture model. `ломтик фасоли адзуки` survives it,
# because nothing available here knows that adzuki beans do not come in slices.
LIQUID_ONLY, SOLID_MASS = "liquid", "solid"
LIQUID_HEADS = frozenset({
    "масло", "сок", "молоко", "вода", "напиток", "сироп", "уксус", "бульон",
    "пиво", "вино", "сливки", "мед", "сыворотка", "настой", "отвар",
})

# Measure phrases, every measure already in QUANTITY_WORDS, all governing the
# genitive. A `None` number means the number the catalog holds: `200 граммов
# свинины` is singular because `свинина` is, and `200 граммов морских окуней` is
# plural because `морские окуни` is. Changing the number would invent a reading
# of the record, which is a different claim from inflecting it.
MEASURE_CONSTRUCTIONS = [
    ("200 граммов", None, ANY_FOOD, "genitive governed by the measure `граммов`"),
    ("150 грамма", None, ANY_FOOD, "genitive governed by the measure `грамма`"),
    ("100 грамм", None, ANY_FOOD, "genitive after the colloquial genitive plural `грамм`"),
    ("порция", None, ANY_FOOD, "genitive governed by the measure `порция`"),
    ("две порции", None, ANY_FOOD, "genitive governed by the measure `порции`"),
    ("половина", None, ANY_FOOD, "genitive governed by `половина`"),
    ("стакан", None, LIQUID_ONLY, "genitive governed by the measure `стакан`"),
    ("два стакана", None, LIQUID_ONLY, "genitive governed by the measure `стакана`"),
    ("ложка", None, MASS_ONLY, "genitive governed by the measure `ложка`"),
    ("кусок", None, SOLID_MASS, "genitive governed by the measure `кусок`"),
    ("три куска", None, SOLID_MASS, "genitive governed by the measure `куска`"),
    ("ломтик", None, SOLID_MASS, "genitive governed by the measure `ломтик`"),
    ("пара", "plur", COUNT_ONLY, "genitive plural governed by `пара`"),
]

# The numeral's own genitive, which puts noun and adjectives alike in the
# genitive plural. Absent from QUANTITY_WORDS: each reaches it through the same
# fold as the food, which the `because` on every one of these cases says.
OBLIQUE_NUMERALS = ["двух", "трех", "четырех", "пяти", "шести", "семи"]

# Five and above take the genitive plural throughout. All are in QUANTITY_WORDS.
LARGE_NUMERALS = ["пять", "шесть", "семь", "восемь", "девять", "десять"]

# The paucal, gendered. Built only for names with no adjective: see the header.
PAUCAL = {"masc": "два", "neut": "два", "femn": "две"}
PAUCAL_EXTRA = ["три", "четыре"]

YO_NOTE = ", `ё` as OpenCorpora spells it"


def with_preposition(following):
    """`с` or its allomorph `со`, chosen by what the next word starts with.

    Russian takes `со` before a cluster opening with с, з, ш or ж plus another
    consonant, and before `льд-`, `мн-` and `вс-`. `с свеклой` is not a spelling
    variant, it is a mistake, and a corpus of mistakes measures the wrong thing.
    Both forms are already in TextFragments.WEAK_SEPARATORS, so neither is a new
    word for the segmenter to learn.
    """
    word = following.lstrip()
    if word[:3] in ("льд", "мно", "мне", "вся") or word[:2] == "вс":
        return "со"
    if len(word) >= 2 and word[0] in "сзшж" and word[1] in CONSONANTS:
        return "со"
    return "с"


def usable(query):
    return bool(query) and len(query) <= MAX_QUERY


def is_countable(record):
    """True when the catalog names this food in the plural. See ANY_FOOD above."""
    return record["phrase"].number == "plur"


def pours(record):
    """True when the head of this name is something a glass could hold."""
    return fold_yo(record["phrase"].head_parse.normal_form) in LIQUID_HEADS


def in_scope(record, scope):
    if scope == COUNT_ONLY:
        return is_countable(record)
    if scope == MASS_ONLY:
        return not is_countable(record)
    if scope == LIQUID_ONLY:
        return pours(record)
    if scope == SOLID_MASS:
        return not is_countable(record) and not pours(record)
    return True


def build_single_cases(builder, records, limit):
    """One food in an oblique case, bare or after a preposition or verb."""
    cases = []
    seen = set()
    kept_yo = 0
    for round_index, _ in enumerate(SINGLE_CONSTRUCTIONS):
        for offset, record in enumerate(records):
            if len(cases) >= limit:
                return cases
            prefix, case, why = SINGLE_CONSTRUCTIONS[
                (offset + round_index) % len(SINGLE_CONSTRUCTIONS)
            ]
            food = builder.food_segment(record, case)
            if food is None:
                continue
            keep_yo = False
            if kept_yo < YO_QUOTA:
                spelled = builder.yo_variant(record, case)
                if spelled is not None:
                    food, keep_yo = spelled, True
            if prefix == "с":
                prefix = with_preposition(food)
            if prefix and not builder.scaffolding_is_clear([prefix]):
                continue
            query = f"{prefix} {food}".strip()
            if not usable(query) or query in seen:
                continue
            seen.add(query)
            if keep_yo:
                kept_yo += 1
            cases.append(Case(
                query, [record["id"]], "ru-inflected-single", why + (YO_NOTE if keep_yo else ""),
            ))
    return cases


def quantity_shapes(builder, record, rotation):
    """The four quantity shapes, each with the case its amount governs."""
    shapes = []

    prefix, number, scope, why = MEASURE_CONSTRUCTIONS[rotation % len(MEASURE_CONSTRUCTIONS)]
    if in_scope(record, scope) and builder.scaffolding_is_clear(prefix.split()):
        food = builder.food_segment(record, "gent", number)
        if food is not None:
            shapes.append((f"{prefix} {food}", why))

    if is_countable(record):
        numeral = OBLIQUE_NUMERALS[rotation % len(OBLIQUE_NUMERALS)]
        if builder.scaffolding_is_clear([numeral]):
            food = builder.food_segment(record, "gent", "plur")
            if food is not None:
                shapes.append((
                    f"{numeral} {food}",
                    f"genitive plural after the oblique numeral `{numeral}`, itself no quantity word",
                ))

        numeral = LARGE_NUMERALS[rotation % len(LARGE_NUMERALS)]
        if builder.scaffolding_is_clear([numeral]):
            food = builder.food_segment(record, "gent", "plur")
            if food is not None:
                shapes.append((f"{numeral} {food}", f"genitive plural governed by `{numeral}`"))

        phrase = record["phrase"]
        if not phrase.has_modifiers and phrase.gender in PAUCAL:
            choices = [PAUCAL[phrase.gender]] + PAUCAL_EXTRA
            numeral = choices[rotation % len(choices)]
            if builder.scaffolding_is_clear([numeral]):
                food = builder.food_segment(record, "gent", "sing")
                if food is not None:
                    shapes.append((
                        f"{numeral} {food}",
                        f"genitive singular, the Russian paucal after `{numeral}`",
                    ))
    return shapes


def build_quantity_cases(builder, records, limit):
    cases = []
    seen = set()
    for round_index in range(4):
        for offset, record in enumerate(records):
            if len(cases) >= limit:
                return cases
            shapes = quantity_shapes(builder, record, offset + round_index)
            if not shapes:
                continue
            query, why = shapes[(offset + round_index) % len(shapes)]
            if not usable(query) or query in seen:
                continue
            seen.add(query)
            cases.append(Case(query, [record["id"]], "ru-inflected-quantity", why))
    return cases


def two_item_shapes(builder, first, second):
    """Two-food shapes, each with at least one food carrying a real case."""
    shapes = []

    nominative = builder.nominative_segment(first)
    instrumental = builder.food_segment(second, "ablt")
    if nominative is not None and instrumental is not None:
        shapes.append((
            f"{nominative} {with_preposition(instrumental)} {instrumental}",
            "second food instrumental after `с`; the first is nominative, as typed",
        ))

    left, right = builder.food_segment(first, "ablt"), builder.food_segment(second, "ablt")
    if left is not None and right is not None:
        shapes.append((
            f"{with_preposition(left)} {left} и {right}",
            "both foods instrumental, under one `с` carried across the conjunction",
        ))

    left, right = builder.food_segment(first, "gent"), builder.food_segment(second, "gent")
    if left is not None and right is not None:
        shapes.append((
            f"200 граммов {left} и 100 граммов {right}",
            "two measured amounts, each governing the genitive of its own food",
        ))
        shapes.append((
            f"без {left} и {right}",
            "both foods genitive, under one `без` carried across the conjunction",
        ))

    if is_countable(first) and is_countable(second):
        left = builder.food_segment(first, "gent", "plur")
        right = builder.food_segment(second, "gent", "plur")
        if left is not None and right is not None:
            shapes.append((
                f"двух {left} и трех {right}",
                "genitive plural on both, after the oblique numerals `двух` and `трех`",
            ))

    left, right = builder.food_segment(first, "accs"), builder.food_segment(second, "accs")
    if left is not None and right is not None:
        shapes.append((
            f"съел {left} и {right}",
            "two accusative objects, both differing from the nominative the catalog holds",
        ))
    return shapes


def build_two_item_cases(builder, records, limit):
    cases = []
    seen = set()
    total = len(records)
    if total < 2:
        return cases
    for stride in (17, 41, 89):
        for index in range(total):
            if len(cases) >= limit:
                return cases
            first = records[index]
            second = records[(index + stride) % total]
            if first["id"] == second["id"]:
                continue
            shapes = two_item_shapes(builder, first, second)
            if not shapes:
                continue
            query, why = shapes[index % len(shapes)]
            if not usable(query) or query in seen:
                continue
            seen.add(query)
            cases.append(Case(
                query, [first["id"], second["id"]], "ru-inflected-two-item", why,
            ))
    return cases


# Everyday Russian nouns that name no food. Inflected here for the same reason
# the foods are: a negative written in the nominative would only show that the
# catalog does not carry the word, not that an inflected form cannot reach it.
NON_FOOD_NOUNS = [
    "телефон", "ноутбук", "подушка", "зонтик", "чемодан", "карандаш",
    "будильник", "занавеска", "табуретка", "пылесос", "галстук", "велосипед",
    "молоток", "калитка", "варежка", "рюкзак", "простыня", "лампочка",
]

# Grammatical scaffolding standing alone. Each is a word this corpus uses to
# build a real case; by itself it names no food and must answer nothing.
BARE_SCAFFOLDING = [
    ("двух", "the oblique numeral alone, quantifying nothing"),
    ("трех", "the oblique numeral alone, quantifying nothing"),
    ("пяти", "the oblique numeral alone, quantifying nothing"),
    ("четырех", "the oblique numeral alone, quantifying nothing"),
    ("граммов", "a measure alone, measuring nothing"),
    ("порции", "a measure alone, measuring nothing"),
    ("стакана", "a measure alone, measuring nothing"),
    ("куска", "a measure alone, measuring nothing"),
    ("ломтика", "a measure alone, measuring nothing"),
    ("ложки", "a measure alone, measuring nothing"),
    ("половины", "a measure alone, measuring nothing"),
    ("из", "a preposition alone"),
    ("без", "a preposition alone"),
    ("двух граммов", "an amount with nothing to amount to"),
    ("пяти порций", "an amount with nothing to amount to"),
    ("200 граммов", "a measured amount naming no food"),
]

# Cyrillic strings shaped like inflected Russian and belonging to no word. A
# negative that merely looked foreign would not test the fold; these decline
# plausibly and still mean nothing.
NONSENSE_INFLECTED = [
    "фырвалом", "щуцкеней", "гдьжэяха", "пшыжвртом", "зюмблякой", "хрюндиков",
    "плюрсканью", "брынтозлами", "квендишей", "трафквиксом", "шморгалях",
    "вздрюпнями",
]


def build_negative_cases(builder, shared, morph, concepts, selected_ids, limit):
    """Queries a correct resolver answers with nothing at all.

    Two conditions, both required. The lemmas must reach no record, which is
    what a working lemmatizer would decide, and every token must be beyond the
    fuzzy tier's reach, which is what the matcher decides today. A negative that
    satisfied only the second would start resolving the moment the fold lands.
    """
    cases = []
    seen = set()

    def accept(query, why):
        if len(cases) >= limit or not usable(query) or query in seen:
            return
        tokens = builder.tokens_of(query)
        if not tokens or builder.lemma_index.resolvers(tokens):
            return
        if not all(builder.catalog_tokens.is_far(token) for token in tokens):
            return
        seen.add(query)
        cases.append(Case(query, [], "must-not-resolve", why))

    for word, why in BARE_SCAFFOLDING:
        accept(word, why)

    for word in NONSENSE_INFLECTED:
        accept(word, "shaped like an inflected Russian word and belonging to none")

    for index, word in enumerate(NON_FOOD_NOUNS):
        phrase = analyze_phrase(morph, word)
        if phrase is None:
            continue
        oblique = inflect(phrase, ("gent", "ablt", "accs")[index % 3])
        if oblique is None or oblique == word:
            continue
        if index % 2:
            accept(oblique, "an everyday Russian word that names no food, in an oblique case")
            continue
        plural = inflect(phrase, "gent", "plur")
        if plural is not None:
            accept(f"двух {plural}", "a numeral in front of a word that names no food")

    for concept in concepts:
        identifier = ITEM_ID_PREFIX + concept["id"].split(":", 1)[1]
        if identifier in selected_ids:
            continue
        display, _ = russian_terms(concept)
        if not is_plain_russian(shared, display):
            continue
        phrase = analyze_phrase(morph, display)
        if phrase is None:
            continue
        genitive = inflect(phrase, "gent")
        if genitive is None:
            continue
        accept(genitive, "a real Russian food this catalog does not carry, in the genitive")
    return cases


# ---------------------------------------------------------------------------
# Kotlin emission.
# ---------------------------------------------------------------------------


def render_record(shared, record, indent):
    return shared.render_call(
        "CatalogRecord",
        [
            [shared.kotlin_string(record["id"])],
            [shared.kotlin_string(record["display"])],
            shared.render_string_list(record["alternates"], indent + 4),
        ],
        indent,
    )


def render_case(shared, case, indent):
    return shared.render_call(
        "MealTextCase",
        [
            [shared.kotlin_string(case.query)],
            shared.render_string_list(case.expected, indent + 4),
            [shared.kotlin_string(case.language)],
            [shared.kotlin_string(case.kind)],
            [shared.kotlin_string(case.because)],
        ],
        indent,
    )


HEADER_TEMPLATE = '''package com.supernutrition.domain.nutrition

// GENERATED FILE. Produced by scripts/generate-russian-phrase-corpus.py from
// mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json,
// the Cyrillic entries of QUANTITY_WORDS in TextFragments.kt, and the OpenCorpora
// dictionary via pymorphy3.
// Do not edit by hand: the next run of the generator overwrites every line.
//
// PROVENANCE
//   inflection   OpenCorpora dictionary via pymorphy3 {pymorphy_version}
//                (source {source} {source_version} rev {source_revision})
//   word list    the shipped synonym table, generated {synonym_generated}
//
// LICENCE -- THREE SEPARATE OBLIGATIONS, DELIBERATELY NOT MERGED.
//   * The inflected forms derive from the OpenCorpora dictionary. pymorphy3-dicts-ru
//     declares MIT in its own metadata; OpenCorpora publishes the dictionary under
//     CC BY-SA. Those are not the same obligation, and if share-alike applies it
//     attaches to THIS CORPUS. Recorded here, unresolved: a question for counsel.
//   * The Russian words come from the AGROVOC Multilingual Thesaurus, (c) Food and
//     Agriculture Organization of the United Nations (FAO), licensed under
//     CC-BY IGO 3.0 -- https://creativecommons.org/licenses/by/3.0/igo/. That
//     credit is a licence condition, not a courtesy.
//   * Colloquial and regional names come from Wikidata, CC0 1.0, which imposes no
//     attribution requirement. See ATTRIBUTION.md beside the source JSON.
'''

CATALOG_DOC_TEMPLATE = '''/**
 * The records the Russian phrase cases resolve against: nominative Russian only.
 *
 * {records} of them, each carrying the Russian display name as its own
 * [CatalogRecord.displayName], with all {alternates} alternates Russian match
 * terms -- because that is what a real catalog holds, one citation form per
 * name. No English or Spanish name appears, so a case can only be answered
 * through the language its query is written in; mixing them in would let
 * `с куриным мясом` resolve through `chicken meat` and put the Russian score
 * straight back where it was.
 *
 * A few records carry a name containing `и`, which AGROVOC really does use
 * inside a single concept (`фрукты и ягоды`). No case is built from those --
 * the segmenter would split the query and the expectation would stop being
 * well defined -- but they stay here, because a name like that is a real source
 * of ambiguity the corpus should have to search past.
 *
 * The ids are the canonical ids [corpusCatalog] uses, so a resolver reading both
 * corpora sees one set of foods rather than two.
 */'''

CASES_DOC_TEMPLATE = '''/**
 * {total} cases of Russian meal text as it is actually typed, in the case the
 * construction governs.
 *
 * WHAT THIS REPLACES. [corpusCases] builds its Russian quantity cases by gluing a
 * quantity word onto an untouched nominative display name -- `две луцианы`, `два
 * акулы`. Those are not Russian: `две` governs the genitive, and `луциан` is
 * masculine. Strip the quantity word and what is left is the record's own
 * spelling, which the EXACT tier answers with no Russian support at all. That is
 * why the suite reports a high Russian score while no lemmatizer exists.
 *
 * Here the food carries the case: `двух морских окуней`, `200 граммов свинины`,
 * `с куриным мясом`, `три банана`. Not one of those endings was written by hand
 * -- each is pymorphy3's answer from the OpenCorpora dictionary for the case
 * named in the case's own `because`, with adjectives agreed to their head in
 * case, number, gender, and animacy where the accusative needs it.
 *
 * MOST OF THESE FAIL TODAY, AND THAT IS THE POINT. [expected] is the food a
 * Russian speaker named, never a recording of what the matcher returns. Nothing
 * folds an inflected form to its lemma yet, so `окуней` does not reach `окуни`:
 * EXACT and TOKEN_SUBSET need the same string, PREFIX runs the other way round
 * (`окуни`.startsWith(`окуней`) is false), and the fuzzy tier is bounded below
 * that distance deliberately. No expectation here was relaxed to make a case
 * pass, and no case was dropped for being expected to fail.
 *
 * No positive can resolve to its own record without the fold. Each was checked
 * against every form that record carries -- display name and all alternates --
 * by the rule TOKEN_SUBSET applies, and refused if any stored form already
 * satisfied its tokens by plain equality. That removes two ways a case could
 * look like a test of morphology and not be one: the accusative of an inanimate
 * noun is its nominative, and some AGROVOC synonyms are themselves inflected.
 *
 * The kinds:
 *  - `ru-inflected-single` ({single}) -- one food in an oblique case, bare or
 *    after a preposition or a verb.
 *  - `ru-inflected-quantity` ({quantity}) -- an amount in front of a food, in the
 *    case the amount governs. Every measure and numeral is already in
 *    QUANTITY_WORDS except the oblique numerals `двух`/`трех`/`пяти`, which reach
 *    it through the same fold as the food; those say so in their `because`.
 *  - `ru-inflected-two-item` ({two_item}) -- two foods, at least one inflected,
 *    joined by `и` or by a preposition carried across it.
 *  - `must-not-resolve` ({negative}) -- inflected words no record answers:
 *    scaffolding alone, everyday non-food nouns, real Russian foods outside this
 *    catalog, and Cyrillic strings that decline plausibly and mean nothing. Each
 *    is unreachable by lemma AND beyond the fuzzy tier, so it stays negative once
 *    the fold lands rather than only while it is missing.
 */'''


def render_file(shared, records, cases, stats):
    lines = stats["header"].split("\n")

    catalog_chunks = shared.chunk_blocks(
        [render_record(shared, record, 8) for record in records], CHUNK_LINE_BUDGET,
    )
    case_chunks = shared.chunk_blocks(
        [render_case(shared, case, 8) for case in cases], CHUNK_LINE_BUDGET,
    )
    catalog_names = [f"RussianPhraseCatalog{index}" for index in range(len(catalog_chunks))]
    case_names = [f"RussianPhraseCases{index}" for index in range(len(case_chunks))]

    lines += CATALOG_DOC_TEMPLATE.format(
        records=stats["records"], alternates=stats["alternates"],
    ).split("\n")
    lines += ["val russianPhraseCatalog: List<CatalogRecord> = listOf("]
    lines += [f"    {name}.records," for name in catalog_names]
    lines += [").flatten()", ""]

    lines += CASES_DOC_TEMPLATE.format(
        total=len(cases),
        single=stats["kinds"].get("ru-inflected-single", 0),
        quantity=stats["kinds"].get("ru-inflected-quantity", 0),
        two_item=stats["kinds"].get("ru-inflected-two-item", 0),
        negative=stats["kinds"].get("must-not-resolve", 0),
    ).split("\n")
    lines += ["val russianPhraseCases: List<MealTextCase> = listOf("]
    lines += [f"    {name}.cases," for name in case_names]
    lines += [").flatten()"]

    for name, chunk in zip(catalog_names, catalog_chunks):
        lines += ["", f"internal object {name} {{", "    val records: List<CatalogRecord> = listOf("]
        for block in chunk:
            lines += block
        lines += ["    )", "}"]

    for name, chunk in zip(case_names, case_chunks):
        lines += ["", f"internal object {name} {{", "    val cases: List<MealTextCase> = listOf("]
        for block in chunk:
            lines += block
        lines += ["    )", "}"]

    text = "\n".join(line.rstrip() for line in lines)
    while "\n\n\n" in text:
        text = text.replace("\n\n\n", "\n\n")
    return text.rstrip("\n") + "\n"


# ---------------------------------------------------------------------------
# Entry point.
# ---------------------------------------------------------------------------


def summarize(records, cases):
    kinds = {}
    for case in cases:
        kinds[case.kind] = kinds.get(case.kind, 0) + 1
    return {
        "records": len(records),
        "alternates": sum(len(record["alternates"]) for record in records),
        "cases": len(cases),
        "kinds": kinds,
    }


def check_quantity_vocabulary(quantity_words):
    """Every measure and numeral used here must be one the product knows.

    A case built on a word QUANTITY_WORDS does not carry fails twice at once --
    once for the unknown quantity word and once for the case ending -- and the
    reader cannot tell which. The oblique numerals are the deliberate exception
    and are excluded here because each case that uses one says so itself.
    """
    declared = {word for shape in MEASURE_CONSTRUCTIONS for word in shape[0].split()}
    declared |= set(LARGE_NUMERALS) | set(PAUCAL.values()) | set(PAUCAL_EXTRA)
    unknown = sorted(word for word in declared if word.isalpha() and word not in quantity_words)
    if unknown:
        raise GeneratorError(
            f"these measures are not in QUANTITY_WORDS: {unknown}. A case built on "
            "one would fail for two reasons at once and name neither."
        )


def report(stats, builder, out_path, size, problems):
    print(f"records      {stats['records']} ({stats['alternates']} alternate names)")
    print(f"cases        {stats['cases']}")
    for kind in sorted(stats["kinds"]):
        print(f"  {kind:<24} {stats['kinds'][kind]}")
    print("refused")
    for reason in sorted(builder.rejected):
        print(f"  {reason:<24} {builder.rejected[reason]}")
    print(f"output       {out_path} ({size} bytes)")
    for problem in problems:
        print(f"PROBLEM      {problem}", file=sys.stderr)


def parse_arguments(argv):
    parser = argparse.ArgumentParser(description="Generate the Russian phrase corpus.")
    parser.add_argument("--out", default=str(OUTPUT_KT))
    parser.add_argument("--concepts", type=int, default=DEFAULT_CONCEPTS)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def generate(args):
    shared = load_shared()
    analyzer, pymorphy3 = load_analyzer()
    morph = Morphology(analyzer)
    check_quantity_vocabulary(read_quantity_words(QUANTITY_SOURCE))
    separators = read_separator_words(QUANTITY_SOURCE)

    concepts, provenance = load_concepts(SOURCE_JSON)
    selected = select_concepts(shared, morph, concepts, args.concepts)
    if len(selected) < 50:
        raise GeneratorError(
            f"only {len(selected)} concepts were analysable; the corpus would be thin"
        )
    records = build_records(shared, selected, separators)
    builder = CaseBuilder(
        shared,
        morph,
        build_form_index(shared, records),
        LemmaIndex(shared, morph, records),
        shared.CatalogTokens(records),
    )

    selected_ids = {record["id"] for record in records}
    cases = build_single_cases(builder, records, CAP_SINGLE)
    cases += build_quantity_cases(builder, records, CAP_QUANTITY)
    cases += build_two_item_cases(builder, records, CAP_TWO_ITEM)
    cases += build_negative_cases(builder, shared, morph, concepts, selected_ids, CAP_NEGATIVE)

    over_long = [case.because for case in cases if len(case.because) > MAX_BECAUSE]
    if over_long:
        raise GeneratorError(
            f"{len(over_long)} `because` strings exceed {MAX_BECAUSE} characters, "
            f"the first being {over_long[0]!r}"
        )

    dictionary = dictionary_provenance(analyzer)
    stats = summarize(records, cases)
    stats["header"] = HEADER_TEMPLATE.format(
        pymorphy_version=pymorphy3.__version__,
        source=dictionary["source"],
        source_version=dictionary["source_version"],
        source_revision=dictionary["source_revision"],
        synonym_generated=provenance.get("generated_utc", "unknown"),
    )
    text = render_file(shared, records, cases, stats)
    problems = shared.check_output(text)

    out_path = Path(args.out)
    if not args.dry_run:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(text, encoding="utf-8")
    report(stats, builder, out_path, len(text.encode("utf-8")), problems)
    return 1 if problems else 0


def main(argv=None):
    args = parse_arguments(argv if argv is not None else sys.argv[1:])
    try:
        return generate(args)
    except GeneratorError as failure:
        print(str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
