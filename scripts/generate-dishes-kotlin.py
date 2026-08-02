#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-dishes-kotlin.py -- BUILD-TIME generator for the shipped dish
composition set searched by DishExpansion.

This script runs at build time only. It never runs on device, and nothing it
touches is a runtime dependency. Its single output is one deterministic Kotlin
file under commonMain:

    mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/
    nutrition/GeneratedDishes.kt

    stdlib only. No third-party packages. Python 3.9+.
    (If a future change needs a third-party package, declare it HERE in this
    header rather than assuming the build host has it installed.)


WHAT IS EMITTED, AND WHAT IS DELIBERATELY NOT
=============================================
`val generatedDishes: List<DishRecord>` -- composition only. A dish carries a
canonical id, a display name, the alternate names the source can be trusted
for, and the catalog ids of the foods it contains.

EVERY INGREDIENT IS EMITTED WITH `grams = null`, AND THAT IS NOT AN OVERSIGHT.
The source file carries a gram weight for every ingredient, and this generator
reads none of them into Kotlin. A weight that reaches the app without an
approved package is fabricated nutrition wearing a valid provenance chain: the
number would be signed, versioned and arithmetically sound and would still
describe a meal nobody ate (DC-3, DFM-2). Gate G-2 owns that approval. The
FNDDS weights stay in the JSON, where their provenance -- batch quantities
before cooking, plus a recipe-level moisture change -- lives beside them and
can be read by whoever opens the gate.

What crosses into Kotlin is WHICH foods a dish contains. That is useful with no
number attached at all, because tapping a dish adds the right items to the log
and the amount is asked for at serving confirmation anyway.

The self-check at the bottom of this script fails the run if a `DishIngredient`
is ever emitted with a second argument, so this property is enforced rather
than remembered.


SOURCES
=======
1. USDA FoodData Central, Survey Foods (FNDDS 2021-2023), release 2024-10-31,
   via mobile/domain/nutrition/src/commonMain/resources/dishes/dishes-fndds.json
   (3,829 multi-ingredient recipes and 1,602 single-ingredient entries). Public
   domain, CC0 1.0. Supplies the dish id, the display name, the composition,
   and the alternate-name candidates. Read the README.md beside it first: the
   gram weights are not per-100 g and the `additionalDescriptions` field is not
   a synonym list.

2. The shipped identity catalog, via GeneratedCatalog.kt. Supplies the set of
   `food:usda-srl-NNNNN` ids that actually exist in Kotlin, which is what an
   ingredient id has to join to be worth anything. Note that this is a SUBSET
   of the SR Legacy TSV -- generate-catalog-kotlin.py selects 3,519 of 7,793
   foods -- so joining against the TSV instead would produce ids that compile
   and resolve to nothing at runtime. The Kotlin file is therefore the join
   target, deliberately.


HOW AN INGREDIENT JOINS -- AND WHY NOTHING IS INFERRED
======================================================
The source records how far each ingredient resolved, in `reference.kind`. This
script reads that field; it never re-derives a join.

  * `sr_legacy_ndb` carries `canonicalItemId`. Kept when that id is present in
    the Kotlin catalog, dropped when it is not.
  * `fndds_subrecipe` names another entry in the same file. DROPPED, and NOT
    followed. 3,562 of the 4,035 such references in recipes point at another
    multi-ingredient recipe -- "Beef as ingredient in recipes" is FNDDS's model
    of unspecified beef, an aggregate of several cuts -- so flattening one
    would expand a single beef line into five beef cuts the user did not eat.
    The remaining 473 point at single-ingredient entries and could be
    dereferenced safely, but were measured to rescue only 53 dishes and 302
    ingredient rows, which does not pay for a second resolution rule.
  * `ndb_not_in_sr_legacy`, `fndds_pseudo_ingredient`, `unresolved` carry no
    id by construction. Dropped. The source is explicit that emitting one would
    be a fabricated join.

Repeated ids within one dish are collapsed. With weights removed, a food listed
twice carries no more information than the same food listed once, and two
identical entries would put the same item into the log twice.


HOW THE DISH SET IS BOUNDED  (the selection rule -- widen it here)
==================================================================
A dish is emitted when all of these hold:

  1. `kind == "recipe"`. The 1,602 `single-ingredient` entries exist so that
     sub-recipe references have something to point at; they are not dishes.

  2. EVERY food ingredient joined -- `--min-ingredient-coverage`, default 1.0.
     This is the rule that matters, and it is a correctness bound rather than a
     size one. At 0.5 the emitted set is 2,086 dishes and "Stew, beef" ships as
     tap water plus table salt, because its beef and its vegetables are both
     sub-recipes; "Tuna noodle casserole" ships without the tuna and "Potato
     salad" without the potatoes, each for the same reason. A partial
     composition is not a smaller answer, it is a wrong one, and it is wrong in
     the direction this repository treats as most dangerous: it looks complete.
     Pseudo-ingredients ("Calcium as ingredient") are excluded from the
     denominator, being bookkeeping rather than food.

  3. At least `--min-ingredients` distinct joined ids, default 2. A dish that
     expands to one item is just that food, and the catalog already has it.

  4. The id and the display name each fit in 120 columns when the record is
     wrapped -- 105 characters. There is no legal way to break a Kotlin string
     literal across lines, so a longer one could only be emitted as source
     Detekt rejects. Nothing surviving rules 1-3 comes close at the default
     bound (the longest is 92 characters), but this is not decoration: at
     --min-ingredient-coverage 0.5 it drops 45 records, and all 45 overflow on
     the ID where only 24 overflow on the name. Bounding the display name alone
     let those 45 through, and the emitted file carried 21 over-long lines.

  5. Source order is kept, so raising a bound yields a strict superset.


WHERE THE COVERAGE BOUND COSTS, AND WHAT IT WOULD TAKE TO WIDEN IT
==================================================================
Measured on this release, reproducible with --stats-only at any setting. Sizes
are of the emitted Kotlin.

    coverage  dishes   lines    bytes   what ships
    1.00         582   3,829   135 KB   the default: complete compositions
    0.90         595   4,027   141 KB   one ingredient missing in ten
    0.80         830   6,455   222 KB   cheeseburger without the cheese
    0.67       1,112   9,127   313 KB
    0.50       2,086  15,520   546 KB   beef stew as tap water and salt
    0.00       2,351  17,066   609 KB   every dish with two joined ingredients

Size is not what makes 1.00 the default -- 0.00 emits 609 KB, larger than
GeneratedCatalog.kt's 436 KB but not impossible. Completeness is.

The honest way to widen this is to widen the CATALOG, not this bound. 429 of
the 1,130 distinct ingredient ids the recipes ask for exist in SR Legacy and
are absent from GeneratedCatalog.kt, costing 3,080 ingredient rows. They are
absent because generate-catalog-kotlin.py reads them as assays or as branded:
"Wheat flour, white, all-purpose, enriched, bleached" (204 rows) and "Rice,
white, long-grain, regular, enriched, cooked" (154 rows) exceed its clause and
word caps, and 27 ids including "Cheese, cheddar (Includes foods for USDA's
Food Distribution Program)" (82 rows) are dropped by its capital-run branded
test matching the word USDA. Every one of those is a cooked or prepared staple
that recipes are built from. Raising the catalog's caps would widen this file
for free, since the rule here is defined against whatever the catalog holds.


ALTERNATE NAMES -- WHY MOST OF THE SOURCE FIELD IS REFUSED
==========================================================
The source's `additionalDescriptions` is FNDDS's own field, verbatim, and its
README warns in as many words that it is not a synonym list. The measurement
agrees: of 6,864 entries across the recipes, the most frequent are `any
source` (102), `spaghetti, any shape pasta` (99), `with or without fat` (94),
`cheese` (79) and `NS as to fat added` (69). Those are coverage notes, not
names.

Feeding them in whole would be actively harmful rather than merely useless.
CatalogSearch indexes every alternate name as its own searchable form and
TOKEN_SUBSET matches when all query tokens appear in one form, so `any type of
jelly, jam, spreadable fruit, all fruit, honey` would answer the query `honey`
with an almond butter sandwich, and the bare term `cheese` would answer
`cheese` at the EXACT tier -- outranking every real cheese record in the merged
result.

A term is kept only when all of these hold. The rule is conservative by
construction and drops real names to do it:

  * one or two words, no comma and no slash. Longer entries are lists or
    sentences, and a longer form is strictly more dangerous under TOKEN_SUBSET
    because any single word inside it matches.
  * no ATTRIBUTE_WORDS token. Scope words (`any`, `all`, `NS`, `other`),
    quality words (`low`, `reduced`, `free`, `homemade`) and preparation words
    (`frozen`, `roasted`) describe a record rather than name it. Without this,
    `frozen` becomes an alternate name of "Peruvian beans, from dried" and
    `homemade` of a cheeseburger.
  * used by at most MAX_ALTERNATE_DISHES dishes. A note is reused across the
    survey; a name belongs to its dish. This is the single most effective test
    in the set: 1,422 of the 6,864 entries reach it and none get past.
  * not a substring of the dish's own display name.
  * no record in the food catalog or in the dish set carries all of the term's
    tokens in its own name -- covers_a_record(), which is MatchTier.TOKEN_SUBSET
    read from the other end. A query equal to such a term already reaches that
    record at the subset tier, and attaching the term here would promote THIS
    record to EXACT and take the whole result set, hiding the record the word
    belongs to. `black bean` on "Bean chips" hides SR Legacy's black beans;
    `vanilla`, `cinnamon` and `strawberry` on assorted doughnuts hide the
    flavourings; `Pak-choi` on a stir-fry hides the pak-choi record. This is
    the catalog generator's "some other record's head" rule, generalised from
    the head segment to the whole name because a dish name is not one word.

494 of the 6,864 entries survive across all recipes, and 143 of those land on a
dish the composition bounds also keep. What survives is mostly what the field
is worth having for -- `unagi`, `sashimi`, `bok choy`, `nopal`, `giardiniera`,
`guarapo`, `malasada`, `Kadayif`, `glogg`, `gluhwein`, `hoe cake`,
`Anadama bread`, `Zucaritas`, `habanero`, `prosecco`, `sticky rice`. Cross-
script and regional names are the whole reason DishRecord carries the field.

The residue is honest to state: a good share of the survivors are brand names
(`Klondike Bar`, `Grape-Nuts`, `Ritz Bits`, `Rice Krispies`, `McDonald's` on a
milk shake), and a few are FNDDS saying one record also covers a neighbouring
dish. Both are the source's own claim rather than this script's, and
DishProposal carries the matched dish's display name precisely so a user can
see which dish answered before confirming it. Brands were left in deliberately:
no test separates `Klondike Bar` from `Arroz guisado` without also taking
`Tortilla Espanola`, and someone typing a product name is asking a question
this data can answer. `McDonald's` is the weak case -- it names the outlet, not
the food -- and it is one entry; a reviewer who wants brands gone should say so
and get an explicit rule rather than a heuristic that also eats the Spanish.


OUTPUT SHAPE
============
The records are split across several `internal object` holders rather than
sitting in one top-level property, following GeneratedCatalog.kt and
MealTextCorpus.kt. Two limits are respected at once: a JVM method may not
exceed 64 KB of bytecode and a single initializer holding thousands of
constructor calls approaches it, and the same split keeps every generated
declaration inside Detekt's LargeClass threshold. The public `generatedDishes`
flattens them.

Records are written through two private helpers rather than as literal
`DishRecord(... listOf(DishIngredient(...), ...))` calls. Inline construction
was measured at 24,000 lines and 1.2 MB for the same content, because
`DishIngredient("food:usda-srl-01001")` is 38 characters and two of them
already exceed 120 columns inside a wrapped record. The helper also gives the
grams-are-absent property one place to live instead of one per ingredient.

Nothing here can run a Kotlin compiler, so check_output() re-reads the emitted
text and fails the run on what would otherwise surface only as a build or lint
error: an over-long line, trailing whitespace, a missing final newline, a part
object that is declared but never flattened or referenced but never declared, a
lost or duplicated dish, a record whose ingredients differ from what was
selected, an ingredient id that is not in the catalog, an ingredient repeated
inside one dish, a record with fewer than two ingredients, and any
DishIngredient carrying a second argument.

Each of those checks was confirmed to fire by mutating a known-good file --
including two that turned out to be matching nothing at all, because their
anchored patterns were compiled without re.MULTILINE and so found no lines to
fail on. A check that cannot fail is worse than no check, and this one shape of
mistake is what the repository's own build-output guidance warns about.

Usage:
    python3 scripts/generate-dishes-kotlin.py
    python3 scripts/generate-dishes-kotlin.py --min-ingredient-coverage 0.8 --stats-only
"""

import argparse
import collections
import json
import math
import pathlib
import re
import sys
import textwrap
import unicodedata

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_DISHES = (
    REPO_ROOT / "mobile/domain/nutrition/src/commonMain/resources/dishes/dishes-fndds.json"
)
DEFAULT_CATALOG = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition"
    / "domain/nutrition/GeneratedCatalog.kt"
)
DEFAULT_OUT = (
    REPO_ROOT
    / "mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition"
    / "domain/nutrition/GeneratedDishes.kt"
)

MAX_LINE = 120
CHUNK_LINE_BUDGET = 380

# A wrapped record indents its string arguments by twelve spaces, and a Kotlin
# string literal cannot be broken across lines. 120 - 12 - 2 quotes - 1 comma.
# It bounds the ID as well as the display name: the id is a slug of the name
# with `dish:fndds:` in front of it, so it is the longer of the two in all
# 3,829 recipes, and it is what overflows first at any looser coverage bound.
MAX_LITERAL = 105

# Selection bounds. See the header: raising either yields a strict superset.
DEFAULT_MIN_INGREDIENTS = 2
DEFAULT_MIN_COVERAGE = 1.0

# Alternate-name bounds. See the header for what each one is holding back.
MAX_ALTERNATE_TOKENS = 2
MAX_ALTERNATE_DISHES = 2

# Words that describe a record rather than name it. The preparation words are
# CatalogSearch.PREPARATION_WORDS; the rest are the scope and quality
# vocabulary FNDDS writes its coverage notes in.
ATTRIBUTE_WORDS = frozenset(
    """
    raw fresh cooked boiled poached steamed grilled griddled roasted baked fried seared sauteed
    toasted smoked cured pickled dried frozen canned
    any all other others includes including include except type types kind kinds ns nfs
    unspecified made variety varieties or and plus with without from as to no not none based
    source sources flavors flavor style styles commercially only added homemade restaurant
    regular light lite low high reduced free fat sugar sodium calorie calories plain unsalted
    salted sweetened unsweetened diet nonfat skinless prepared coated uncoated crispy soft hard
    whole part small medium large mini jumbo bite size mix mixed frosted flavored fortified
    store brands brand
    """.split()
)

CATALOG_RECORD = re.compile(r'CatalogRecord\(\s*"([^"]+)",\s*"([^"]+)"')
INGREDIENT_ID_PREFIX = "food:usda-srl-"


# ---------------------------------------------------------------------------
# Input.
# ---------------------------------------------------------------------------


def load_catalog(path):
    """The ids and names the shipped Kotlin catalog actually holds.

    Read from the emitted Kotlin rather than from the source TSV on purpose:
    the TSV carries 7,793 foods and the catalog selects 3,519 of them, so an
    ingredient id taken from the TSV would compile and resolve to nothing.
    """
    text = path.read_text(encoding="utf-8")
    pairs = CATALOG_RECORD.findall(text)
    if not pairs:
        raise SystemExit(f"no CatalogRecord entries found in {path}; has its shape changed?")
    ids = {item_id for item_id, _ in pairs}
    names = [name for _, name in pairs]
    return ids, names


def load_dishes(path):
    document = json.loads(path.read_text(encoding="utf-8"))
    recipes = [entry for entry in document["dishes"] if entry.get("kind") == "recipe"]
    if not recipes:
        raise SystemExit(f"{path} carries no recipe entries")
    return document, recipes


# ---------------------------------------------------------------------------
# Normalization. Mirrors CatalogSearch.normalizeForSearch closely enough to
# decide which alternate names are dangerous; it is a filter input, never an
# emitted value, so an exact reimplementation would buy nothing.
# ---------------------------------------------------------------------------


def fold(value):
    decomposed = unicodedata.normalize("NFKD", value.lower())
    stripped = "".join(character for character in decomposed if not unicodedata.combining(character))
    return " ".join(re.findall(r"[a-z0-9]+", stripped))


# ---------------------------------------------------------------------------
# Selection.
# ---------------------------------------------------------------------------


def joined_ingredients(entry, catalog_ids, stats):
    """(food ingredient count, distinct joined ids in source order).

    Pseudo-ingredients are FNDDS bookkeeping rather than food -- "Calcium as
    ingredient" -- so they are excluded from the denominator instead of being
    counted as an ingredient this file failed to resolve. Every other
    ingredient is counted, and every reason one was lost is counted with it.
    """
    food = 0
    joined = []
    for ingredient in entry["ingredients"]:
        reference = ingredient["reference"]
        kind = reference["kind"]
        if kind == "fndds_pseudo_ingredient":
            stats["ingredient_pseudo"] += 1
            continue
        food += 1
        item_id = reference.get("canonicalItemId")
        if item_id is None:
            stats["ingredient_no_id_" + kind] += 1
        elif item_id not in catalog_ids:
            stats["ingredient_not_in_catalog"] += 1
        elif item_id in joined:
            stats["ingredient_repeated"] += 1
        else:
            joined.append(item_id)
    return food, joined


def select_dishes(recipes, catalog_ids, arguments, stats):
    selected = []
    for entry in recipes:
        food, joined = joined_ingredients(entry, catalog_ids, stats)
        stats["food_ingredients"] += food
        stats["joined_ingredients"] += len(joined)
        stats["dropped_ingredients"] += food - len(joined)
        if len(joined) < arguments.min_ingredients:
            stats["dropped_too_few"] += 1
            continue
        if len(joined) < math.ceil(food * arguments.min_ingredient_coverage):
            stats["dropped_incomplete"] += 1
            continue
        if max(len(entry["displayName"]), len(entry["dishId"])) > MAX_LITERAL:
            stats["dropped_long_name"] += 1
            continue
        selected.append((entry, joined))
    return selected


def alternate_name_index(recipes):
    """How many dishes each candidate term is used by.

    A coverage note is reused across the survey and a name belongs to its dish,
    so this count is the single most effective filter in the set.
    """
    counts = collections.Counter()
    for entry in recipes:
        for term in {fold(term) for term in entry.get("additionalDescriptions") or []}:
            counts[term] += 1
    return counts


def alternate_names(entry, context, stats):
    kept = []
    for term in entry.get("additionalDescriptions") or []:
        folded = fold(term)
        if not folded:
            reason = "empty"
        elif "," in term or "/" in term:
            reason = "list"
        elif len(folded.split()) > MAX_ALTERNATE_TOKENS:
            reason = "long"
        elif any(token in ATTRIBUTE_WORDS for token in folded.split()):
            reason = "attribute"
        elif context["term_dishes"][folded] > MAX_ALTERNATE_DISHES:
            reason = "shared"
        elif folded in fold(entry["displayName"]):
            reason = "restates"
        elif covers_a_record(folded, context["food_forms"]):
            reason = "hides food"
        elif covers_a_record(folded, context["dish_forms"]):
            reason = "hides dish"
        elif folded in {fold(existing) for existing in kept}:
            reason = "duplicate"
        else:
            reason = "keep"
            kept.append(term)
        stats[f"alternate_{reason.replace(' ', '_')}"] += 1
    return kept


def index_forms(names):
    """Record names as token sets, bucketed by token.

    Bucketing is not an optimisation for its own sake: covers_a_record() is
    asked about every candidate term against every record in two sets, and the
    bucket makes that the handful of records that could possibly match.
    """
    buckets = collections.defaultdict(list)
    for name in names:
        tokens = frozenset(fold(name).split())
        if not tokens:
            continue
        for token in tokens:
            buckets[token].append(tokens)
    return buckets


def covers_a_record(folded, buckets):
    """True when some record's name contains every token of [folded].

    This is MatchTier.TOKEN_SUBSET read from the other end. A query equal to
    this term already reaches that record at the subset tier; attaching the
    term here would promote THIS record to the EXACT tier for it and take the
    whole result set, hiding the record the word belongs to. `black bean` on
    "Bean chips" hides SR Legacy's black beans, and `vanilla` on a doughnut
    hides vanilla extract.
    """
    tokens = folded.split()
    if not tokens:
        return False
    candidates = buckets.get(tokens[0], ())
    return any(all(token in record for token in tokens) for record in candidates)


def build_context(recipes, catalog_names):
    return {
        "term_dishes": alternate_name_index(recipes),
        "food_forms": index_forms(catalog_names),
        "dish_forms": index_forms(entry["displayName"] for entry in recipes),
    }


# ---------------------------------------------------------------------------
# Kotlin emission. Mirrors the shape proven by GeneratedCatalog.kt.
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
    """One `name(a, b, c),` line when it fits, otherwise one argument per line
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


def render_name_list(terms, indent):
    """The alternate names as one `listOf(...)` argument, wrapped if it has to
    be. Returned as a list of lines so render_call can place it."""
    single = f"listOf({', '.join(kotlin_string(term) for term in terms)})"
    if indent + len(single) + 1 <= MAX_LINE:
        return [single]
    return ["listOf("] + [f"    {kotlin_string(term)}," for term in terms] + [")"]


def render_dish(entry, ingredients, names, indent):
    arguments = [[kotlin_string(entry["dishId"])], [kotlin_string(entry["displayName"])]]
    if names:
        arguments.append(render_name_list(names, indent + 4))
    arguments += [[kotlin_string(item_id)] for item_id in ingredients]
    return render_call("namedDish" if names else "dish", arguments, indent)


def chunk_blocks(blocks, budget):
    """Group rendered records so no generated object grows unbounded.

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


SPLIT_NOTE = (
    "// The split is a size limit, not a grouping: one initializer holding every",
    "// record would approach the 64 KB JVM method limit and Detekt's LargeClass",
    "// threshold at once.",
)

HELPERS = """/**
 * One dish, its ingredients named by catalog id and by nothing else.
 *
 * [DishIngredient.grams] is never passed. The source carries a weight for
 * every one of these, and none of them crosses into Kotlin: a gram figure
 * without an approved package is fabricated nutrition with a valid provenance
 * chain, which is the failure the grounding chain exists to prevent (DC-3,
 * DFM-2). Weights arrive with the package G-2 approves, or not at all.
 */
private fun dish(id: String, name: String, vararg items: String): DishRecord =
    DishRecord(id, name, emptyList(), items.map { DishIngredient(it) })"""

NAMED_HELPER = """/** As [dish], for a dish the source offers usable alternate names for. */
private fun namedDish(id: String, name: String, names: List<String>, vararg items: String): DishRecord =
    DishRecord(id, name, names, items.map { DishIngredient(it) })"""


def render_header(provenance):
    primary = provenance["primarySource"]
    join = provenance["joinSource"]
    return [
        "package com.supernutrition.domain.nutrition",
        "",
        "// GENERATED FILE. Produced by scripts/generate-dishes-kotlin.py from",
        "// mobile/domain/nutrition/src/commonMain/resources/dishes/dishes-fndds.json",
        "// joined against the ids in GeneratedCatalog.kt.",
        "// Do not edit by hand: the next run of the generator overwrites every line.",
        "//",
        f"// Dish names and compositions come from {primary['name']},",
        f"// {primary['dataset']}, release {primary['releaseDate']}. Ingredient ids are",
        f"// derived from {join['dataset']}, release {join['releaseDate']}, which supplies",
        "// the NDB numbers the catalog's id scheme is built on.",
        "//",
        f"// {provenance['licence']}. USDA data are in the public",
        "// domain; no permission is needed, and FoodData Central is credited as the",
        "// source at its request.",
        f"// {provenance['citation']}",
        "//",
        "// COMPOSITION ONLY: every ingredient is emitted with grams = null. The source",
        "// carries a weight for each of them and this file deliberately carries none,",
        "// because a weight without an approved package is a fabricated number with a",
        "// valid provenance chain. The weights stay in the JSON pending G-2 (DC-3,",
        "// DFM-2). Which foods a dish contains is useful without them.",
    ]


def render_kdoc(paragraphs):
    """A KDoc block, reflowed. Written as paragraphs rather than as literal
    lines so that changing a count cannot leave the wrapping ragged."""
    lines = ["/**"]
    for index, paragraph in enumerate(paragraphs):
        if index:
            lines.append(" *")
        lines += [f" * {line}" for line in textwrap.wrap(" ".join(paragraph.split()), width=72)]
    return lines + [" */"]


def render_file(selected, stats, arguments, provenance):
    blocks = [render_dish(entry, ingredients, names, 8) for entry, ingredients, names in selected]
    chunks = chunk_blocks(blocks, CHUNK_LINE_BUDGET)
    objects = [f"GeneratedDishesPart{index}" for index in range(len(chunks))]
    uses_names = any(names for _, _, names in selected)

    subrecipe = stats["ingredient_no_id_fndds_subrecipe"]
    lines = render_header(provenance)
    lines += [""]
    lines += render_kdoc(
        [
            f"""{len(selected)} dishes drawn from the {stats['recipes']} FNDDS recipes, carrying
            {stats['emitted_ingredients']} ingredient references and not one gram weight.""",
            f"""A dish is here only when EVERY food ingredient in it joined the catalog --
            coverage bound {arguments.min_ingredient_coverage:.2f} -- which is why so many are not:
            {stats['dropped_incomplete']} were dropped as incomplete and {stats['dropped_too_few']}
            for holding fewer than {arguments.min_ingredients} joinable ingredients. A partial
            composition is not a smaller answer but a wrong one. At a looser bound `Stew, beef`
            ships as tap water and table salt, its beef and its vegetables both being sub-recipes
            that this file does not follow.""",
            f"""{stats['dropped_ingredients']} of the {stats['food_ingredients']} ingredient
            references across all recipes did not join. {subrecipe} of those name another FNDDS
            recipe rather than a food, and {stats['ingredient_not_in_catalog']} name a food that SR
            Legacy has and the catalog left out -- so widening the catalog widens this file, since
            the bound is defined against whatever the catalog holds.""",
            f"""{stats['dishes_with_names']} dishes carry alternate names, {stats['alternate_keep']}
            terms in all. The source field they come from is FNDDS's own coverage note rather than a
            synonym list, so most of it is refused: a bare `cheese` there would answer the query
            `cheese` at the EXACT tier, ahead of every real cheese record.""",
        ]
    )
    lines += ["val generatedDishes: List<DishRecord> = listOf("]
    for object_name in objects:
        lines.append(f"    {object_name}.records,")
    lines += [").flatten()", ""]
    lines += HELPERS.split("\n")
    if uses_names:
        lines += [""] + NAMED_HELPER.split("\n")

    for index, (object_name, chunk) in enumerate(zip(objects, chunks)):
        lines += [
            "",
            f"// Dishes, part {index} of {len(chunks)}.",
            *SPLIT_NOTE,
            f"internal object {object_name} {{",
            "    val records: List<DishRecord> = listOf(",
        ]
        for block in chunk:
            lines += block
        lines += ["    )", "}"]

    text = "\n".join(line.rstrip() for line in lines)
    while "\n\n\n" in text:
        text = text.replace("\n\n\n", "\n\n")
    return text.rstrip("\n") + "\n"


# ---------------------------------------------------------------------------
# Self-checks. Nothing here can run a Kotlin compiler.
# ---------------------------------------------------------------------------

# re.MULTILINE is load-bearing on both of these: without it `^` anchors to the
# start of the whole file, findall returns nothing, and the reference check
# passes by matching no lines at all rather than by finding no problem.
PART_DECLARATION = re.compile(r"^internal object (GeneratedDishesPart\d+) \{$", re.MULTILINE)
PART_REFERENCE = re.compile(r"^    (GeneratedDishesPart\d+)\.records,$", re.MULTILINE)
DISH_CALL = re.compile(r"^        (namedDish|dish)\($")
DISH_ONE_LINER = re.compile(r'^        (namedDish|dish)\((.*)\),$')
EMITTED_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
INGREDIENT_WITH_WEIGHT = re.compile(r"DishIngredient\([^)\n]*,")


def emitted_dishes(text):
    """Every emitted record as (dish id, ingredient ids), read back from the
    text rather than from the model that produced it."""
    records = []
    lines = text.split("\n")
    index = 0
    while index < len(lines):
        line = lines[index]
        single = DISH_ONE_LINER.match(line)
        if single:
            values = EMITTED_STRING.findall(single.group(2))
            records.append((values[0], [v for v in values[2:] if v.startswith(INGREDIENT_ID_PREFIX)]))
            index += 1
            continue
        if DISH_CALL.match(line):
            index += 1
            values = []
            while index < len(lines) and lines[index] != "        ),":
                values += EMITTED_STRING.findall(lines[index])
                index += 1
            records.append((values[0], [v for v in values[2:] if v.startswith(INGREDIENT_ID_PREFIX)]))
        index += 1
    return records


def check_output(text, expected, catalog_ids, min_ingredients):
    problems = []
    for number, line in enumerate(text.split("\n"), start=1):
        if len(line) > MAX_LINE:
            problems.append(f"line {number} is {len(line)} characters: {line[:60]!r}")
        if line != line.rstrip():
            problems.append(f"line {number} has trailing whitespace")
    if not text.endswith("\n") or text.endswith("\n\n"):
        problems.append("file must end with exactly one newline")

    # The one property this whole file exists to preserve.
    weighted = INGREDIENT_WITH_WEIGHT.search(text)
    if weighted:
        problems.append(f"a DishIngredient carries a second argument: {weighted.group(0)!r}")

    declared = set(PART_DECLARATION.findall(text))
    referenced = set(PART_REFERENCE.findall(text))
    if not declared or not referenced:
        problems.append("found no part objects or no part references; has the emitted shape changed?")
    for missing in sorted(referenced - declared):
        problems.append(f"unresolved reference {missing}.records")
    for orphan in sorted(declared - referenced):
        problems.append(f"{orphan} is declared but never flattened into generatedDishes")

    records = emitted_dishes(text)
    if len(records) != len(expected):
        problems.append(f"emitted {len(records)} dishes, expected {len(expected)}")
    ids = [dish_id for dish_id, _ in records]
    if len(set(ids)) != len(ids):
        duplicates = sorted({item for item in ids if ids.count(item) > 1})[:5]
        problems.append(f"duplicate canonical dish ids, e.g. {duplicates}")
    for dish_id, ingredients in records:
        if len(ingredients) < min_ingredients:
            problems.append(f"{dish_id} emitted with {len(ingredients)} ingredients")
        if len(set(ingredients)) != len(ingredients):
            problems.append(f"{dish_id} repeats an ingredient id")
        for item_id in ingredients:
            if item_id not in catalog_ids:
                problems.append(f"{dish_id} names {item_id}, which is not in the catalog")

    emitted = {dish_id: ingredients for dish_id, ingredients in records}
    for entry, ingredients, _ in expected:
        if emitted.get(entry["dishId"]) != ingredients:
            problems.append(f"{entry['dishId']} read back with the wrong ingredients")
    return problems


# ---------------------------------------------------------------------------
# Entry point.
# ---------------------------------------------------------------------------


def parse_arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--dishes", type=pathlib.Path, default=DEFAULT_DISHES)
    parser.add_argument("--catalog", type=pathlib.Path, default=DEFAULT_CATALOG)
    parser.add_argument("--out", type=pathlib.Path, default=DEFAULT_OUT)
    parser.add_argument("--min-ingredients", type=int, default=DEFAULT_MIN_INGREDIENTS)
    parser.add_argument(
        "--min-ingredient-coverage",
        type=float,
        default=DEFAULT_MIN_COVERAGE,
        help="fraction of a dish's food ingredients that must join; 1.0 emits complete dishes only",
    )
    parser.add_argument(
        "--stats-only",
        action="store_true",
        help="report the counts and the emitted size without writing the file",
    )
    return parser.parse_args(argv)


def main(argv):
    arguments = parse_arguments(argv)
    catalog_ids, catalog_names = load_catalog(arguments.catalog)
    document, recipes = load_dishes(arguments.dishes)

    stats = collections.Counter({"recipes": len(recipes), "catalog_records": len(catalog_ids)})
    context = build_context(recipes, catalog_names)
    selected = [
        (entry, ingredients, alternate_names(entry, context, stats))
        for entry, ingredients in select_dishes(recipes, catalog_ids, arguments, stats)
    ]
    stats["emitted_ingredients"] = sum(len(ingredients) for _, ingredients, _ in selected)
    stats["dishes_with_names"] = sum(1 for _, _, names in selected if names)

    text = render_file(selected, stats, arguments, document["provenance"])
    problems = check_output(text, selected, catalog_ids, arguments.min_ingredients)
    for problem in problems:
        print(f"OUTPUT PROBLEM: {problem}", file=sys.stderr)

    encoded = text.encode("utf-8")
    print(f"catalog records      {stats['catalog_records']}")
    print(f"source recipes       {stats['recipes']}")
    print(f"  dropped incomplete {stats['dropped_incomplete']}")
    print(f"  dropped too few    {stats['dropped_too_few']}")
    print(f"  dropped long name  {stats['dropped_long_name']}")
    print(f"selected dishes      {len(selected)}")
    print(f"food ingredients     {stats['food_ingredients']}")
    print(f"  joined             {stats['joined_ingredients']}")
    print(f"  dropped, no join   {stats['dropped_ingredients']}")
    print(f"    sub-recipe       {stats['ingredient_no_id_fndds_subrecipe']}")
    print(f"    not in catalog   {stats['ingredient_not_in_catalog']}")
    print(f"    no SR Legacy id  {stats['ingredient_no_id_ndb_not_in_sr_legacy']}")
    print(f"    unresolved       {stats['ingredient_no_id_unresolved']}")
    print(f"    repeated in dish {stats['ingredient_repeated']}")
    print(f"  pseudo, not food   {stats['ingredient_pseudo']} (outside the denominator)")
    print(f"emitted ingredients  {stats['emitted_ingredients']}")
    print(f"dishes with names    {stats['dishes_with_names']}")
    print(f"alternate terms      {stats['alternate_keep']} kept of {sum(v for k, v in stats.items() if k.startswith('alternate_'))}")
    for reason in ("long", "list", "attribute", "shared", "restates", "hides_food", "hides_dish", "duplicate", "empty"):
        print(f"  dropped {reason:12s} {stats['alternate_' + reason]}")
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
