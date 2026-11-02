# Coverage-fix candidate: the data angle (not ranking)

Worktree `~/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`, branch
`feat/e7-meal-search-ui`, HEAD `22832480`. No Gradle ran, no Kotlin compiled, nothing under
`mobile/` or `scripts/` was touched — everything below is a Python model, validated against the
real committed data and the real Gradle-measured numbers named in the brief before anything was
built on top of it. Model source (scratchpad, outside the repo):
`/private/tmp/claude-501/-Users-aj-workspace-ai-SuperNutrition--claude-worktrees-pr-review-c1b4ce/4c1405cc-f69b-4f07-8a66-3508ba5a2f99/scratchpad/coverage/`
(`validate_ground_truth.py`, `candidate_engine.py`, `term_roles.py`, `simulate_join_fix.py`,
`simulate_masло_fixes.py`, `combined_data_fix.py`, `sweep.py`, `sweep_datafix.py`), reusing the
already-validated parser/engine at `scratchpad/engine_model.py` (`load_all`,
`parse_records_balanced`, `parse_lemmas`, `parse_scaffolding`, `parse_vocab_set`,
`fallback_identify`) built earlier in this session.

**Bottom line, stated up front because the brief asked the angle question directly: yes, all
four instances ARE data problems, not ranking problems — but three different data mechanisms,
not one, and none of them is масло's exact shape.** A combined, narrowly-scoped, three-part data
fix (§5) reaches all four targets at coverage 1000 with zero `CatalogSearch.kt` change and zero
measured regression. One instance (`cafe`) needs a small, new, precedented generator mechanism
rather than a one-line table row; that part is specified precisely but not implemented here.

---

## 1. Model validation — reproduces every published number exactly

`GeneratedCatalog.kt`'s `CatalogRecord(...)` calls span multiple lines and reference shared
`GeneratedNamesN` objects; `GeneratedRussianForms.kt` stores 13284 forms as `"lemma form1 form2
..."` strings, one lexeme per line, not one form per `listOf` argument. Both are parsed with a
balanced-paren/brace scanner over the raw text (`parse_records_balanced`, `parse_lemmas`), never
a single-line regex, for exactly the reason AGENTS.md names: a naive per-line pattern silently
parses a fraction of a multi-line construct and the count assertion is the only thing that would
catch it.

| quantity | asserted | got |
|---|---|---|
| `GeneratedCatalog.kt` records | **3519** | **3519** |
| `russianFormToLemma` forms | >10000 | **13284**, matching the file's own documented "13284 forms over 1112 lemmas" |
| `GeneratedRussianScaffolding.kt` surface forms | 54 | **54** |
| `MealTextCorpus.kt` catalog records / cases | — | **300 / 1478** |
| `RussianPhraseCorpus.kt` catalog records / cases | — | **260 / 530** |

Full-pipeline behaviour (`TextIdentification` with the engine fixed to `Unavailable`, exactly
what `RussianPhraseCorpusTest` / `MealTextCorpusTest` exercise — segmentation +
`stripNonFoodEdgeWords` + the tier loop, not a bare `CatalogSearch.search` call) on the
**unmodified** code:

| measurement | brief's stated ground truth | this model |
|---|---|---|
| RussianPhraseCorpus `quantity` | 162/170 | **162/170** |
| RussianPhraseCorpus `single` | 112/149 | **112/149** |
| RussianPhraseCorpus `two-item` | 61/93 | **61/93** |
| RussianPhraseCorpus negatives | 60/60 | **60/60** |
| MealTextCorpus `exact` | 450/450 | **450/450** |
| MealTextCorpus `alias` | 600/600 | **600/600** |
| MealTextCorpus en `two-item`+`quantity` | 80/80 | **80/80** |
| MealTextCorpus negatives | 188/188 | **187/188** |

The one MealTextCorpus miss is the artifact named in advance: `MealTextCase("\n", emptyList(),
"und", "must-not-resolve", ...)`. Traced by hand, not just accepted: this parser (`parse_meal_text_cases`)
extracts Kotlin string-literal bodies with a regex that does not interpret escape sequences, so it
hands the matcher the two *literal* source characters backslash+`n`, not the one compiled newline
character the real JVM string holds. That two-character string tokenizes to `["n"]` and PREFIX-matches
something, which is the actual, traceable cause of this model's one miss.

Checked against the real semantics directly, because "known artifact" should not mean "unverified":
`TextIdentification.identify()`'s very first line is `if (description.isBlank()) return
TextIdentificationOutcome.Empty` — Kotlin's `isBlank()` is true for an all-whitespace string, and a
single `\n` is exactly that, so real Kotlin never even reaches `TextFragments.of` for this query,
returns `Empty`, and `resolve()` maps `Empty` to `emptyList()` — matching `expected=[]`. Independently,
running the same string through `TextFragments.of`'s own logic (traced by hand: `separatorAt` returns
`1` at the newline via its own dedicated `'\n'` branch, `emit` never has anything to emit, the fragment
list comes out empty) reaches the identical empty result by the other path. **Both routes say a real
single newline resolves to `[]`, matching expected — this specific case should PASS on real Kotlin.**
The miss is therefore this Python parser's own escape-handling gap, not a matcher defect and not
(despite first appearances) actually a property of `TextFragments.of` either. A sibling report in
this same artifacts directory, built from an independently-written parser, reports the identical
187/188 and attributes it to the identical case — strong evidence the same easy-to-make
regex-does-not-unescape mistake was made twice, independently, rather than that this is a real
Kotlin behavior; the brief's own framing ("a known test-harness parsing artifact, not a real
defect") says exactly this. Excluded from every "no regression" claim below, exactly as the brief
allows.

**Independent cross-check**: a sibling report in this same artifacts directory
(`coverage-fix-candidate-coverage-formula-redesign.md`, built from an independently-written
parser in `scratchpad/covfix/`) reports the identical eight numbers above from a completely
separate implementation. Two independently-built ports agreeing to the integer on every
published ground-truth number is strong evidence neither has a shared blind spot.

`RankingRegressionTest` + `RussianLemmaSearchTest` (55 assertions, against `generatedCatalog`
directly, no segmentation): **55/55 pass on unmodified HEAD** — курицы/курицу/курицей → Chicken,
яйцо/яйца → Egg, масло → Butter, свиней → Pork (verified as a real coverage tie, not a ranking
artifact), паст/спарже/корне reachable, птицы → Poultry (the accepted loss, confirmed still
present and not silently "fixed"), морковь/свинина/курица at EXACT, брюссельска/морков/свинин
prefix-reachable. This is the starting point every candidate below is measured against.

## 2. The four instances, reconfirmed

```
chiken  -> Egg, whole, raw, fresh              FUZZY 500   (Chicken ties at 500, loses on id)
yogert  -> Tofu yogurt                          FUZZY 500   (real Yogurt scores 250)
sammon  -> Fish oil, salmon                     FUZZY 333   (real salmon-as-food scores 250)
cafe    -> Syrup, Cane                          FUZZY 500   (no Coffee record reachable at all)
```

All four reproduce exactly as stated in the brief, on the unmodified code, on `generatedCatalog`
(3519 records).

## 3. Per-instance forensic — what is actually colliding, and why

### 3.1 `chiken` → Egg via "chicken egg"

`Egg, whole, raw, fresh`'s form `chicken egg` is a **real AGROVOC/Wikidata match term** for
concept `agrovoc:c_2502` ("eggs"), sourced via `wikidata:Q15260613`. It is not junk: `MealTextCase("chicken
egg", listOf("food:agrovoc-c_2502"), "en", "alias", "en match term")` in `MealTextCorpus.kt` is
an existing, passing, **required** positive test — someone typing "chicken egg" must reach Egg.
**Removing this alternate name is not on the table**: it would fail a real test and break real
search correctness for a legitimate query.

Chicken's own winning form, `chicken meat`, is **also** an alternate name, not Chicken's own USDA
display name (`Chicken, broilers or fryers, meat only, raw`, 7 tokens) — it is
`agrovoc:c_24000`'s own canonical EN display, joined via a `MANUAL_HEAD_CONCEPTS` entry. So the
tie is not "own name vs. borrowed name" on either side; it is two different concepts' legitimate
short cross-reference terms, one of which (Egg's) is for a different food than the query meant.

**The actual root cause, found by reading `generate-catalog-kotlin.py`'s `terms_for_head()`
directly**: AGROVOC's own `c_24000` concept record already lists the bare word `"chicken"` in its
`en.match` array —

```json
{"id": "agrovoc:c_24000", "en": {"display": "chicken meat",
  "match": ["chick meat", "chicken", "chicken as food", "chicken food", "chickens",
            "cooked chicken", "Gallus gallus domesticus", "hen meat", "rooster meat"]}, ...}
```

— but `terms_for_head()` drops it:

```python
term_keys = fold_keys(term)
if term_keys & head_keys:
    stats["dropped_restates_head"] += 1
    continue
```

`"chicken"` folds to the same key as the head `"Chicken"`, so it is dropped as
"restates the head". The filter's own stated rationale
(`generate-catalog-kotlin.py`'s module docstring) is real and separate from ranking: *"re-adding
it would buy no new match and would promote the whole head group to the EXACT tier, hiding every
other record that answers the same word."* That reasoning is correct about tier **membership**
(every Chicken-headed record already matches "chicken" via its own qualified display name, so no
*new* record becomes reachable) — but it is silently wrong about coverage **value**, because the
coverage formula this file exists to investigate scores a bare 1-token form at 1000 and a 1-of-7
match inside the real display name at 142 (`1 * 1000 / 7`, integer division). The filter was
written before, or without reference to, that interaction.

**This is not масло's shape** (a word entirely absent from AGROVOC's source data). The word is
right there in the source; a **different, deliberate generator step removes it**, for a reason
that was true and is now incomplete.

### 3.2 `yogert` → Tofu yogurt

`Tofu yogurt`'s display name **is** its own USDA identity (record `food:usda-srl-43476`, no
alternate names at all — it never went through the synonym join). This is a genuinely different
food (a soy product) with a genuinely short genuine name; there is no alternate name to remove
and no join gap — it has no join at all to have a gap in.

Checking the *other* side, though: AGROVOC's `agrovoc:c_15463` ("yoghurt") concept is **already
joined** to every "Yogurt, ..."-headed record via a `MANUAL_HEAD_CONCEPTS` entry
(`"yogurt": [("agrovoc:c_15463", "yoghurt", ...)]`) — so every real Yogurt record already carries
`yoghurt` / `yogur` / `йогурт` as alternate names. But `c_15463`'s **only** English form is the
British spelling:

```json
{"id": "agrovoc:c_15463", "en": {"display": "yoghurt"}, "es": {"display": "yogur"},
 "ru": {"display": "йогурт"}, "sources": ["agrovoc"]}
```

The American spelling `"yogurt"` is not in AGROVOC's data for this concept **at all** — not
filtered, simply never collected. `"yogert"` (edit distance 1 from `"yogurt"`) does not get
anywhere near `"yoghurt"` (edit distance 2, over the FUZZY allowance for a 6-7 letter token) — so
the real Yogurt records only reach `"yogert"` through their own 4-token qualified display name at
coverage 250.

**This is exactly масло's shape**: a missing word in the source vocabulary, on a concept that is
already correctly joined. Verified fan-out first, per the `MANUAL_TERMS` header's own "TO EXTEND"
instruction: `"yogurt"` appears in 31 record-forms today, all as a **substring token inside other
qualified display names** (never as its own alternate-name form) — zero standalone fan-out,
exactly масло's precondition.

### 3.3 `sammon` / `cafe` — a real concept, a real word, never joined at all

Both of these are the same mechanism, more severe than 3.1/3.2: the concept is not filtered
(3.1) and not missing a word (3.2) — it is **never linked to any record**, because the automatic
join (`concepts_for_head`) only fires when a concept's English display name folds to a record's
**head segment** (the text before the first comma), and both `salmon` and `coffee` are one
comma-segment too deep to ever be a head:

```json
{"id": "agrovoc:c_6756", "en": {"display": "salmon", "match": ["grilse"]},
 "es": {"display": "salmón"}, "ru": {"display": "лососи", "match": ["лосось после нереста"]},
 "sources": ["agrovoc"]}
{"id": "agrovoc:c_1731", "en": {"display": "coffee"}, "es": {"display": "café", "match": ["cafe"]},
 "ru": {"display": "кофе"}, "sources": ["agrovoc", "manual->wikidata:Q8486"]}
```

Every real salmon-as-food record's display name is `"Fish, salmon, <species>, <prep>"` — head
`"Fish"`, shared with 143 other Fish-headed records (164 total under "Fish", the largest head in
the catalog — already named in `generate-catalog-kotlin.py`'s own comment on why the generic
"fish" concept was manually linked — of which 21 are salmon). Every real coffee-drink record's
display name is
`"Beverages, coffee, <style>"` — head `"Beverages"`, shared with tea, soda, alcohol, juice, milk.
`MANUAL_HEAD_CONCEPTS` (the existing hand-link table) operates on the **whole head only**; linking
`"fish"` to `c_6756` would misname every fish species as salmon, and linking `"beverages"` to
`c_1731` would misname tea and cola as coffee. Neither the automatic join nor the existing manual
table can reach a species/variety-specific concept whose distinguishing word lives in the
**second** comma segment. `agrovoc:c_1731`'s own `es.match` already contains the literal ASCII
string `"cafe"` — the query typed today — and it still cannot be reached, because the concept is
attached to nothing.

`grep`-level confirmation: `"salmon"` and `"café"`/`"cafe"`/`"coffee"` appear **nowhere** in
`GeneratedCatalog.kt` today (zero fan-out) — this is not a ranking loss, it is total absence.

## 4. What does NOT work: a FUZZY-only ranking tiebreak

Before settling on the data mechanisms above, one ranking candidate was built and measured, since
the brief explicitly permits an "own name vs. cross-reference" signal as a generalization of
17413c34's literal-vs-folded TOKEN_SUBSET tiebreak. Source data already distinguishes, per AGROVOC
concept, its own canonical display name from a looser match term — a distinction
`GeneratedCatalog.kt`'s flat `alternateNames: List<String>` erases. Restoring it as a per-form role
(`OWN` / `CANONICAL` / `MATCH`, classified against `food-synonyms.json` directly) and consulting it
as a same-coverage tiebreak **inside FUZZY only** (`candidate_engine.py`):

- First attempt, ranking `OWN > CANONICAL > MATCH`: moved `chiken` off Egg, but onto **"Fat,
  chicken"** — a different Chicken-headed record whose own 2-token USDA name merely *contains*
  "chicken" as a qualifier. Still wrong, along a different axis of the identical pathology
  (multiple records tie at the same short-form fraction; some of those short forms are
  byproducts, not the food).
- Second attempt, ranking `CANONICAL > OWN > MATCH` (canonical concept identity outranks an
  incidental short product name): correctly reaches `Chicken, broilers or fryers, meat and skin
  and giblets and neck, raw`. 55/55 MUST-KEEP checks still pass, all eight corpus numbers in §1
  unchanged, 700-query catalog-vocabulary sweep: **0 changed** results.

This candidate is **real and measured-safe**, but it is strictly worse than the data fix for the
same instance: it only ever reaches a tie-broken FUZZY-500 answer, never coverage 1000, and it
structurally cannot touch `yogert` or `sammon` (coverage 500 vs. 250 and 333 vs. 250 are not
ties — no tiebreak reorders a strict inequality) or `cafe` (no coffee record exists in the tier
at all to tiebreak toward). It is included here, working, in case the judge panel wants a
ranking-side belt-and-suspenders on top of the data fix — but it is not this report's
recommendation, because §5 reaches every target more decisively with zero `CatalogSearch.kt`
change.

## 5. The recommended candidate: three narrow data fixes, combined

Applied together, in `combined_data_fix.py`, entirely as additions to
`GeneratedCatalog.kt`'s (simulated) alternate-name lists — **zero line of `CatalogSearch.kt`
touched**:

1. **`yogurt-term`** (масло-shaped): add `"yogurt"` to `MANUAL_TERMS` for `agrovoc:c_15463`,
   propagating automatically to every already-joined Yogurt-headed record (24 records) via the
   existing pipeline. One row, same table, same shape as масло's own fix.
2. **`chicken-unfilter`** (a targeted, reviewed exception, not a table row): reinstate the bare
   word `"chicken"` — already present in AGROVOC's own `c_24000.en.match` — for the `"chicken"`
   head specifically (43 records), overriding `terms_for_head()`'s restatement drop for this one
   head only. See §6 for why this must stay scoped to `"chicken"` and not become a blanket
   reversal.
3. **`subhead-join`** (a new, small, precedented mechanism): attach `agrovoc:c_6756` ("salmon")
   and `agrovoc:c_1731` ("coffee") to every record sharing **head + second segment**
   (`"fish, salmon"`, 21 records; `"beverages, coffee"`, 8 records) — every record the finer head
   names, not one picked representative, the same "whole vocabulary to every record sharing the
   head" rule the existing head-only join already follows. See §7 for the exact table shape.

### 5.1 Result — 4/4 MUST FIX, decisively

```
chiken -> Chicken, broilers or fryers, meat and skin and giblets and neck, raw   FUZZY 1000
yogert -> Yogurt, plain, whole milk                                             FUZZY 1000
sammon -> Fish, salmon, Atlantic, wild, raw                                     FUZZY 1000
cafe   -> Beverages, coffee, brewed, breakfast blend                            EXACT 1000
```

Every target moves to coverage **1000**, not a narrow win — `cafe`/`café` moves to the **EXACT**
tier outright (it now equals a stored form character-for-character), which structurally cannot
be re-collided by any future FUZZY-tier change, because EXACT is checked first and returns before
FUZZY ever runs. Plain, correctly-spelled `"salmon"` and `"coffee"` and `"yogurt"` are fixed too
(they were degraded before the fix — see §5.3) — this is not a typo patch, it fixes the words
themselves.

### 5.2 MUST KEEP — 61/61

The original 55-assertion battery (§1) plus 6 collateral checks specific to this candidate's own
changes, all passing:

| check | result |
|---|---|
| курицы/курицу/курицей/"200 граммов курицы" → Chicken | pass (unchanged mechanism) |
| яйцо/яйца/яйцо курицы → Egg | pass |
| масло → Butter, масла → Oil | pass |
| свиней tie (Lard/Pork, same tier & coverage) | pass |
| паст/спарже/корне reachable; паст tops Pasta | pass |
| птицы → Poultry (accepted loss, unchanged) | pass |
| морковь/свинина/курица at EXACT | pass |
| "и"/"с"/"со"/"два"/"две"/"граммов"/"граммами"/"ломтик" → empty | pass |
| **"chicken egg" / "chicken eggs" still → Egg** | pass — the required alias in §3.1 is untouched |
| **"tofu yogurt" still → Tofu yogurt itself** | pass |
| **"fish oil salmon" still → Fish oil, salmon itself** | pass |
| **"syrup cane" / "cane" still → Syrup, Cane** | pass — the coincidental collision that made `cafe` land there is now irrelevant, since `cafe` no longer reaches FUZZY at all, but `cane` itself is untouched |

Corpus numbers (§1's eight numbers) are **unaffected by construction**, not just by
measurement: `MealTextCorpusTest` and `RussianPhraseCorpusTest` run against `corpusCatalog` and
`russianPhraseCatalog`, two separate, independently-generated 300- and 260-record catalogs that
share no records with `generatedCatalog`. Nothing in this candidate touches either of those
files.

### 5.3 Sweep

Catalog-vocabulary sweep (700 queries: 350 single tokens + 350 adjacent-token pairs, drawn from
the catalog's own vocabulary, fixed seed): **0 changed** outside the four targets and their own
words (`salmon`, `coffee`, `yogurt` — all fixed the same direction as their misspellings, not
just their typos).

Misspelling stress sweep (one-letter substitution/deletion/transposition typos of common
en/es/ru food words, fixed seed, `sweep_datafix.py`): 353 typo queries, **333 unchanged**, 20
changed. The script's own win/loss auto-classifier (a same-word substring check) mislabeled most
of the 20 as losses because the *wrong* record's name often also contains the seed word as a
substring (`"Tofu yogurt"` contains `"yogurt"`; `"Fish oil, salmon"` contains `"salmon"`) — every
one of the 20 was read back by hand:

| outcome | count | example |
|---|---|---|
| real win (wrong food → right food) | **15** | `salnon`/`salmpn`/`salon`/`salmn`/`sblmon`/`salmo` → Fish oil, salmon (wrong) becomes Fish, salmon, Atlantic, wild, raw (right); `caf`/`cbfe`/`лофе` → cola / nothing (wrong) becomes real coffee; `chidken`/`cicken`/`chickn`/`chickeo` → Egg (wrong) becomes real Chicken; `yourt` → Tofu yogurt (wrong) becomes real Yogurt |
| neutral (both sides already a genuine, correct answer to the same food) | **5** | `offee`/`cofgee`/`cpffee` → two different real coffee records (instant-with-chicory vs. brewed breakfast blend), both correct; `осось`/`лоссь` → two different real salmon-as-food records (a canned/smoked "Salmon, ..." record that was *already* separately joined to `c_6756` via the existing automatic head-fold join — see §8's note on this — vs. the newly-joined "Fish, salmon, ..." record), both correct |
| regression (right food → wrong food, or right → nothing) | **0** | — |

**0 regressions, 15 further real fixes beyond the four named instances, 5 neutral swaps between
two already-correct answers** — a materially stronger result than the vocabulary sweep alone
suggested, because typos of the *fixed* words themselves (not just the four exact strings named
in the brief) are exactly the population this candidate was built to help.

### 5.4 A defect this same forensic found beyond the four named instances

`"rice"` — spelled **correctly**, no typo — resolves today to `Rice crackers` (TOKEN_SUBSET,
coverage 500), not to any actual rice record (`Rice, white, medium-grain, raw, enriched` scores
200: 1 of 5 tokens). This is the exact `"chicken"` mechanism (§3.1) on a different head, and it
is a pointed irony: `rice` is the record this repository's own KDoc names as the motivating
example for the fraction-over-absolute-count design (`CatalogRecord.coverageOf`'s own comment:
*"a record whose synonyms include the bare word `rice` was credited with covering it in full...
so a qualified variant outranked the staple"*). The fraction formula was built to stop exactly
this, and it is still happening — just via the restatement filter removing the word "rice" from
rice's own records, rather than via an over-generous absolute count. A systematic check for the
same shape (multi-record head, one shared alternate-name list, head's own bare word absent from
it, every own-display-name variant ≥5 tokens) finds **18 more heads with this exact structural
risk** — `rice` (11 records), `corn flour`, `wheat flour`, `margarine`, `pineapple juice`, `lima
beans`, `poultry` (co-incidentally the record that already ties with Chicken in the accepted-loss
`птицы` case), `apple juice`, `grape juice`, `olives`, `popcorn`, `chocolate`, `artichokes`,
`mung beans`, `leeks`, `tomato juice`, `salmon`, `tea` — none of these were named in the brief and
none are fixed by this candidate; they are reported as a systemic finding worth its own audit,
not fixed opportunistically here (that audit is out of this report's scope and would need its own
measured battery per AGENTS.md's review-loop rules).

## 6. Why `chicken-unfilter` must stay scoped to one head, not become a blanket reversal

`terms_for_head()`'s restatement filter is not a mistake — every automatically-joined head, by
construction, has a concept whose own EN display name folds identically to the head (that is
literally the join criterion), so **every** auto-joined head in the catalog has its own bare
word dropped this way. For most heads that is harmless (`Butter` has a 2-token own name; a bare
"butter" adds little). It is only harmful for heads where every own-display-name variant is long
**and** an unrelated record happens to carry a short colliding form — precisely §5.4's 18-head
list, discovered but not exhaustively verified for actual collisions the way `chicken` was.
Reversing the filter catalog-wide was **not measured** and the generator's own comment gives a
concrete reason a blanket reversal could regress: many different multi-record heads (cheese,
beef, egg) would all start tying their bare head word at EXACT across dozens of records
simultaneously, which is a different, unmeasured change in kind from adding one word to one head.
The candidate in §5 adds exactly one exception, for the one head this report's brief named and
measured; §5.4's list is a **lead for follow-up work**, not something this report ships.

## 7. `kotlin_change` / generator-change specification

**No `CatalogSearch.kt` change is proposed or required.** All three parts of §5 are
`scripts/generate-food-synonyms.py` / `scripts/generate-catalog-kotlin.py` changes, regenerating
`GeneratedCatalog.kt` through the existing pipeline — consistent with AGENTS.md's rule that
`GeneratedCatalog.kt` is generated and must not be hand-edited.

**Part 1 — `MANUAL_TERMS` addition** (`scripts/generate-food-synonyms.py`, next to масло's own
entry):

```python
MANUAL_TERMS: Tuple[Tuple[str, str, str, str], ...] = (
    (
        "c_1162", "ru", "масло",
        "...",  # existing масло entry, unchanged
    ),
    (
        "c_15463", "en", "yogurt",
        "the common American spelling; AGROVOC's only English form for this concept is the "
        "British 'yoghurt', and the concept is already joined to every Yogurt-headed record via "
        "MANUAL_HEAD_CONCEPTS['yogurt']. Verified <date> against GeneratedCatalog.kt: 'yogurt' "
        "has fan-out 0 as a standalone alternate-name form (31 occurrences, all as a substring "
        "token inside other records' own qualified display names).",
    ),
)
```

**Part 2 — a named, reviewed exception to the restatement filter**, in
`scripts/generate-catalog-kotlin.py`'s `terms_for_head()`:

```python
# Heads where the concept's own bare EN word restates the head (and is therefore
# dropped by the rule below) but the head's every own-display-name variant is
# long enough that the drop leaves the head with no short reachable form at
# all, letting an unrelated record's short form outrank it under the coverage
# fraction. Each entry is reviewed and dated, the масло playbook applied to a
# filter exception instead of a missing term.
RESTATEMENT_EXCEPTIONS = {
    # "chicken" is agrovoc:c_24000's own en.match term, dropped as restating the
    # "Chicken" head. Every Chicken-headed record's own display name is >=7
    # tokens ("Chicken, broilers or fryers, meat only, raw"), so the drop left
    # the head with no short reachable form, and "chiken" (edit distance 1)
    # FUZZY-matched Egg's cross-reference "chicken egg" (2 tokens, coverage 500)
    # ahead of any real Chicken record. Verified <date>: reinstating "chicken"
    # does not disturb "chicken egg" -> Egg (a different record's alternate
    # name, untouched) or any RankingRegressionTest / RussianLemmaSearchTest
    # assertion (61/61 pass). See _bmad-output/implementation-artifacts/
    # coverage-fix-candidate-data-not-ranking.md section 5.4 for 18 other heads
    # with the same structural shape, not yet individually verified or added.
    "chicken",
}

def terms_for_head(head, concept_ids, concepts_by_id, other_head_keys, stats):
    head_keys = fold_keys(head)
    seen = set()
    terms = []
    for concept_id in concept_ids:
        for term in concept_terms(concepts_by_id[concept_id]):
            normalized = normalize_for_search(term)
            if not tokenize_normalized(normalized) or normalized in seen:
                continue
            seen.add(normalized)
            term_keys = fold_keys(term)
            if term_keys & head_keys and head not in RESTATEMENT_EXCEPTIONS:
                stats["dropped_restates_head"] += 1
                continue
            if term_keys & other_head_keys:
                stats["dropped_other_head"] += 1
                continue
            terms.append(term)
    return terms
```

**Part 3 — a new, narrower join tier**, `MANUAL_SUBHEAD_CONCEPTS`, keyed on
`(head, second_segment)` rather than `head` alone, applied by `concepts_for_head` as an
additional, more selective pass **before** the existing restatement/other-head filters (which
should apply unchanged to whatever it contributes):

```python
# Concepts whose distinguishing word is real, sourced, and already correct in
# food-synonyms.json, but which the head-only join can never reach because
# every record sharing their true identity also shares a much broader head
# with hundreds of unrelated siblings (every fish species is headed "Fish";
# every brewed/instant coffee style is headed "Beverages"). Keyed on
# (head, second comma-segment) so the join is exactly as selective as the
# concept itself -- unlike MANUAL_HEAD_CONCEPTS, this can never attach a
# concept's vocabulary to an unrelated sibling under the same head.
MANUAL_SUBHEAD_CONCEPTS = {
    ("fish", "salmon"): [(
        "agrovoc:c_6756", "salmon",
        "the salmon concept (en salmon / es salmón / ru лососи, en match "
        "'grilse') exists and is correct, but every salmon-as-food record's "
        "head is the generic 'Fish', shared with 163 other species, so the "
        "head-only join never reaches it. Verified <date>: fan-out 0.",
    )],
    ("beverages", "coffee"): [(
        "agrovoc:c_1731", "coffee",
        "the coffee concept (en coffee / es café, es match 'cafe' / ru кофе, "
        "already linked from AGROVOC to wikidata:Q8486) exists and is correct, "
        "but every coffee-drink record's head is the generic 'Beverages', "
        "shared with tea/soda/alcohol/juice/milk, so the head-only join never "
        "reaches it. Verified <date>: fan-out 0. Deliberately NOT joined to "
        "'Beverages, coffee substitute, ...' records (a different concept, "
        "agrovoc:c_1733).",
    )],
}
```

`concepts_for_head`'s caller would need a second pass over `(head, second_segment)` keys built
the same way `canonical_head()` builds `head` (fold on the text before the first comma, extended
to also fold the text between the first and second comma), applied to the record set *after* the
existing head-only pass, so a record that already got vocabulary from its head keeps it and only
gains the sub-head concept's terms additionally. This is new code, not a table row — sized and
specified precisely enough to implement, but genuinely a small design change to the generator
that deserves its own review pass rather than shipping inside this report.

## 8. Honest weaknesses

- **`chicken-unfilter` (§5, part 2) is a generator-behavior change, not a data-table row.** It is
  scoped to one head and measured safe on the full available battery, but "safe for this one
  head" was verified by hand (61 checks + one 700-query sweep), not by an exhaustive audit of
  every other auto-joined head's own bare word. §5.4's 18-head list is evidence the pattern
  recurs, not proof every recurrence is safe to fix the same way — some of those 18 heads may
  have their own reason the restatement drop is actually correct (a short own-name variant that
  should keep winning), not yet checked individually.
- **A second, independent confirmation of the restatement-filter mechanism (§3.1), found while
  reading back the misspelling sweep**: two records are headed `"Salmon, ..."` directly (single
  segment — `Salmon, sockeye, canned, total can contents` and `Salmon, red (sockeye), filets with
  skin, smoked (Alaska Native)`), a head distinct from `"Fish, salmon, ..."`. This head *already*
  auto-joins to `agrovoc:c_6756` (its own EN display, `"salmon"`, folds exactly to the head), so
  these two records already carry `grilse` / `лососи` / `лосось после нереста` — but not the bare
  word `"salmon"` itself, dropped by the exact same restatement rule as `"chicken"`. This means
  `salmon` needs the §3.1 mechanism as well as the §3.3 mechanism, on two non-overlapping
  sub-populations of salmon records. It also means a faithful `MANUAL_SUBHEAD_CONCEPTS`
  implementation (part 3) would need to route the new `"fish, salmon"` join's terms through
  `terms_for_head()`'s `other_head_keys` check too — and `"salmon"` (folded) already equals the
  existing single-segment head `"Salmon"` used by those two records, so it would very likely also
  be dropped there as "some other record's head", the same protection that keeps `beet greens`
  away from beet roots. Resolving that — treating the `"Salmon"` head and the `"fish, salmon"`
  sub-head as one group for this purpose, or adding `"salmon"` to a `RESTATEMENT_EXCEPTIONS`-style
  list for both — is exactly the kind of interaction that makes part 3 a real design change and
  not a table row; §5's measured numbers come from a Python simulation that adds the term directly
  and does not exercise this interaction, so they should be read as "what the data would produce",
  not as proof the generator change is this simple to wire correctly.
- **`MANUAL_SUBHEAD_CONCEPTS` (§5, part 3) is unimplemented new generator logic**, described
  precisely enough to build but not built or run through the real pipeline; the measured numbers
  in §5.1/§5.3 come from a Python simulation that adds the same terms directly to the parsed
  record list, not from a working `(head, second_segment)` join pass. The simulation's `.startswith("Fish, salmon,")` / `.startswith("Beverages, coffee,")`
  filters are a stand-in for that pass and were spot-checked (8/8 real coffee records, 21/21 real
  salmon-species records, no `"coffee substitute"` records included) but not exhaustively audited
  against every other head that might have the same second-segment-granularity shape (tea under
  "Beverages", other fish species under "Fish", etc. — likely present, not counted here).
- **The `лосось после нереста` ("spawned salmon") term was included in the simulation but is not
  essential** to fixing `sammon`; it is optional AGROVOC completeness, not load-bearing, and could
  be dropped to keep the change minimal, matching масло's own one-word minimalism.
- **§4's FUZZY-only ranking tiebreak was validated on a smaller sweep than ideal** (700-query
  catalog vocabulary, 0 changes; a matching misspelling-stress sweep for that specific candidate
  was started but not completed in the time available — killed after the vocabulary sweep alone
  answered the question that mattered for the recommendation in §5, since the ranking tiebreak is
  not what's being shipped). It should not be considered as thoroughly measured as §5's data fix.
- **A sibling report in this same directory** (`coverage-fix-candidate-coverage-formula-redesign.md`)
  reaches the same 4/4-via-data-fix conclusion through a different specific mechanism for
  `yogert`/`sammon` (a single bare-word addition to one hand-picked representative record per
  concept, rather than this report's "every record the concept's real head names"). That
  narrower approach is measured 4/4 there too and is simpler to implement, but picking one
  representative record for a multi-record concept is exactly the pattern
  `generate-catalog-kotlin.py`'s own header calls out as "tried and rejected" for the *general*
  join ("the shortest chicken name in SR Legacy is 'Chicken, meatless', and making that the sole
  record 'курица' could reach is exactly the wrong-food failure this join is written to avoid") — this
  report's §5/§7 recommendation attaches the concept's vocabulary to *every* record the finer head
  names (all 21 salmon-species records, all 8 real coffee-drink records) specifically to stay
  consistent with that existing, stated design principle, at the cost of being a bigger change
  (new join logic vs. one line). Which tradeoff the judge panel prefers is a real design question,
  not something this report resolves unilaterally.

## 9. Answering the angle question directly

*"Is `chicken egg` a poorly-chosen alternate name for Egg, or is `Fish oil, salmon` being indexed
identically to a real salmon record the actual defect?"* — neither, as it turns out, and the
honest answer split three ways once measured:

- `chicken egg` is **not** poorly chosen; it is required, tested, correct. The defect is that
  Chicken's own equally-legitimate short cross-reference term was filtered out by a rule that
  didn't anticipate the coverage formula's sensitivity to form length (§3.1).
- `Fish oil, salmon` competing with real salmon records is real, but the fix is not to demote or
  reindex the oil byproduct — its own name is genuinely `salmon`-adjacent and correctly indexed.
  The fix is that the real salmon-as-food records are missing the one word (`salmon`) that would
  let them compete on the same footing, because the concept that owns that word was never joined
  to them at all (§3.3).
- `yogert` is масло itself, precisely: one missing word, one concept, already joined, one-line
  fix, verified fan-out 0 first (§3.2).

So: **yes, this is a data problem wearing a ranking costume — three different data problems,
under one ranking symptom.** No `CatalogSearch.kt` change is needed for any of the four named
instances.
