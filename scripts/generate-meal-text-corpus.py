#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-meal-text-corpus.py -- BUILD-TIME generator for the multilingual meal
text corpus used by the deterministic catalog search tests.

This script runs at build time only. It never runs on device, and nothing it
touches is a runtime dependency. Its single output is one deterministic Kotlin
file under commonTest:

    mobile/domain/nutrition/src/commonTest/kotlin/com/supernutrition/domain/
    nutrition/MealTextCorpus.kt

    stdlib only. No third-party packages. Python 3.9+.
    (If a future change needs a third-party package, declare it HERE in this
    header rather than assuming the build host has it installed.)


WHY THIS EXISTS
===============
The hand-written fixtures in IdentificationFixtures.kt are a few dozen cases
over an eight-record seed catalog. They are load-bearing -- every must-not-
resolve entry there is a defect that actually shipped -- but they are far too
small to say anything about the trilingual synonym table, which carries 997
concepts and 3661 match terms. A matcher can pass the hand-written set and
still be wrong about almost everything the synonym table knows.

This generator turns the synonym table into a corpus large enough to measure
that: a bounded catalog subset plus roughly fourteen hundred query cases whose
expected answers are stated independently of what the matcher does today.


WHAT `expected` MEANS -- AND WHAT IT DELIBERATELY DOES NOT MEAN
===============================================================
`expected` is the SEMANTICALLY CORRECT answer: the canonical ids a correct
resolver would return, in order. It is NOT a recording of current behaviour.

Two whole classes of case are known to fail against the code as it stands:

  * Spanish and Russian two-item queries. TextFragments.CONJUNCTIONS is
    {"and", "plus", "&"} -- English only -- so "arroz y pollo" is never split
    and resolves to nothing at all.
  * Spanish and Russian quantity queries. QUANTITY_WORDS is English only, so
    "dos huevos" keeps "dos" as a query token and no record satisfies it.

Both are recorded in `knownFailingKinds` in the emitted file. They are left
failing on purpose. A corpus that encoded today's bugs as correct behaviour
would be worse than no corpus: it would make the bug permanent and call it a
regression test.


HOW THE CATALOG SUBSET IS CHOSEN
================================
The full table is 997 concepts. Emitting all of them produces a Kotlin file
large enough to be a compile-time problem of its own, so the subset is bounded
(--concepts, default 300) and chosen by a rule rather than by taste:

  1. eligible   -- all three display names present, free of characters that
                   would complicate a Kotlin literal or the segmenter (comma,
                   quote, backslash, parenthesis, slash, colon, combining
                   marks), and one to three tokens long in every language.
  2. ranked     -- by descending match-term count, ties broken by concept id.
                   Match terms are what the alias cases test, so concepts that
                   carry more of them are worth more per record. The one-to-
                   three-token filter above is what stops this from selecting
                   nothing but long regional fish names.
  3. truncated  -- to --concepts.

Widen it by raising --concepts; the ordering is stable, so a larger subset is a
superset of a smaller one.


HOW AMBIGUITY IS HANDLED
========================
122 normalized forms in a 300-concept subset name more than one concept:
"chicken" is both c_24000 (chicken meat) and c_1540 (chickens), "tuna" is two
different concepts, and so on. A case built on such a term would have no single
correct answer, so it would be testing the tie-break rather than the match.

Those terms stay in the catalog -- dropping them would misrepresent the table --
but no case is ever built from one. A term is usable as a query only when its
normalized form names exactly one record in the emitted subset.


THE STRING HELPERS BELOW ARE NOT A SECOND MATCHER
=================================================
normalize_for_search, tokenize_normalized and edit_distance mirror three small
helpers in CatalogSearch.kt. AGENTS.md is right that two code paths which must
agree should share an implementation rather than be kept in step, so it is worth
being precise about what these are for.

They decide only which strings are *ambiguous* or *too close to a real term to
be a fair negative* -- that is, which cases to emit at all. No expected answer
is ever computed from them. If they drift from the Kotlin, the effect is a
slightly worse choice of test data, never a wrong expectation, and the corpus
stays honest either way. Tier logic, ranking and coverage are deliberately NOT
reimplemented here.


LICENCE
=======
The corpus is derived from mobile/domain/nutrition/src/commonMain/resources/
synonyms/food-synonyms.json, which is itself generated from AGROVOC
(CC-BY IGO 3.0, attribution REQUIRED) and Wikidata (CC0). See ATTRIBUTION.md
beside that JSON. The AGROVOC credit is a licence condition and is repeated in
the header of the emitted Kotlin file.


USAGE
=====
    python3 scripts/generate-meal-text-corpus.py
    python3 scripts/generate-meal-text-corpus.py --concepts 250 --dry-run
"""

import argparse
import bisect
import json
import sys
import unicodedata
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_JSON = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json"
)
OUTPUT_KT = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonTest/kotlin/com/supernutrition"
    / "domain/nutrition/MealTextCorpus.kt"
)

LANGUAGES = ("en", "es", "ru")
ITEM_ID_PREFIX = "food:agrovoc-"

# Caps, so the emitted file stays a test fixture rather than a build problem.
DEFAULT_CONCEPTS = 300
CAP_EXACT = 450
CAP_ALIAS = 600
CAP_TWO_ITEM_PER_LANGUAGE = 40
CAP_QUANTITY_PER_LANGUAGE = 40

MAX_LINE = 120
CHUNK_LINE_BUDGET = 380

# ---------------------------------------------------------------------------
# String helpers mirroring CatalogSearch.kt. See the header: these choose test
# data, they never compute an expected answer.
# ---------------------------------------------------------------------------

ACCENTED_CHARACTERS = "áàâäãåāéèêëēíìîïīóòôöõøōúùûüūñńçćšžýÿđł"
PLAIN_CHARACTERS = "aaaaaaaeeeeeiiiiiooooooouuuuunnccszyydl"
LETTER_OR_DIGIT_CATEGORIES = {"Lu", "Ll", "Lt", "Lm", "Lo", "Nd"}


def normalize_for_search(value):
    """Case- and diacritic-fold, character for character with the Kotlin."""
    expanded = value.lower().replace("ß", "ss").replace("æ", "ae").replace("œ", "oe")
    out = []
    for character in expanded:
        index = ACCENTED_CHARACTERS.find(character)
        out.append(PLAIN_CHARACTERS[index] if index >= 0 else character)
    return "".join(out)


def tokenize_normalized(normalized):
    """Split on anything that is not a letter or digit, Unicode categories and
    all, because Python's str.isalnum() is wider than Kotlin's isLetterOrDigit."""
    tokens = []
    current = []
    for character in normalized:
        if unicodedata.category(character) in LETTER_OR_DIGIT_CATEGORIES:
            current.append(character)
        elif current:
            tokens.append("".join(current))
            current = []
    if current:
        tokens.append("".join(current))
    return tokens


def edit_distance(left, right):
    previous = list(range(len(right) + 1))
    for i in range(1, len(left) + 1):
        current = [i] + [0] * len(right)
        for j in range(1, len(right) + 1):
            substitution = previous[j - 1] + (0 if left[i - 1] == right[j - 1] else 1)
            current[j] = min(substitution, previous[j] + 1, current[j - 1] + 1)
        previous = current
    return previous[len(right)]


def is_plural_pair(left, right):
    def is_plural_of(plural, singular):
        return bool(singular) and plural in (singular + "s", singular + "es")

    return is_plural_of(left, right) or is_plural_of(right, left)


def could_near_match(query_token, record_token):
    """The bounded fuzzy rule CatalogSearch documents, used here only to reject
    negatives that a correct matcher is *supposed* to absorb as a typo."""
    if query_token == record_token:
        return True
    if is_plural_pair(query_token, record_token):
        return True
    shorter = min(len(query_token), len(record_token))
    if shorter < 4:
        return False
    allowance = 1 if shorter < 8 else 2
    if abs(len(query_token) - len(record_token)) > allowance:
        return False
    return edit_distance(query_token, record_token) <= allowance


# ---------------------------------------------------------------------------
# Vocabularies used to build queries.
# ---------------------------------------------------------------------------

CONJUNCTION = {"en": "and", "es": "y", "ru": "и"}

# Prefixes a person actually types in that language. The English ones are all
# inside QUANTITY_WORDS; the Spanish and Russian ones deliberately are not,
# because that vocabulary does not exist yet and the corpus must say so.
QUANTITY_PREFIXES = {
    "en": ["2 ", "200 g ", "two ", "a slice of ", "three ", "150 g ", "half a "],
    "es": ["dos ", "tres ", "cuatro ", "medio kilo de ", "200 gramos ", "un plato de "],
    "ru": ["две ", "два ", "три ", "200 граммов ", "полкило ", "порция "],
}

# Mirrors TextFragments.CONJUNCTIONS. A term containing one of these would be
# segmented mid-name, so no case is ever built from it -- in any language.
SEGMENTING_WORDS = {"and", "plus", "&"}

# QUANTITY_WORDS from TextFragments.kt. Used only to keep a quantity case from
# being built on a name whose own first or last word is a quantity word, which
# would be stripped along with the prefix.
QUANTITY_WORDS = {
    "a", "an", "the", "some", "of",
    "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "half", "quarter",
    "slice", "slices", "piece", "pieces", "serving", "servings", "portion", "portions",
    "cup", "cups", "bowl", "bowls", "plate", "plates", "glass", "glasses",
    "gram", "grams", "g", "mg", "kg", "ml", "l", "oz", "lb",
    "tbsp", "tsp", "tablespoon", "tablespoons", "teaspoon", "teaspoons",
}

# Characters that would complicate a Kotlin literal, the segmenter, or both.
FORBIDDEN_IN_TERMS = set('",\\$\n\r\t()/:;')
COMBINING_ACUTE = "́"

NONSENSE_POOL = [
    "qwzrtp", "blorfnek", "zzxqvwm", "frimbulax", "gnorpleth", "vurblesk",
    "xylophrag", "kwendish", "plurgex", "brintozzl", "shnorfyx", "twizzlorp",
    "grumbex", "flenkirp", "zorbnath", "dwimplex", "yarvuzz", "chrondil",
    "pflurmit", "skevranth", "morblex", "quandrix", "hexwoble", "jimbraz",
    "vlorntik", "prendaxle", "wuzzlefram", "klimbotch", "snerbald", "trafquix",
    "ЖЗЩЫГП".lower(), "щврфых", "гдьжэях", "пшыжврт", "фхцчшщъ",
    "xqrzune", "ñubrixal", "yerbaxton", "zurbaneque",
]

NONSENSE_LANGUAGE = {
    "щврфых": "und", "гдьжэях": "und", "пшыжврт": "und", "фхцчшщъ": "und",
    "жзщыгп": "und",
}


# ---------------------------------------------------------------------------
# Loading and subset selection.
# ---------------------------------------------------------------------------


def load_concepts(path):
    with path.open(encoding="utf-8") as handle:
        document = json.load(handle)
    if document.get("schema_version") != 1:
        raise SystemExit(f"unexpected schema_version in {path}")
    missing = [lang for lang in LANGUAGES if lang not in document.get("languages", [])]
    if missing:
        raise SystemExit(f"{path} is missing languages {missing}")
    return document["concepts"], document.get("provenance", {})


def is_clean_term(term):
    if not term or not term.strip():
        return False
    if FORBIDDEN_IN_TERMS & set(term):
        return False
    if COMBINING_ACUTE in term:
        return False
    return bool(tokenize_normalized(normalize_for_search(term)))


def match_terms(concept, language):
    return concept.get(language, {}).get("match", [])


def total_match_terms(concept):
    return sum(len(match_terms(concept, lang)) for lang in LANGUAGES)


def select_concepts(concepts, limit):
    eligible = []
    for concept in concepts:
        displays = [concept[lang]["display"] for lang in LANGUAGES]
        if not all(is_clean_term(display) for display in displays):
            continue
        if not all(
            1 <= len(tokenize_normalized(normalize_for_search(display))) <= 3
            for display in displays
        ):
            continue
        eligible.append(concept)
    eligible.sort(key=lambda c: (-total_match_terms(c), c["id"]))
    return eligible[:limit]


def build_records(selected):
    """One record per concept: English display name, everything else an alias.

    Aliases are de-duplicated by normalized form rather than by exact string,
    because two spellings that fold to the same searchable form are the same
    searchable form -- keeping both would inflate the file and change nothing.
    The display name is excluded from its own alias list by construction.
    """
    records = []
    for concept in selected:
        display = concept["en"]["display"]
        seen = {normalize_for_search(display)}
        alternates = []
        candidates = [concept["es"]["display"], concept["ru"]["display"]]
        for lang in LANGUAGES:
            candidates.extend(match_terms(concept, lang))
        for candidate in candidates:
            if not candidate or not candidate.strip():
                continue
            folded = normalize_for_search(candidate)
            if folded in seen:
                continue
            seen.add(folded)
            alternates.append(candidate)
        records.append({
            "id": ITEM_ID_PREFIX + concept["id"].split(":", 1)[1],
            "display": display,
            "alternates": alternates,
            "concept": concept,
            "forms": seen,
        })
    return records


def build_form_index(records):
    index = {}
    for record in records:
        for form in record["forms"]:
            index.setdefault(form, set()).add(record["id"])
    return index


class CatalogTokens:
    """Every token the catalog can be searched by, indexed for the one question
    the negatives need answered: could any record token reach this string?

    Indexed rather than scanned because the near-miss builders ask it tens of
    thousands of times, and a linear scan with an edit distance inside it turns
    a one-second generator into a several-minute one.
    """

    def __init__(self, records):
        tokens = set()
        for record in records:
            for form in record["forms"]:
                tokens.update(tokenize_normalized(form))
        self.tokens = tokens
        self.sorted = sorted(tokens)
        self.by_length = {}
        for token in tokens:
            self.by_length.setdefault(len(token), []).append(token)

    def __len__(self):
        return len(self.tokens)

    def has_prefix(self, token):
        """True when some record token starts with [token], equality included.

        Covers the EXACT, TOKEN_SUBSET and PREFIX tiers at once: every string
        that starts with `token` sorts at or after it, and nothing that does not
        start with it can sort between the two.
        """
        index = bisect.bisect_left(self.sorted, token)
        return index < len(self.sorted) and self.sorted[index].startswith(token)

    def has_plural_pair(self, token):
        if token + "s" in self.tokens or token + "es" in self.tokens:
            return True
        for suffix in ("es", "s"):
            if token.endswith(suffix) and token[: -len(suffix)] in self.tokens:
                return True
        return False

    def is_far(self, token):
        """True when nothing in the catalog could reach [token] under any tier.

        The fuzzy allowance is keyed on the shorter of the two tokens, so a
        query token under four characters can only ever match by equality or
        plural folding -- which the two checks above have already ruled out.
        """
        if self.has_prefix(token) or self.has_plural_pair(token):
            return False
        if len(token) < 4:
            return True
        for length in range(max(4, len(token) - 2), len(token) + 3):
            for record_token in self.by_length.get(length, ()):
                if could_near_match(token, record_token):
                    return False
        return True


# ---------------------------------------------------------------------------
# Case construction.
# ---------------------------------------------------------------------------


class Case:
    __slots__ = ("query", "expected", "language", "kind", "because")

    def __init__(self, query, expected, language, kind, because):
        self.query = query
        self.expected = expected
        self.language = language
        self.kind = kind
        self.because = because


def is_usable_query(term, record_id, form_index):
    """A term is usable as a query when it names exactly one record.

    Ambiguous terms stay in the catalog and never become cases: a query that
    two concepts answer to has no single correct expectation, so a case built
    on one would measure the tie-break instead of the match.
    """
    if not is_clean_term(term):
        return False
    tokens = tokenize_normalized(normalize_for_search(term))
    if not tokens or len(tokens) > 4:
        return False
    if any(token in SEGMENTING_WORDS for token in tokens):
        return False
    owners = form_index.get(normalize_for_search(term))
    return owners == {record_id}


def rotated_language(index, offset):
    return LANGUAGES[(index + offset) % len(LANGUAGES)]


def build_exact_cases(records, form_index, cap):
    """The display name in each language must reach its own concept.

    Emitted in rounds of (record, rotated language) so that every record is
    covered once before any record is covered twice, and so the three languages
    stay balanced however low the cap is set.
    """
    cases = []
    for offset in range(len(LANGUAGES)):
        for index, record in enumerate(records):
            if len(cases) >= cap:
                return cases
            language = rotated_language(index, offset)
            term = record["concept"][language]["display"]
            if not is_usable_query(term, record["id"], form_index):
                continue
            cases.append(Case(
                term, [record["id"]], language, "exact",
                f"{language} display name",
            ))
    return cases


def build_alias_cases(records, form_index, cap):
    """Every match term must reach its concept.

    This is the class that actually tests the synonym table, so it is the
    largest one. Rounds again, so no single fish concept with fifty Spanish
    regional names crowds out three hundred other foods.
    """
    cases = []
    for round_index in range(12):
        term_index = round_index // len(LANGUAGES)
        offset = round_index % len(LANGUAGES)
        for index, record in enumerate(records):
            if len(cases) >= cap:
                return cases
            language = rotated_language(index, offset)
            usable = [
                term for term in match_terms(record["concept"], language)
                if is_usable_query(term, record["id"], form_index)
            ]
            if term_index >= len(usable):
                continue
            cases.append(Case(
                usable[term_index], [record["id"]], language, "alias",
                f"{language} match term",
            ))
    return cases


def display_usable_for(record, language, form_index):
    term = record["concept"][language]["display"]
    return term if is_usable_query(term, record["id"], form_index) else None


def build_two_item_cases(records, form_index, cap_per_language):
    """Two foods joined by the language's own conjunction, both expected.

    English is expected to pass. Spanish and Russian are expected to fail:
    TextFragments only knows the English conjunctions, so "arroz y pollo" is
    one unresolvable fragment rather than two foods.
    """
    cases = []
    for language in LANGUAGES:
        usable = []
        for record in records:
            term = display_usable_for(record, language, form_index)
            if term is not None:
                usable.append((term, record["id"]))
        conjunction = CONJUNCTION[language]
        emitted = 0
        for index in range(0, len(usable) - 1, 2):
            if emitted >= cap_per_language:
                break
            (left_term, left_id), (right_term, right_id) = usable[index], usable[index + 1]
            because = (
                f"{language} conjunction; the segmenter knows only the en list, "
                "so this fails today"
                if language != "en"
                else "two foods joined by the en conjunction"
            )
            cases.append(Case(
                f"{left_term} {conjunction} {right_term}",
                [left_id, right_id],
                language,
                "two-item",
                because,
            ))
            emitted += 1
    return cases


def build_quantity_cases(records, form_index, cap_per_language):
    """A quantity in front of a name, which must not change which food it is.

    English quantity words are stripped before matching. Spanish and Russian
    ones are not, because QUANTITY_WORDS is English only, so those cases are
    expected to fail until the per-market vocabulary lands.
    """
    cases = []
    for language in LANGUAGES:
        prefixes = QUANTITY_PREFIXES[language]
        emitted = 0
        for index, record in enumerate(records):
            if emitted >= cap_per_language:
                break
            term = display_usable_for(record, language, form_index)
            if term is None:
                continue
            folded = normalize_for_search(term)
            tokens = tokenize_normalized(folded)
            # The quantity retry rejoins tokens with single spaces, so a name
            # carrying punctuation would not be re-matched as itself.
            if " ".join(tokens) != folded:
                continue
            if tokens[0] in QUANTITY_WORDS or tokens[-1] in QUANTITY_WORDS:
                continue
            prefix = prefixes[emitted % len(prefixes)]
            because = (
                f"{language} quantity prefix; QUANTITY_WORDS is en only, "
                "so this fails today"
                if language != "en"
                else "en quantity prefix, stripped before matching"
            )
            cases.append(Case(
                prefix + term, [record["id"]], language, "quantity", because,
            ))
            emitted += 1
    return cases


# ---------------------------------------------------------------------------
# Negatives. Half the value of the corpus is here: without them a matcher that
# answers every query confidently would score perfectly.
# ---------------------------------------------------------------------------


def build_nonsense_cases(catalog_tokens):
    cases = []
    for word in NONSENSE_POOL:
        tokens = tokenize_normalized(normalize_for_search(word))
        if len(tokens) != 1 or not catalog_tokens.is_far(tokens[0]):
            continue
        cases.append(Case(
            word, [], NONSENSE_LANGUAGE.get(word, "und"), "must-not-resolve",
            "not a word in any of the three languages",
        ))
    return cases


def build_compound_nonsense_cases(records, form_index, catalog_tokens, limit):
    """A real food beside a word that names nothing.

    Every tier requires one record to satisfy every query token, so a fragment
    half of which is meaningless is not a confident match. Returning the food
    anyway would be a guess dressed as a result.
    """
    cases = []
    fillers = [
        case.query for case in build_nonsense_cases(catalog_tokens)
    ]
    if not fillers:
        return cases
    for index, record in enumerate(records):
        if len(cases) >= limit:
            break
        language = rotated_language(index, 0)
        term = display_usable_for(record, language, form_index)
        if term is None:
            continue
        filler = fillers[len(cases) % len(fillers)]
        cases.append(Case(
            f"{term} {filler}", [], language, "must-not-resolve",
            "one token names nothing, so the fragment is not a confident match",
        ))
    return cases


def build_separator_cases():
    return [
        Case("and", [], "en", "must-not-resolve", "a separator alone"),
        Case("y", [], "es", "must-not-resolve", "the es conjunction alone"),
        Case("и", [], "ru", "must-not-resolve", "the ru conjunction alone"),
        Case("plus", [], "en", "must-not-resolve", "another separator alone"),
        Case("with", [], "en", "must-not-resolve", "a weak separator alone"),
        Case("con", [], "es", "must-not-resolve", "the es weak separator alone"),
        Case("с", [], "ru", "must-not-resolve", "the ru weak separator alone"),
        Case("and and", [], "en", "must-not-resolve", "separators and nothing else"),
    ]


def build_quantity_only_cases():
    return [
        Case("two", [], "en", "must-not-resolve", "a quantity with nothing to quantify"),
        Case("2", [], "en", "must-not-resolve", "a digit with nothing to quantify"),
        Case("200 g", [], "en", "must-not-resolve", "an amount with no food"),
        Case("a slice of", [], "en", "must-not-resolve", "quantity words alone"),
        Case("half a", [], "en", "must-not-resolve", "quantity words alone"),
        Case("three servings of", [], "en", "must-not-resolve", "quantity words alone"),
        Case("dos", [], "es", "must-not-resolve", "an es quantity with nothing to quantify"),
        Case("una", [], "es", "must-not-resolve", "an es quantity with nothing to quantify"),
        Case("200 gramos", [], "es", "must-not-resolve", "an es amount with no food"),
        Case("две", [], "ru", "must-not-resolve", "a ru quantity with nothing to quantify"),
        Case("три", [], "ru", "must-not-resolve", "a ru quantity with nothing to quantify"),
        Case("200 граммов", [], "ru", "must-not-resolve", "a ru amount with no food"),
    ]


def build_emptyish_cases():
    return [
        Case("", [], "und", "must-not-resolve", "nothing typed"),
        Case(" ", [], "und", "must-not-resolve", "one space"),
        Case("    ", [], "und", "must-not-resolve", "only whitespace"),
        Case("\n", [], "und", "must-not-resolve", "one line break"),
        Case(",", [], "und", "must-not-resolve", "one separator character"),
        Case(" , ", [], "und", "must-not-resolve", "a separator between two spaces"),
        Case("- -", [], "und", "must-not-resolve", "punctuation only"),
    ]


CYRILLIC_FLOOR = "Ѐ"


def is_cyrillic(token):
    return any(character >= CYRILLIC_FLOOR for character in token)


def source_tokens(records, predicate, budget):
    """At most [budget] catalog tokens matching [predicate], evenly spread.

    Bounded because the caller tests every one of them against the whole token
    index, and the index answers with an edit distance; the unbounded version of
    this walked four thousand tokens and turned a fifteen-second generator into
    one that had not finished after two minutes.
    """
    tokens = sorted({
        token
        for record in records
        for form in record["forms"]
        for token in tokenize_normalized(form)
        if predicate(token)
    })
    return spread(tokens, budget)


def spread(items, count):
    """[count] items taken evenly across [items] rather than from the front.

    Catalog tokens sort alphabetically, so anything that walks them from the
    beginning and stops at a cap tests the letter `a` and calls it the table.
    The spread is applied to the *surviving* candidates, not to the input:
    filtering after sampling puts the bias straight back.
    """
    if len(items) <= count:
        return items
    return [items[index * len(items) // count] for index in range(count)]


def build_short_near_miss_cases(records, catalog_tokens, limit):
    """One edit from a real short term, and still not a food.

    This is the class `ribs` and `draw` belong to: the shorter side is under the
    fuzzy floor, so the allowance is zero by design and these must miss. Those
    two named defects reached white rice and raw respectively, which is why the
    floor exists at all.

    The language is `und`, not the language of the term the string was derived
    from. A string one edit off a Spanish word is not a Spanish word, and
    labelling it `es` would let a language-filtered run count it as Spanish
    coverage it does not have.
    """
    latin_letters = "abcdeilmnoprstu"
    cyrillic_letters = "абвгдеиклмнорстуя"
    found = []
    seen = set()
    for token in source_tokens(records, lambda t: len(t) == 3, limit * 6):
        letters = cyrillic_letters if is_cyrillic(token) else latin_letters
        candidate = next(
            (
                token[:position] + letter + token[position:]
                for position in range(len(token) + 1)
                for letter in letters
                if token[:position] + letter + token[position:] not in seen
                and catalog_tokens.is_far(token[:position] + letter + token[position:])
            ),
            None,
        )
        if candidate is None:
            continue
        seen.add(candidate)
        found.append((candidate, token))
    return [
        Case(
            candidate, [], "und", "must-not-resolve",
            f"one edit from {token}, which is too short for a fuzzy allowance",
        )
        for candidate, token in spread(found, limit)
    ]


def build_long_near_miss_cases(records, catalog_tokens, limit):
    """Three edits from a real long term, which is past the widest allowance."""
    found = []
    seen = set()
    for token in source_tokens(records, lambda t: len(t) >= 6, limit * 6):
        candidate = token + ("щшъ" if is_cyrillic(token) else "zzq")
        if candidate in seen or not catalog_tokens.is_far(candidate):
            continue
        seen.add(candidate)
        found.append((candidate, token))
    return [
        Case(
            candidate, [], "und", "must-not-resolve",
            f"three edits from {token}, past the widest allowance",
        )
        for candidate, token in spread(found, limit)
    ]


def build_absent_food_cases(concepts, selected_ids, form_index, catalog_tokens, limit):
    """A real food that this catalog does not carry.

    Drawn from the concepts the subset left behind, and kept only when at least
    one of its words is absent from the catalog entirely -- which is what makes
    the miss certain rather than a matter of ranking.
    """
    cases = []
    for concept in concepts:
        if len(cases) >= limit:
            break
        if ITEM_ID_PREFIX + concept["id"].split(":", 1)[1] in selected_ids:
            continue
        language = LANGUAGES[len(cases) % len(LANGUAGES)]
        term = concept.get(language, {}).get("display", "")
        if not is_clean_term(term):
            continue
        folded = normalize_for_search(term)
        if folded in form_index:
            continue
        tokens = tokenize_normalized(folded)
        if len(tokens) > 3:
            continue
        if not any(catalog_tokens.is_far(token) for token in tokens):
            continue
        cases.append(Case(
            term, [], language, "must-not-resolve",
            "a real food this catalog does not carry",
        ))
    return cases


# ---------------------------------------------------------------------------
# Kotlin emission.
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


def render_string_list(values, indent):
    """A list literal on one line when it fits, one element per line when it
    does not. ktlint allows either, but never a half-wrapped argument list."""
    pad = " " * indent
    single = f"listOf({', '.join(kotlin_string(v) for v in values)})"
    if not values:
        return ["emptyList()"]
    # The caller appends a trailing comma to the last line of every argument,
    # so the budget is one character short of the limit, not equal to it.
    if len(pad) + len(single) + 1 <= MAX_LINE:
        return [single]
    lines = ["listOf("]
    for value in values:
        lines.append(f"    {kotlin_string(value)},")
    lines.append(")")
    return lines


def render_call(name, arguments, indent):
    """One `Name(a, b, c),` line when it fits, otherwise one argument per line
    with a trailing comma and the closing parenthesis on its own line."""
    pad = " " * indent
    flat = [argument for argument in arguments if len(argument) == 1]
    if len(flat) == len(arguments):
        single = f"{pad}{name}({', '.join(a[0] for a in arguments)}),"
        if len(single) <= MAX_LINE:
            return [single]
    lines = [f"{pad}{name}("]
    for argument in arguments:
        for offset, line in enumerate(argument):
            lines.append(f"{pad}    {line}" + ("," if offset == len(argument) - 1 else ""))
    lines.append(f"{pad}),")
    return lines


def render_record(record, indent):
    return render_call(
        "CatalogRecord",
        [
            [kotlin_string(record["id"])],
            [kotlin_string(record["display"])],
            render_string_list(record["alternates"], indent + 4),
        ],
        indent,
    )


def render_case(case, indent):
    return render_call(
        "MealTextCase",
        [
            [kotlin_string(case.query)],
            render_string_list(case.expected, indent + 4),
            [kotlin_string(case.language)],
            [kotlin_string(case.kind)],
            [kotlin_string(case.because)],
        ],
        indent,
    )


def chunk_blocks(blocks, budget):
    """Group rendered elements so no generated object grows unbounded.

    Two limits are being respected at once. A JVM method may not exceed 64 KB of
    bytecode, and a single top-level property holding fourteen hundred
    constructor calls would approach it in the file initializer; splitting the
    data across objects gives each its own initializer. The same split keeps
    every generated declaration well inside Detekt's LargeClass threshold.
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


HEADER = '''package com.supernutrition.domain.nutrition

// GENERATED FILE. Produced by scripts/generate-meal-text-corpus.py from
// mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json.
// Do not edit by hand: the next run of the generator overwrites every line.
//
// Contains information from the AGROVOC Multilingual Thesaurus, (c) Food and
// Agriculture Organization of the United Nations (FAO), licensed under
// CC-BY IGO 3.0 -- https://creativecommons.org/licenses/by/3.0/igo/. That
// credit is a licence condition, not a courtesy.
// Colloquial and regional names come from Wikidata, CC0 1.0, which imposes no
// attribution requirement. See ATTRIBUTION.md beside the source JSON.

/**
 * One query and the canonical ids a correct resolver returns for it, in order.
 *
 * [expected] is the semantically correct answer, never a recording of what the
 * matcher does today. Two classes of case are known to fail as things stand and
 * are named in [knownFailingKinds]; they are left failing deliberately, because
 * a corpus that encoded the current bugs as correct behaviour would make them
 * permanent and call it a regression test.
 *
 * An empty [expected] means the query must resolve to nothing at all. Those
 * cases are not padding: without them a matcher that answered every query
 * confidently would score perfectly, and the failure that matters most -- a
 * plausible wrong food -- would never be measured.
 *
 * [language] is the BCP-47 subtag the query is written in, or `und` when the
 * string belongs to no language: nonsense, whitespace, bare punctuation.
 *
 * [kind] names the class the case belongs to, so a failure names the class that
 * broke rather than one query out of fourteen hundred:
 *  - `exact` -- a display name in one of the three languages.
 *  - `alias` -- a match term from the synonym table. The largest class, because
 *    it is the one that actually measures the table.
 *  - `two-item` -- two foods joined by that language's conjunction.
 *  - `quantity` -- an amount in front of a name, which must not change the food.
 *  - `must-not-resolve` -- a query no record answers.
 *
 * [because] says why the case is what it is, so a failure can be read without
 * re-deriving it from the source table.
 */
data class MealTextCase(
    val query: String,
    val expected: List<String>,
    val language: String,
    val kind: String,
    val because: String,
)
'''


def render_file(records, cases, stats):
    lines = HEADER.split("\n")

    catalog_chunks = chunk_blocks(
        [render_record(record, 8) for record in records], CHUNK_LINE_BUDGET,
    )
    case_chunks = chunk_blocks(
        [render_case(case, 8) for case in cases], CHUNK_LINE_BUDGET,
    )
    catalog_names = [f"MealTextCorpusCatalog{index}" for index in range(len(catalog_chunks))]
    case_names = [f"MealTextCorpusCases{index}" for index in range(len(case_chunks))]

    lines += [
        "/**",
        f" * The corpus catalog: {len(records)} records drawn from the AGROVOC concepts",
        " * that carry the most match terms.",
        " *",
        " * The English display name is the record's own name. Every other name -- the",
        " * Spanish and Russian display names, and every match term in all three",
        f" * languages, {stats['alternates']} of them -- is an alternate, de-duplicated by",
        " * searchable form rather than by spelling.",
        " *",
        " * Names that two concepts share are kept. Folded forms like `chicken` and",
        " * `tuna` name more than one concept here, and a catalog that quietly dropped",
        " * them would be easier to search than the one that ships. No case is ever",
        " * built from such a term, because a query two concepts answer to has no",
        " * single correct expectation.",
        " */",
        "val corpusCatalog: List<CatalogRecord> = listOf(",
    ]
    lines += [f"    {name}.records," for name in catalog_names]
    lines += [
        ").flatten()",
        "",
        "/**",
        f" * {len(cases)} query cases: {stats['kinds'].get('alias', 0)} alias,"
        f" {stats['kinds'].get('exact', 0)} exact,"
        f" {stats['kinds'].get('two-item', 0)} two-item,",
        f" * {stats['kinds'].get('quantity', 0)} quantity and"
        f" {stats['kinds'].get('must-not-resolve', 0)} must-not-resolve.",
        " *",
        " * Grouped by kind and stable between runs, so a run that stops early stops",
        " * somewhere predictable. Nothing should depend on that order: it is an",
        " * artefact of how the generator walks the table, not a property of the data.",
        " */",
        "val corpusCases: List<MealTextCase> = listOf(",
    ]
    lines += [f"    {name}.cases," for name in case_names]
    lines += [
        ").flatten()",
        "",
        "/**",
        " * The case kinds expected to fail against the code as it stands.",
        " *",
        " * The failure is language-scoped in both, and the English members of both",
        " * kinds are expected to pass:",
        " *",
        " *  - `two-item`: TextFragments.CONJUNCTIONS is {and, plus, &}, so a Spanish",
        " *    or Russian conjunction never separates two foods and the whole",
        " *    description arrives at the catalog as one unresolvable fragment.",
        " *  - `quantity`: QUANTITY_WORDS is English only, so a Spanish or Russian",
        " *    amount stays in the query as a token no record satisfies.",
        " *",
        " * Both vocabularies are per-market language-matrix data rather than",
        " * constants in search code, which is why neither is worth extending one",
        " * word at a time. Each affected case says so in its own `because`.",
        " */",
        'val knownFailingKinds: Set<String> = setOf("quantity", "two-item")',
    ]

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


def check_output(text):
    """Cheap self-checks for the rules this repository enforces and this script
    cannot run: line length, trailing whitespace, and a final newline."""
    problems = []
    for number, line in enumerate(text.split("\n"), start=1):
        if len(line) > MAX_LINE:
            problems.append(f"line {number} is {len(line)} characters")
        if line != line.rstrip():
            problems.append(f"line {number} has trailing whitespace")
    if not text.endswith("\n") or text.endswith("\n\n"):
        problems.append("file must end with exactly one newline")
    return problems


# ---------------------------------------------------------------------------
# Entry point.
# ---------------------------------------------------------------------------


def build_cases(records, concepts, form_index, catalog_tokens):
    selected_ids = {record["id"] for record in records}
    cases = []
    cases += build_exact_cases(records, form_index, CAP_EXACT)
    cases += build_alias_cases(records, form_index, CAP_ALIAS)
    cases += build_two_item_cases(records, form_index, CAP_TWO_ITEM_PER_LANGUAGE)
    cases += build_quantity_cases(records, form_index, CAP_QUANTITY_PER_LANGUAGE)
    cases += build_separator_cases()
    cases += build_quantity_only_cases()
    cases += build_emptyish_cases()
    cases += build_nonsense_cases(catalog_tokens)
    cases += build_compound_nonsense_cases(records, form_index, catalog_tokens, 12)
    cases += build_short_near_miss_cases(records, catalog_tokens, 45)
    cases += build_long_near_miss_cases(records, catalog_tokens, 20)
    cases += build_absent_food_cases(
        concepts, selected_ids, form_index, catalog_tokens, 45,
    )
    return cases


def summarize(records, cases):
    kinds = {}
    languages = {}
    by_kind_language = {}
    for case in cases:
        kinds[case.kind] = kinds.get(case.kind, 0) + 1
        languages[case.language] = languages.get(case.language, 0) + 1
        key = (case.kind, case.language)
        by_kind_language[key] = by_kind_language.get(key, 0) + 1
    return {
        "records": len(records),
        "alternates": sum(len(record["alternates"]) for record in records),
        "cases": len(cases),
        "kinds": kinds,
        "languages": languages,
        "by_kind_language": by_kind_language,
    }


def report(stats, path, size, problems):
    print(f"records      {stats['records']}")
    print(f"alternates   {stats['alternates']}")
    print(f"cases        {stats['cases']}")
    print("by kind")
    for kind in sorted(stats["kinds"]):
        print(f"  {kind:<18} {stats['kinds'][kind]}")
    print("by language")
    for language in sorted(stats["languages"]):
        print(f"  {language:<18} {stats['languages'][language]}")
    print("by kind and language")
    for key in sorted(stats["by_kind_language"]):
        print(f"  {key[0]:<18} {key[1]:<4} {stats['by_kind_language'][key]}")
    print(f"output       {path}")
    print(f"size         {size} bytes")
    if problems:
        print("PROBLEMS")
        for problem in problems:
            print(f"  {problem}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--source", default=str(SOURCE_JSON), help="synonym table to read")
    parser.add_argument("--out", default=str(OUTPUT_KT), help="Kotlin file to write")
    parser.add_argument(
        "--concepts", type=int, default=DEFAULT_CONCEPTS,
        help="how many concepts to keep in the emitted catalog",
    )
    parser.add_argument("--dry-run", action="store_true", help="do not write the file")
    args = parser.parse_args(argv)

    concepts, provenance = load_concepts(Path(args.source))
    selected = select_concepts(concepts, args.concepts)
    if len(selected) < args.concepts:
        print(
            f"warning: only {len(selected)} of {args.concepts} concepts were eligible",
            file=sys.stderr,
        )
    records = build_records(selected)
    form_index = build_form_index(records)
    catalog_tokens = CatalogTokens(records)
    cases = build_cases(records, concepts, form_index, catalog_tokens)

    stats = summarize(records, cases)
    text = render_file(records, cases, stats)
    problems = check_output(text)

    out_path = Path(args.out)
    if not args.dry_run:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(text, encoding="utf-8")
    report(stats, out_path, len(text.encode("utf-8")), problems)
    print(f"source       {provenance.get('generated_utc', 'unknown')}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
