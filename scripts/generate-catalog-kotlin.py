#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-catalog-kotlin.py -- BUILD-TIME generator for the shipped identity
catalog searched by the deterministic text engine.

This script runs at build time only. It never runs on device, and nothing it
touches is a runtime dependency. Its single output is one deterministic Kotlin
file under commonMain:

    mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/
    nutrition/GeneratedCatalog.kt

    stdlib only. No third-party packages. Python 3.9+.
    (If a future change needs a third-party package, declare it HERE in this
    header rather than assuming the build host has it installed.)


WHAT IS EMITTED, AND WHAT IS DELIBERATELY NOT
=============================================
`val generatedCatalog: List<CatalogRecord>` -- identity only. CatalogRecord
carries a canonical id, a display name, and alternate names, and it carries no
nutrient fields at all.

That is not an oversight of this generator: gate G-2 blocks grounding beyond a
fake catalog, so no calorie, protein, fat, carbohydrate, fibre, sugar or sodium
value from the source TSV is emitted here, even though every one of them is
sitting in the input file. The TSV keeps them for when the gate opens; this
file must not, because a nutrient number that reaches the app through an
ungated path is exactly the plausible-but-unapproved figure the grounding chain
exists to prevent.


SOURCES
=======
1. USDA FoodData Central, SR Legacy Foods, release 2018-04, via
   mobile/domain/nutrition/src/commonMain/resources/catalog/usda-sr-legacy.tsv
   (7,793 foods). Public domain, CC0 1.0 -- no permission needed, attribution
   requested. Supplies the canonical id and the display name.

2. The AGROVOC/Wikidata food synonym table, via
   mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json
   (997 concepts, en/es/ru). AGROVOC is CC-BY IGO 3.0 and its attribution is a
   LICENCE CONDITION that must appear in the shipped acknowledgements screen;
   Wikidata is CC0 and imposes nothing. See ATTRIBUTION.md beside the JSON.
   Supplies the alternate names -- the consumer words, in three languages.

The two are joined because neither is usable alone. USDA names are analytical
("Avocados, raw, all commercial varieties"); nobody types that. The synonym
table has the words people do type but no food records to attach them to.


HOW THE RECORD SET IS BOUNDED  (the selection rule -- widen it here)
===================================================================
All 7,793 foods emit a Kotlin file large enough to be a compile-time problem of
its own, and most of the bulk is not food anybody logs: 960 of them are beef
assays differing only in trim and grade. The set is therefore bounded by rule,
not by taste and not by alphabet:

  1. drop BRANDED and INSTITUTIONAL records.
     USDA writes manufacturer and outlet names in capitals, so any name
     carrying a run of two or more capital letters is dropped (KRAFT, CHOBANI,
     McDONALD'S, QUAKER), as is any name opening with one of the institutional
     prefixes in INSTITUTIONAL_PREFIXES (Babyfood, Infant formula, Fast foods,
     Restaurant, ...). Those are not what somebody types when logging a meal,
     and their names are display-hostile besides.

  2. keep records that are still a FOOD NAME rather than an ASSAY.
     USDA appends analytical qualifiers -- "separable lean and fat, trimmed to
     1/8\" fat, all grades, cooked, braised". Length is the signal, and it shows
     up on two different axes, so a record is kept when it is short on EITHER:
     at most MAX_SEGMENTS comma-separated clauses, OR at most MAX_NAME_TOKENS
     words. Neither test alone is enough. The clause test alone drops
     "Rice, white, medium-grain, raw, enriched"; the word test alone drops
     "Milk, whole, 3.25% milkfat, with added vitamin D". Both are foods people
     log every day.

  3. keep source order (ascending NDB number). Stable between runs, and raising
     either cap yields a strict superset of the smaller set.

Widen by raising --max-segments / --max-name-tokens, or by emptying the
exclusion lists. Nothing downstream may depend on the count.


WHERE THE CAPS CAME FROM -- MEASURED, NOT CHOSEN
================================================
Run --stats-only at any setting to reproduce this table. Sizes are of the
emitted Kotlin, after the shared-name grouping described below:

    caps    records   lines    bytes   what it costs
    4 / 6     3,519   7,229   436 KB   nothing measurable; the default
    3 / 6     2,918   5,364   346 KB   variants only
    3 / 5     2,481   4,630   292 KB   ground beef, chicken breast, whole milk,
                                       plain white rice -- see below
    3 / 4     2,141   4,202   255 KB   as above, and more
    2 / 4     1,506   3,128   182 KB   as above, and much more
    all       6,367  20,404   988 KB   refused: some names exceed 120 columns

4 / 6 is the tightest setting at which the staples survive, and that is why it
is the default rather than a smaller number that looks better on disk. At
3 / 5 SR Legacy's ground beef, chicken breast, whole milk and long-grain white
rice are all gone: USDA writes them as "Beef, ground, 93% lean meat / 7% fat,
raw" and "Milk, whole, 3.25% milkfat, with added vitamin D", which are four
clauses and nine words each. A catalog that drops the four most-logged foods on
earth to save 144 KB is not a smaller catalog, it is a broken one.

436 KB IS STILL LARGE, and this is the honest limit of what was measured. It
makes GeneratedCatalog.kt the biggest source file in the repository -- the
previous holder is MealTextCorpus.kt at 362 KB, a test fixture that compiles
today with a comparable number of constructor calls, which is the only
empirical evidence available that this workload is tractable. COMPILE TIME WAS
NOT MEASURED: the generator ran where Gradle could not. If a build turns out to
be unacceptably slow, --max-segments 3 --max-name-tokens 6 is the next step
down and costs variants rather than foods.


HOW THE JOIN WORKS -- AND WHY IT IS DELIBERATELY NARROW
=======================================================
A wrong join is worse than no join. If "говядина" reaches a pork record the
user is told they ate something they did not, in a language they cannot check
the English name against. So the join is exact-key only; there is no fuzzy
matching anywhere in this script.

  * The join key is the USDA HEAD SEGMENT -- the text before the first comma --
    normalized with the same case/diacritic fold CatalogSearch.kt uses, and
    compared under the same singular/plural folding Kotlin's isPluralPair
    applies (a word, that word less a trailing "s", that word less a trailing
    "es"). "Avocados, raw, all commercial varieties" joins on "avocados".

  * A head joins to an AGROVOC concept when the concept's ENGLISH DISPLAY NAME
    folds to the same key AND exactly one concept does. Concept match terms are
    NOT used for the automatic join: several of them are broader or narrower
    than the term itself, and taking them would have produced "quinoa" ->
    "quinoa flour" and "pimento" -> "allspice", both of which are the wrong
    food rather than a loose one.

  * MANUAL_HEAD_CONCEPTS adds the high-value links the display join cannot
    make, one line per link with the reason beside it. Each entry states the
    concept id AND its expected English display name; the script aborts if the
    two have drifted apart in the source JSON, so a re-generated synonym table
    can never silently repoint a link at a different food.

  * A joined concept contributes its display names and match terms in all three
    languages as alternateNames, to EVERY selected record sharing that head --
    not to one chosen representative. Picking a representative was tried and
    rejected: the shortest chicken name in SR Legacy is "Chicken, meatless",
    and making that the sole record reachable by "курица" would have been the
    exact wrong-food failure this join is written to avoid.

  * REJECTED_HEADS refuses a head outright where the English word means one
    food to USDA and another to AGROVOC. Both entries are "pepper": SR Legacy's
    peppers are Capsicum, AGROVOC's `pepper` is the Piper nigrum spice, and the
    join would have answered `pimienta` and `перец черный` with bell peppers.

Two filters run over the alternate terms themselves:

  * TERMS THAT RESTATE THE HEAD ARE DROPPED. The concept's own English name is
    by construction the record's head segment, which is already inside the
    display name, so re-adding it would buy no new match and would promote the
    whole head group to the EXACT tier, hiding every other record that answers
    the same word. "bread" is dropped from "Bread, egg"; "bun" is kept.

  * TERMS THAT ARE SOME OTHER RECORD'S HEAD ARE DROPPED, for the same reason
    read from the other end: a word this catalog already spends on one food
    belongs to that food. AGROVOC files beet leaves under beetroot, so without
    this "beet greens" would have been answered at the EXACT tier by beet ROOT
    records -- and SR Legacy has a "Beet greens" record of its own, which would
    have been hidden behind them. It also unpicks kale/collards and
    rice/rice flour, each of which lists the other as a synonym.

WHAT IS NOT FILTERED, AND WHY
=============================
An earlier draft dropped every term that named more than one concept anywhere
in the 997-concept table. It was wrong, and the audit is worth keeping: of the
151 head/term pairs it removed, almost none were ambiguous in any way that
could mislead. It deleted `курица`, `pollo` and `цыплёнок` from chicken because
AGROVOC also files them under `hens`; `aguacate` and `авокадо` from avocados
because of `avocado oil`; `рис` and `arroz` from rice because of `rice flour`;
`фасоль` from beans, `индейка` from turkey, `оливки` from olives. Every one of
those is the right word for the food, and the competing concept was the same
food seen from another angle.

The rule that survives the audit is narrower: a word is only dangerous when it
means a DIFFERENT food, and in this vocabulary that is a property of the head,
not of the term -- which is what REJECTED_HEADS is for. Where two linked heads
genuinely share a word (`plátano` is both banana and plantain; `свинья` reaches
pork and lard) both keep it, and the search returns both, which is the honest
answer.


OUTPUT SHAPE
============
The records are split across several `internal object` holders rather than
sitting in one top-level property. Two limits are respected at once: a JVM
method may not exceed 64 KB of bytecode and a single initializer holding
thousands of constructor calls approaches it, and the same split keeps every
generated declaration well inside Detekt's LargeClass threshold. The public
`generatedCatalog` flattens them.

Alternate names are emitted once per head segment into `GeneratedNamesN`
objects and referenced by the records, not repeated inline. Seventy cheese
records share one list. Inlining would write 12,834 term literals instead of
1,304 -- the same words over and over -- and was measured at 843 KB and 21,330
lines against 436 KB and 7,229, with identical content.

Nothing here can run a Kotlin compiler, so check_output() re-reads the emitted
text and fails the run on the mistakes that would otherwise only surface as a
build error: an over-long line, trailing whitespace, a missing final newline, a
shared-name reference with no matching declaration, a lost or duplicated record.

One consequence to know before widening the caps: a wrapped record indents its
display name by twelve spaces, so a name longer than about 105 characters
cannot be emitted inside 120 columns and there is no legal way to break a
Kotlin string literal across lines. The generator refuses to write in that case
rather than emitting source that Detekt will reject. At the default caps no
name comes close; at --max-segments 99 --max-name-tokens 99 several do.

Usage:
    python3 scripts/generate-catalog-kotlin.py
    python3 scripts/generate-catalog-kotlin.py --max-segments 5 --stats-only
"""

import argparse
import collections
import json
import pathlib
import re
import sys
import unicodedata

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_CATALOG = (
    REPO_ROOT / "mobile/domain/nutrition/src/commonMain/resources/catalog/usda-sr-legacy.tsv"
)
DEFAULT_SYNONYMS = (
    REPO_ROOT / "mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json"
)
DEFAULT_OUT = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition"
    / "domain/nutrition/GeneratedCatalog.kt"
)

LANGUAGES = ("en", "es", "ru")
ID_PREFIX = "food:usda-srl-"

# Selection caps. See the header: raising either yields a strict superset.
DEFAULT_MAX_SEGMENTS = 4
DEFAULT_MAX_NAME_TOKENS = 6

MAX_LINE = 120
CHUNK_LINE_BUDGET = 380

# Records whose name opens with one of these are catalogue infrastructure or a
# named outlet's menu, not a food somebody types into a meal log.
INSTITUTIONAL_PREFIXES = (
    "babyfood",
    "fast food",
    "formulated bar",
    "infant formula",
    "meal replacement",
    "nutritional supplement",
    "protein supplement",
    "restaurant",
    "school lunch",
    "usda commodity",
)

# Hand-checked links the English-display join cannot make, because AGROVOC
# files the food under a different headword. Each value is
# (concept id, expected English display, why this link is right).
#
# Rejected on purpose, recorded so they are not "found" again by a later pass:
#   quinoa      -> quinoa flour  : a flour is not the grain.
#   pimento     -> allspice      : USDA "Pimento" is the sweet pepper; allspice
#                                  is an unrelated plant.
#   beet greens -> beetroot      : the leaves are not the root.
#   steelhead   -> trout         : the Russian gloss there is the salmon genus,
#                                  not this fish.
MANUAL_HEAD_CONCEPTS = {
    "lamb": [("agrovoc:c_5015", "mutton", "AGROVOC files sheep meat under mutton; ru баранина covers both")],
    "sausage": [("agrovoc:c_6824", "sausages", "singular head, plural concept, same food")],
    "mushrooms": [("agrovoc:c_2487", "edible fungi", "AGROVOC headword is the botanical class")],
    "cabbage": [("agrovoc:c_1173", "cabbages", "singular head, plural concept, same food")],
    "peanuts": [("agrovoc:c_11368", "groundnuts", "groundnut is the AGROVOC name for the peanut")],
    "sour cream": [("agrovoc:c_28325", "cultured cream", "cultured cream is сметана / crema ácida")],
    "beets": [("agrovoc:c_867", "beetroot", "the USDA head is the root, which is what beetroot names")],
    "artichokes": [("agrovoc:c_11171", "globe artichokes", "the USDA head is the globe artichoke")],
    "melons": [("agrovoc:c_12487", "muskmelons", "SR Legacy melons are Cucumis melo cultivars")],
    "corn flour": [("agrovoc:c_25472", "maize flour", "maize is the AGROVOC name for corn")],
    "persimmons": [("agrovoc:c_11934", "kaki", "kaki is the AGROVOC name for the persimmon")],
    "arugula": [("agrovoc:c_5c9e76bc", "rocket", "rocket is the AGROVOC name for arugula")],
    "endive": [("agrovoc:c_10596", "endives", "singular head, plural concept, same food")],
    "collards": [("agrovoc:c_4070", "kales", "collard greens are a kale cultivar group")],
    "snacks": [("agrovoc:c_34338", "snack foods", "singular-word head, compound concept, same class")],
    # `fish` folds to two concepts -- c_15903 the food and c_2943 the animals --
    # so the automatic join refuses it. The food one is the right one here, and
    # 164 records is the largest head in the catalog to be left without a word
    # for itself in any language.
    "fish": [("agrovoc:c_15903", "fish", "the food sense; c_2943 `fishes` is the animals")],
    "yogurt": [("agrovoc:c_15463", "yoghurt", "US and international spellings of one food")],
    "oranges": [("agrovoc:c_7550", "sweet oranges", "SR Legacy oranges are the sweet orange")],
    "corn": [("agrovoc:c_12332", "maize", "maize is the AGROVOC name for corn")],
    "asparagus": [("agrovoc:c_17156", "asparagus spears", "the spear is the part USDA measures")],
    # Additive, not a replacement: the automatic join already reaches
    # `chickens`, which is where the typed words курица / pollo / цыплёнок
    # live. This adds the meat concept whose own name nobody types.
    "chicken": [("agrovoc:c_24000", "chicken meat", "the USDA head is the meat, not the live bird")],
}

# Heads whose English word means one food to USDA and a different one to
# AGROVOC. The join is exact and would succeed; the result would be wrong, so
# the head is refused outright and those records ship with no alternate names.
REJECTED_HEADS = {
    # SR Legacy "Peppers, sweet, green, raw" is Capsicum. AGROVOC's `pepper` is
    # Piper nigrum, the spice. Joining them would answer `pimienta` and
    # `перец черный` with bell peppers.
    "peppers": "USDA Peppers are Capsicum; the AGROVOC concept is the Piper nigrum spice",
    # The singular head is Capsicum too -- its one record is "Pepper, banana,
    # raw", a banana pepper. SR Legacy files the actual spice under the head
    # `Spices` ("Spices, pepper, black"), which is unaffected by either entry.
    "pepper": "the one record under this head is a banana pepper, not the spice",
}

# Records whose head is shared with unrelated foods, so no head- or
# concept-level mechanism above (MANUAL_HEAD_CONCEPTS, MANUAL_TERMS,
# MANUAL_WIKIDATA_LINKS -- all scoped to a whole head or concept) can add a
# term to only these records without also joining it to every other record
# under the same head. "Fish" is 164 records across every species this
# catalog carries; "Beverages" spans every drink. Each entry below carves its
# matching records into their own alternate-name group, keeping the terms
# their shared head-group already gave them plus the new word.
#
# Reviewed and dated the same way MANUAL_TERMS is: fan-out checked against
# GeneratedCatalog.kt before being added, never a way to bulk-restore terms
# a filter dropped for a real reason.
SUBHEAD_OVERRIDES = (
    (
        "real-salmon-fish",
        lambda r: canonical_head(r["display"]) == "fish" and r["display"].split(",")[1].strip().lower() == "salmon",
        ("salmon",),
        # 2026-08-03: 22 records ("Fish, salmon, ..."). Excludes "Fish oil,
        # salmon" (head "fish oil", a byproduct, not a food someone eats
        # plain) and "Salmon nuggets, ..."/"Salmonberries, ..." (head
        # "salmon nuggets"/"salmonberries", already a different head
        # entirely). Fan-out of bare "salmon" before this entry: 0 -- no
        # record carried it, and "sammon" (a realistic typo) resolved to
        # the "Fish oil, salmon" byproduct instead of any fish.
    ),
    (
        "real-coffee",
        lambda r: (
            canonical_head(r["display"]) == "beverages"
            and "coffee" in r["display"].lower()
            and "substitute" not in r["display"].lower()
        ),
        ("cafe", "café"),
        # 2026-08-03: 8 records ("Beverages, coffee, ..."), excluding the 2
        # "coffee substitute" records. cafe/café were unreachable at any tier
        # before this entry -- "cafe" (4 characters) and "coffee" (6) fail
        # the FUZZY tier's length-difference bound before an edit distance is
        # even computed -- and the query landed by coincidence on the
        # unrelated "Syrup, Cane" instead (edit distance 1 from "cafe").
    ),
)


def apply_subhead_overrides(records, groups, used_identifiers):
    """Splits each override's matching records into their own alternate-name
    group, carrying the shared head-group's own terms plus the new word(s).

    A new group object per override, not a mutation of the shared one: every
    OTHER record under "Fish" or "Beverages" must keep exactly the terms it
    had, and giving the matched records their own group is what makes that
    true by construction rather than by a second filtering pass.
    """
    for name, predicate, new_terms in SUBHEAD_OVERRIDES:
        matching = [record for record in records if record["group"] is not None and predicate(record)]
        if not matching:
            continue
        base_group = matching[0]["group"]
        base_terms = base_group["terms"]
        combined_terms = base_terms + [term for term in new_terms if term not in base_terms]
        sub_group = {
            "head": f"{base_group['head']} ({name})",
            "concepts": base_group["concepts"],
            "terms": combined_terms,
            "identifier": identifier_for(name, used_identifiers),
            "records": len(matching),
        }
        for record in matching:
            record["group"] = sub_group
        groups[f"__subhead__{name}"] = sub_group

# ---------------------------------------------------------------------------
# String helpers mirroring CatalogSearch.kt character for character. They decide
# which records join; they never compute a nutrition value or a search result.
# ---------------------------------------------------------------------------

ACCENTED_CHARACTERS = "áàâäãåāéèêëēíìîïīóòôöõøōúùûüūñńçćšžýÿđł"
PLAIN_CHARACTERS = "aaaaaaaeeeeeiiiiiooooooouuuuunnccszyydl"


def normalize_for_search(value):
    """Case- and diacritic-fold, exactly as normalizeForSearch does in Kotlin."""
    expanded = value.lower().replace("ß", "ss").replace("æ", "ae").replace("œ", "oe")
    out = []
    for character in expanded:
        index = ACCENTED_CHARACTERS.find(character)
        out.append(PLAIN_CHARACTERS[index] if index >= 0 else character)
    return "".join(out)


# Kotlin's Char.isLetterOrDigit() is isLetter() || isDigit(), which is exactly
# these Unicode categories. Python's str.isalnum() is wider -- it accepts No and
# Nl, so `²` would be a token character here and not in Kotlin.
LETTER_OR_DIGIT_CATEGORIES = frozenset(("Lu", "Ll", "Lt", "Lm", "Lo", "Nd"))


def tokenize_normalized(normalized):
    """Split on anything that is not a letter or digit, as tokenizeNormalized does."""
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


def plural_variants(token):
    """The forms Kotlin's isPluralPair treats as the same word.

    Deliberately no -ies rule and no stemmer: Kotlin folds only a trailing "s"
    and a trailing "es", and a join that folded more than the matcher does
    would link records the search itself considers different words.
    """
    variants = {token}
    if token.endswith("es") and len(token) > 2:
        variants.add(token[:-2])
    if token.endswith("s") and len(token) > 1:
        variants.add(token[:-1])
    return variants


def fold_keys(value):
    """Every key a phrase may join under, as a frozenset of joined token forms."""
    tokens = tokenize_normalized(normalize_for_search(value))
    if not tokens:
        return frozenset()
    keys = {""}
    for token in tokens:
        keys = {(prefix + " " + variant).strip() for prefix in keys for variant in plural_variants(token)}
    return frozenset(keys)


# ---------------------------------------------------------------------------
# Inputs.
# ---------------------------------------------------------------------------


def load_catalog(path):
    """Read the F lines of the provenance-headed TSV. P (portion) lines and the
    header are skipped, and so is every nutrient column -- see the file header."""
    foods = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            fields = line.rstrip("\n").split("\t")
            if not fields or fields[0] != "F":
                continue
            if len(fields) < 3:
                raise SystemExit(f"{path}: malformed F line: {line!r}")
            item_id, name = fields[1], fields[2]
            if not item_id.startswith(ID_PREFIX):
                raise SystemExit(f"{path}: unexpected id scheme {item_id!r}")
            foods.append((item_id, name))
    if not foods:
        raise SystemExit(f"{path}: no food rows found")
    return foods


def load_concepts(path):
    document = json.loads(path.read_text(encoding="utf-8"))
    concepts = document.get("concepts")
    if not concepts:
        raise SystemExit(f"{path}: no concepts found")
    missing = set(LANGUAGES) - set(document.get("languages", []))
    if missing:
        raise SystemExit(f"{path} is missing languages {sorted(missing)}")
    return concepts


def concept_terms(concept):
    """Every string this concept answers to, display names first, in a stable order."""
    terms = []
    for language in LANGUAGES:
        block = concept.get(language, {})
        display = block.get("display")
        if display:
            terms.append(display)
        terms.extend(block.get("match", []))
    return terms


# ---------------------------------------------------------------------------
# Selection.
# ---------------------------------------------------------------------------

CAPITAL_RUN = re.compile(r"[A-Z]{2,}")
WORD = re.compile(r"[A-Za-z'&.]+")


def is_branded(name):
    """USDA writes manufacturer and outlet names in capitals.

    The run is looked for from the second character on as well as over the whole
    token, so `McDONALD'S` is caught alongside `KRAFT`, while an ordinary
    capitalized word such as `Italian` is not.
    """
    for token in WORD.findall(name):
        if len(token) >= 2 and token.upper() == token and token.lower() != token:
            return True
        if CAPITAL_RUN.search(token[1:]):
            return True
    return False


def is_institutional(name):
    lowered = name.lower()
    return any(lowered.startswith(prefix) for prefix in INSTITUTIONAL_PREFIXES)


def select_foods(foods, max_segments, max_name_tokens):
    """Apply the selection rule from the header. Returns (kept, reasons)."""
    kept = []
    reasons = collections.Counter()
    for item_id, name in foods:
        if is_branded(name):
            reasons["branded"] += 1
            continue
        if is_institutional(name):
            reasons["institutional"] += 1
            continue
        segments = len(name.split(","))
        words = len(tokenize_normalized(normalize_for_search(name)))
        if segments > max_segments and words > max_name_tokens:
            reasons["analytical"] += 1
            continue
        kept.append((item_id, name))
    return kept, reasons


# ---------------------------------------------------------------------------
# Join.
# ---------------------------------------------------------------------------


def build_display_index(concepts):
    """key -> set of concept ids, over English display names only."""
    index = collections.defaultdict(set)
    for concept in concepts:
        for key in fold_keys(concept["en"]["display"]):
            index[key].add(concept["id"])
    return index


def resolve_manual_links(concepts_by_id):
    """Expand MANUAL_HEAD_CONCEPTS, failing loudly if the table has drifted."""
    resolved = collections.defaultdict(list)
    for head, links in MANUAL_HEAD_CONCEPTS.items():
        for concept_id, expected_display, _reason in links:
            concept = concepts_by_id.get(concept_id)
            if concept is None:
                raise SystemExit(
                    f"MANUAL_HEAD_CONCEPTS: {concept_id} for head {head!r} is not in the synonym table"
                )
            actual = concept["en"]["display"]
            if actual != expected_display:
                raise SystemExit(
                    f"MANUAL_HEAD_CONCEPTS: {concept_id} is now {actual!r}, "
                    f"not {expected_display!r} -- re-check the link for head {head!r}"
                )
            for key in fold_keys(head):
                resolved[key].append(concept_id)
    return resolved


def canonical_head(name):
    """The head segment -- the text before the first comma -- normalized.

    Whitespace is collapsed by rebuilding from tokens, so `Game meat` and
    `Game  meat`, which SR Legacy spells both ways, are one head rather than two
    groups holding identical lists.
    """
    return " ".join(tokenize_normalized(normalize_for_search(name.split(",")[0])))


def concepts_for_head(head_keys, display_index, manual_index):
    """The concept ids a head joins to: at most one automatic, plus any manual.

    Candidates are pooled across every fold of the head before the count is
    taken. Testing each fold on its own would let a head that names two concepts
    under "beans" and one under "bean" join anyway -- the same ambiguity
    arriving through the other spelling.
    """
    candidates = set()
    for key in head_keys:
        candidates |= display_index.get(key, set())
    found = sorted(candidates) if len(candidates) == 1 else []
    for key in sorted(head_keys):
        found.extend(manual_index.get(key, ()))
    ordered = []
    for concept_id in found:
        if concept_id not in ordered:
            ordered.append(concept_id)
    return ordered


# The restates-head filter below is correct in general: a term identical to
# the display name's own head is usually redundant, since every record under
# that head already carries the head word in its own display name. It is
# operationally WRONG for a head whose records have no OTHER short form,
# because CatalogSearch.kt's coverageOf scores a coverage FRACTION per
# searchable form: a twelve-token qualified USDA name ("Chicken, broilers or
# fryers, meat and skin and giblets and neck, raw") scores a low fraction even
# on its own perfectly-spelled head word, while an unrelated record's two-token
# alternate name ("chicken egg", "tofu yogurt") scores a high one and wins the
# tier outright. Measured on the shipped catalog: bare "chicken" and "yogurt"
# resolved to Egg and Tofu yogurt respectively, not to any chicken or yogurt
# record, with no typo involved at all.
#
# Reviewed and dated per head, the same bar MANUAL_TERMS is held to -- this is
# not a way to bulk-restore every dropped term, it is a named exception for a
# head measured to need one.
RESTATES_HEAD_EXCEPTIONS = {
    # 2026-08-03: fan-out 0 in GeneratedCatalog.kt before this entry -- no
    # record carried the bare word. "chicken" already exists in AGROVOC source
    # (concepts c_1540 and c_24000 both list it as a match term) and only
    # needs this carve-out, no food-synonyms.json change.
    "chicken": {"chicken"},
    # 2026-08-03: fan-out 0. "yogurt" does not exist in AGROVOC source at all
    # (c_15463's entire en entry was the British spelling with no match list)
    # and was added as a MANUAL_TERMS entry in generate-food-synonyms.py
    # first; this carve-out is what keeps the generator from then dropping it
    # again as restating its own head.
    "yogurt": {"yogurt"},
}


def terms_for_head(head, concept_ids, concepts_by_id, other_head_keys, stats):
    """The alternate names a head segment contributes, in a stable order.

    A pure function of the head, which is what lets every record sharing that
    head share one emitted list rather than carrying its own copy.
    """
    head_keys = fold_keys(head)
    exceptions = RESTATES_HEAD_EXCEPTIONS.get(head, frozenset())
    seen = set()
    terms = []
    for concept_id in concept_ids:
        for term in concept_terms(concepts_by_id[concept_id]):
            normalized = normalize_for_search(term)
            if not tokenize_normalized(normalized) or normalized in seen:
                continue
            seen.add(normalized)
            term_keys = fold_keys(term)
            if term_keys & head_keys and normalized not in exceptions:
                stats["dropped_restates_head"] += 1
                continue
            if term_keys & other_head_keys:
                stats["dropped_other_head"] += 1
                continue
            terms.append(term)
    return terms


KOTLIN_KEYWORDS = frozenset(
    """as break class continue do else false for fun if in interface is null object package
    return super this throw true try typealias typeof val var when while""".split()
)


def identifier_for(head, used):
    """A camelCase Kotlin property name for a head segment, collision-guarded.

    Kept short on purpose. The reference is repeated on every record that head
    names, and each character of it costs a hundred lines of wrapping across the
    file once the argument list no longer fits on one line.
    """
    tokens = tokenize_normalized(normalize_for_search(head))
    ascii_tokens = ["".join(character for character in token if character.isascii()) for token in tokens]
    ascii_tokens = [token for token in ascii_tokens if token]
    candidate = "".join(
        token if index == 0 else token[:1].upper() + token[1:] for index, token in enumerate(ascii_tokens)
    )
    if not candidate or not candidate[0].isalpha() or candidate in KOTLIN_KEYWORDS:
        candidate = f"food{candidate[:1].upper()}{candidate[1:]}" if candidate else "food"
    if candidate in used:
        suffix = 2
        while f"{candidate}{suffix}" in used:
            suffix += 1
        candidate = f"{candidate}{suffix}"
    used.add(candidate)
    return candidate


def build_records(foods, concepts, stats):
    """Records plus the alternate-name groups they share.

    Terms are grouped by head segment rather than copied onto each record. The
    join attaches a concept's whole vocabulary to every record that head names
    -- all seventy cheeses answer to `сыр`, because they are all cheese -- and
    repeating nine thousand terms inline instead of referencing two hundred
    lists is the difference between a 680 KB source file and a third of that.
    """
    concepts_by_id = {concept["id"]: concept for concept in concepts}
    display_index = build_display_index(concepts)
    manual_index = resolve_manual_links(concepts_by_id)

    # Every head this catalog actually carries, so a term that is some other
    # record's head can be recognised as belonging to that record.
    heads = [canonical_head(name) for _, name in foods]
    all_head_keys = set()
    for head in set(heads):
        all_head_keys |= fold_keys(head)

    records = []
    groups = {}
    used_identifiers = set()
    joined_concepts = set()
    for (item_id, name), head in zip(foods, heads):
        record = {"id": item_id, "display": name, "group": None}
        records.append(record)
        if head not in groups:
            head_keys = fold_keys(head)
            if head in REJECTED_HEADS:
                concept_ids, terms = [], []
                stats["rejected_heads"] += 1
            else:
                concept_ids = concepts_for_head(head_keys, display_index, manual_index)
                terms = terms_for_head(head, concept_ids, concepts_by_id, all_head_keys - head_keys, stats)
            groups[head] = {
                "head": head,
                "concepts": concept_ids,
                "terms": terms,
                "identifier": identifier_for(head, used_identifiers) if terms else None,
                "records": 0,
            }
        group = groups[head]
        if not group["terms"]:
            continue
        record["group"] = group
        group["records"] += 1
        joined_concepts.update(group["concepts"])
        stats["joined_records"] += 1

    apply_subhead_overrides(records, groups, used_identifiers)

    emitted = [group for group in groups.values() if group["terms"] and group["records"]]
    stats["joined_concepts"] = len(joined_concepts)
    stats["name_groups"] = len(emitted)
    stats["alternate_terms"] = sum(len(group["terms"]) for group in emitted)
    return records, emitted


# ---------------------------------------------------------------------------
# Kotlin emission. Mirrors the shape proven by MealTextCorpus.kt.
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


def render_call(name, arguments, indent):
    """One `Name(a, b, c),` line when it fits, otherwise one argument per line
    with a trailing comma and the closing parenthesis on its own line."""
    pad = " " * indent
    if all(len(argument) == 1 for argument in arguments):
        single = f"{pad}{name}({', '.join(argument[0] for argument in arguments)}),"
        if len(single) <= MAX_LINE:
            return [single]
    lines = [f"{pad}{name}("]
    for argument in arguments:
        for offset, line in enumerate(argument):
            lines.append(f"{pad}    {line}" + ("," if offset == len(argument) - 1 else ""))
    lines.append(f"{pad}),")
    return lines


def render_record(record, indent):
    """The third parameter defaults to emptyList(), so an unjoined record is
    emitted with two arguments rather than a redundant empty list."""
    arguments = [[kotlin_string(record["id"])], [kotlin_string(record["display"])]]
    group = record["group"]
    if group is not None:
        arguments.append([f"{group['object']}.{group['identifier']}"])
    return render_call("CatalogRecord", arguments, indent)


def render_name_group(group, indent):
    """One shared alternate-name list, with the head and concepts it came from."""
    pad = " " * indent
    concepts = ", ".join(group["concepts"])
    provenance = f"{pad}/** {group['head']} -- {concepts} */"
    lines = [provenance if len(provenance) <= MAX_LINE else f"{pad}/** {group['head']} */"]
    declaration = f"{pad}val {group['identifier']}: List<String> = listOf("
    single = declaration + ", ".join(kotlin_string(term) for term in group["terms"]) + ")"
    if len(single) <= MAX_LINE:
        return lines + [single]
    lines.append(declaration)
    for term in group["terms"]:
        lines.append(f"{pad}    {kotlin_string(term)},")
    lines.append(f"{pad})")
    return lines


def chunk_blocks(blocks, budget):
    """Group rendered elements so no generated object grows unbounded.

    A JVM method may not exceed 64 KB of bytecode, and a single top-level
    property holding thousands of constructor calls would approach that in the
    file initializer; splitting the data across objects gives each its own.
    The same split keeps every declaration inside Detekt's LargeClass threshold.
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


FILE_HEADER = """package com.supernutrition.domain.nutrition

// GENERATED FILE. Produced by scripts/generate-catalog-kotlin.py from
// mobile/domain/nutrition/src/commonMain/resources/catalog/usda-sr-legacy.tsv
// and mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json.
// Do not edit by hand: the next run of the generator overwrites every line.
//
// Display names and canonical ids come from USDA FoodData Central, SR Legacy
// Foods (release 2018-04). USDA data are in the public domain and released
// under CC0 1.0; no permission is needed, and FoodData Central is credited as
// the source at its request.
//
// Alternate names come from the food synonym table, which has two sources with
// different obligations. Contains information from the AGROVOC Multilingual
// Thesaurus, (c) Food and Agriculture Organization of the United Nations (FAO),
// licensed under CC-BY IGO 3.0 -- https://creativecommons.org/licenses/by/3.0/igo/.
// That credit is a licence condition, not a courtesy, and must appear in the
// shipped acknowledgements screen. Colloquial and regional names come from
// Wikidata, CC0 1.0, which imposes no attribution requirement.
// See ATTRIBUTION.md beside the source JSON.
//
// Identity only: no nutrient value from the source TSV is emitted here, because
// grounding beyond a fake catalog is gated behind G-2."""


SPLIT_NOTE = (
    "// The split is a size limit, not a grouping: one initializer holding every",
    "// entry would approach the 64 KB JVM method limit and Detekt's LargeClass",
    "// threshold at once.",
)


def render_file(records, groups, stats, arguments):
    # Names first: a record cannot be rendered until it knows which object holds
    # the list it points at.
    name_chunks = chunk_blocks([render_name_group(group, 4) for group in groups], CHUNK_LINE_BUDGET)
    name_objects = [f"GeneratedNames{index}" for index in range(len(name_chunks))]
    offset = 0
    for object_name, chunk in zip(name_objects, name_chunks):
        for group in groups[offset : offset + len(chunk)]:
            group["object"] = object_name
        offset += len(chunk)

    record_chunks = chunk_blocks([render_record(record, 8) for record in records], CHUNK_LINE_BUDGET)
    record_objects = [f"GeneratedCatalogPart{index}" for index in range(len(record_chunks))]

    lines = [FILE_HEADER, ""]
    lines += [
        "/**",
        f" * {len(records)} identity records drawn from the {stats['source_foods']} SR Legacy foods.",
        " *",
        " * Bounded by rule rather than by alphabet, because most of what was left",
        " * out is not food anybody logs. Branded and institutional records are",
        f" * dropped ({stats['branded']} branded, {stats['institutional']} institutional), and so is any name that",
        f" * reads as an assay rather than a food: more than {arguments.max_segments} comma-separated",
        f" * clauses AND more than {arguments.max_name_tokens} words, {stats['analytical']} of them. Order is the",
        " * source's own, ascending NDB number, so widening either bound yields a",
        " * strict superset.",
        " *",
        f" * {stats['joined_records']} of these records carry alternate names: {stats['alternate_terms']} terms across",
        f" * {stats['name_groups']} shared lists, joined to {stats['joined_concepts']} AGROVOC concepts on an exact",
        " * fold of the name's head segment -- the text before the first comma.",
        " * The alternates are the English, Spanish and Russian words people",
        " * actually type; the display name stays USDA's own, because that is the",
        " * one the catalog can vouch for.",
        " *",
        " * A concept's whole vocabulary attaches to every record its head names,",
        " * not to one chosen representative: all seventy cheeses answer to `сыр`,",
        " * because all seventy are cheese. The shortest chicken name in SR Legacy",
        " * is `Chicken, meatless`, and making that the sole record `курица` could",
        " * reach is exactly the wrong-food failure the join exists to avoid.",
        " *",
        " * The join is exact-key only. It rejects two classes of term:",
        f" * {stats['dropped_restates_head']} that only restate the head segment the display name already",
        f" * carries, and {stats['dropped_other_head']} that are some other record's head segment -- AGROVOC",
        " * files beet leaves under beetroot, and `beet greens` belongs to the",
        " * record of that name, not to beet roots. It also refuses whole heads",
        f" * whose English word means a different food to each source ({stats['rejected_heads']} of them,",
        " * both `pepper`: SR Legacy's are Capsicum, AGROVOC's is the spice).",
        " */",
        "val generatedCatalog: List<CatalogRecord> = listOf(",
    ]
    for object_name in record_objects:
        lines.append(f"    {object_name}.records,")
    lines += [").flatten()"]

    for index, (object_name, chunk) in enumerate(zip(name_objects, name_chunks)):
        lines += [
            "",
            f"// Alternate names, part {index} of {len(name_chunks)}. Each list is shared by every record",
            "// whose head segment is the one named in its comment.",
            *SPLIT_NOTE,
            f"internal object {object_name} {{",
        ]
        for position, block in enumerate(chunk):
            # ktlint wants a blank line between declarations, but never one
            # immediately after the opening brace.
            if position:
                lines.append("")
            lines += block
        lines += ["}"]

    for index, (object_name, chunk) in enumerate(zip(record_objects, record_chunks)):
        lines += [
            "",
            f"// Records, part {index} of {len(record_chunks)}.",
            *SPLIT_NOTE,
            f"internal object {object_name} {{",
            "    val records: List<CatalogRecord> = listOf(",
        ]
        for block in chunk:
            lines += block
        lines += ["    )", "}"]

    text = "\n".join(line.rstrip() for line in lines)
    while "\n\n\n" in text:
        text = text.replace("\n\n\n", "\n\n")
    return text.rstrip("\n") + "\n"


DECLARED_PROPERTY = re.compile(r"^internal object (\w+) \{|^    val (\w+): List<String>")
REFERENCE = re.compile(r"(GeneratedNames\d+)\.(\w+)")
RECORD_ID = re.compile(r'CatalogRecord\(\s*"([^"]+)"')


def check_output(text, expected_records):
    """Cheap self-checks for what this repository enforces and this script cannot
    run: line length, trailing whitespace, a single final newline -- and, because
    nothing here can invoke a Kotlin compiler, that every shared-name reference
    actually resolves to a property this same file declares.
    """
    problems = []
    for number, line in enumerate(text.split("\n"), start=1):
        if len(line) > MAX_LINE:
            problems.append(f"line {number} is {len(line)} characters: {line[:60]!r}")
        if line != line.rstrip():
            problems.append(f"line {number} has trailing whitespace")
    if not text.endswith("\n") or text.endswith("\n\n"):
        problems.append("file must end with exactly one newline")

    declared = set()
    current_object = None
    for line in text.split("\n"):
        match = DECLARED_PROPERTY.match(line)
        if not match:
            continue
        if match.group(1):
            current_object = match.group(1)
        elif current_object:
            declared.add(f"{current_object}.{match.group(2)}")
    for object_name, property_name in set(REFERENCE.findall(text)):
        if f"{object_name}.{property_name}" not in declared:
            problems.append(f"unresolved reference {object_name}.{property_name}")

    ids = RECORD_ID.findall(text)
    if len(ids) != expected_records:
        problems.append(f"emitted {len(ids)} records, expected {expected_records}")
    if len(set(ids)) != len(ids):
        duplicates = sorted({item for item in ids if ids.count(item) > 1})[:5]
        problems.append(f"duplicate canonical ids, e.g. {duplicates}")
    return problems


# ---------------------------------------------------------------------------
# Entry point.
# ---------------------------------------------------------------------------


def parse_arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--catalog", type=pathlib.Path, default=DEFAULT_CATALOG)
    parser.add_argument("--synonyms", type=pathlib.Path, default=DEFAULT_SYNONYMS)
    parser.add_argument("--out", type=pathlib.Path, default=DEFAULT_OUT)
    parser.add_argument("--max-segments", type=int, default=DEFAULT_MAX_SEGMENTS)
    parser.add_argument("--max-name-tokens", type=int, default=DEFAULT_MAX_NAME_TOKENS)
    parser.add_argument(
        "--stats-only",
        action="store_true",
        help="report the counts and the emitted size without writing the file",
    )
    return parser.parse_args(argv)


def main(argv):
    arguments = parse_arguments(argv)
    foods = load_catalog(arguments.catalog)
    concepts = load_concepts(arguments.synonyms)

    selected, reasons = select_foods(foods, arguments.max_segments, arguments.max_name_tokens)
    stats = collections.Counter(
        {
            "source_foods": len(foods),
            "source_concepts": len(concepts),
            "branded": reasons["branded"],
            "institutional": reasons["institutional"],
            "analytical": reasons["analytical"],
        }
    )
    records, groups = build_records(selected, concepts, stats)
    text = render_file(records, groups, stats, arguments)

    problems = check_output(text, len(records))
    for problem in problems:
        print(f"OUTPUT PROBLEM: {problem}", file=sys.stderr)

    encoded = text.encode("utf-8")
    print(f"source foods         {stats['source_foods']}")
    print(f"  dropped branded    {stats['branded']}")
    print(f"  dropped institutional {stats['institutional']}")
    print(f"  dropped analytical {stats['analytical']}")
    print(f"selected records     {len(records)}")
    print(f"joined records       {stats['joined_records']}")
    print(f"joined concepts      {stats['joined_concepts']} of {stats['source_concepts']}")
    print(f"name groups          {stats['name_groups']}")
    print(f"alternate terms      {stats['alternate_terms']}")
    print(f"  dropped restates head {stats['dropped_restates_head']} (counted once per head, not per record)")
    print(f"  dropped other head {stats['dropped_other_head']}")
    print(f"  rejected heads     {stats['rejected_heads']}")
    print(f"emitted lines        {text.count(chr(10))}")
    print(f"emitted bytes        {len(encoded)}")

    if arguments.stats_only:
        return 1 if problems else 0
    if problems:
        print("refusing to write: fix the problems above", file=sys.stderr)
        return 1
    arguments.out.parent.mkdir(parents=True, exist_ok=True)
    arguments.out.write_text(text, encoding="utf-8")
    print(f"wrote {arguments.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
