#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate-food-synonyms.py -- BUILD-TIME generator for the trilingual (en/es/ru)
food-name synonym table consumed by the deterministic catalog matcher.

This script runs at build time only. It never runs on device, and nothing it
touches is a runtime dependency. Its single output is a static, deterministic
JSON table plus the attribution file that AGROVOC's licence requires.

    stdlib only. No third-party packages. Python 3.9+.
    (If a future change needs a third-party package, declare it HERE in this
    header rather than assuming the build host has it installed.)


WHY THIS EXISTS
===============
AGROVOC alone is not a usable search vocabulary, and this is measured rather
than assumed. Concept c_24000 "chicken meat" carries Russian prefLabel
"курятина" and *zero* Russian altLabels, so a user typing "курица" -- the word
Russians actually use -- matches nothing. Wikidata supplies the missing
consumer register, and it is CC0, so it carries no downstream obligation.

The two sources therefore play different roles:

  AGROVOC  -- the SPINE. Supplies concept identity (stable c_NNNNN ids), the
              subset hierarchy, and the curated display name. Trilingual parity
              is genuine: all 41,825 concepts carry en/es/ru prefLabels.
  Wikidata -- the COLLOQUIAL LAYER. Supplies the words people type: "chicken"
              for chicken meat, "palta" for the Spanish-American avocado.


HOW A CONCEPT'S ALIASES ARE ASSEMBLED (four tiers, each independently sourced)
=============================================================================
  T1 agrovoc:prefLabel   the display name (per language)
  T2 agrovoc:altLabel    curated synonyms; thin in ru, better in en
  T3 wikidata:label+alias  joined on P8061 (AGROVOC ID). Exact id join, no
                           fuzzy name matching, so no homograph risk.
  T4 agrovoc:isDerivedFrom -> wikidata  the SOURCE-ORGANISM hop. This is the
     tier that actually closes the "курица" gap, and every hop is citable:

         c_24000 chicken meat
           --agrontology:isDerivedFrom-->  c_1540 chickens
           --wikidata P8061-->             Q780
           --labels-->                     ru "курица", es "pollo"
                                           ru aliases кура/куры/цыплёнок

     T4 deliberately broadens: it lets the animal/plant word find the food.
     That is correct for a meal logger (a user logging "курица" means chicken
     meat) but it is a judgement call, so it is recorded per concept in
     `sources` and can be switched off with --no-derived.

Tier membership is recorded per concept so any alias can be traced back to the
source that justified it. A nutrition-adjacent string with no citable source is
treated as fabricated by this repository's rules.


HOW THE SUBSET IS CHOSEN  (and how to widen it later)
=====================================================
Pulling all 41,825 AGROVOC concepts would be mostly agronomy -- soil types,
farming practices, plant diseases -- none of which anyone types into a food
log. The subset is instead the transitive `skos:broader+` closure of the
FOOD_ROOTS table below, plus the roots themselves.

TO WIDEN: add a row to FOOD_ROOTS. That is the whole procedure. Each row is
(agrovoc_id, english_gloss); the closure and every downstream stage adapt
automatically. Sizes were measured per root before selection so that no single
row can explode the table -- the largest, c_2943 "fishes", contributes 180.

Deliberately EXCLUDED, and why:
  - Whole-taxonomy roots (e.g. plant genera). AGROVOC's biological branches run
    to thousands of species nobody eats or types.
  - Non-food agronomy roots. No search value, pure size cost.
  - Prepared/branded dishes. Not in AGROVOC; that is the FNDDS layer's job and
    a separate generator.


LICENCES -- TWO SOURCES, ATTRIBUTED SEPARATELY
==============================================
  AGROVOC   CC-BY IGO 3.0  ATTRIBUTION REQUIRED. Attribution is emitted to
                           ATTRIBUTION.md next to the data and must be
                           surfaced in the shipped app's licences screen.
  Wikidata  CC0 1.0        Public domain dedication. No obligation. Credited
                           here as good practice, not as a licence term.

The obligations differ, so the two are never merged into one blended credit.
Neither source is ODbL, so nothing here inherits the share-alike problem the
research flagged for Open Food Facts.


POLITENESS
==========
Both endpoints are free public infrastructure. This script:
  - sends a descriptive User-Agent naming the project and a contact address
    (WDQS blocks generic agents and asks for exactly this);
  - rate-limits every request (--rate, default 1.0s between calls);
  - caches every response on disk keyed by SHA-256 of endpoint+query, so a
    re-run costs zero requests and development iteration is free;
  - retries 429/5xx with exponential backoff and honours Retry-After.

Output is deterministic -- stable sort order everywhere -- so an unchanged
upstream produces a byte-identical file and no build churn.


USAGE
=====
    python3 scripts/generate-food-synonyms.py                 # generate
    python3 scripts/generate-food-synonyms.py --refresh       # bypass cache
    python3 scripts/generate-food-synonyms.py --dry-run       # counts only
    python3 scripts/generate-food-synonyms.py --print-plan    # no network
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

# --------------------------------------------------------------------------
# Constants
# --------------------------------------------------------------------------

SCHEMA_VERSION = 1
GENERATOR_VERSION = "0.1.0"

AGROVOC_SPARQL = "https://agrovoc.fao.org/sparql"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"

AGROVOC_CONCEPT_PREFIX = "http://aims.fao.org/aos/agrovoc/"

# AGROVOC concept ids come in TWO shapes and a filter that assumes one silently
# loses the other. Legacy ids are decimal ("c_24000"); newer concepts use a hex
# hash ("c_1e758d0a"). Measured: the c_3032 "foods" closure returns 147 concepts
# of which 46 -- 31%, and disproportionately the newer, more specific food
# terms -- are hex. An earlier decimal-only filter here dropped all 46 without
# any error, which is precisely the kind of invisible loss that reads as a
# clean run. Both shapes are accepted.
AGROVOC_ID_RE = re.compile(r"c_[0-9a-f]+\Z", re.IGNORECASE)


def agrovoc_sort_key(cid: str) -> Tuple[int, int, str]:
    """
    Deterministic ordering across both id shapes: decimal ids first in numeric
    order, then hex-hash ids lexicographically. Output must be byte-stable so an
    unchanged upstream produces no build churn.
    """
    rest = cid.split("_", 1)[1]
    return (0, int(rest), "") if rest.isdigit() else (1, 0, rest)

LANGUAGES = ("en", "es", "ru")

CONTACT = "https://github.com/flexsent-labs/SuperNutrition"
USER_AGENT = (
    f"SuperNutrition-food-synonym-generator/{GENERATOR_VERSION} "
    f"(build-time thesaurus extract; {CONTACT})"
)

# Endpoint documentation, cited in the emitted provenance header.
#
# LICENCE, verified 2026-08-02 against FAO's own FAQ rather than secondary
# summaries: https://aims.fao.org/standards/agrovoc/faq
#
#   "AGROVOC thesaurus content in English, Russian, French, Spanish, Arabic and
#    Chinese is licensed under the international Creative Commons Attribution
#    License (CC-BY IGO 3.0)."
#   "Copyright for AGROVOC content in FAO languages [...] is with FAO, while
#    content in other languages rests with the institutions that authored it."
#
# Two consequences this generator depends on:
#
#  1. en/es/ru are ALL FAO languages, so this extract sits entirely inside the
#     FAO-copyright, single-licence zone. Pulling a fourth language could drag
#     in institution-held rights with different terms. That is a rights reason
#     to keep LANGUAGES as it is, on top of the product reason.
#  2. Some third-party summaries now describe AGROVOC as CC-BY 4.0. FAO's own
#     FAQ still says CC-BY IGO 3.0, so that is what is recorded. The two differ
#     in ways that matter (IGO 3.0 has no ShareAlike either, so the practical
#     obligation here -- attribution -- is identical). Flagged rather than
#     silently resolved; confirm with counsel before release.
AGROVOC_HOMEPAGE = "https://agrovoc.fao.org/"
AGROVOC_LICENCE_NAME = "CC-BY IGO 3.0"
AGROVOC_LICENCE_URL = "https://creativecommons.org/licenses/by/3.0/igo/"
AGROVOC_LICENCE_SOURCE = "https://aims.fao.org/standards/agrovoc/faq"
WIKIDATA_HOMEPAGE = "https://www.wikidata.org/"
WIKIDATA_LICENCE_NAME = "CC0 1.0 Universal (public domain dedication)"
WIKIDATA_LICENCE_URL = "https://creativecommons.org/publicdomain/zero/1.0/"
WIKIDATA_LICENCE_SOURCE = "https://www.wikidata.org/wiki/Wikidata:Licensing"

# --------------------------------------------------------------------------
# Subset definition -- THE ONE TABLE TO EDIT WHEN WIDENING COVERAGE
# --------------------------------------------------------------------------
# (agrovoc_id, english gloss, measured size of skos:broader+ closure)
#
# The measured column is documentation, not a constraint -- it is the size the
# root contributed when it was added, recorded so that an unexpected jump is
# visible on the next run. `--report-roots` re-measures and prints drift.
FOOD_ROOTS: Tuple[Tuple[str, str, int], ...] = (
    ("c_3032", "foods", 147),
    ("c_8174", "vegetables", 119),
    ("c_3131", "fruits", 103),
    ("c_4669", "meat", 34),
    ("c_4680", "meat products", 16),
    ("c_1474", "cereals", 39),
    ("c_4830", "milk products", 77),
    ("c_4826", "milk", 10),
    ("c_12873", "nuts", 15),
    ("c_15685", "spices", 32),
    ("c_1805", "condiments", 3),
    ("c_896", "beverages", 56),
    ("c_2941", "fishery products", 24),
    ("c_2930", "fish products", 18),
    ("c_2943", "fishes", 180),
    ("c_15903", "fish", 0),   # merged into c_2943 upstream
    ("c_4255", "legumes", 11),
    ("c_2502", "eggs", 4),
    ("c_1071", "bread", 3),
    ("c_1507", "cheese", 30),
    ("c_5621", "pasta", 2),
    ("c_2988", "flours", 27),
    ("c_7498", "sugar", 9),
    ("c_5334", "oils", 57),
    ("c_2816", "fats", 21),
    ("c_6145", "poultry", 22),
    ("c_1731", "coffee", 0),  # leaf concept; kept for the term itself
    ("c_7634", "tea", 2),
    ("c_438", "animal products", 71),
    ("c_6651", "roots", 5),
    ("c_7998", "tubers", 0),  # leaf concept; kept for the term itself
    ("c_6946", "seeds", 5),
)

# --------------------------------------------------------------------------
# Display-name casing normalisation
# --------------------------------------------------------------------------
# Measured on the c_8174 "vegetables" closure (119 concepts), counting labels
# whose first character is uppercase:
#
#     es  119/119     ru  0/119      en  10/119
#
# Spanish AGROVOC labels are systematically initial-capitalised ("Carne de
# pollo"), which is an AGROVOC editorial convention, not Spanish orthography --
# Spanish does not capitalise common nouns. Russian never capitalises. English
# capitalises only genuine proper nouns ("Brussels sprouts", "Chinese
# cabbage"), so English MUST be left alone or those get corrupted.
#
# Rule: lowercase the first character of es/ru display labels only, and only
# when the first token has no other uppercase letter (so acronyms like "UHT"
# survive). English is never touched. The raw AGROVOC string is always also
# emitted into the match list, so the sourced form stays matchable and the
# transform is never lossy.
CASE_NORMALISED_LANGS = frozenset({"es", "ru"})

CYRILLIC_RE = re.compile(r"[Ѐ-ӿ]")
LATIN_RE = re.compile(r"[A-Za-zÀ-ɏ]")
WS_RE = re.compile(r"\s+")

# Aliases longer than this are descriptions, not search terms.
MAX_ALIAS_CHARS = 60

# --------------------------------------------------------------------------
# Display qualifier stripping
# --------------------------------------------------------------------------
# AGROVOC disambiguates homographs with a trailing parenthetical, and it leaks
# badly into Russian. Measured on the 730-concept subset, display names ending
# in a parenthetical:
#
#     ru  145/730     es  26/730     en  9/730
#
# Showing a user "картофель (клубни)" or "авокадо (плоды)" in a food list is a
# defect. But the qualifier is sometimes LOAD-BEARING -- "фасоль (овощи)" and
# "фасоль (продукт)" are distinct concepts that both strip to "фасоль".
#
# So the qualifier is stripped only when the stripped form is unique within
# that language across the whole emitted set; otherwise it is kept. Measured
# outcome: ru 145 stripped minus 6 kept, es 26 minus 6, en 9 minus 0.
# The unstripped form always stays in the match list, so nothing is lost.
TRAILING_PAREN_RE = re.compile(r"\s*\([^()]*\)\s*$")

# --------------------------------------------------------------------------
# Junk-alias denylist
# --------------------------------------------------------------------------
# Wikidata is openly editable, which is what makes it a good colloquial source
# and also means junk gets in. Verified example, observed 2026-08-02: item Q780
# ("chicken") carries the English alias "vistas", which is not a word for
# chicken in any register. Left in, it makes "vistas" match chicken meat.
#
# This cannot be detected algorithmically -- "palta" also shares nothing with
# "aguacate" and is entirely correct. So junk is handled by review: entries
# found during review are recorded here with a reason and a date, keyed by
# (lang, casefolded term) so the drop is reproducible and auditable.
#
# THIS LIST IS NOT A CLEAN BILL OF HEALTH. It contains only what has actually
# been reviewed. Regenerate-and-diff is the intended workflow, not blind trust.
JUNK_ALIASES: Tuple[Tuple[str, str, str], ...] = (
    ("en", "vistas", "Wikidata Q780 alias; not an English word for chicken. Observed 2026-08-02."),
)

# --------------------------------------------------------------------------
# Manually verified AGROVOC -> Wikidata links
# --------------------------------------------------------------------------
# The P8061 id join is exact but SPARSE: only 145 of the 997 subset concepts
# carry it, because relatively few Wikidata items have been tagged with an
# AGROVOC id at all. Staples are missing, and the consequences are real -- with
# P8061 alone, AGROVOC gives Russian "томаты" and nothing else, so a user typing
# "помидор", the ordinary Russian word for tomato, matches nothing. That is the
# same class of failure as the "курица" case this table exists to fix.
#
# A fuzzy English-label join was built and MEASURED as the alternative, and
# rejected. On a 60-concept probe it resolved 30, but "chops" resolved to
# Q3850202 ("Инкан"), an unrelated item, and "cod"/"chocolate" each returned
# several competing items. A wrong food match is a user-visible wrong answer, so
# no fuzzy tier ships.
#
# Instead, high-traffic staples are linked by hand. Every row below was verified
# by fetching the item and reading its en/es/ru labels before being added --
# NOT from memory. That check is not ceremony: while building this table,
# Q10988 was proposed for "yoghurt" and turned out to be *Periegopidae*, a
# family of spiders. It was dropped. Two further candidates (Q500 lemon, Q434
# pear) were dropped for pointing at the tree/genus rather than the food.
#
# TO EXTEND: verify the item first, then add (agrovoc_id, wikidata_qid, note).
MANUAL_WIKIDATA_LINKS: Tuple[Tuple[str, str, str], ...] = (
    ("c_7805", "Q23501", "tomato; supplies ru помидор/помидоры, missing from AGROVOC"),
    ("c_4826", "Q8495", "milk; ru молоко, es leche"),
    ("c_1071", "Q7802", "bread; ru хлеб, es pan"),
    ("c_7498", "Q11002", "sugar; ru сахар, es azúcar"),
    ("c_7634", "Q6097", "tea; ru чай, es té"),
    ("c_1731", "Q8486", "coffee; ru кофе, es café"),
    ("c_3359", "Q10978", "grape; ru виноград, es uva"),
    ("c_1582", "Q195", "chocolate; ru шоколад"),
    ("c_806", "Q503", "banana; supplies es plátano alongside AGROVOC banano"),
    ("c_5621", "Q178", "pasta; supplies the everyday ru макароны"),
    ("c_5638", "Q13202263", "peach; supplies es melocotón, a regional variant"),
)

# --------------------------------------------------------------------------
# Curated additive terms
# --------------------------------------------------------------------------
# The inverse of JUNK_ALIASES, on the same terms: reviewed, reasoned, dated.
# AGROVOC and Wikidata between them do not always carry the bare colloquial
# word a person actually types, and the matcher's Russian lemma fold makes the
# gap dangerous rather than merely incomplete: a missing word that is ALSO an
# inflected form of some OTHER concept's name gets folded onto that concept
# with full confidence instead of failing to match at all.
#
# `масло` is the case that motivated this table. AGROVOC's only Russian name
# for butter (c_1162) is the qualified `сливочное масло`; the oils concept
# (c_5334) carries the bare plural `масла` as its sole Russian name. The lemma
# table (built once, offline, from OpenCorpora) folds the singular `масло`
# onto `масла`'s paradigm, so the ordinary word for butter answered soybean
# oil at coverage 1000 against butter's qualified name at 500. Verified
# against the shipped catalog before being added: `масло` had fan-out 0 --
# no record carried it at all.
#
# TO EXTEND: check the word's fan-out in GeneratedCatalog.kt first (a term
# already reachable needs no entry here), then add
# (agrovoc_id, lang, term, reason-and-date).
MANUAL_TERMS: Tuple[Tuple[str, str, str, str], ...] = (
    (
        "c_1162", "ru", "масло",
        "the ordinary Russian word for butter; AGROVOC has only the qualified "
        "`сливочное масло`, and the lemma fold sent the bare word to the oils "
        "concept c_5334 instead. Verified 2026-08-02 against GeneratedCatalog.kt: "
        "fan-out 0, no record carried it.",
    ),
    (
        "c_15463", "en", "yogurt",
        "the American spelling; AGROVOC's entire en entry for this concept is "
        "the British spelling `yoghurt` as a bare display with no match list, so "
        "`yogurt` is not anywhere in the source data. Without it, and paired with "
        "generate-catalog-kotlin.py's RESTATES_HEAD_EXCEPTIONS carve-out (the "
        "term would otherwise be dropped as restating its own head `Yogurt`), "
        "the coverage formula sent `yogert`/`yogurt` to the unrelated `Tofu "
        "yogurt` record instead of any real yogurt. Verified 2026-08-03 against "
        "GeneratedCatalog.kt: fan-out 0, no record carried the American spelling.",
    ),
    # The five entries below are the reviewed slice of a curated slang batch
    # (shroom/taters/nanner/cheez/chkn) identified by a 20-round, 875-scenario
    # multilingual slang sweep. Each was independently re-measured against the
    # real, current GeneratedCatalog.kt before being added -- not carried over
    # from the sweep's own estimate -- including the FUZZY-tier edit-distance
    # check the established entries above use, since a short slang word is
    # exactly the shape that can already land on an unrelated record by
    # coincidence (as cafe/sammon originally did). Two other candidates from
    # the same batch, `PB` (peanut butter) and `za` (pizza), are NOT here:
    # both were found to have no correct target this mechanism can reach --
    # peanut butter's USDA head carries no AGROVOC-sourced terms at all, and
    # the generator's SUBHEAD_OVERRIDES can only carve into an existing
    # populated group, never originate one; pizza's only real-food USDA
    # records are dropped by the generator's own segment/token caps before
    # either curated-term mechanism runs. A third, `avo` (avocado), needed no
    # entry: "avo" already resolves correctly today via the PREFIX tier
    # against "avocado"/"avocados", the record's own display tokens.
    (
        "c_2487", "en", "shroom",
        "English slang for mushroom; c_2487's (edible fungi) only en match "
        "term is the plain plural `mushrooms`, and `shroom` is not a case, "
        "plural, or lemma variant of it that CatalogSearch.kt's "
        "isPluralPair/sharesLemma fold together, so a query for it fails "
        "EXACT, TOKEN_SUBSET, PREFIX and FUZZY alike rather than landing on "
        "some other food. Verified 2026-08-03 against GeneratedCatalog.kt: "
        "fan-out 0 -- every case-insensitive hit for the substring `shroom` is "
        "inside `mushroom`/`mushrooms`, none is the standalone token. Also "
        "checked the FUZZY tier directly: a 6-character query only ever gets "
        "allowance 1, and no catalog token of length 5-7 is within edit "
        "distance 1 of `shroom`, so the word is not already reachable by "
        "accident either.",
    ),
    (
        "c_13551", "en", "taters",
        "common English slang for potatoes (as in 'tater tots'); AGROVOC's "
        "entire en entry for this concept is the qualified matches `fresh "
        "potatoes`/`Irish potatoes`, so the informal word is not anywhere in "
        "the source data and a user typing it gets no match at any tier. Not "
        "a restated-head case (the head is `potatoes`, not `taters`) and not "
        "reachable via FUZZY either: `taters` (6 chars, allowance 1 under "
        "FUZZY_LONG_TOKEN_LENGTH) needs an unrelated record or synonym token "
        "within edit distance 1 to land by coincidence -- checked `waters`, "
        "`haters`, `caters`, `eaters`, `batters`, `taster(s)`, `tatters` and "
        "others against the catalog, none exist as a token. Verified "
        "2026-08-03 against GeneratedCatalog.kt and food-synonyms.json: "
        "fan-out 0, no record, concept, or head named `tater`/`taters`.",
    ),
    (
        "c_806", "en", "nanner",
        "informal/childish American English spelling of banana; AGROVOC's en "
        "entry for this concept carries only `banana` and `banana fruit`, so "
        "`nanner` is not anywhere in the source data. Verified 2026-08-03 "
        "against GeneratedCatalog.kt: fan-out 0, no record carries it as a "
        "searchable form, and it is not reachable via the FUZZY tier either -- "
        "no existing token in the catalog is within edit distance 1 of "
        "`nanner` at this length (bare `banana` itself is distance 4 away), so "
        "a missing entry leaves the query unresolved rather than merely slow "
        "to reach the right food.",
    ),
    (
        "c_1507", "en", "cheez",
        "the informal/slang spelling of cheese, as typed describing a meal; "
        "AGROVOC's en entry for this concept has only the single match "
        "`cheeses`, with no colloquial spelling anywhere in the source data. "
        "Verified 2026-08-03 against GeneratedCatalog.kt: fan-out 0 by literal "
        "grep, and 0 under the FUZZY tier too -- no length-4-to-6 catalog "
        "token anywhere in the file is within edit distance 1, and `cheese` "
        "itself sits at edit distance 2, one past FUZZY's allowance of 1 for "
        "a 5-character token -- so the addition cannot redirect any existing "
        "query.",
    ),
    (
        "c_1540", "en", "chkn",
        "common texting/slang abbreviation for chicken; c_1540 (chickens) and "
        "c_24000 (chicken meat) both already carry literal `chicken` in their "
        "en match list (see RESTATES_HEAD_EXCEPTIONS in "
        "generate-catalog-kotlin.py), so `chkn` is added beside its sibling "
        "on c_1540, the concept the plain word itself already lives on. "
        "Verified 2026-08-03 against GeneratedCatalog.kt: fan-out 0, no "
        "record or concept carries `chkn` in any form, and the FUZZY tier "
        "cannot already reach it either -- at 4 characters it clears "
        "FUZZY_MIN_TOKEN_LENGTH, but the length-difference bound (allowance "
        "1) rules out every real-catalog token 3 or fewer characters longer "
        "or shorter, including `chicken` itself (length 7, difference 3).",
    ),
)


# --------------------------------------------------------------------------
# HTTP with cache, rate limit and backoff
# --------------------------------------------------------------------------

class SparqlClient:
    """Polite, caching SPARQL client. One instance per run."""

    def __init__(self, cache_dir: str, rate: float, refresh: bool, verbose: bool):
        self.cache_dir = cache_dir
        self.rate = rate
        self.refresh = refresh
        self.verbose = verbose
        self._last_call = 0.0
        self.requests_made = 0
        self.cache_hits = 0
        os.makedirs(cache_dir, exist_ok=True)

    def _cache_path(self, endpoint: str, query: str) -> str:
        key = hashlib.sha256(f"{endpoint}\n{query}".encode("utf-8")).hexdigest()[:32]
        return os.path.join(self.cache_dir, f"{key}.json")

    def _throttle(self) -> None:
        wait = self.rate - (time.monotonic() - self._last_call)
        if wait > 0:
            time.sleep(wait)
        self._last_call = time.monotonic()

    def query(self, endpoint: str, query: str, label: str = "") -> dict:
        path = self._cache_path(endpoint, query)
        if not self.refresh and os.path.exists(path):
            self.cache_hits += 1
            if self.verbose:
                print(f"  [cache] {label}", file=sys.stderr)
            with open(path, "r", encoding="utf-8") as fh:
                return json.load(fh)

        data = urllib.parse.urlencode({"query": query}).encode("utf-8")
        req = urllib.request.Request(
            endpoint,
            data=data,
            headers={
                "User-Agent": USER_AGENT,
                "Accept": "application/sparql-results+json",
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            },
        )

        delay = 2.0
        last_err: Optional[str] = None
        for attempt in range(1, 6):
            self._throttle()
            try:
                if self.verbose:
                    print(f"  [http ] {label} (attempt {attempt})", file=sys.stderr)
                with urllib.request.urlopen(req, timeout=300) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                self.requests_made += 1
                tmp = path + ".tmp"
                with open(tmp, "w", encoding="utf-8") as fh:
                    json.dump(payload, fh)
                os.replace(tmp, path)
                return payload
            except urllib.error.HTTPError as exc:
                last_err = f"HTTP {exc.code}"
                if exc.code in (429, 500, 502, 503, 504):
                    retry_after = exc.headers.get("Retry-After") if exc.headers else None
                    sleep_for = float(retry_after) if (retry_after or "").isdigit() else delay
                    print(
                        f"  ! {label}: {last_err}, backing off {sleep_for:.0f}s",
                        file=sys.stderr,
                    )
                    time.sleep(sleep_for)
                    delay = min(delay * 2, 60.0)
                    continue
                raise
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
                last_err = str(exc)
                print(f"  ! {label}: {last_err}, retrying in {delay:.0f}s", file=sys.stderr)
                time.sleep(delay)
                delay = min(delay * 2, 60.0)

        raise RuntimeError(f"{label}: giving up after 5 attempts ({last_err})")


def bindings(payload: dict) -> List[dict]:
    return payload.get("results", {}).get("bindings", [])


def cell(row: dict, name: str) -> Optional[str]:
    node = row.get(name)
    return node.get("value") if node else None


def chunked(seq: Sequence[str], size: int) -> Iterable[Sequence[str]]:
    for i in range(0, len(seq), size):
        yield seq[i:i + size]


# --------------------------------------------------------------------------
# String handling
# --------------------------------------------------------------------------

def clean(text: str) -> str:
    """NFC-normalise, collapse whitespace, trim. Never changes case."""
    return WS_RE.sub(" ", unicodedata.normalize("NFC", text)).strip()


def fold(text: str) -> str:
    """Dedupe key: case-insensitive, whitespace-insensitive. Not for output."""
    return WS_RE.sub(" ", unicodedata.normalize("NFC", text)).strip().casefold()


def script_ok(text: str, lang: str) -> bool:
    """
    Reject strays in the wrong script. Wikidata carries the odd Cyrillic string
    on an English alias and vice versa; a Russian speaker never types Latin
    into a Russian food search, and the mirror holds for en/es.

    Scientific binomials ("Gallus gallus domesticus") survive this filter in
    en/es by design -- they are Latin-script and harmless as match-only terms.
    """
    if lang == "ru":
        return bool(CYRILLIC_RE.search(text))
    return bool(LATIN_RE.search(text)) and not CYRILLIC_RE.search(text)


def usable_alias(text: str, lang: str) -> bool:
    if not text or len(text) > MAX_ALIAS_CHARS:
        return False
    if not script_ok(text, lang):
        return False
    # Bare identifiers and numerics are not search terms.
    if not re.search(r"[^\W\d_]", text, re.UNICODE):
        return False
    return True


def normalise_display(text: str, lang: str) -> str:
    """See CASE_NORMALISED_LANGS above for the measurement behind this."""
    if lang not in CASE_NORMALISED_LANGS or not text:
        return text
    first_token = text.split(" ", 1)[0]
    # Preserve acronyms: any further uppercase in the first token means leave it.
    if any(ch.isupper() for ch in first_token[1:]):
        return text
    if not text[0].isupper():
        return text
    return text[0].lower() + text[1:]


DENIED = {(lang, term.casefold()) for lang, term, _reason in JUNK_ALIASES}


def strip_qualifier(text: str) -> str:
    """Trailing '(...)' removed. Returns the original if that would empty it."""
    stripped = TRAILING_PAREN_RE.sub("", text).strip()
    return stripped or text


def yo_variant(text: str) -> Optional[str]:
    """Russian ё/е spelling counterpart. Off by default -- see --ru-yo-variants."""
    if "ё" in text or "Ё" in text:
        return text.replace("ё", "е").replace("Ё", "Е")
    return None


# --------------------------------------------------------------------------
# Stage 1 -- resolve the concept subset from AGROVOC
# --------------------------------------------------------------------------

def q_closure(root: str) -> str:
    return f"""
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
SELECT DISTINCT ?c WHERE {{
  ?c skos:broader+ <{AGROVOC_CONCEPT_PREFIX}{root}> .
}}"""


def resolve_subset(client: SparqlClient, roots: Sequence[Tuple[str, str, int]],
                   report_roots: bool) -> Tuple[List[str], Dict[str, int]]:
    ids: Set[str] = set()
    per_root: Dict[str, int] = {}
    for root_id, gloss, expected in roots:
        payload = client.query(AGROVOC_SPARQL, q_closure(root_id), f"closure {root_id} ({gloss})")
        found = {
            cell(row, "c").rsplit("/", 1)[-1]
            for row in bindings(payload)
            if (cell(row, "c") or "").startswith(AGROVOC_CONCEPT_PREFIX)
        }
        found = {cid for cid in found if AGROVOC_ID_RE.fullmatch(cid)}
        per_root[root_id] = len(found)
        ids.update(found)
        ids.add(root_id)  # the root itself is a food term too
        if report_roots:
            drift = "" if len(found) == expected else f"   <-- DRIFT (recorded {expected})"
            print(f"    {root_id:9s} {gloss:20s} {len(found):5d}{drift}")
    ordered = sorted(ids, key=agrovoc_sort_key)
    return ordered, per_root


# --------------------------------------------------------------------------
# Stage 2 -- AGROVOC labels for the subset
# --------------------------------------------------------------------------

def q_agrovoc_labels(batch: Sequence[str]) -> str:
    values = " ".join(f"<{AGROVOC_CONCEPT_PREFIX}{c}>" for c in batch)
    langs = ", ".join(f'"{lang}"' for lang in LANGUAGES)
    return f"""
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
SELECT ?c ?kind ?lang ?label WHERE {{
  VALUES ?c {{ {values} }}
  {{ ?c skos:prefLabel ?label . BIND("pref" AS ?kind) }}
  UNION
  {{ ?c skos:altLabel  ?label . BIND("alt"  AS ?kind) }}
  BIND(LANG(?label) AS ?lang)
  FILTER(?lang IN ({langs}))
}}"""


def q_derived_from(batch: Sequence[str]) -> str:
    values = " ".join(f"<{AGROVOC_CONCEPT_PREFIX}{c}>" for c in batch)
    return f"""
PREFIX ag: <http://aims.fao.org/aos/agrontology#>
SELECT ?c ?src WHERE {{
  VALUES ?c {{ {values} }}
  ?c ag:isDerivedFrom ?src .
  FILTER(STRSTARTS(STR(?src), "{AGROVOC_CONCEPT_PREFIX}"))
}}"""


# --------------------------------------------------------------------------
# Stage 3 -- Wikidata labels + aliases, joined on P8061 (AGROVOC ID)
# --------------------------------------------------------------------------

def q_wikidata(batch: Sequence[str]) -> str:
    values = " ".join(f'"{c}"' for c in batch)
    langs = ", ".join(f'"{lang}"' for lang in LANGUAGES)
    return f"""
SELECT ?ag ?item ?kind ?lang ?label WHERE {{
  VALUES ?ag {{ {values} }}
  ?item wdt:P8061 ?ag .
  {{ ?item rdfs:label   ?label . BIND("label" AS ?kind) }}
  UNION
  {{ ?item skos:altLabel ?label . BIND("alias" AS ?kind) }}
  BIND(LANG(?label) AS ?lang)
  FILTER(?lang IN ({langs}))
}}"""


def q_wikidata_items(qids: Sequence[str]) -> str:
    """Same shape as q_wikidata but keyed by Q-id, for MANUAL_WIKIDATA_LINKS."""
    values = " ".join(f"wd:{q}" for q in qids)
    langs = ", ".join(f'"{lang}"' for lang in LANGUAGES)
    return f"""
SELECT ?item ?kind ?lang ?label WHERE {{
  VALUES ?item {{ {values} }}
  {{ ?item rdfs:label   ?label . BIND("label" AS ?kind) }}
  UNION
  {{ ?item skos:altLabel ?label . BIND("alias" AS ?kind) }}
  BIND(LANG(?label) AS ?lang)
  FILTER(?lang IN ({langs}))
}}"""


# --------------------------------------------------------------------------
# Assembly
# --------------------------------------------------------------------------

class Concept:
    __slots__ = ("cid", "display", "matches", "seen", "sources")

    def __init__(self, cid: str):
        self.cid = cid
        self.display: Dict[str, str] = {}
        self.matches: Dict[str, List[str]] = {lang: [] for lang in LANGUAGES}
        self.seen: Dict[str, Set[str]] = {lang: set() for lang in LANGUAGES}
        self.sources: Set[str] = set()

    def add_match(self, lang: str, text: str) -> bool:
        text = clean(text)
        if not usable_alias(text, lang):
            return False
        key = fold(text)
        if (lang, key) in DENIED:
            return False
        if key in self.seen[lang]:
            return False
        self.seen[lang].add(key)
        self.matches[lang].append(text)
        return True

    def reserve_display(self, lang: str, text: str) -> None:
        """Displayed strings must never also appear in the match list."""
        self.seen[lang].add(fold(text))


Q_AGROVOC_VERSION = """
PREFIX dct: <http://purl.org/dc/terms/>
SELECT ?modified WHERE { <http://aims.fao.org/aos/agrovoc> dct:modified ?modified . }
"""


def agrovoc_version(client: SparqlClient) -> str:
    """
    AGROVOC publishes no licence or version triple in its SPARQL store, so the
    concept scheme's dct:modified is the only machine-readable release marker
    available. Recorded as the dataset version rather than left blank -- a
    nutrition-adjacent table with an unpinnable source is not acceptable here.
    """
    try:
        rows = bindings(client.query(AGROVOC_SPARQL, Q_AGROVOC_VERSION, "agrovoc version"))
        return cell(rows[0], "modified") if rows else "unknown"
    except Exception as exc:  # non-fatal: never fail a build over a version string
        print(f"  ! could not read AGROVOC dct:modified ({exc})", file=sys.stderr)
        return "unknown"


def build(client: SparqlClient, args) -> Tuple[List[Concept], dict]:
    stats: dict = {}

    print("Stage 0/6  reading AGROVOC dataset version ...")
    stats["agrovoc_modified"] = agrovoc_version(client)
    print(f"           dct:modified = {stats['agrovoc_modified']}")

    print("Stage 1/6  resolving AGROVOC subset from FOOD_ROOTS ...")
    subset, per_root = resolve_subset(client, FOOD_ROOTS, args.report_roots)
    if args.max_concepts:
        subset = subset[: args.max_concepts]
    print(f"           {len(subset)} concepts in subset")
    stats["subset_size"] = len(subset)
    stats["per_root"] = per_root

    concepts: Dict[str, Concept] = {cid: Concept(cid) for cid in subset}

    print("Stage 2/6  AGROVOC prefLabel + altLabel ...")
    pref_count = {lang: 0 for lang in LANGUAGES}
    alt_count = {lang: 0 for lang in LANGUAGES}
    raw_pref: Dict[str, Dict[str, str]] = {}
    pending_alt: List[Tuple[str, str, str]] = []
    for batch in chunked(subset, args.batch):
        payload = client.query(AGROVOC_SPARQL, q_agrovoc_labels(batch), f"agrovoc labels x{len(batch)}")
        for row in bindings(payload):
            uri = cell(row, "c") or ""
            cid = uri.rsplit("/", 1)[-1]
            if cid not in concepts:
                continue
            lang, kind, label = cell(row, "lang"), cell(row, "kind"), cell(row, "label")
            if not lang or not label:
                continue
            label = clean(label)
            if kind == "pref":
                # AGROVOC guarantees one prefLabel per language per concept.
                raw_pref.setdefault(cid, {})[lang] = label
            else:
                pending_alt.append((cid, lang, label))

    # Pass 1: casing normalisation only, so collisions can be counted.
    cased: Dict[str, Dict[str, str]] = {}
    for cid, langs in raw_pref.items():
        for lang, label in langs.items():
            cased.setdefault(cid, {})[lang] = normalise_display(label, lang)

    # Pass 2: count what each language's names would collapse to if the
    # trailing qualifier were stripped. A qualifier shared by two concepts is
    # load-bearing and must survive.
    collapsed: Dict[str, Dict[str, int]] = {lang: {} for lang in LANGUAGES}
    for langs in cased.values():
        for lang, label in langs.items():
            key = fold(strip_qualifier(label))
            collapsed[lang][key] = collapsed[lang].get(key, 0) + 1

    # Pass 3: commit display names, stripping only where unambiguous.
    qualifier_stripped = {lang: 0 for lang in LANGUAGES}
    qualifier_kept = {lang: 0 for lang in LANGUAGES}
    for cid, langs in cased.items():
        con = concepts[cid]
        for lang, label in langs.items():
            bare = strip_qualifier(label)
            if bare != label:
                if collapsed[lang].get(fold(bare), 0) == 1:
                    label_out = bare
                    qualifier_stripped[lang] += 1
                else:
                    label_out = label
                    qualifier_kept[lang] += 1
            else:
                label_out = label
            con.display[lang] = label_out
            con.reserve_display(lang, label_out)
            pref_count[lang] += 1
        con.sources.add("agrovoc")

    # Every sourced spelling AGROVOC gave us re-enters as a match term: the raw
    # prefLabel (pre-casing) and the qualified form (pre-stripping). Display
    # normalisation is therefore never lossy for matching.
    for cid, langs in raw_pref.items():
        for lang, label in langs.items():
            concepts[cid].add_match(lang, label)
            concepts[cid].add_match(lang, strip_qualifier(label))

    stats["qualifier_stripped"] = qualifier_stripped
    stats["qualifier_kept_ambiguous"] = qualifier_kept
    print("           qualifiers stripped " +
          ", ".join(f"{l}={qualifier_stripped[l]}" for l in LANGUAGES) +
          "  (kept as ambiguous: " +
          ", ".join(f"{l}={qualifier_kept[l]}" for l in LANGUAGES) + ")")
    for cid, lang, label in pending_alt:
        if concepts[cid].add_match(lang, label):
            alt_count[lang] += 1

    stats["agrovoc_pref"] = pref_count
    stats["agrovoc_alt"] = alt_count
    print("           prefLabels " + ", ".join(f"{l}={pref_count[l]}" for l in LANGUAGES))
    print("           altLabels  " + ", ".join(f"{l}={alt_count[l]}" for l in LANGUAGES))

    print("Stage 3/6  Wikidata labels + aliases via P8061 ...")
    wd_count = {lang: 0 for lang in LANGUAGES}
    wd_items: Dict[str, Set[str]] = {}
    for batch in chunked(subset, args.batch):
        payload = client.query(WIKIDATA_SPARQL, q_wikidata(batch), f"wikidata x{len(batch)}")
        for row in bindings(payload):
            cid, lang, label = cell(row, "ag"), cell(row, "lang"), cell(row, "label")
            item = (cell(row, "item") or "").rsplit("/", 1)[-1]
            if cid not in concepts or not lang or not label:
                continue
            if concepts[cid].add_match(lang, label):
                wd_count[lang] += 1
            wd_items.setdefault(cid, set()).add(item)
    for cid, items in wd_items.items():
        for item in items:
            concepts[cid].sources.add(f"wikidata:{item}")
    stats["wikidata_linked_concepts"] = len(wd_items)
    stats["wikidata_terms"] = wd_count
    print(f"           {len(wd_items)}/{len(subset)} concepts joined to Wikidata")
    print("           terms      " + ", ".join(f"{l}={wd_count[l]}" for l in LANGUAGES))

    derived_count = {lang: 0 for lang in LANGUAGES}
    derived_pairs = 0
    if not args.no_derived:
        print("Stage 4/6  AGROVOC isDerivedFrom -> Wikidata source-organism hop ...")
        src_of: Dict[str, Set[str]] = {}
        for batch in chunked(subset, args.batch):
            payload = client.query(AGROVOC_SPARQL, q_derived_from(batch), f"derivedFrom x{len(batch)}")
            for row in bindings(payload):
                cid = (cell(row, "c") or "").rsplit("/", 1)[-1]
                src = (cell(row, "src") or "").rsplit("/", 1)[-1]
                if cid in concepts and src and AGROVOC_ID_RE.fullmatch(src):
                    src_of.setdefault(cid, set()).add(src)
        derived_pairs = sum(len(v) for v in src_of.values())
        all_srcs = sorted({s for v in src_of.values() for s in v}, key=agrovoc_sort_key)
        print(f"           {len(src_of)} concepts have a source organism "
              f"({len(all_srcs)} distinct sources)")

        src_terms: Dict[str, Dict[str, List[str]]] = {}
        src_item: Dict[str, Set[str]] = {}
        for batch in chunked(all_srcs, args.batch):
            payload = client.query(WIKIDATA_SPARQL, q_wikidata(batch), f"wikidata src x{len(batch)}")
            for row in bindings(payload):
                sid, lang, label = cell(row, "ag"), cell(row, "lang"), cell(row, "label")
                item = (cell(row, "item") or "").rsplit("/", 1)[-1]
                if not sid or not lang or not label:
                    continue
                src_terms.setdefault(sid, {}).setdefault(lang, []).append(clean(label))
                src_item.setdefault(sid, set()).add(item)

        for cid, srcs in sorted(src_of.items()):
            for sid in sorted(srcs, key=agrovoc_sort_key):
                for lang, labels in src_terms.get(sid, {}).items():
                    for label in labels:
                        if concepts[cid].add_match(lang, label):
                            derived_count[lang] += 1
                for item in sorted(src_item.get(sid, ())):
                    concepts[cid].sources.add(f"agrovoc:{sid}->wikidata:{item}")
        print("           terms      " + ", ".join(f"{l}={derived_count[l]}" for l in LANGUAGES))
    else:
        print("Stage 4/6  skipped (--no-derived)")
    stats["derived_terms"] = derived_count
    stats["derived_pairs"] = derived_pairs

    manual_count = {lang: 0 for lang in LANGUAGES}
    manual_applied = 0
    if not args.no_manual:
        print("Stage 5/6  manually verified AGROVOC -> Wikidata links ...")
        wanted = [(cid, qid) for cid, qid, _note in MANUAL_WIKIDATA_LINKS if cid in concepts]
        missing = [cid for cid, _q, _n in MANUAL_WIKIDATA_LINKS if cid not in concepts]
        if missing:
            # A manual row pointing outside the subset is a silent no-op, so say so.
            print(f"           ! {len(missing)} manual link(s) not in subset: {missing}")
        qids = sorted({qid for _c, qid in wanted})
        item_terms: Dict[str, Dict[str, List[str]]] = {}
        for batch in chunked(qids, args.batch):
            payload = client.query(WIKIDATA_SPARQL, q_wikidata_items(batch),
                                   f"wikidata manual x{len(batch)}")
            for row in bindings(payload):
                item = (cell(row, "item") or "").rsplit("/", 1)[-1]
                lang, label = cell(row, "lang"), cell(row, "label")
                if item and lang and label:
                    item_terms.setdefault(item, {}).setdefault(lang, []).append(clean(label))
        for cid, qid in wanted:
            hit = False
            for lang, labels in item_terms.get(qid, {}).items():
                for label in labels:
                    if concepts[cid].add_match(lang, label):
                        manual_count[lang] += 1
                        hit = True
            if item_terms.get(qid):
                concepts[cid].sources.add(f"manual->wikidata:{qid}")
                manual_applied += 1
            if not hit and item_terms.get(qid):
                print(f"           . {cid} -> {qid} added no new terms (already covered)")
        print(f"           {manual_applied}/{len(MANUAL_WIKIDATA_LINKS)} links applied")
        print("           terms      " + ", ".join(f"{l}={manual_count[l]}" for l in LANGUAGES))
    else:
        print("Stage 5/6  skipped (--no-manual)")
    stats["manual_links_applied"] = manual_applied
    stats["manual_terms"] = manual_count

    curated_count = {lang: 0 for lang in LANGUAGES}
    curated_applied = 0
    if not args.no_curated:
        print("Stage 6/6  curated additive terms ...")
        for cid, lang, term, _reason in MANUAL_TERMS:
            if cid not in concepts:
                print(f"           ! curated term for {cid} ({lang}): concept not in subset")
                continue
            if concepts[cid].add_match(lang, term):
                curated_count[lang] += 1
                curated_applied += 1
        print(f"           {curated_applied}/{len(MANUAL_TERMS)} curated terms applied")
    else:
        print("Stage 6/6  skipped (--no-curated)")
    stats["curated_terms_applied"] = curated_applied
    stats["curated_terms"] = curated_count

    if args.ru_yo_variants:
        added = 0
        for con in concepts.values():
            for text in list(con.matches["ru"]):
                variant = yo_variant(text)
                if variant and con.add_match("ru", variant):
                    added += 1
        stats["ru_yo_variants"] = added
        print(f"           +{added} ru ё/е spelling variants")

    # Drop concepts that ended up with no display name in any language.
    result = [c for c in (concepts[cid] for cid in subset) if c.display]
    stats["dropped_no_display"] = len(subset) - len(result)
    return result, stats


# --------------------------------------------------------------------------
# Emit
# --------------------------------------------------------------------------

def provenance(stats: dict, args) -> dict:
    return {
        "generated_by": f"scripts/generate-food-synonyms.py v{GENERATOR_VERSION}",
        "generated_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "schema_version": SCHEMA_VERSION,
        "build_time_only": (
            "Generated at build time from public thesaurus endpoints. "
            "No device runtime ever contacts these services."
        ),
        "subset_rule": (
            "Transitive skos:broader+ closure of the FOOD_ROOTS table in the "
            "generator, plus the roots themselves. Widen by adding a root."
        ),
        "sources": [
            {
                "name": "AGROVOC Multilingual Thesaurus",
                "role": "spine: concept identity, subset hierarchy, display names",
                "publisher": "Food and Agriculture Organization of the United Nations (FAO)",
                "url": AGROVOC_HOMEPAGE,
                "endpoint": AGROVOC_SPARQL,
                "version": stats.get("agrovoc_modified", "unknown"),
                "version_basis": "dct:modified of the AGROVOC concept scheme at query time",
                "licence": AGROVOC_LICENCE_NAME,
                "licence_url": AGROVOC_LICENCE_URL,
                "licence_verified_against": AGROVOC_LICENCE_SOURCE,
                "licence_verified_on": "2026-08-02",
                "attribution_required": True,
                "attribution": (
                    "Contains information from the AGROVOC Multilingual Thesaurus, "
                    "(c) FAO, licensed under CC-BY IGO 3.0."
                ),
                "copyright_note": (
                    "FAO holds copyright for AGROVOC content in its six official "
                    "languages, which include all three languages extracted here "
                    "(en, es, ru). Content in other languages is copyright of the "
                    "authoring institutions and may carry different terms -- do not "
                    "add a fourth language without re-checking rights."
                ),
                "open_question": (
                    "Some third-party summaries describe AGROVOC as CC-BY 4.0. FAO's "
                    "own FAQ states CC-BY IGO 3.0, recorded here. Both are "
                    "attribution-only with no ShareAlike, so the practical obligation "
                    "is identical, but confirm with counsel before release."
                ),
            },
            {
                "name": "Wikidata",
                "role": "colloquial layer: consumer words, regional variants",
                "publisher": "Wikimedia Foundation and Wikidata contributors",
                "url": WIKIDATA_HOMEPAGE,
                "endpoint": WIKIDATA_SPARQL,
                "version": "live query; see generated_utc",
                "version_basis": "Wikidata is continuously edited and has no release version",
                "licence": WIKIDATA_LICENCE_NAME,
                "licence_url": WIKIDATA_LICENCE_URL,
                "licence_verified_against": WIKIDATA_LICENCE_SOURCE,
                "attribution_required": False,
                "attribution": (
                    "Wikidata data is released under CC0 1.0 and carries no "
                    "attribution obligation. Credited voluntarily, not as a term."
                ),
                "join_key": "P8061 (AGROVOC ID) -- exact id join, no name matching",
            },
        ],
        "licence_note": (
            "TWO DISTINCT LICENCES. AGROVOC is CC-BY IGO 3.0 and its attribution "
            "MUST be surfaced in the shipped application's licences screen; see "
            "ATTRIBUTION.md next to this file. Wikidata is CC0 and imposes no "
            "obligation. They are recorded separately and must not be merged "
            "into a single blended credit. Neither source is ODbL, so no "
            "share-alike obligation attaches to this table."
        ),
        "field_semantics": {
            "id": "Canonical concept id. AGROVOC c_NNNNN ids are stable.",
            "display": "The ONLY string that may be shown to a user.",
            "match": (
                "Alternate names that MUST match a user query and MUST NEVER be "
                "displayed. Includes colloquialisms, regionalisms, plurals, "
                "source-organism names and scientific names."
            ),
            "sources": "Which upstream records justified this concept's terms.",
        },
        "counts": stats,
    }


ATTRIBUTION_MD = """# Attribution -- food synonym table

<!-- GENERATED FILE. Produced by scripts/generate-food-synonyms.py. Do not edit by hand. -->

`food-synonyms.json` in this directory is generated at build time from two
upstream sources with **different licences and different obligations**. They are
listed separately because merging them would misstate both.

## AGROVOC -- attribution REQUIRED

> Contains information from the **AGROVOC Multilingual Thesaurus**, (c) Food and
> Agriculture Organization of the United Nations (FAO), licensed under
> **CC-BY IGO 3.0**.

- Source: <https://agrovoc.fao.org/>
- Licence: **CC-BY IGO 3.0** -- <https://creativecommons.org/licenses/by/3.0/igo/>
- Licence verified 2026-08-02 against FAO's own FAQ,
  <https://aims.fao.org/standards/agrovoc/faq>, not against a secondary summary.
- Used for: canonical concept ids, the food subset hierarchy, and display names
  in English, Spanish and Russian.

**This credit is a licence condition.** It must appear in the shipped
application's licences/acknowledgements screen. Removing it breaks the licence.

Two caveats recorded rather than assumed away:

- FAO holds copyright for AGROVOC content in its six official languages. English,
  Spanish and Russian are all inside that set, so this extract sits under a
  single licence. Content in **other** languages is copyright of the authoring
  institutions and may carry different terms -- adding a fourth language is a
  rights question, not just a product one.
- Some third-party summaries now describe AGROVOC as CC-BY 4.0 while FAO's FAQ
  still says CC-BY IGO 3.0. Both are attribution-only with no ShareAlike, so the
  obligation discharged above is the same either way, but the discrepancy should
  be confirmed with counsel before release.

## Wikidata -- CC0, no obligation

- Source: <https://www.wikidata.org/>
- Licence: **CC0 1.0 Universal** -- <https://creativecommons.org/publicdomain/zero/1.0/>
- Used for: colloquial and regional alternate names (the words people actually
  type) joined to AGROVOC via property **P8061 (AGROVOC ID)**.

Wikidata's CC0 dedication imposes **no** attribution requirement. The credit
above is voluntary good practice and may be reworded freely, unlike the AGROVOC
credit.

## Not used

Open Food Facts taxonomies were evaluated and **rejected**: they are ODbL, whose
share-alike terms would propagate to this derived table. Nothing in this
directory derives from an ODbL source.
"""


def emit(concepts: List[Concept], stats: dict, args) -> Tuple[str, int]:
    out_dir = args.out
    os.makedirs(out_dir, exist_ok=True)

    records = []
    for con in concepts:
        rec: dict = {"id": f"agrovoc:{con.cid}"}
        for lang in LANGUAGES:
            display = con.display.get(lang)
            matches = sorted(con.matches[lang], key=lambda s: (fold(s), s))
            if not display and not matches:
                continue
            entry: dict = {}
            if display:
                entry["display"] = display
            if matches:
                entry["match"] = matches
            rec[lang] = entry
        rec["sources"] = sorted(con.sources)
        records.append(rec)

    document = {
        "schema_version": SCHEMA_VERSION,
        "provenance": provenance(stats, args),
        "languages": list(LANGUAGES),
        "concepts": records,
    }

    data_path = os.path.join(out_dir, "food-synonyms.json")
    tmp = data_path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(document, fh, ensure_ascii=False, indent=1, sort_keys=False)
        fh.write("\n")
    os.replace(tmp, data_path)

    with open(os.path.join(out_dir, "ATTRIBUTION.md"), "w", encoding="utf-8") as fh:
        fh.write(ATTRIBUTION_MD)

    return data_path, os.path.getsize(data_path)


def apply_curated_terms_offline(path: str) -> int:
    """
    Applies MANUAL_TERMS to an on-disk food-synonyms.json in place, touching
    only the concepts it names and contacting neither AGROVOC nor Wikidata.

    A full run re-crawls both endpoints for all 997 subset concepts to change
    one reviewed string, and if either endpoint has drifted since the file's
    own provenance.generated_utc, the resulting diff mixes that drift in with
    the term being added -- which is unrelated noise a reviewer cannot easily
    separate from the actual change. This function cannot introduce that
    noise: it does not query anything, so nothing outside the named concepts'
    match lists can move.

    Idempotent by the same rule add_match uses elsewhere in this file: a term
    already present (case- and whitespace-folded, checked against the display
    string too) is left alone, so a second run changes nothing and reports 0.
    """
    with open(path, "r", encoding="utf-8") as fh:
        document = json.load(fh)

    by_id = {con["id"]: con for con in document["concepts"]}
    applied_utc = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    applied: List[dict] = []

    for cid, lang, term, reason in MANUAL_TERMS:
        full_id = f"agrovoc:{cid}"
        con = by_id.get(full_id)
        if con is None:
            print(f"  ! {full_id} ({lang}): concept not in this file", file=sys.stderr)
            continue
        entry = con.setdefault(lang, {})
        matches: List[str] = entry.setdefault("match", [])
        taken = {fold(m) for m in matches}
        display = entry.get("display")
        if display:
            taken.add(fold(display))
        if fold(term) in taken:
            continue
        matches.append(term)
        matches.sort(key=lambda s: (fold(s), s))
        applied.append({"concept": full_id, "lang": lang, "term": term, "reason": reason})

    if applied:
        document.setdefault("provenance", {}).setdefault("curated_terms_applied_offline", []).extend(
            {**row, "applied_utc": applied_utc} for row in applied
        )
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(document, fh, ensure_ascii=False, indent=1, sort_keys=False)
            fh.write("\n")
        os.replace(tmp, path)

    return len(applied)


# --------------------------------------------------------------------------
# Reporting
# --------------------------------------------------------------------------

def report(concepts: List[Concept], data_path: str, size: int, client: SparqlClient) -> None:
    print()
    print("=" * 68)
    print("RESULT")
    print("=" * 68)
    print(f"concepts emitted        {len(concepts)}")
    print()
    header = f"{'lang':6s}{'display':>10s}{'match terms':>14s}{'avg/concept':>13s}{'max':>6s}"
    print(header)
    print("-" * len(header))
    for lang in LANGUAGES:
        shown = sum(1 for c in concepts if c.display.get(lang))
        terms = sum(len(c.matches[lang]) for c in concepts)
        biggest = max((len(c.matches[lang]) for c in concepts), default=0)
        avg = terms / len(concepts) if concepts else 0.0
        print(f"{lang:6s}{shown:>10d}{terms:>14d}{avg:>13.2f}{biggest:>6d}")
    total_terms = sum(len(c.matches[l]) for c in concepts for l in LANGUAGES)
    print("-" * len(header))
    print(f"{'total':6s}{'':>10s}{total_terms:>14d}")
    print()
    print(f"output   {data_path}")
    print(f"size     {size:,} bytes ({size / 1024:.1f} KiB)")
    print(f"network  {client.requests_made} requests, {client.cache_hits} cache hits")


def show_examples(concepts: List[Concept], ids: Sequence[str]) -> None:
    index = {c.cid: c for c in concepts}
    print()
    print("=" * 68)
    print("SPOT CHECKS")
    print("=" * 68)
    for cid in ids:
        con = index.get(cid)
        if not con:
            print(f"{cid}: NOT IN SUBSET")
            continue
        print(f"\nagrovoc:{cid}")
        for lang in LANGUAGES:
            display = con.display.get(lang, "-")
            matches = sorted(con.matches[lang], key=lambda s: (fold(s), s))
            print(f"  {lang}  display={display!r}")
            print(f"      match={matches}")
        print(f"  sources={sorted(con.sources)}")


def print_plan() -> None:
    print("Subset plan -- no network access performed.\n")
    print(f"{'root':10s}{'gloss':22s}{'recorded closure':>18s}")
    print("-" * 50)
    total = 0
    for root_id, gloss, size in FOOD_ROOTS:
        total += size
        print(f"{root_id:10s}{gloss:22s}{size:>18d}")
    print("-" * 50)
    print(f"{'':32s}{total:>18d}  (sum with overlap; union is smaller)")
    print(f"\n{len(FOOD_ROOTS)} roots. Widen coverage by adding a row to FOOD_ROOTS.")


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------

def main(argv: Optional[List[str]] = None) -> int:
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    default_out = os.path.join(
        here, "mobile", "domain", "nutrition", "src", "commonMain", "resources", "synonyms"
    )
    default_cache = os.environ.get("SYNONYM_CACHE_DIR") or os.path.join(
        os.environ.get("TMPDIR", "/tmp"), "supernutrition-synonym-cache"
    )

    parser = argparse.ArgumentParser(
        description="Build-time generator for the en/es/ru food synonym table.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--out", default=default_out, help="output directory")
    parser.add_argument("--cache", default=default_cache, help="HTTP response cache directory")
    parser.add_argument("--rate", type=float, default=1.0,
                        help="minimum seconds between requests (default 1.0)")
    parser.add_argument("--batch", type=int, default=200,
                        help="concept ids per SPARQL VALUES block (default 200)")
    parser.add_argument("--refresh", action="store_true", help="ignore cached responses")
    parser.add_argument("--no-derived", action="store_true",
                        help="disable the isDerivedFrom source-organism alias tier")
    parser.add_argument("--no-manual", action="store_true",
                        help="disable the manually verified AGROVOC->Wikidata link tier")
    parser.add_argument("--no-curated", action="store_true",
                        help="disable the curated additive term tier (MANUAL_TERMS)")
    parser.add_argument(
        "--apply-curated-terms-offline", metavar="FILE",
        help=(
            "apply MANUAL_TERMS to an existing food-synonyms.json IN PLACE, "
            "with no network access at all, and exit. For adding one reviewed "
            "term without re-crawling both SPARQL endpoints for all 997 "
            "concepts -- a live run's diff would mix in whatever has drifted "
            "upstream since the file's own generated_utc, which is unrelated "
            "noise the term being added did not cause."
        ),
    )
    parser.add_argument("--ru-yo-variants", action="store_true",
                        help="also emit е-spellings of Russian terms containing ё")
    parser.add_argument("--max-concepts", type=int, default=0, help="cap subset size (0 = no cap)")
    parser.add_argument("--report-roots", action="store_true",
                        help="print per-root closure sizes and flag drift")
    parser.add_argument("--examples", default="",
                        help="comma-separated AGROVOC ids to spot-check, e.g. c_24000,c_9022")
    parser.add_argument("--dry-run", action="store_true", help="do not write output files")
    parser.add_argument("--print-plan", action="store_true",
                        help="print the subset plan and exit without network access")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    if args.print_plan:
        print_plan()
        return 0

    if args.apply_curated_terms_offline:
        applied = apply_curated_terms_offline(args.apply_curated_terms_offline)
        print(f"{applied} curated term(s) applied to {args.apply_curated_terms_offline}")
        print("no network access performed")
        return 0

    print(f"cache    {args.cache}")
    print(f"rate     {args.rate}s between requests")
    print(f"agent    {USER_AGENT}")
    print()

    client = SparqlClient(args.cache, args.rate, args.refresh, args.verbose)
    concepts, stats = build(client, args)

    if args.dry_run:
        print("\n[--dry-run] no files written")
        data_path, size = "(dry run)", 0
    else:
        data_path, size = emit(concepts, stats, args)

    report(concepts, data_path, size, client)
    if args.examples:
        show_examples(concepts, [e.strip() for e in args.examples.split(",") if e.strip()])
    return 0


if __name__ == "__main__":
    sys.exit(main())
